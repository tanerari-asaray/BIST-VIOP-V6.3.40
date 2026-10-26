// Pure diagnostic logic shared by the gateway, the deploy bundle and regression tests.
export const GATEWAY_VERSION = '6.3.35-gateway-diagnostics.1';

const knownCode = /^[A-Z][A-Z0-9_]{2,95}$/;
export function failureBody(status, inputCode) {
  const code = typeof inputCode === 'string' && knownCode.test(inputCode) ? inputCode : 'UPSTREAM_UNAVAILABLE';
  const upstream = /_UPSTREAM_HTTP_([1-5][0-9]{2})$/.exec(code);
  const sourceMissing = /^LICENSED_(BIST|VIOP)_PROVIDER_NOT_CONNECTED$/.test(code);
  const stage = code === 'INVALID_GATEWAY_APP_TOKEN' ? 'GATEWAY_AUTH' :
    code === 'PRIVATE_GATEWAY_KEY_NOT_CONFIGURED' ? 'GATEWAY_CONFIG' :
    sourceMissing || /_URL_INVALID$|_SELF_REFERENCE$|_NOT_CONFIGURED$/.test(code) ? 'PROVIDER_CONFIG' :
    upstream && ['401', '403'].includes(upstream[1]) ? 'PROVIDER_AUTH' :
    /UPSTREAM|SCHEMA|RESPONSE|CONTRACT|HISTORY|QUOTE/.test(code) ? 'PROVIDER' : 'REQUEST';
  let message = 'İstek tamamlanamadı.';
  if (sourceMissing) message = (code.includes('VIOP') ? 'VİOP' : 'BIST') + ' veri kaynağı sunucuda yapılandırılmamış.';
  else if (stage === 'GATEWAY_AUTH') message = 'Uygulamanın sunucu erişim anahtarı reddedildi.';
  else if (stage === 'GATEWAY_CONFIG') message = 'Sunucunun uygulama erişim anahtarı yapılandırılmamış.';
  else if (stage === 'PROVIDER_AUTH') message = 'Sunucuya erişildi; veri sağlayıcısı kimlik doğrulamasını reddetti.';
  else if (/_SELF_REFERENCE$/.test(code)) message = 'Veri kaynağı adresi ağ geçidinin kendi adresi olamaz.';
  else if (/_URL_INVALID$/.test(code)) message = 'Sunucudaki veri kaynağı adresi geçersiz; güvenilir HTTPS adresi gerekli.';
  else if (upstream) message = 'Veri sağlayıcısı HTTP ' + upstream[1] + ' döndürdü.';
  else if (/SCHEMA|ROW_INVALID/.test(code)) message = 'Veri sağlayıcısının yanıtı beklenen veri sözleşmesine uymuyor.';
  else if (/NOT_READY/.test(code)) message = 'Veri sağlayıcısı henüz hazır olduğunu doğrulamadı.';
  else if (/UNAVAILABLE/.test(code)) message = 'Sunucu veri sağlayıcısından geçerli yanıt alamadı.';
  else if (/_NOT_CONFIGURED$|_NOT_CONNECTED$/.test(code)) message = 'İlgili veri kaynağı sunucuda yapılandırılmamış.';
  return {ok: false,
    ...(stage === 'GATEWAY_AUTH' ? {gatewayAuthenticated:false} : stage.startsWith('PROVIDER') ? {gatewayAuthenticated:true} : {}),
    detail: {
    code, httpStatus: status, stage, message,
    ...(upstream ? {upstreamHttpStatus: Number(upstream[1])} : {})
  }};
}

function skipped() {
  return {attempted: false, healthOk: false, state: 'NOT_TESTED', httpStatus: null, code: null};
}

async function probeMarket(market, probe) {
  try {
    const response = await probe();
    const status = response?.status;
    const body = await response.json();
    if (status === 200 && body?.ok === true) {
      return {attempted: true, healthOk: true, state: 'HEALTH_OK', httpStatus: 200, code: null};
    }
    const validStatus = Number.isInteger(status) && status >= 400 && status <= 599 ? status : 502;
    // Only our own controlled error-code families may enter the diagnostic report.
    const supplied = body?.detail?.code;
    const code = typeof supplied === 'string' &&
      /^(LICENSED_(BIST|VIOP)_PROVIDER_NOT_CONNECTED|(BIST|VIOP)_(UPSTREAM_[A-Z0-9_]+|SCHEMA_INVALID|[A-Z_]+_INVALID|EMPTY_RESPONSE|RESPONSE_TOO_LARGE))$/.test(supplied)
      ? supplied : market + '_UPSTREAM_NOT_READY';
    return {attempted: true, healthOk: false, state: 'ERROR', ...failureBody(validStatus, code).detail};
  } catch {
    return {attempted: true, healthOk: false, state: 'ERROR', ...failureBody(503, market + '_UPSTREAM_UNAVAILABLE').detail};
  }
}

/** Health scope never falls back to another market. A health check is not quote/trade verification. */
export async function marketHealth(scope, probes, now = Date.now) {
  if (!['BIST', 'VIOP', 'ALL'].includes(scope)) {
    return {status: 400, body: failureBody(400, 'HEALTH_SCOPE_INVALID')};
  }
  const selected = scope === 'ALL' ? ['BIST', 'VIOP'] : [scope];
  const markets = {BIST: skipped(), VIOP: skipped()};
  await Promise.all(selected.map(async market => { markets[market] = await probeMarket(market, probes[market]); }));
  const ok = selected.every(market => markets[market].healthOk);
  const firstFailure = selected.map(market => markets[market]).find(result => !result.healthOk);
  const status = ok ? 200 : scope === 'ALL' ? 503 : firstFailure.httpStatus;
  return {status, body: {
    ok, provider: 'BIST/VİOP Gateway', serverTime: now(), scope,
    gatewayReady: true, gatewayAuthenticated: true,
    // These probes do not test quotes, timeliness, trading permissions or order execution.
    marketDataReady: false, quotesVerified: false, liveTradingVerified: false,
    markets,
    ...(ok ? {} : {detail: firstFailure}),
    message: ok ? scope + ' sağlık kontrolü tamamlandı; fiyat doğrulaması ayrıca gerekir.' : firstFailure.message
  }};
}
