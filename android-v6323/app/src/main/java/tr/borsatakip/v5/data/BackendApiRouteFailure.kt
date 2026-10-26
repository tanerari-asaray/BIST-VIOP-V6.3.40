package tr.borsatakip.v5.data

/** Pure diagnosis, never treats a public root landing page as market-data readiness. */
internal object BackendApiRouteFailure {
    fun describe(httpStatus: Int, endpoint: String): String? {
        if (httpStatus != 404) return null
        val publicEndpoint = endpoint.substringBefore('?')
        val explanation = when (publicEndpoint) {
            "/v1/health" -> "İlk sağlık kontrolü bulunamadı; diğer API adımları test edilemedi."
            "/v1/preflight" -> "Sağlık yanıtı alınsa da BIST yetki/veri denetim uç noktası bulunamadı."
            else -> "İstenen API uç noktası bulunamadı; farklı rota veya eksik servis olabilir."
        }
        return "API_ROUTE_NOT_FOUND • HTTP 404 • $publicEndpoint. $explanation " +
            "Railway'de dağıtılan servis/versiyon ve /v1/... API yollarını denetleyin. " +
            "API anahtarının geçerli olduğu veya canlı veri bulunduğu doğrulanmadı."
    }
}
