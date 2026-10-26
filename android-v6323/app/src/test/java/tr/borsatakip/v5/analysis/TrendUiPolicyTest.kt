package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.TechnicalSnapshot

class TrendUiPolicyTest {
    private fun technical(
        ema20: Double? = 110.0,
        ema50: Double? = 100.0,
        ema200: Double? = 90.0,
        rsi: Double? = 58.0,
        macd: Double? = 2.0,
        signal: Double? = 1.0
    ) = TechnicalSnapshot(
        ema20 = ema20,
        ema50 = ema50,
        ema200 = ema200,
        rsi14 = rsi,
        macd = macd,
        macdSignal = signal,
        bbUpper = null,
        bbLower = null,
        atr14 = null,
        vwap = null,
        volumeRatio = null,
        support = null,
        resistance = null
    )

    @Test fun strongBullishUsesBrightGreenState() {
        val x = TrendUiPolicy.resolve(120.0, technical(), DecisionState.VERIFIED_OPPORTUNITY)
        assertEquals(TrendUiState.STRONG_BULLISH, x.state)
        assertEquals("GÜÇLÜ YÜKSELİŞ", x.label)
        assertEquals(0, x.red)
        assertEquals(255, x.green)
        assertEquals(157, x.blue)
    }

    @Test fun bullishDoesNotRequireDailyPriceChange() {
        val x = TrendUiPolicy.resolve(
            115.0,
            technical(ema200 = 120.0, rsi = 49.0, macd = 0.5, signal = 1.0),
            DecisionState.WATCH
        )
        assertEquals(TrendUiState.BULLISH, x.state)
    }

    @Test fun strongBearishUsesBrightRedState() {
        val x = TrendUiPolicy.resolve(
            80.0,
            technical(ema20 = 90.0, ema50 = 100.0, ema200 = 110.0, rsi = 42.0, macd = -2.0, signal = -1.0),
            DecisionState.VERIFIED_OPPORTUNITY
        )
        assertEquals(TrendUiState.STRONG_BEARISH, x.state)
        assertEquals(255, x.red)
        assertEquals(145, x.green)
        assertEquals(155, x.blue)
    }

    @Test fun bearishStateIsRed() {
        val x = TrendUiPolicy.resolve(
            92.0,
            technical(ema20 = 95.0, ema50 = 100.0, ema200 = 80.0, rsi = 55.0, macd = 1.0, signal = 0.0),
            DecisionState.WATCH
        )
        assertEquals(TrendUiState.BEARISH, x.state)
        assertTrue(x.red > x.green && x.red > x.blue)
    }

    @Test fun mixedEmaStructureIsNeutral() {
        val x = TrendUiPolicy.resolve(102.0, technical(ema20 = 100.0, ema50 = 105.0), DecisionState.WATCH)
        assertEquals(TrendUiState.NEUTRAL, x.state)
    }

    @Test fun missingRequiredEmaIsInsufficientData() {
        val x = TrendUiPolicy.resolve(100.0, technical(ema20 = null), DecisionState.WATCH)
        assertEquals(TrendUiState.INSUFFICIENT_DATA, x.state)
    }

    @Test fun rejectedDecisionOverridesTrend() {
        val x = TrendUiPolicy.resolve(120.0, technical(), DecisionState.REJECTED)
        assertEquals(TrendUiState.REJECTED, x.state)
    }

    private fun luminance(rgb: List<Int>): Double {
        val linear = rgb.map { component ->
            val c = component / 255.0
            if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
    }

    private fun directionalStyles(): List<TrendUiStyle> = listOf(
        TrendUiPolicy.resolve(120.0, technical(ema200 = null), DecisionState.WATCH),
        TrendUiPolicy.resolve(120.0, technical(), DecisionState.WATCH),
        TrendUiPolicy.resolve(80.0, technical(90.0, 100.0, null, 42.0, -2.0, -1.0), DecisionState.WATCH),
        TrendUiPolicy.resolve(80.0, technical(90.0, 100.0, 110.0, 42.0, -2.0, -1.0), DecisionState.WATCH)
    )

    @Test fun strongTrendsAreBrighterThanOrdinaryTrendsInBothDirections() {
        directionalStyles().chunked(2).forEach { (ordinary, strong) ->
            assertTrue(luminance(listOf(strong.red, strong.green, strong.blue)) >
                luminance(listOf(ordinary.red, ordinary.green, ordinary.blue)))
            assertTrue(strong.rowFillAlpha > ordinary.rowFillAlpha)
            assertTrue(strong.borderAlpha > ordinary.borderAlpha)
            assertTrue(strong.borderWidthDp > ordinary.borderWidthDp)
            assertTrue(strong.sparklineFillAlpha > ordinary.sparklineFillAlpha)
            assertTrue(strong.sparklineWidthDp > ordinary.sparklineWidthDp)
        }
    }

    @Test fun trendTextRemainsReadableOnTintedCompactAndScanRows() {
        directionalStyles().forEach { style ->
            val foreground = listOf(style.red, style.green, style.blue)
            listOf(listOf(3, 22, 25), listOf(5, 28, 45)).forEach { base ->
                val alpha = style.rowFillAlpha / 255.0
                val background = base.zip(foreground) { b, f -> (b * (1 - alpha) + f * alpha).toInt() }
                val contrast = (luminance(foreground) + 0.05) / (luminance(background) + 0.05)
                assertTrue("${style.state} contrast $contrast must reach 4.5:1", contrast >= 4.5)
            }
        }
    }

    @Test fun neutralInsufficientAndRejectedRowsHaveNoDirectionalHighlight() {
        val styles = listOf(
            TrendUiPolicy.resolve(100.0, technical(100.0, 100.0), DecisionState.WATCH),
            TrendUiPolicy.resolve(120.0, technical(), DecisionState.INSUFFICIENT_DATA),
            TrendUiPolicy.resolve(120.0, technical(), DecisionState.REJECTED)
        )
        styles.forEach {
            assertEquals(0, it.strengthLevel)
            assertEquals(0, it.rowFillAlpha)
            assertTrue(it.borderAlpha < directionalStyles().first().borderAlpha)
        }
    }

    @Test fun invalidDecisionPriceCannotProduceBrightTrend() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).forEach { price ->
            val style = TrendUiPolicy.resolve(price, technical(), DecisionState.WATCH)
            assertEquals(TrendUiState.INSUFFICIENT_DATA, style.state)
            assertEquals(0, style.strengthLevel)
        }
    }

    @Test fun outOfRangeRsiCannotAmplifyBullishTrend() {
        listOf(100.0001, 150.0, Double.MAX_VALUE).forEach { rsi ->
            val style = TrendUiPolicy.resolve(120.0, technical(rsi = rsi), DecisionState.WATCH)
            assertEquals(TrendUiState.BULLISH, style.state)
            assertEquals(1, style.strengthLevel)
        }
    }

    @Test fun outOfRangeRsiCannotAmplifyBearishTrend() {
        listOf(-0.0001, -50.0, -Double.MAX_VALUE).forEach { rsi ->
            val style = TrendUiPolicy.resolve(
                80.0, technical(90.0, 100.0, 110.0, rsi, -2.0, -1.0), DecisionState.WATCH
            )
            assertEquals(TrendUiState.BEARISH, style.state)
            assertEquals(1, style.strengthLevel)
        }
    }

    @Test fun nonFiniteRsiCannotAmplifyEitherDirection() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { rsi ->
            assertEquals(TrendUiState.BULLISH,
                TrendUiPolicy.resolve(120.0, technical(rsi = rsi), DecisionState.WATCH).state)
            assertEquals(TrendUiState.BEARISH,
                TrendUiPolicy.resolve(80.0, technical(90.0, 100.0, 110.0, rsi, -2.0, -1.0), DecisionState.WATCH).state)
        }
    }

    @Test fun validRsiBoundaryValuesRemainUsableEvidence() {
        assertEquals(TrendUiState.STRONG_BULLISH,
            TrendUiPolicy.resolve(120.0, technical(rsi = 100.0), DecisionState.WATCH).state)
        assertEquals(TrendUiState.STRONG_BEARISH,
            TrendUiPolicy.resolve(80.0, technical(90.0, 100.0, 110.0, 0.0, -2.0, -1.0), DecisionState.WATCH).state)
    }
}
