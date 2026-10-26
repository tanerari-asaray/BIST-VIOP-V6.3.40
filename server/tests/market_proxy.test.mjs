import {test,afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {handleRequest} from '../railway_gateway_function.ts';
import {gatewayServerOptions,streamConfig,trustedUpstream} from '../market_proxy.ts';
const oldFetch=globalThis.fetch;
const envNames=['BORSA_GATEWAY_APP_KEY','BORSA_BIST_UPSTREAM_URL','BORSA_BIST_UPSTREAM_TOKEN',
  'BORSA_NEWS_UPSTREAM_URL','BORSA_BIST_WSS_URL','BORSA_BIST_WSS_TOKEN'];
const saved=Object.fromEntries(envNames.map(k=>[k,process.env[k]]));
afterEach(()=>{globalThis.fetch=oldFetch;for(const k of envNames){if(saved[k]===undefined)delete process.env[k];else process.env[k]=saved[k];}});
function setup(){process.env.BORSA_GATEWAY_APP_KEY='test-app';process.env.BORSA_BIST_UPSTREAM_URL='https://provider.invalid';}
const req=path=>new Request('https://gateway.invalid'+path,{headers:{authorization:'Bearer test-app'}});
test('configured BIST quote reaches upstream and keeps its price/time unchanged',async()=>{
  setup();process.env.BORSA_BIST_UPSTREAM_TOKEN='server-only';
  const quote={symbol:'TEST',price:110,previousClose:100,exchangeTimestamp:Date.now(),source:'TEST',realtime:false,delaySeconds:900,currentSessionIncluded:false};
  globalThis.fetch=async(url,options)=>{assert.equal(new URL(url).pathname,'/v1/bist/quote/TEST');assert.equal(options.headers.authorization,'Bearer server-only');assert.equal(options.redirect,'error');return Response.json(quote);};
  const r=await handleRequest(req('/v1/bist/quote/TEST'));assert.equal(r.status,200);assert.deepEqual(await r.json(),quote);
});
test('BIST preflight is forwarded rather than manufactured from configured URL',async()=>{
  setup();const body={ok:false,authentication:{ok:true},symbols:{ok:false},quote:{ok:false},history:{ok:false},symbolCount:0};
  globalThis.fetch=async()=>Response.json(body);const r=await handleRequest(req('/v1/preflight'));assert.equal(r.status,200);assert.equal((await r.json()).ok,false);
});
test('wrong BIST symbol and nonfinite quote are rejected',async()=>{
  setup();globalThis.fetch=async()=>Response.json({symbol:'OTHER',price:110,exchangeTimestamp:Date.now(),source:'TEST',realtime:true,currentSessionIncluded:true});
  assert.equal((await handleRequest(req('/v1/bist/quote/TEST'))).status,502);
});
test('valid forming BIST history is passed through for chart/analysis separation',async()=>{
  setup();const time=Date.now()-300000;const body={symbol:'TEST',interval:'5m',exchangeTimestamp:Date.now(),lastBarClosed:false,
    candles:[{timestamp:time,open:10,high:11,low:9,close:10.5,volume:100}]};
  globalThis.fetch=async()=>Response.json(body);const r=await handleRequest(req('/v1/bist/history/TEST?interval=5m&range=1y'));
  assert.equal(r.status,200);assert.deepEqual(await r.json(),body);
});
test('malformed OHLC and duplicate symbols cannot masquerade as usable data',async()=>{
  setup();globalThis.fetch=async()=>Response.json({items:['TEST','TEST']});assert.equal((await handleRequest(req('/v1/bist/symbols'))).status,502);
});
test('news route is configured separately and preserves provider disclosure time',async()=>{
  setup();assert.equal((await handleRequest(req('/v1/news?category=ALL'))).status,503);
  process.env.BORSA_NEWS_UPSTREAM_URL='https://news.invalid';const body={items:[{title:'TEST',source:'TEST',publishedAt:Date.now()-1000}]};
  globalThis.fetch=async()=>Response.json(body);const r=await handleRequest(req('/v1/news?category=ALL'));assert.equal(r.status,200);assert.deepEqual(await r.json(),body);
});
test('scanner route preserves attestation envelope and does not invent a signal',async()=>{
  setup();const body={mode:'REALTIME_MARKET_ENGINE',realtime:true,tradingViewUsedAsMarketData:false,providerId:'TEST',snapshotHash:'abc',attestationKeyId:'key',generatedAt:Date.now(),serverTime:Date.now(),items:[]};
  globalThis.fetch=async()=>Response.json(body);const r=await handleRequest(req('/v1/scanner/opportunities?analysisTimeframeMinutes=5&scanCadenceMinutes=5&scanMode=MANUAL'));
  assert.equal(r.status,200);assert.deepEqual(await r.json(),body);
});
test('unauthorized and unknown query never reach a BIST upstream',async()=>{
  setup();let calls=0;globalThis.fetch=async()=>{calls++;return Response.json({});};
  assert.equal((await handleRequest(new Request('https://gateway.invalid/v1/bist/symbols'))).status,401);
  assert.equal((await handleRequest(req('/v1/bist/symbols?token=secret'))).status,400);assert.equal(calls,0);
});
for(const status of [401,403,429,503])test('BIST upstream HTTP '+status+' remains explicit',async()=>{
  setup();globalThis.fetch=async()=>new Response('{}',{status});assert.equal((await handleRequest(req('/v1/bist/symbols'))).status,status);
});
test('unsafe upstream addresses are refused and WSS is explicit',()=>{
  for(const v of ['http://x.invalid','https://localhost','https://127.0.0.1','https://u:p@x.invalid'])assert.equal(trustedUpstream(v),null);
  process.env.BORSA_BIST_WSS_URL='wss://stream.invalid/v1/live';assert.equal(streamConfig('/v1/live').url,'wss://stream.invalid/v1/live');
});
test('WSS bridge forwards read-only messages with upstream credentials only',async()=>{
  setup();process.env.BORSA_BIST_WSS_URL='wss://stream.invalid/v1/live';process.env.BORSA_BIST_WSS_TOKEN='server-stream';
  let socket;const sent=[];const options=gatewayServerOptions(handleRequest,()=>true,(url,headers)=>{
    assert.equal(headers.headers.Authorization,'Bearer server-stream');
    socket={readyState:1,send:x=>sent.push(x),close:()=>{}};queueMicrotask(()=>socket.onopen());return socket;
  });
  let data;const r=await options.fetch(new Request('https://gateway.invalid/v1/live',{headers:{upgrade:'websocket'}}),{upgrade:(_,input)=>{data=input.data;return true;}});
  assert.equal(r,undefined);const received=[];const ws={data,send:x=>received.push(x),close:()=>{}};options.websocket.open(ws);
  options.websocket.message(ws,JSON.stringify({action:'subscribe',symbols:['TEST']}));assert.equal(sent.length,1);
  socket.onmessage({data:'TEST MARKET EVENT'});assert.deepEqual(received,['TEST MARKET EVENT']);
  let code;options.websocket.message({...ws,close:x=>code=x},JSON.stringify({action:'order'}));assert.equal(code,1008);
});

test('news-risk endpoint preserves reviewed coverage without inventing a clear verdict',async()=>{
  setup();process.env.BORSA_NEWS_UPSTREAM_URL='https://news.invalid';
  const asOf=Date.now()-1000;
  const body={symbol:'ASELS',state:'CRITICAL',coverageThrough:asOf,availableAt:asOf-1,source:'TEST REVIEW',verified:true};
  globalThis.fetch=async url=>{assert.equal(new URL(url).searchParams.get('asOf'),String(asOf));return Response.json(body);};
  const r=await handleRequest(req('/v1/bist/news-risk/ASELS?asOf='+asOf));
  assert.equal(r.status,200);assert.deepEqual(await r.json(),body);
});
test('news-risk rejects unverified, future-known and mismatched coverage',async()=>{
  setup();process.env.BORSA_NEWS_UPSTREAM_URL='https://news.invalid';const asOf=Date.now()-1000;
  const base={symbol:'ASELS',state:'CLEAR',coverageThrough:asOf,availableAt:asOf-1,source:'TEST REVIEW',verified:true};
  for(const body of [{...base,verified:false},{...base,availableAt:asOf+1},{...base,symbol:'OTHER'},{...base,coverageThrough:asOf-1}]){
    globalThis.fetch=async()=>Response.json(body);
    assert.equal((await handleRequest(req('/v1/bist/news-risk/ASELS?asOf='+asOf))).status,502);
  }
});
test('unconfigured news-risk endpoint remains unavailable',async()=>{
  setup();assert.equal((await handleRequest(req('/v1/bist/news-risk/ASELS?asOf='+Date.now()))).status,503);
});
