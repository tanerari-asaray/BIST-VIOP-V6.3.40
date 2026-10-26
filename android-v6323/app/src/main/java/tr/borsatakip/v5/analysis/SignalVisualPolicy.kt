package tr.borsatakip.v5.analysis

enum class SignalIntensity { VERY_LOW, LOW, MEDIUM, HIGH, MAXIMUM }

data class SignalVisualStyle(
    val direction: String,
    val quality: Int,
    val intensity: SignalIntensity,
    val red: Int,
    val green: Int,
    val blue: Int,
    val arrow: String
)

object SignalVisualPolicy {
    fun qualityFor(direction: String, longScore: Int, shortScore: Int): Int? = when {
        direction.equals("LONG", ignoreCase = true) -> longScore.coerceIn(0, 100)
        direction.equals("SHORT", ignoreCase = true) -> shortScore.coerceIn(0, 100)
        else -> null
    }

    fun resolve(direction: String, longScore: Int, shortScore: Int): SignalVisualStyle? {
        val quality = qualityFor(direction, longScore, shortScore) ?: return null
        val intensity = when (quality) {
            in 0..39 -> SignalIntensity.VERY_LOW
            in 40..59 -> SignalIntensity.LOW
            in 60..74 -> SignalIntensity.MEDIUM
            in 75..89 -> SignalIntensity.HIGH
            else -> SignalIntensity.MAXIMUM
        }
        val long = direction.equals("LONG", ignoreCase = true)
        val rgb = if (long) {
            when (intensity) {
                SignalIntensity.VERY_LOW -> intArrayOf(29, 78, 59)
                SignalIntensity.LOW -> intArrayOf(34, 113, 78)
                SignalIntensity.MEDIUM -> intArrayOf(39, 156, 102)
                SignalIntensity.HIGH -> intArrayOf(42, 205, 129)
                SignalIntensity.MAXIMUM -> intArrayOf(42, 245, 149)
            }
        } else {
            when (intensity) {
                SignalIntensity.VERY_LOW -> intArrayOf(101, 46, 52)
                SignalIntensity.LOW -> intArrayOf(139, 50, 58)
                SignalIntensity.MEDIUM -> intArrayOf(190, 55, 65)
                SignalIntensity.HIGH -> intArrayOf(232, 65, 78)
                SignalIntensity.MAXIMUM -> intArrayOf(255, 78, 91)
            }
        }
        return SignalVisualStyle(
            direction = if (long) "LONG" else "SHORT",
            quality = quality,
            intensity = intensity,
            red = rgb[0], green = rgb[1], blue = rgb[2],
            arrow = if (long) "▲" else "▼"
        )
    }
}
