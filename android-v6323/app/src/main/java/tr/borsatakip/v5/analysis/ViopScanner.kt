package tr.borsatakip.v5.analysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import tr.borsatakip.v5.data.BackendProvider
import tr.borsatakip.v5.data.MarketDataProvider
import tr.borsatakip.v5.data.MtfHistoryCache
import tr.borsatakip.v5.data.ViopStrategyDecisionService
import tr.borsatakip.v5.data.ViopContractSelector
import tr.borsatakip.v5.model.ScanRunStatus
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.RiskPlan
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.CalculationEngineMode
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.model.ViopOpportunity
import tr.borsatakip.v5.model.ViopScanError
import tr.borsatakip.v5.model.ViopScanProgress
import tr.borsatakip.v5.model.ViopScanResult
import tr.borsatakip.v5.analysis.v531.V531CalculationEngine
import tr.borsatakip.v5.analysis.v540.V540FreshnessPolicy
import tr.borsatakip.v5.analysis.v540.V540LiquidityEngine
import tr.borsatakip.v5.analysis.v531.V531CalculationInput
import tr.borsatakip.v5.analysis.v531.V531DataQualityInput
import tr.borsatakip.v5.analysis.v531.V531Direction
import tr.borsatakip.v5.analysis.v531.V531ContextEngine
import tr.borsatakip.v5.analysis.v531.ScoringConfig
import java.time.LocalDate
import java.util.UUID
import tr.borsatakip.v5.data.ScanProfile
import tr.borsatakip.v5.data.ScanTelemetry
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class ViopScanner(
    private val backend: BackendProvider,
    private val marketProvider: MarketDataProvider? = null,
    private val strategyDecisionService: ViopStrategyDecisionService? = null
) {
    companion object {
        const val MIN_HISTORY_BARS = 220
        const val MIN_VOLUME = 1.0
        const val MIN_OPEN_INTEREST = 1L
        const val MAX_DATA_AGE_MS = 60_000L
        const val MTF_CONCURRENCY = 4
        const val MTF_TIMEOUT_MS = 12_000L
        const val DAILY_HISTORY_CACHE_TTL_MS = 5 * 60_000L
        const val MTF_1M_CACHE_TTL_MS = 30_000L
        const val MTF_1H_CACHE_TTL_MS = 5 * 60_000L

        fun isPastLastTradingAt(contract: ViopContract, nowMs: Long = System.currentTimeMillis()): Boolean =
            contract.lastTradingAt?.let { it <= nowMs } == true

        fun sortOpportunities(items: List<ViopOpportunity>): List<ViopOpportunity> =
            items.sortedWith(
                compareByDescending<ViopOpportunity> { it.rankingScore }
                    .thenByDescending { it.finalScore }
                    .thenBy { it.riskScore }
                    .thenBy { it.contract.symbol }
            )
    }

    suspend fun scanOne(symbol: String): Result<ViopOpportunity> {
        val result = scan(onlySymbol = symbol)
        return result.opportunities.firstOrNull { it.contract.symbol.equals(symbol, true) }?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException(result.errors.firstOrNull()?.code ?: "NO_VERIFIED_ANALYSIS"))
    }

    suspend fun scan(onlySymbol: String? = null, onProgress: (ViopScanProgress) -> Unit = {}): ViopScanResult {
        val startedAt = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()
        val stageStarted = mutableMapOf<String, Long>()
        val cacheStats = MtfHistoryCache.Stats()
        fun stage(name: String, at: Long) { stageStarted[name] = (stageStarted[name] ?: 0L) + (System.currentTimeMillis() - at).coerceAtLeast(0L) }
        // Refresh only VERIFIED_HISTORICAL VİOP outcomes before the current scan so same-scan
        // data can never train the weights used by this scan.
        strategyDecisionService?.refreshOutcomes()
        val contractsStarted = System.currentTimeMillis()
        val universeResult = backend.loadViop()
        stage("contracts", contractsStarted)
        if (universeResult.isFailure) {
            val err = ViopScanError("-", codeOf(universeResult.exceptionOrNull(), "CONTRACT_ERROR"), universeResult.exceptionOrNull()?.message ?: "VİOP sözleşme evreni alınamadı.")
            val completedAt = System.currentTimeMillis()
            ScanTelemetry.record(ScanProfile(runId, startedAt, completedAt, stageStarted.toMap(), processed = 1, successful = 0, failed = 1, cacheHits = cacheStats.hits, cacheMisses = cacheStats.misses))
            return ViopScanResult(emptyList(), listOf(err), ViopScanProgress(failed = 1), startedAt, completedAt, ScanRunStatus.FAILED)
        }
        val universe = ViopContractSelector.candidates(universeResult.getOrDefault(emptyList()).filter {
            onlySymbol == null || it.symbol.equals(onlySymbol, true)
        }, allowWatch = false)
        if (universe.isEmpty()) {
            val err = ViopScanError("-", "CONTRACT_ERROR", "Aktif Production VİOP sözleşme evreni boş.")
            val completedAt = System.currentTimeMillis()
            ScanTelemetry.record(ScanProfile(runId, startedAt, completedAt, stageStarted.toMap(), processed = 0, successful = 0, failed = 0, cacheHits = cacheStats.hits, cacheMisses = cacheStats.misses))
            return ViopScanResult(emptyList(), listOf(err), ViopScanProgress(), startedAt, completedAt, ScanRunStatus.FAILED)
        }

        var p = ViopScanProgress(total = universe.size)
        val opportunities = mutableListOf<ViopOpportunity>()
        val errors = mutableListOf<ViopScanError>()
        onProgress(p)

        for (contract in universe) {
            if (isPastLastTradingAt(contract, startedAt)) {
                p = p.copy(eliminated = p.eliminated + 1)
                errors += ViopScanError(contract.symbol, "EXPIRED", "Son işlem zamanı geçmiş sözleşme taramaya alınmadı.")
                onProgress(p)
                continue
            }
            if (contract.validity == SignalValidity.REJECTED) {
                p = p.copy(eliminated = p.eliminated + 1)
                errors += ViopScanError(contract.symbol, "EXPIRED", contract.validityReason)
                onProgress(p)
                continue
            }

            val dataStarted = System.currentTimeMillis()
            val quoteResult = backend.loadViopQuote(contract.symbol)
            if (quoteResult.isFailure) {
                stage("market_data", dataStarted)
                p = p.copy(failed = p.failed + 1)
                errors += ViopScanError(contract.symbol, codeOf(quoteResult.exceptionOrNull(), "QUOTE_ERROR"), quoteResult.exceptionOrNull()?.message ?: "Quote alınamadı.")
                onProgress(p)
                continue
            }
            val quote = quoteResult.getOrThrow()
            p = p.copy(quoteSuccess = p.quoteSuccess + 1)
            onProgress(p)

            val nowQuote = System.currentTimeMillis()
            val rawAge = nowQuote - quote.exchangeTimestamp
            if (rawAge < -tr.borsatakip.v5.data.RealTimeIntegrityPolicy.MAX_FUTURE_CLOCK_SKEW_MS) {
                p = p.copy(eliminated = p.eliminated + 1)
                errors += ViopScanError(contract.symbol, "FUTURE_TIMESTAMP", "Quote piyasa zamanı gelecekte.")
                onProgress(p); continue
            }
            val age = rawAge.coerceAtLeast(0L)
            if (quote.delaySeconds == null || quote.delaySeconds !in 0..tr.borsatakip.v5.data.RealTimeIntegrityPolicy.MAX_DECLARED_DELAY_SECONDS || age > MAX_DATA_AGE_MS) {
                p = p.copy(eliminated = p.eliminated + 1)
                errors += ViopScanError(contract.symbol, "STALE_DATA", "Quote veri yaşı ${age} ms; limit $MAX_DATA_AGE_MS ms.")
                onProgress(p)
                continue
            }

            // Main 220-daily-bar strategy is unchanged until a separate 5m walk-forward
            // backtest exists. Reuse a verified 1d series briefly instead of re-downloading
            // it for every 30-second scan, while ALWAYS rechecking the actual live quote.
            val historyResult = try {
                Result.success(MtfHistoryCache.loadFresh(
                    sourceKey = backend.mtfCacheSourceKey(),
                    symbol = contract.symbol,
                    timeframe = "1d",
                    ttlMs = DAILY_HISTORY_CACHE_TTL_MS,
                    loader = { backend.loadViopHistory(contract.symbol).getOrThrow() },
                    stats = cacheStats,
                    sourceKeyNow = { backend.mtfCacheSourceKey() }
                ))
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (historyResult.isFailure) {
                stage("market_data", dataStarted)
                val code = codeOf(historyResult.exceptionOrNull(), "HISTORY_ERROR")
                p = if (code == "INSUFFICIENT_HISTORY") p.copy(insufficient = p.insufficient + 1) else p.copy(failed = p.failed + 1)
                errors += ViopScanError(contract.symbol, code, historyResult.exceptionOrNull()?.message ?: "History alınamadı.")
                onProgress(p)
                continue
            }
            val candles = historyResult.getOrThrow()
            stage("market_data", dataStarted)
            p = p.copy(historySuccess = p.historySuccess + 1)
            onProgress(p)

            if (candles.size < MIN_HISTORY_BARS) {
                p = p.copy(insufficient = p.insufficient + 1)
                errors += ViopScanError(contract.symbol, "INSUFFICIENT_HISTORY", "${candles.size} mum; minimum $MIN_HISTORY_BARS.")
                onProgress(p)
                continue
            }

            val volume = quote.volume ?: contract.volume
            val oi = quote.openInterest ?: contract.openInterest
            if ((volume == null || volume < MIN_VOLUME) || (oi == null || oi < MIN_OPEN_INTEREST)) {
                p = p.copy(eliminated = p.eliminated + 1)
                errors += ViopScanError(contract.symbol, "LOW_LIQUIDITY", "Hacim/açık pozisyon eşiği sağlanmadı.")
                onProgress(p)
                continue
            }

            val publishAge = System.currentTimeMillis() - quote.exchangeTimestamp
            if (publishAge < -tr.borsatakip.v5.data.RealTimeIntegrityPolicy.MAX_FUTURE_CLOCK_SKEW_MS || publishAge > MAX_DATA_AGE_MS) {
                p = p.copy(eliminated = p.eliminated + 1)
                errors += ViopScanError(contract.symbol, "STALE_DATA", "History sonrası quote tazeliğini kaybetti.")
                onProgress(p); continue
            }
            if (contract.lastTradingAt == null) {
                p = p.copy(insufficient = p.insufficient + 1)
                errors += ViopScanError(contract.symbol, "EXPIRY_UNVERIFIED", "Gerçek son işlem zamanı sağlayıcı tarafından verilmedi.")
                onProgress(p); continue
            }
            val analysisStarted = System.currentTimeMillis()
            val technical = TechnicalAnalyzer.analyze(candles)
            val opportunity = score(contract, quote, candles, technical, publishAge.coerceAtLeast(0L))
            stage("analysis", analysisStarted)
            p = p.copy(
                analyzed = p.analyzed + 1,
                longCount = p.longCount + if (opportunity.direction == "LONG") 1 else 0,
                shortCount = p.shortCount + if (opportunity.direction == "SHORT") 1 else 0
            )
            opportunities += opportunity
            onProgress(p)
        }

        val regime = loadMarketRegime()
        val contextual = applyDecisionContext(opportunities, regime, cacheStats)
        val optimized = try {
            strategyDecisionService?.apply(contextual) ?: contextual
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            contextual.map { it.copy(ensembleStatus = "FALLBACK_COMBINED • OPTIMIZATION_ERROR") }
        }
        val rankingStarted = System.currentTimeMillis()
        // Final price MUST be fetched anew: inspecting an old exchangeTimestamp does not
        // protect a long sequential scan. Bounded concurrent refresh; error accounting
        // remains serial to avoid lost progress updates.
        val finalSemaphore = Semaphore(MTF_CONCURRENCY)
        val finalCandidates = sortOpportunities(optimized)
        val finalQuotes = supervisorScope {
            finalCandidates.map { candidate ->
                async(Dispatchers.IO) {
                    finalSemaphore.withPermit {
                        withTimeoutOrNull(MTF_TIMEOUT_MS) {
                            backend.loadViopQuote(candidate.contract.symbol)
                        } ?: Result.failure(IllegalStateException("FINAL_QUOTE_TIMEOUT"))
                    }
                }
            }.map { it.await() }
        }
        val sorted = buildList {
            finalCandidates.zip(finalQuotes).forEach { (candidate, result) ->
                val now = System.currentTimeMillis()
                val c = candidate.contract
                if (result.isFailure) {
                    errors += ViopScanError(c.symbol, "FINAL_QUOTE_FAILED",
                        "Yeni fiyat istenemedi: ${result.exceptionOrNull()?.message ?: "bilinmeyen hata"}")
                    p = p.copy(failed = p.failed + 1)
                    return@forEach
                }
                val latest = result.getOrThrow()
                val reject = ViopFinalQuotePolicy.rejectReason(
                    c.symbol, latest.symbol, latest.price, latest.exchangeTimestamp, now,
                    latest.realtime, latest.delaySeconds, latest.currentSessionIncluded
                ) ?: if (c.lastTradingAt == null || c.lastTradingAt <= now) "FINAL_CONTRACT_EXPIRED" else null
                if (reject != null) {
                    errors += ViopScanError(c.symbol, reject, "Yenilenen VİOP quote/vade son yayın kapısını geçemedi.")
                    p = p.copy(eliminated = p.eliminated + 1)
                    return@forEach
                }
                val repriced = candidate.copy(
                    quote = latest,
                    dataAgeMs = (now - latest.exchangeTimestamp).coerceAtLeast(0L),
                    signalReason = candidate.signalReason + " • FINAL_QUOTE_REFRESHED"
                )
                if (repriced.direction == "LONG" || repriced.direction == "SHORT") {
                    val entry = ViopV6Policy.currentEntry(repriced)
                    if (!entry.valid) {
                        errors += ViopScanError(c.symbol, entry.reason,
                            "Yeni alış/satış fiyatında giriş / 1. hedef RR uygun değil.")
                        p = p.copy(eliminated = p.eliminated + 1)
                        return@forEach
                    }
                }
                add(repriced)
            }
        }
        // Summary counters must reflect published validated records, not initial
        // directions that were removed by a failed final quote/reprice gate.
        p = p.copy(
            longCount = sorted.count { it.direction == "LONG" && it.validity == SignalValidity.VALID },
            shortCount = sorted.count { it.direction == "SHORT" && it.validity == SignalValidity.VALID }
        )
        onProgress(p)
        stage("ranking", rankingStarted)
        strategyDecisionService?.record(sorted)
        val completedAt = System.currentTimeMillis()
        ScanTelemetry.record(ScanProfile(
            runId = runId, startedAt = startedAt, completedAt = completedAt,
            stagesMs = stageStarted.toMap(), processed = p.analyzed + p.failed + p.insufficient + p.eliminated,
            successful = p.analyzed, failed = p.failed, cacheHits = cacheStats.hits, cacheMisses = cacheStats.misses
        ))
        val status = when {
            sorted.isEmpty() && p.failed > 0 -> ScanRunStatus.FAILED
            p.failed > 0 || p.insufficient > 0 || p.eliminated > 0 -> ScanRunStatus.PARTIAL
            else -> ScanRunStatus.COMPLETE
        }
        return ViopScanResult(sorted, errors, p, startedAt, completedAt, status)
    }

    private fun score(
        contract: ViopContract,
        quote: tr.borsatakip.v5.model.ViopQuote,
        candles: List<Candle>,
        t: tr.borsatakip.v5.model.TechnicalSnapshot,
        age: Long
    ): ViopOpportunity {
        val price = DecisionPricePolicy.historyBarPrice(candles)
            ?: error("HISTORY_ERROR: Karar fiyatı için geçerli kapanmış history mumu yok.")
        val structure = ViopBreakoutPolicy.evaluate(candles, t.volumeRatio)
        val volumeAnomalyPct = structure.volumeAnomalyPct
        val breakoutState = structure.state
        val breakoutConfirmed = structure.confirmed
        val expiryRisk = expiryRisk(contract.lastTradingAt)
        val spreadPct = if (quote.bid != null && quote.ask != null && quote.bid > 0.0 && quote.ask >= quote.bid) {
            val mid = (quote.bid + quote.ask) / 2.0
            if (mid > 0.0) ((quote.ask - quote.bid) / mid) * 100.0 else null
        } else null
        val liquidity = V540LiquidityEngine.score(
            t.volumeRatio, spreadPct, candles.lastOrNull()?.volume, candles.dropLast(1).map { it.volume }
        ) ?: 0
        val atr = t.atr14?.takeIf { it.isFinite() && it > 0.0 }
        val support = t.support?.takeIf { it.isFinite() && it > 0.0 }
        val resistance = t.resistance?.takeIf { it.isFinite() && it > 0.0 }
        val structureDistanceAtr = if (atr != null) {
            listOfNotNull(support, resistance).minOfOrNull { abs(price - it) }?.div(atr)
        } else null
        val volumeAvailable = (quote.volume ?: contract.volume)?.let { it.isFinite() && it > 0.0 } == true
        val oiAvailable = (quote.openInterest ?: contract.openInterest ?: 0L) > 0L
        val v531 = V531CalculationEngine.calculate(
            V531CalculationInput(
                candles = candles,
                technical = t,
                currentPrice = price,
                dataQuality = V531DataQualityInput(
                    dataAgeMs = age,
                    candleCount = candles.size,
                    candlesValid = candles.size >= MIN_HISTORY_BARS,
                    quoteValid = quote.price.isFinite() && quote.price > 0.0 && quote.exchangeTimestamp > 0L,
                    providerHealthy = quote.source.isNotBlank(),
                    symbolResolved = contract.symbol.isNotBlank() && quote.symbol.equals(contract.symbol, true),
                    volumeAvailable = volumeAvailable,
                    openInterestApplicable = true,
                    openInterestAvailable = oiAvailable
                ),
                // OI directional evidence is used only when the provider supplies a point-in-time change.
                openInterestChangePct = quote.openInterestChangePct,
                openInterestAsOfTimestamp = quote.openInterestAsOfTimestamp,
                spreadPct = spreadPct,
                liquidityScore = liquidity,
                structureDistanceAtr = structureDistanceAtr,
                contractRiskPct = expiryRisk,
                evaluationTimestamp = candles.lastOrNull()?.timestamp,
                technicalAsOfTimestamp = candles.lastOrNull()?.timestamp
            )
        )
        val longScore = v531.longScore
        val shortScore = v531.shortScore
        val final = v531.finalSignalScore
        val risk = v531.riskScore
        val dataConfidence = v531.dataConfidence
        val validity = if (dataConfidence >= 60) SignalValidity.VALID else SignalValidity.WATCH
        val freshnessScore = V540FreshnessPolicy.score(age, MAX_DATA_AGE_MS)
        val finalized = V540FinalDecisionFlow.finalize(
            engineDirection = v531.decision,
            finalSignalScore = final,
            dataConfidence = dataConfidence,
            riskScore = risk,
            price = price,
            technical = t,
            candles = candles,
            freshnessScore = freshnessScore,
            mtfAvailableCount = 0,
            mtfExpectedCount = 0,
            minFirstTargetGrossRr = ViopRiskRewardPolicy.MIN_TARGET1_GROSS_RR
        )
        val direction = when (finalized.publishedDirection) {
            V531Direction.LONG -> "LONG"
            V531Direction.SHORT -> "SHORT"
            V531Direction.WATCH -> "NEUTRAL"
        }
        val rr1 = finalized.rr1
        val rr2 = finalized.rr2
        val riskPlan = finalized.riskPlan
        val rankingScore = finalized.rankingScore
        val decisionState = if (validity == SignalValidity.VALID && finalized.publishedDirection != V531Direction.WATCH) {
            DecisionState.VERIFIED_OPPORTUNITY
        } else DecisionState.WATCH
        val reason = buildList {
            add("${v531.engineVersion} $direction skoru $final/100")
            add("LONG=$longScore • SHORT=$shortScore")
            add("Veri güveni=$dataConfidence/100")
            add("Risk=$risk/100")
            add("Likidite=$liquidity/100")
            add("Vade riski=$expiryRisk/100")
            add("Sinyal kapsamı=${v531.availableSignalWeight}/100")
            when {
                !oiAvailable -> add("OI verisi yok")
                quote.openInterestChangePct != null -> add("OI değişimi=${"%+.2f".format(quote.openInterestChangePct)}%")
                else -> add("OI seviyesi mevcut; yön değişimi N/A")
            }
            volumeAnomalyPct?.let { add("Hacim anomalisi=${"%+.0f".format(it)}%") }
            if (breakoutState != "YOK") add("$breakoutState${if (breakoutConfirmed) " • hacim teyitli" else " • hacim teyitsiz"}")
            if (v531.reasonCodes.isNotEmpty()) add("Neden=${v531.reasonCodes.joinToString(",") { it.name }}")
            add("RR kapısı=${finalized.rrGateCode}")
            add("Sıralama=$rankingScore/100")
            add("Veri yaşı=${age / 1000}s")
        }.joinToString(" • ")
        return ViopOpportunity(
            contract = contract, quote = quote, candles = candles, technical = t,
            technicalScore = final, riskScore = risk, liquidityScore = liquidity,
            expiryRisk = expiryRisk, longScore = longScore, shortScore = shortScore,
            finalScore = final, direction = direction, signalReason = reason,
            validity = validity, dataAgeMs = age, historyCandleCount = candles.size,
            decisionState = decisionState, dataConfidenceScore = dataConfidence, riskPlan = riskPlan, rankingScore = rankingScore,
            rankingStatus = finalized.rankingStatus, mtfAvailableCount = finalized.mtfAvailableCount,
            mtfExpectedCount = finalized.mtfExpectedCount, mtfCompletenessPct = finalized.mtfCompletenessPct,
            volumeAnomalyPct = volumeAnomalyPct, breakoutState = breakoutState, breakoutConfirmed = breakoutConfirmed,
            decisionPrice = price, calculationEngineVersion = v531.engineVersion, calculationEngineMode = CalculationEngineMode.LOCAL,
            signalAvailableWeight = v531.availableSignalWeight,
            signalReasonCodes = (v531.reasonCodes.map { it.name } + finalized.rrGateCode).distinct(), signalConflictPenalty = v531.conflictPenalty
        )
    }


    private suspend fun loadMarketRegime(): MarketRegimeEngine.Result? {
        val provider = marketProvider ?: return null
        for (symbol in listOf("XU100", "XU100.IS")) {
            val candles = try {
                provider.fetchDailyHistory(symbol, maximumRange = false)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                emptyList()
            }
            if (candles.size >= 60) return MarketRegimeEngine.evaluate(symbol, candles)
        }
        return null
    }

    /** VİOP MTF bütün uygun adaylara final ranking'den önce aynı kuralla uygulanır. */
    private suspend fun applyDecisionContext(
        items: List<ViopOpportunity>,
        regime: MarketRegimeEngine.Result?,
        cacheStats: MtfHistoryCache.Stats
    ): List<ViopOpportunity> = supervisorScope {
        if (items.isEmpty()) return@supervisorScope items

        val semaphore = Semaphore(MTF_CONCURRENCY)
        val evaluated = items.map { opportunity ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val mtf = withTimeoutOrNull(MTF_TIMEOUT_MS) {
                        val symbol = opportunity.contract.symbol
                        val sourceKey = backend.mtfCacheSourceKey()
                        val oneMin = MtfHistoryCache.loadOptionalFresh(
                            sourceKey = sourceKey,
                            symbol = symbol,
                            timeframe = "1m",
                            ttlMs = MTF_1M_CACHE_TTL_MS,
                            loader = { backend.loadViopHistoryFlexible(symbol, "7d", "1m", 0).getOrThrow() },
                            stats = cacheStats,
                            sourceKeyNow = { backend.mtfCacheSourceKey() }
                        )
                        val oneHour = MtfHistoryCache.loadOptionalFresh(
                            sourceKey = sourceKey,
                            symbol = symbol,
                            timeframe = "60m",
                            ttlMs = MTF_1H_CACHE_TTL_MS,
                            loader = { backend.loadViopHistoryFlexible(symbol, "120d", "60m", 0).getOrThrow() },
                            stats = cacheStats,
                            sourceKeyNow = { backend.mtfCacheSourceKey() }
                        )
                        val clean1 = OhlcvResampler.sanitize(oneMin)
                        MtfConsensusEngine.evaluate(
                            listOf(
                                MtfConsensusEngine.Input("1 DK", clean1, 0.08),
                                MtfConsensusEngine.Input("3 DK", OhlcvResampler.aggregate(clean1, 1, 3), 0.12),
                                MtfConsensusEngine.Input("5 DK", OhlcvResampler.aggregate(clean1, 1, 5), 0.15),
                                MtfConsensusEngine.Input("15 DK", OhlcvResampler.aggregate(clean1, 1, 15), 0.20),
                                MtfConsensusEngine.Input("1 SA", OhlcvResampler.sanitize(oneHour), 0.20),
                                MtfConsensusEngine.Input("1 GÜN", OhlcvResampler.sanitize(opportunity.candles), 0.25)
                            )
                        )
                    }
                    opportunity.contract.symbol to mtf
                }
            }
        }.map { it.await() }.toMap()

        items.map { opportunity ->
            val mtf = evaluated[opportunity.contract.symbol]?.takeIf { it.availableCount >= 3 }
            val context = mtf?.let {
                V531ContextEngine.applyMtf(
                    longScore = opportunity.longScore,
                    shortScore = opportunity.shortScore,
                    dataConfidence = opportunity.dataConfidenceScore,
                    riskScore = opportunity.riskScore,
                    availableSignalWeight = opportunity.signalAvailableWeight,
                    mtfConsensusScore = it.score
                )
            }
            val engineDirection = context?.direction ?: when (opportunity.direction) {
                "LONG" -> V531Direction.LONG
                "SHORT" -> V531Direction.SHORT
                else -> V531Direction.WATCH
            }
            val finalSignal = context?.finalSignalScore ?: opportunity.finalScore
            val finalRisk = context?.riskScore ?: opportunity.riskScore
            val engineDirectionLabel = when (engineDirection) {
                V531Direction.LONG -> "LONG"
                V531Direction.SHORT -> "SHORT"
                V531Direction.WATCH -> "NEUTRAL"
            }
            val regimeAdj = regime?.let { MarketRegimeEngine.rankingAdjustment(engineDirectionLabel, it) } ?: 0
            val freshnessScore = V540FreshnessPolicy.score(opportunity.dataAgeMs, MAX_DATA_AGE_MS)
            val finalized = V540FinalDecisionFlow.finalize(
                engineDirection = engineDirection,
                finalSignalScore = finalSignal,
                dataConfidence = opportunity.dataConfidenceScore,
                riskScore = finalRisk,
                price = opportunity.decisionPrice,
                technical = opportunity.technical,
                candles = opportunity.candles,
                freshnessScore = freshnessScore,
                mtfAvailableCount = mtf?.availableCount ?: 0,
                mtfExpectedCount = 6,
                marketRegimeAdjustment = regimeAdj,
                minFirstTargetGrossRr = ViopRiskRewardPolicy.MIN_TARGET1_GROSS_RR
            )
            val direction = when (finalized.publishedDirection) {
                V531Direction.LONG -> "LONG"
                V531Direction.SHORT -> "SHORT"
                V531Direction.WATCH -> "NEUTRAL"
            }
            val decisionState = when {
                opportunity.validity == SignalValidity.INSUFFICIENT -> DecisionState.INSUFFICIENT_DATA
                opportunity.validity == SignalValidity.REJECTED -> DecisionState.REJECTED
                opportunity.validity == SignalValidity.VALID && finalized.publishedDirection != V531Direction.WATCH -> DecisionState.VERIFIED_OPPORTUNITY
                else -> DecisionState.WATCH
            }
            val suffix = buildList {
                if (mtf != null) add("MTF=${mtf.label} (${mtf.score})")
                if (context != null) add("${ScoringConfig.ENGINE_VERSION}=${context.finalSignalScore}/100 • conflict=${context.conflictPenalty}")
                add("RR_GATE=${finalized.rrGateCode}")
                add("FLOW=${V540FinalDecisionFlow.FLOW_VERSION}")
                if (regime != null && regime.regime != MarketRegimeEngine.Regime.UNKNOWN) add("Rejim=${regime.regime.label} (${regime.confidence})")
            }.joinToString(" • ")
            opportunity.copy(
                longScore = context?.longScore ?: opportunity.longScore,
                shortScore = context?.shortScore ?: opportunity.shortScore,
                finalScore = finalSignal,
                technicalScore = finalSignal,
                riskScore = finalRisk,
                direction = direction,
                decisionState = decisionState,
                riskPlan = finalized.riskPlan,
                rankingScore = finalized.rankingScore,
                rankingStatus = finalized.rankingStatus,
                mtfAvailableCount = finalized.mtfAvailableCount,
                mtfExpectedCount = finalized.mtfExpectedCount,
                mtfCompletenessPct = finalized.mtfCompletenessPct,
                mtfConsensusScore = mtf?.score,
                mtfConsensusLabel = mtf?.label ?: "MTF VERİ YOK",
                marketRegime = regime?.regime?.label ?: "VERİ YOK",
                marketRegimeConfidence = regime?.confidence ?: 0,
                signalAvailableWeight = context?.availableSignalWeight ?: opportunity.signalAvailableWeight,
                signalReasonCodes = if (context == null) (opportunity.signalReasonCodes + finalized.rrGateCode).distinct() else (opportunity.signalReasonCodes + context.reasonCodes.map { it.name } + finalized.rrGateCode).distinct(),
                signalConflictPenalty = context?.conflictPenalty ?: opportunity.signalConflictPenalty,
                signalReason = if (suffix.isBlank()) opportunity.signalReason else opportunity.signalReason + " • " + suffix
            )
        }
    }

    private fun expiryRisk(lastTradingAt: Long?): Int {
        val at = lastTradingAt ?: return 100
        val days = ChronoUnit.DAYS.between(LocalDate.now(), java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()).toInt()
        return when { days < 0 -> 100; days <= 5 -> 90; days <= 10 -> 75; days <= 20 -> 55; days <= 40 -> 35; else -> 20 }
    }

    private fun codeOf(t: Throwable?, fallback: String): String {
        val text = t?.message.orEmpty().uppercase()
        return listOf("AUTH_ERROR","CONTRACT_ERROR","QUOTE_ERROR","HISTORY_ERROR","INSUFFICIENT_HISTORY","STALE_DATA","LOW_LIQUIDITY","EXPIRED","RATE_LIMIT","SERVER_ERROR").firstOrNull { text.contains(it) } ?: fallback
    }
}
