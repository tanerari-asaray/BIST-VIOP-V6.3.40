package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Opportunity
import java.time.Instant
import java.time.ZoneId

data class BistPublicationAssessment(val score: Int, val confirmations: Set<String>,
    val averageTurnoverTry: Double?, val riskFactor: Double, val eligible: Boolean,
    val reasons: List<String>, val parts: Map<String, Int>,
    val profileId: String = "BIST-INTRADAY-CONSERVATIVE-670-AUDIT1",
    val model: String? = null, val rs20Pct: Double? = null, val sma200: Double? = null,
    val return5Pct: Double? = null, val return20Pct: Double? = null) : java.io.Serializable

/** Independent daily trade publication criteria; MTF count is NOT a substitute for 3/6 evidence. */
object BistPublicationPolicy {
    const val MIN_SCORE = 75
    const val MIN_RR = 1.5
    const val MIN_AVERAGE_TURNOVER_TRY = 30_000_000.0
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun day(time: Long) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    data class Evidence(val trend: Boolean, val price: Boolean, val volume: Boolean, val relativeStrength: Boolean,
        val momentum: Boolean, val market: Boolean, val marketKnown: Boolean, val neutralMarket: Boolean,
        val clv: Boolean, val volatility: Boolean, val space: Boolean, val news: Boolean,
        val averageTurnoverTry: Double?, val rr: Double?, val freshSignal: Boolean,
        val gapPct: Double?, val excessiveRsi: Boolean,
        val newsRiskKnown: Boolean = false, val criticalNewsRisk: Boolean = false, val obstacleTooNear: Boolean = false)

    fun assess(x: Evidence): BistPublicationAssessment {
        val confirmations = listOf("TREND" to x.trend,"PRICE" to x.price,"VOLUME" to x.volume,
            "RS" to x.relativeStrength,"MOMENTUM" to x.momentum,"MARKET" to x.market)
            .filter { it.second }.map { it.first }.toSet()
        val parts = linkedMapOf("Market" to if (x.market) 10 else if (x.neutralMarket) 5 else 0,
            "Trend" to if (x.trend) 15 else 0, "Price" to if (x.price) 15 else 0,
            "Volume" to if (x.volume) 15 else 0, "RS" to if (x.relativeStrength) 15 else 0,
            "Momentum" to if (x.momentum) 10 else 0, "CLV" to if (x.clv) 5 else 0,
            "Risk" to if (x.volatility) 5 else 0, "Space" to if (x.space) 5 else 0,
            "News" to if (x.news) 5 else 0)
        val score=parts.values.sum()
        val reasons=buildList {
            if(!x.freshSignal)add("LIVE_SIGNAL_UNVERIFIED")
            if(!x.newsRiskKnown)add("NEWS_RISK_UNVERIFIED")
            if(x.criticalNewsRisk)add("CRITICAL_NEWS_RISK")
            if(x.obstacleTooNear)add("WAIT_OBSTACLE_WITHIN_8_PERCENT")
            if(score<MIN_SCORE)add("SCORE_BELOW_75")
            if(confirmations.size<3)add("INDEPENDENT_CONFIRMATIONS_BELOW_3")
            if(x.averageTurnoverTry==null || !x.averageTurnoverTry.isFinite() || x.averageTurnoverTry<MIN_AVERAGE_TURNOVER_TRY)add("DAILY_TURNOVER_BELOW_30M_OR_UNKNOWN")
            if(x.rr==null || !x.rr.isFinite() || x.rr<MIN_RR)add("RR_BELOW_1_5_OR_UNKNOWN")
            if(!x.volume)add("WAIT_VOLUME_CONFIRMATION")
            if(!x.marketKnown)add("MARKET_REGIME_UNKNOWN")
            if(x.gapPct?.let { kotlin.math.abs(it)>5.0 }==true)add("WAIT_GAP_OVER_5_PERCENT")
            if(x.excessiveRsi || !x.volatility)add("WAIT_EXCESSIVE_RSI_OR_VOLATILITY")
        }
        return BistPublicationAssessment(score, confirmations, x.averageTurnoverTry,
            if(x.market) 1.0 else if(x.neutralMarket) 0.5 else 0.25, reasons.isEmpty(), reasons, parts)
    }

    fun evaluate(item: Opportunity, dailyRaw: List<Candle>, benchmark: MarketRegimeEngine.Result?,
                 newsConfirmedAt: Long? = null, newsRisk: BistNewsRisk = BistNewsRisk()): BistPublicationAssessment {
        val direction=item.direction.uppercase(java.util.Locale.ROOT)
        val long=direction=="LONG"
        val decision=item.candles.lastOrNull()
        val asOf=decision?.timestamp ?: 0L
        val prior=OhlcvResampler.sanitize(dailyRaw).filter { day(it.timestamp)<day(asOf) }.takeLast(20)
        val turnover=prior.takeIf { it.size==20 }?.map { it.close*it.volume }?.average()
        val t=item.technical
        val trend=if(long) t.ema20!=null && t.ema50!=null && t.ema200!=null && item.price>t.ema20 && t.ema20>t.ema50 && t.ema50>t.ema200
            else t.ema20!=null && t.ema50!=null && t.ema200!=null && item.price<t.ema20 && t.ema20<t.ema50 && t.ema50<t.ema200
        val previous=item.candles.dropLast(1).takeLast(20)
        val breakout=previous.size==20 && if(long) item.price>previous.maxOf { it.high } else item.price<previous.minOf { it.low }
        val pullback=trend && t.ema20!=null && t.atr14!=null && kotlin.math.abs(item.price-t.ema20)<=t.atr14 &&
            decision?.let { if(long)it.close>it.open else it.close<it.open }==true
        val momentum=t.rsi14?.let { if(long)it in 50.0..70.0 else it in 30.0..50.0 }==true &&
            t.macd!=null && t.macdSignal!=null && if(long)t.macd>t.macdSignal else t.macd<t.macdSignal
        val includeT = item.analysisTimeframeMinutes == 1440
        fun available(c: Candle) = if(includeT) day(c.timestamp)<=day(asOf) else day(c.timestamp)<day(asOf)
        val knownBenchmark = benchmark?.let { MarketRegimeEngine.evaluate(it.benchmarkSymbol, it.candles.filter(::available)) }
        val bench=knownBenchmark?.candles.orEmpty().associateBy { day(it.timestamp) }
        val stockDays=OhlcvResampler.sanitize(dailyRaw).filter { available(it) && day(it.timestamp) in bench }.takeLast(21)
        val relative=if(stockDays.size==21) {
            val first=stockDays.first();val last=stockDays.last()
            val b0=bench[day(first.timestamp)]!!.close;val b1=bench[day(last.timestamp)]!!.close
            last.close/first.close-b1/b0
        } else null
        val regime=knownBenchmark?.regime
        val market=regime==(if(long) MarketRegimeEngine.Regime.TREND_UP else MarketRegimeEngine.Regime.TREND_DOWN)
        val clv=decision?.let { if(it.high>it.low) (it.close-it.low)/(it.high-it.low) else null }
        val rr=item.riskPlan?.rr1
        val gap=dailyRaw.lastOrNull { day(it.timestamp)==day(asOf) }?.open?.let { open ->
            prior.lastOrNull()?.close?.takeIf { it>0 }?.let { (open/it-1)*100 } }
        val evidence = Evidence(trend,breakout||pullback,(t.volumeRatio?:0.0)>=1.5,
            relative?.let { if(long)it>0 else it<0 }==true,momentum,market,
            regime!=null && regime!=MarketRegimeEngine.Regime.UNKNOWN && regime!=MarketRegimeEngine.Regime.UNCERTAIN,
            regime==MarketRegimeEngine.Regime.SIDEWAYS,clv?.let { if(long)it>=0.8 else it<=0.2 }==true,
            t.atr14?.let { it/item.price*100<=5.0 }==true && item.riskScore<=60,rr?.let { it>=MIN_RR }==true,
            newsRisk.knownFor(item.symbol, asOf) && newsRisk.state == "CLEAR",turnover,rr,
            OpportunityTrendPresentation.from(item).verifiedSignal,gap,
            t.rsi14?.let { if(long)it>=75 else it<=25 }==true,
            newsRiskKnown = newsRisk.knownFor(item.symbol, asOf),
            criticalNewsRisk = newsRisk.state == "CRITICAL")
        val daily = if (item.analysisTimeframeMinutes == 1440)
            BistDailyProfile.evaluate(item.candles, knownBenchmark?.candles.orEmpty(), long, rr) else null
        val effective = daily?.let { evidence.copy(trend=it.trend, price=it.structure, momentum=it.momentum,
            clv=it.clv, volatility=it.volatility, space=it.space, market=it.market, marketKnown=it.marketKnown,
            neutralMarket=it.neutralMarket, excessiveRsi=it.excessive, obstacleTooNear=it.obstacleTooNear) } ?: evidence
        return assess(effective).copy(profileId=if(daily!=null)BistDailyProfile.ID else "BIST-INTRADAY-CONSERVATIVE-670-AUDIT1",
            model=daily?.model, rs20Pct=relative?.times(100.0), sma200=daily?.sma200,
            return5Pct=daily?.return5Pct, return20Pct=daily?.return20Pct)
    }
}
