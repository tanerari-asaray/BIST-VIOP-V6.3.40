package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.Candle
import kotlin.math.abs

/** Versioned daily-only rules. Intraday bars must never be called 20 trading days. */
object BistDailyProfile {
    const val ID = "BIST-DAILY-670-AUDIT1"
    data class Result(val trend: Boolean, val structure: Boolean, val momentum: Boolean,
        val clv: Boolean, val volatility: Boolean, val space: Boolean, val obstacleTooNear: Boolean,
        val market: Boolean, val marketKnown: Boolean, val neutralMarket: Boolean,
        val excessive: Boolean, val model: String?, val sma200: Double?, val return5Pct: Double?, val return20Pct: Double?)

    fun evaluate(raw: List<Candle>, benchmarkRaw: List<Candle>, long: Boolean, rr: Double?): Result {
        val bars = OhlcvResampler.sanitize(raw)
        val benchmark = OhlcvResampler.sanitize(benchmarkRaw)
        val close = bars.lastOrNull()?.close
        val t = TechnicalAnalyzer.analyze(bars)
        val prior = TechnicalAnalyzer.analyze(bars.dropLast(5))
        val r5 = if (bars.size >= 6) (bars.last().close / bars[bars.lastIndex-5].close - 1.0)*100 else null
        val r20 = if (bars.size >= 21) (bars.last().close / bars[bars.lastIndex-20].close - 1.0)*100 else null
        val sma200 = bars.takeIf { it.size >= 200 }?.takeLast(200)?.map { it.close }?.average()
        val e20=t.ema20; val e50=t.ema50; val old20=prior.ema20
        val trend = close!=null && e20!=null && e50!=null && old20!=null && sma200!=null &&
            if (long) close>e20 && e20>e50 && e20>old20 else close<e20 && e20<e50 && e20<old20
        val history=bars.dropLast(1)
        val breakout=close!=null && listOf(10,20).any { n -> history.size>=n &&
            if(long) close>history.takeLast(n).maxOf { it.close } else close<history.takeLast(n).minOf { it.close } }
        val last=bars.lastOrNull();val atr=t.atr14;val rsi=t.rsi14
        val atrPct=if(close!=null && atr!=null) atr/close*100 else null
        val volumeContracting=history.size>=6 && history.takeLast(3).map { it.volume }.average()<history.dropLast(3).takeLast(3).map { it.volume }.average()
        val pullback=trend && last!=null && e20!=null && atr!=null && abs(last.close-e20)<=atr && volumeContracting &&
            rsi!=null && rsi in 45.0..60.0 && if(long) last.close>last.open else last.close<last.open
        val momentum=rsi!=null && r5!=null && r20!=null && if(long)
            rsi in 50.0..68.0 && r5>0 && r20>0 else rsi in 32.0..50.0 && r5<0 && r20<0
        val continuation=trend && momentum && (t.volumeRatio ?: 0.0)>1.0
        val clv=last?.let { if(it.high>it.low) (it.close-it.low)/(it.high-it.low) else null }
        val levels=if(close!=null) ExtendedStockEvidence.ladder(bars,close) else null
        val obstacle=if(long) levels?.resistances?.firstOrNull() else levels?.supports?.firstOrNull()
        val distance=if(close!=null && obstacle!=null) abs(obstacle-close)/close*100 else null
        val bt=TechnicalAnalyzer.analyze(benchmark);val bp=TechnicalAnalyzer.analyze(benchmark.dropLast(5))
        val bclose=benchmark.lastOrNull()?.close;val be20=bt.ema20;val be50=bt.ema50;val beold=bp.ema20
        val br20=if(benchmark.size>=21) benchmark.last().close/benchmark[benchmark.lastIndex-20].close-1 else null
        val known=bclose!=null && be20!=null && be50!=null && beold!=null && br20!=null
        val bull=known && bclose!!>be20!! && be20>be50!! && be20>beold!! && br20!!>0
        val bear=known && bclose!!<be20!! && be20<be50!! && be20<beold!! && br20!!<0
        val model=when { breakout && trend -> if(long) "BREAKOUT" else "BREAKDOWN"
            pullback -> if(long) "PULLBACK_REVERSAL" else "PULLBACK_REJECTION"
            continuation -> "MOMENTUM_CONTINUATION"; else -> null }
        return Result(trend,model!=null,momentum,clv?.let { if(long)it>=0.70 else it<=0.30 }==true,
            atrPct?.let { it>0 && it<=6.0 }==true, distance!=null && distance>=8.0 && rr!=null && rr>=1.5,
            distance!=null && distance<8.0, if(long)bull else bear,known,known && !bull && !bear,
            (rsi?.let { if(long)it>75 else it<25 }==true) || (r5?.let { abs(it)>15 }==true), model,sma200,r5,r20)
    }
}
