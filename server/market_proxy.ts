// Configurable v1 adapters. A configured URL is never treated as verified market readiness.
import {failureBody} from './gateway_diagnostics.mjs';
const reply = (status: number, code: string) => Response.json(failureBody(status, code), {status, headers: {'cache-control':'no-store'}});
const validTime = (v: unknown) => Number.isSafeInteger(v) && Number(v) >= 1_000_000_000_000 && Number(v) <= Date.now()+15000;
const positive = (v: unknown) => typeof v === 'number' && Number.isFinite(v) && v > 0;
const nonempty = (v: unknown) => typeof v === 'string' && v.trim().length > 0;

export function isGatewayUpstream(value: string): boolean {
  try {
    const ownDomain = (process.env.RAILWAY_PUBLIC_DOMAIN || '').toLowerCase().split(':')[0];
    return !!ownDomain && new URL(value).hostname.toLowerCase() === ownDomain;
  } catch { return false; }
}

export function trustedUpstream(value: string, websocket = false): URL | null {
  try {
    const u = new URL(value);
    if (u.protocol !== (websocket ? 'wss:' : 'https:') || u.username || u.password || u.search || u.hash ||
        !u.hostname || u.hostname === 'localhost' || u.hostname.endsWith('.localhost') ||
        /^[0-9.]+$/.test(u.hostname) || u.hostname.includes(':')) return null;
    // Refuse a provider URL that points back to this gateway (HTTP or websocket).
    if (isGatewayUpstream(value)) return null;
    return u;
  } catch { return null; }
}

function validCandles(data: any, symbol: string, interval: string | null): boolean {
  if (data?.symbol !== symbol || (interval && data.interval !== interval) || !validTime(data.exchangeTimestamp) ||
      typeof data.lastBarClosed !== 'boolean' || !Array.isArray(data.candles) || data.candles.length > 10000) return false;
  let prior = 0;
  for (const c of data.candles) {
    if (!validTime(c.timestamp) || c.timestamp <= prior || ![c.open,c.high,c.low,c.close].every(positive) ||
        !Number.isFinite(c.volume) || c.volume < 0 || c.low > c.high || c.open < c.low || c.open > c.high ||
        c.close < c.low || c.close > c.high) return false;
    prior = c.timestamp;
  }
  return true;
}

function validQuote(data: any, symbol: string): boolean {
  return data?.symbol === symbol && positive(data.price) && validTime(data.exchangeTimestamp) &&
    nonempty(data.source) && typeof data.realtime === 'boolean' && typeof data.currentSessionIncluded === 'boolean' &&
    (data.delaySeconds == null || (Number.isSafeInteger(data.delaySeconds) && data.delaySeconds >= 0)) &&
    (data.previousClose == null || positive(data.previousClose)) &&
    (data.dailyChangePct == null || (typeof data.dailyChangePct === 'number' && Number.isFinite(data.dailyChangePct)));
}

function validPayload(path: string, query: URLSearchParams, data: any): boolean {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return false;
  const symbol = path.split('/').at(-1)!;
  if (path === '/v1/health') return data.ok === true;
  if (path === '/v1/preflight') return typeof data.ok === 'boolean' &&
    ['authentication','symbols','quote','history'].every(k => typeof data[k]?.ok === 'boolean') &&
    Number.isSafeInteger(data.symbolCount) && data.symbolCount >= 0;
  if (path === '/v1/bist/symbols') {
    const rows = data.items ?? data.symbols;
    if (!Array.isArray(rows) || rows.length > 10000) return false;
    const symbols = rows.map(r => typeof r === 'string' ? r : r?.symbol);
    return symbols.every(s => typeof s === 'string' && /^[A-Z0-9]{2,12}$/.test(s)) && new Set(symbols).size === symbols.length;
  }
  if (path.includes('/news-risk/')) {
    const asOf = Number(query.get('asOf'));
    return validTime(asOf) && data.symbol === symbol && data.verified === true && nonempty(data.source) &&
      ['CLEAR','CRITICAL','UNKNOWN'].includes(data.state) && validTime(data.availableAt) &&
      data.availableAt <= asOf && validTime(data.coverageThrough) && data.coverageThrough >= asOf &&
      data.coverageThrough <= asOf + 15000;
  }
  if (path.includes('/quote/')) return validQuote(data, symbol);
  if (path.includes('/history')) return validCandles(data, symbol, query.get('interval'));
  if (path === '/v1/bist/snapshot-batch') return Array.isArray(data.items) && data.items.length <= 1000 &&
    data.items.every((row: any) => row?.error || (validQuote(row.quote, row.symbol) && validCandles(row.history,row.symbol,null)));
  if (path === '/v1/news') return Array.isArray(data.items) && data.items.length <= 10000 && data.items.every((row: any) =>
    nonempty(row.title) && nonempty(row.source) && validTime(row.publishedAt));
  if (path === '/v1/scanner/opportunities') return data.mode === 'REALTIME_MARKET_ENGINE' && data.realtime === true &&
    data.tradingViewUsedAsMarketData === false && nonempty(data.providerId) && nonempty(data.snapshotHash) &&
    nonempty(data.attestationKeyId) && validTime(data.generatedAt) && validTime(data.serverTime) &&
    Array.isArray(data.items);
  return false;
}

export async function forwardMarket(path: string, search: string, kind: 'BIST' | 'NEWS' = 'BIST'): Promise<Response> {
  const key = kind === 'NEWS' ? 'BORSA_NEWS_UPSTREAM_URL' : 'BORSA_BIST_UPSTREAM_URL';
  const source = process.env[key]?.trim();
  if (!source) return reply(503, kind === 'NEWS' ? 'NEWS_PROVIDER_NOT_CONNECTED' : 'LICENSED_BIST_PROVIDER_NOT_CONNECTED');
  if (isGatewayUpstream(source)) return reply(503, kind + '_UPSTREAM_SELF_REFERENCE');
  const base = trustedUpstream(source);
  if (!base) return reply(503, kind + '_UPSTREAM_URL_INVALID');
  const target = new URL(base.toString().replace(/\/$/,'') + path);
  const query = new URLSearchParams(search);
  const allowed = new Set(['symbols','symbol','range','interval','from','to','limit','analysisTimeframeMinutes',
    'scanCadenceMinutes','scanMode','minScore','actionableOnly','category','asOf']);
  for (const [k,v] of query) {
    if (!allowed.has(k) || v.length > 3000 || !/^[A-Za-z0-9_,.:-]+$/.test(v)) return reply(400,kind+'_QUERY_INVALID');
    target.searchParams.set(k,v);
  }
  try {
    const headers: Record<string,string> = {accept:'application/json'};
    const token = process.env[kind === 'NEWS' ? 'BORSA_NEWS_UPSTREAM_TOKEN' : 'BORSA_BIST_UPSTREAM_TOKEN'];
    if (token) headers.authorization = 'Bearer '+token;
    const response = await fetch(target,{headers,redirect:'error',signal:AbortSignal.timeout(20000)});
    if (!response.ok) return reply([401,403,429,503].includes(response.status) ? response.status : 502,
      kind+'_UPSTREAM_HTTP_'+response.status);
    if (!response.body) return reply(502,kind+'_EMPTY_RESPONSE');
    const reader=response.body.getReader(); const blocks:Uint8Array[]=[];let size=0;
    while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;
      if(size>8_388_608){await reader.cancel();return reply(502,kind+'_RESPONSE_TOO_LARGE');}blocks.push(value);}
    const bytes=new Uint8Array(size);let offset=0;for(const b of blocks){bytes.set(b,offset);offset+=b.length;}
    const text=new TextDecoder().decode(bytes);const data=JSON.parse(text);
    if(!validPayload(path,query,data)) return reply(502,kind+'_SCHEMA_INVALID');
    // Preserve scanner hashes, attestation and all provider timestamps unchanged.
    return new Response(text,{status:200,headers:{'content-type':'application/json','cache-control':'no-store'}});
  } catch(e) {return reply(e instanceof Error && ['TimeoutError','AbortError'].includes(e.name)?504:503,
    e instanceof SyntaxError ? kind+'_SCHEMA_INVALID' : kind+'_UPSTREAM_UNAVAILABLE');}
}

export function streamConfig(path: string): {url: string; token: string} | null {
  const key = path === '/v1/scanner/live' ? 'BORSA_SCANNER_WSS_URL' : path === '/v1/viop/live' ? 'BORSA_VIOP_WSS_URL' : 'BORSA_BIST_WSS_URL';
  const url=trustedUpstream(process.env[key] || '',true);
  if (!url) return null;
  return {url:url.toString(),token:process.env[key.replace('_URL','_TOKEN')] || ''};
}

/** Runtime websocket bridge is read-only: only subscription and ping messages are accepted. */
export function gatewayServerOptions(handle: (req:Request)=>Promise<Response>, authorized: (req:Request)=>boolean,
  connect: (url:string,options:any)=>WebSocket = (url,options)=>new (WebSocket as any)(url,options)): any {
  return {port:Number(process.env.PORT||'3000'),
    async fetch(req:Request,server:any) {
      const path=new URL(req.url).pathname;
      if(!['/v1/live','/v1/scanner/live','/v1/viop/live'].includes(path) || req.headers.get('upgrade')?.toLowerCase()!=='websocket') return handle(req);
      if(!authorized(req)) return reply(401,'INVALID_GATEWAY_APP_TOKEN');
      if(req.headers.get('origin') && !(process.env.BORSA_ALLOWED_ORIGINS||'').split(',').includes(req.headers.get('origin')!)) return reply(403,'ORIGIN_NOT_ALLOWED');
      const config=streamConfig(path);if(!config)return reply(503,'STREAM_PROVIDER_NOT_CONFIGURED');
      const upstream=connect(config.url,{headers:config.token?{Authorization:'Bearer '+config.token}:{}});
      const data:any={upstream,pending:[],client:null};
      upstream.onmessage=(e:any)=>{if(data.client)data.client.send(e.data);else if(data.pending.length<32)data.pending.push(e.data);else upstream.close(1009,'backpressure');};
      upstream.onclose=()=>data.client?.close(1011,'provider-disconnected');
      try {await new Promise<void>((resolve,reject)=>{const timer=setTimeout(()=>{upstream.close();reject(new Error('timeout'));},15000);
        upstream.onopen=()=>{clearTimeout(timer);resolve();};upstream.onerror=()=>{clearTimeout(timer);reject(new Error('provider'));};});}
      catch{return reply(503,'STREAM_UPSTREAM_UNAVAILABLE');}
      if(server.upgrade(req,{data}))return;
      upstream.close();return reply(400,'WEBSOCKET_UPGRADE_FAILED');
    },
    websocket:{maxPayloadLength:1_048_576,idleTimeout:60,
      open(ws:any){ws.data.client=ws;for(const message of ws.data.pending)ws.send(message);ws.data.pending=[];},
      message(ws:any,message:any){try{const text=typeof message==='string'?message:new TextDecoder().decode(message);
        const x=JSON.parse(text);if(!['subscribe','unsubscribe','ping'].includes(x.action))return ws.close(1008,'read-only-channel');
        if(ws.data.upstream.readyState===1)ws.data.upstream.send(text);else ws.close(1011,'provider-not-ready');
      }catch{ws.close(1007,'invalid-json');}},
      close(ws:any){ws.data.upstream.close();ws.data.client=null;}
    }};
}
