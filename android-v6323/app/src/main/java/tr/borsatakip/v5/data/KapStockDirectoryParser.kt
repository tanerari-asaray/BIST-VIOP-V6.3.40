package tr.borsatakip.v5.data

import org.json.JSONArray
import org.json.JSONObject

/** The official page's structured hydration data, not a guessed/hard-coded constituent list. */
object KapStockDirectoryParser {
    fun parse(html: String, now: Long = System.currentTimeMillis()): KapStockDirectory {
        require(html.length <= 4_000_000) { "KAP yanıt boyutu geçersiz" }
        val payload = Regex("self\\.__next_f\\.push\\((\\[.*?)\\)</script>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { JSONArray(it.groupValues[1]).optString(1) }.joinToString("")
        fun rows(code: String, required: Boolean = true): List<JSONObject> {
            val objects = Regex("\\{\\s*\"code\"\\s*:\\s*\"$code\"").findAll(payload).mapNotNull { match ->
                val obj = JSONObject(objectAt(payload, match.range.first))
                obj.optJSONArray("content")?.takeIf { it.length() > 0 }?.let { a ->
                    (0 until a.length()).map { a.getJSONObject(it) }
                }
            }.toList()
            if (objects.isEmpty() && !required) return emptyList()
            require(objects.isNotEmpty()) { "KAP $code içeriği yok" }
            require(objects.all { a -> a.map { it.getString("stockCode") }.toSet() == objects.first().map { it.getString("stockCode") }.toSet() })
            return objects.first()
        }
        val all = rows("XUTUM")
        val members = rows("XU030")
        fun names(rows: List<JSONObject>): Map<String, String> = buildMap {
            rows.forEach { row ->
                val code = row.getString("stockCode").trim()
                val title = row.getString("title").trim()
                require(!containsKey(code)) { "KAP tekrarlı sembol" }
                put(code, title)
            }
        }
        val memberNames = names(members)
        require(members.size == 30 && memberNames.size == 30)
        val sectors = listOf("XUBNK", "XUSIN", "XUTEK").associateWith { rows(it, required = false) }
            .filterValues { it.isNotEmpty() }.mapValues { names(it.value) }
        val completeNames = names(all) + memberNames + sectors.values.fold(emptyMap<String, String>()) { a, b -> a + b }
        val directory = KapStockDirectory(completeNames, memberNames.keys, now,
            memberships = sectors.mapValues { it.value.keys })
        require(directory.valid()) { "KAP üyelik/şirket kaydı geçersiz" }
        return directory
    }

    private fun objectAt(text: String, start: Int): String {
        var depth = 0; var quoted = false; var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        error("KAP eksik nesne")
    }
}
