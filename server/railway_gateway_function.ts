import {forwardMarket, streamConfig, gatewayServerOptions, trustedUpstream, isGatewayUpstream} from "./market_proxy.ts";
import {failureBody, marketHealth, GATEWAY_VERSION} from "./gateway_diagnostics.mjs";
import { timingSafeEqual } from "node:crypto";

const json = (status: number, body: object) => new Response(JSON.stringify(body), {
  status, headers: {"content-type": "application/json; charset=utf-8", "cache-control": "no-store",
                   "x-content-type-options":"nosniff"}
});
const fault = (status: number, code: string) => json(status, failureBody(status, code));
export function isAuthorized(req: Request): boolean {
  const key = process.env.BORSA_GATEWAY_APP_KEY || "";
  const header = req.headers.get("authorization") || "";
  const actual=Buffer.from(header), expected=Buffer.from("Bearer " + key);
  if (!key || actual.length !== expected.length) return false;
  return timingSafeEqual(actual, expected);
}
async function financialData(): Promise<Response> {
  const source = (process.env.BORSA_FUNDAMENTALS_UPSTREAM_URL || "").trim();
  if (!source) return fault(503, "FINANCIAL_STATEMENTS_SOURCE_NOT_CONFIGURED");
  let upstream: URL;
  try {
    upstream = new URL(source);
    if (upstream.protocol !== "https:" || !upstream.hostname || upstream.username || upstream.password)
      return fault(503, "FINANCIAL_STATEMENTS_SOURCE_URL_INVALID");
  } catch { return fault(503, "FINANCIAL_STATEMENTS_SOURCE_URL_INVALID"); }
  try {
    const headers: Record<string,string> = {"accept":"application/json"};
    const token = process.env.BORSA_FUNDAMENTALS_UPSTREAM_TOKEN || "";
    if (token) headers.authorization = "Bearer " + token;
    const response = await fetch(upstream, {method:"GET",headers,redirect:"error",signal:AbortSignal.timeout(20000)});
    if (!response.ok) return fault(503, "FINANCIAL_STATEMENTS_UPSTREAM_UNAVAILABLE");
    const declared = Number(response.headers.get("content-length") || "0");
    if (declared > 8_388_608) return fault(502,"FINANCIAL_STATEMENTS_TOO_LARGE");
    if (!response.body) return fault(503,"FINANCIAL_STATEMENTS_EMPTY");
    const reader=response.body.getReader(); let size=0; const blocks: Uint8Array[]=[];
    while (true) {
      const {done,value}=await reader.read();
      if(done) break;
      size += value.byteLength;
      if (size>8_388_608) { await reader.cancel(); return fault(502,"FINANCIAL_STATEMENTS_TOO_LARGE"); }
      blocks.push(value);
    }
    const buffer=new Uint8Array(size);let offset=0;
    for(const block of blocks){buffer.set(block,offset);offset+=block.byteLength;}
    const data=JSON.parse(new TextDecoder().decode(buffer));
    if(!data || typeof data!=="object" || data.schemaVersion!==1 || !Array.isArray(data.items) ||
       data.items.length===0 || data.items.length>10000)
      return fault(502,"FINANCIAL_STATEMENTS_SCHEMA_INVALID");
    const seen=new Set<string>();
    for (const row of data.items) {
      if(!row || typeof row!=="object" || !/^[A-Z0-9]{3,10}$/.test(row.symbol) ||
         seen.has(row.symbol) || typeof row.sector!=="string" || !row.sector.trim() ||
         !["BANK","FINANCIAL","NON_FINANCIAL"].includes(row.businessType) ||
         !["TMS29","IFRS","LOCAL_GAAP"].includes(row.accountingBasis) ||
         row.financialCurrency!=="TRY" ||
         !["financialPeriodEndMs","disclosurePublishedAtMs","marketObservedAtMs"].every(k=>Number.isSafeInteger(row[k])&&row[k]>0) ||
         !["priceTry","marketCapTry","sharesOutstanding"].every(k=>typeof row[k]==="number"&&Number.isFinite(row[k])&&row[k]>0) ||
         !/^https:\/\/(www\.)?kap\.org\.tr\//.test(row.disclosureUrl) ||
         Math.abs(row.priceTry*row.sharesOutstanding/row.marketCapTry-1)>0.05)
         return fault(502,"FINANCIAL_STATEMENT_ROW_INVALID");
      for(const k of ["netIncomeTtmTry","equityTry","ebitdaTtmTry","netDebtTry","freeCashFlowTtmTry"])
        if(row[k]!=null && (typeof row[k]!=="number" || !Number.isFinite(row[k])))
           return fault(502,"FINANCIAL_STATEMENT_NUMBER_INVALID");
      seen.add(row.symbol);
    }
    return json(200,{schemaVersion:1,items:data.items,sourceKind:"UPSTREAM_NOT_INDEPENDENTLY_VERIFIED",verifiedByGateway:false});
  } catch { return fault(503,"FINANCIAL_STATEMENTS_UPSTREAM_UNAVAILABLE"); }
}
// The upstream must implement this documented v1 contract. Credentials remain server-side.
async function viopData(pathname: string, search: string): Promise<Response> {
  const source=(process.env.BORSA_VIOP_UPSTREAM_URL || "").trim();
  if(!source) return fault(503,"LICENSED_VIOP_PROVIDER_NOT_CONNECTED");
  if (isGatewayUpstream(source)) return fault(503, "VIOP_UPSTREAM_SELF_REFERENCE");
  let base: URL;
  try {
    base=new URL(source);
    if (!trustedUpstream(source)) return fault(503, "VIOP_UPSTREAM_URL_INVALID");
    if(base.protocol!=="https:" || base.username || base.password || base.search || base.hash ||
       base.hostname==="localhost" || base.hostname.endsWith(".localhost") ||
       /^[0-9.]+$/.test(base.hostname) || base.hostname.includes(":"))
      return fault(503,"VIOP_UPSTREAM_URL_INVALID");
  } catch { return fault(503,"VIOP_UPSTREAM_URL_INVALID"); }
  const target=new URL(base.toString().replace(/\/$/,"")+pathname);
  const query=new URLSearchParams(search);
  for(const [key,value] of query) {
    if(key==="range" && /^[0-9]{1,3}[dmy]$/.test(value)) target.searchParams.set(key,value);
    else if(key==="interval" && ["1m","3m","5m","15m","60m","1d"].includes(value)) target.searchParams.set(key,value);
    else return fault(400,"VIOP_QUERY_INVALID");
  }
  try {
    const headers: Record<string,string>={accept:"application/json"};
    const token=process.env.BORSA_VIOP_UPSTREAM_TOKEN;
    if(token) headers.authorization="Bearer "+token;
    const response=await fetch(target,{headers,redirect:"error",signal:AbortSignal.timeout(15000)});
    if(!response.ok) {
      const status=[401,403,429,503].includes(response.status)?response.status:502;
      return fault(status,`VIOP_UPSTREAM_HTTP_${response.status}`);
    }
    if(!response.body) return fault(502,"VIOP_EMPTY_RESPONSE");
    const reader=response.body.getReader();const blocks: Uint8Array[]=[];let size=0;
    while(true) {
      const {done,value}=await reader.read();if(done)break;
      size+=value.byteLength;
      if(size>8_388_608){await reader.cancel();return fault(502,"VIOP_RESPONSE_TOO_LARGE");}
      blocks.push(value);
    }
    const buffer=new Uint8Array(size);let offset=0;
    for(const block of blocks){buffer.set(block,offset);offset+=block.length;}
    const data=JSON.parse(new TextDecoder().decode(buffer));
    const timestamp=(v:unknown)=>typeof v==="number"&&Number.isSafeInteger(v)&&v>=1_000_000_000_000&&v<=Date.now()+60000;
    const positive=(v:unknown)=>typeof v==="number"&&Number.isFinite(v)&&v>0;
    const nonnegative=(v:unknown)=>typeof v==="number"&&Number.isFinite(v)&&v>=0;
    const str=(v:unknown)=>typeof v==="string"&&v.trim().length>0;
    const symbol=pathname.split("/").at(-1);
    if(!data || typeof data!=="object" || Array.isArray(data)) return fault(502,"VIOP_SCHEMA_INVALID");
    if(pathname==="/v1/health") {
      // Readiness is never inferred merely from configured credentials or HTTP 200.
      if(data.ok!==true || data.viopReady!==true) return fault(503,"VIOP_UPSTREAM_NOT_READY");
      return json(200,{...data,verifiedByGateway:false});
    }
    if(pathname==="/v1/viop/contracts") {
      if(!Array.isArray(data.items)||data.items.length>10000) return fault(502,"VIOP_CONTRACT_SCHEMA_INVALID");
      const seen=new Set<string>();
      for(const row of data.items) {
        if(!row || !/^([FO]_)[A-Z0-9_.:-]+$/.test(row.symbol) || seen.has(row.symbol) ||
           !str(row.underlying)||!/^\d{4}-(0[1-9]|1[0-2])$/.test(row.expiry)||!str(row.contractType)||
           !str(row.source)||!timestamp(row.dataTimestamp)||!positive(row.tickSize)||!positive(row.multiplier)||
           !Number.isSafeInteger(row.lastTradingAt ?? row.lastTradableAt) ||
           (row.lastTradingAt ?? row.lastTradableAt)<1_000_000_000_000 ||
           (row.lastPrice!=null&&!positive(row.lastPrice))) return fault(502,"VIOP_CONTRACT_ROW_INVALID");
        seen.add(row.symbol);
      }
    } else if(pathname.includes("/quote/")) {
      if(data.symbol!==symbol||!positive(data.price)||!timestamp(data.exchangeTimestamp)||!str(data.source)||
         typeof data.realtime!=="boolean"||!Number.isSafeInteger(data.delaySeconds)||data.delaySeconds<0)
        return fault(502,"VIOP_QUOTE_SCHEMA_INVALID");
    } else if(pathname.includes("/history/")) {
      if(data.symbol!==symbol||data.interval!==target.searchParams.get("interval")||!timestamp(data.exchangeTimestamp)||
         !Array.isArray(data.candles)||data.candles.length>10000||typeof data.lastBarClosed!=="boolean")
        return fault(502,"VIOP_HISTORY_SCHEMA_INVALID");
      let prior=0;
      for(const c of data.candles) {
        if(!c||!timestamp(c.timestamp)||c.timestamp<=prior||![c.open,c.high,c.low,c.close].every(positive)||
           !nonnegative(c.volume)||c.low>c.high||c.open<c.low||c.open>c.high||c.close<c.low||c.close>c.high)
          return fault(502,"VIOP_HISTORY_ROW_INVALID");
        prior=c.timestamp;
      }
    }
    return json(200,data);
  } catch(e) {
    return fault(e instanceof Error && (e.name==="TimeoutError"||e.name==="AbortError")?504:503,
      e instanceof Error && e.name==="SyntaxError"?"VIOP_SCHEMA_INVALID":"VIOP_UPSTREAM_UNAVAILABLE");
  }
}
export async function handleRequest(req:Request):Promise<Response>{
    const {pathname,search}=new URL(req.url);
    if(req.method!=="GET") return fault(405,"METHOD_NOT_ALLOWED");
    if(pathname==="/" || pathname==="/v1/service-info")
      return json(200,{service:"bist-viop-gateway",apiContract:"v1",version:GATEWAY_VERSION,
                       healthPath:"/v1/health",marketDataReady:false,quotesVerified:false});
    if(!pathname.startsWith("/v1/")) return fault(404,"API_ROUTE_NOT_FOUND");
    if(!process.env.BORSA_GATEWAY_APP_KEY) return fault(503,"PRIVATE_GATEWAY_KEY_NOT_CONFIGURED");
    if(!isAuthorized(req)) return fault(401,"INVALID_GATEWAY_APP_TOKEN");
    if(pathname==="/v1/health" || pathname==="/v1/readiness") {
      const query = new URLSearchParams(search);
      if ([...query.keys()].some(key => key !== "market") || query.getAll("market").length > 1)
        return fault(400, "HEALTH_QUERY_INVALID");
      const scope = query.get("market") || (pathname === "/v1/readiness" ? "ALL" : "VIOP");
      const result = await marketHealth(scope, {
        BIST: () => forwardMarket("/v1/health", ""),
        VIOP: () => viopData("/v1/health", "")
      });
      return json(result.status, result.body);
    }
    if(pathname==="/v1/news") return forwardMarket(pathname,search,"NEWS");
    if(/^\/v1\/bist\/news-risk\/[A-Z0-9]+$/.test(pathname)) return forwardMarket(pathname,search,"NEWS");
    if(pathname==="/v1/scanner/opportunities") return forwardMarket(pathname,search);
    if(["/v1/live","/v1/scanner/live","/v1/viop/live"].includes(pathname))
      return streamConfig(pathname) ? fault(426,"WEBSOCKET_UPGRADE_REQUIRED") : fault(503,"STREAM_PROVIDER_NOT_CONFIGURED");
    if(pathname==="/v1/bist/fundamentals") return financialData();
    if(pathname==="/v1/preflight" || pathname==="/v1/bist/symbols" ||
       pathname==="/v1/bist/snapshot-batch" ||
       /^\/v1\/bist\/(quote|history|history-window)\/[A-Z0-9]+$/.test(pathname))
      return forwardMarket(pathname,search);
    if(pathname==="/v1/viop/contracts" ||
       /^\/v1\/viop\/(quote|history)\/[A-Z0-9_.:-]+$/.test(pathname))
      return viopData(pathname,search);
    if(pathname==="/v1/viop/live") return fault(503,"VIOP_STREAM_NOT_CONFIGURED");
    return fault(404,"API_ROUTE_NOT_FOUND");
}
if (typeof Bun!=="undefined" && import.meta.main) {
  Bun.serve(gatewayServerOptions(handleRequest,isAuthorized));
}
