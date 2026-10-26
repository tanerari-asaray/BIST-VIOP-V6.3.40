import {test,afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {handleRequest} from '../railway_gateway_function.ts';
const originalFetch=globalThis.fetch;
const key='test-only-not-a-production-credential';
function req(path='/v1/viop/contracts',token=key){return new Request('https://gateway.invalid'+path,{headers:{authorization:'Bearer '+token}});}
function configured(){process.env.BORSA_GATEWAY_APP_KEY=key;process.env.BORSA_VIOP_UPSTREAM_URL='https://licensed-provider.invalid';}
afterEach(()=>{globalThis.fetch=originalFetch;delete process.env.BORSA_GATEWAY_APP_KEY;delete process.env.BORSA_VIOP_UPSTREAM_URL;delete process.env.BORSA_VIOP_UPSTREAM_TOKEN;});
test('missing provider fails closed on all requested routes',async()=>{process.env.BORSA_GATEWAY_APP_KEY=key;for(const p of ['/v1/health','/v1/viop/contracts','/v1/viop/quote/F_TEST','/v1/viop/history/F_TEST']){const r=await handleRequest(req(p));assert.equal(r.status,503);assert.equal((await r.json()).detail.code,'LICENSED_VIOP_PROVIDER_NOT_CONNECTED');}});
test('wrong or unicode credential is rejected without crash',async()=>{configured();assert.equal((await handleRequest(req(undefined,'wrong'))).status,401);assert.equal((await handleRequest(req(undefined,'é'.repeat(key.length)))).status,401);});
for(const status of [401,403,429,503])test('upstream HTTP '+status+' is preserved',async()=>{configured();globalThis.fetch=async()=>new Response('{}',{status});assert.equal((await handleRequest(req())).status,status);});
test('network failure is unavailable',async()=>{configured();globalThis.fetch=async()=>{throw new TypeError('network')};assert.equal((await handleRequest(req())).status,503);});
test('timeout is explicit',async()=>{configured();globalThis.fetch=async()=>{throw new DOMException('timeout','TimeoutError')};assert.equal((await handleRequest(req())).status,504);});
test('empty contract list remains empty',async()=>{configured();globalThis.fetch=async()=>Response.json({items:[]});const r=await handleRequest(req());assert.equal(r.status,200);assert.deepEqual(await r.json(),{items:[]});});
test('malformed schema and invented stock-as-contract rejected',async()=>{configured();for(const data of [{}, {items:[{symbol:'THYAO'}]}]){globalThis.fetch=async()=>Response.json(data);assert.equal((await handleRequest(req())).status,502);}});
test('delayed quote retains original time and delay; upstream gets only its credential',async()=>{configured();process.env.BORSA_VIOP_UPSTREAM_TOKEN='test-upstream';const body={symbol:'F_TEST',price:12,exchangeTimestamp:Date.now()-900000,source:'TEST FIXTURE',realtime:false,delaySeconds:900};globalThis.fetch=async(url,options)=>{assert.equal(new URL(url).pathname,'/v1/viop/quote/F_TEST');assert.equal(options.headers.authorization,'Bearer test-upstream');assert.equal(options.redirect,'error');return Response.json(body);};const r=await handleRequest(req('/v1/viop/quote/F_TEST'));assert.equal(r.status,200);assert.deepEqual(await r.json(),body);});
test('quote identity mismatch rejected',async()=>{configured();globalThis.fetch=async()=>Response.json({symbol:'F_OTHER',price:12,exchangeTimestamp:Date.now(),source:'TEST',realtime:true,delaySeconds:0});assert.equal((await handleRequest(req('/v1/viop/quote/F_TEST'))).status,502);});
test('health does not infer readiness from HTTP 200',async()=>{configured();globalThis.fetch=async()=>Response.json({});assert.equal((await handleRequest(req('/v1/health'))).status,503);});
test('history validates OHLC and interval',async()=>{configured();globalThis.fetch=async()=>Response.json({symbol:'F_TEST',interval:'1d',exchangeTimestamp:Date.now(),lastBarClosed:true,candles:[{timestamp:Date.now()-86400000,open:2,high:3,low:1,close:4,volume:10}]});assert.equal((await handleRequest(req('/v1/viop/history/F_TEST?interval=1d&range=1y'))).status,502);});
test('non HTTPS upstream and unknown query rejected',async()=>{configured();process.env.BORSA_VIOP_UPSTREAM_URL='http://provider.invalid';assert.equal((await handleRequest(req())).status,503);configured();assert.equal((await handleRequest(req('/v1/viop/contracts?token=secret'))).status,400);});

test('VIOP health does not fall back to BIST',async()=>{
 configured();process.env.BORSA_BIST_UPSTREAM_URL='https://bist.invalid';
 globalThis.fetch=async url=>new URL(url).hostname==='bist.invalid'?Response.json({ok:true}):new Response('{}',{status:503});
 try{const r=await handleRequest(req('/v1/health'));assert.equal(r.status,503);assert.equal((await r.json()).scope,'VIOP');}
 finally{delete process.env.BORSA_BIST_UPSTREAM_URL;}
});
test('BIST scoped health is independent and scope is not forwarded',async()=>{
 process.env.BORSA_GATEWAY_APP_KEY=key;process.env.BORSA_BIST_UPSTREAM_URL='https://bist.invalid';
 globalThis.fetch=async url=>{assert.equal(new URL(url).search,'');return Response.json({ok:true});};
 try{const r=await handleRequest(req('/v1/health?market=BIST'));assert.equal(r.status,200);assert.equal((await r.json()).markets.VIOP.state,'NOT_TESTED');}
 finally{delete process.env.BORSA_BIST_UPSTREAM_URL;}
});
test('readiness reports both missing markets',async()=>{
 process.env.BORSA_GATEWAY_APP_KEY=key;
 const r=await handleRequest(req('/v1/readiness'));const body=await r.json();
 assert.equal(r.status,503);assert.equal(body.gatewayAuthenticated,true);
 assert.equal(body.markets.BIST.code,'LICENSED_BIST_PROVIDER_NOT_CONNECTED');
 assert.equal(body.markets.VIOP.code,'LICENSED_VIOP_PROVIDER_NOT_CONNECTED');
});
test('unknown or duplicate health query is rejected',async()=>{
 configured();for(const p of ['/v1/health?token=secret','/v1/health?market=BIST&market=VIOP','/v1/health?market=OTHER'])
 assert.equal((await handleRequest(req(p))).status,400);
});
test('actual upstream 500 remains in detail while gateway returns 502',async()=>{
 configured();globalThis.fetch=async()=>new Response('{}',{status:500});
 const r=await handleRequest(req());const body=await r.json();
 assert.equal(r.status,502);assert.equal(body.detail.code,'VIOP_UPSTREAM_HTTP_500');
 assert.equal(body.detail.upstreamHttpStatus,500);assert.equal(body.detail.stage,'PROVIDER');
});
test('gateway itself cannot be the VIOP upstream',async()=>{
 configured();process.env.RAILWAY_PUBLIC_DOMAIN='gateway.invalid';process.env.BORSA_VIOP_UPSTREAM_URL='https://gateway.invalid';
 try{const r=await handleRequest(req());assert.equal(r.status,503);assert.equal((await r.json()).detail.code,'VIOP_UPSTREAM_SELF_REFERENCE');}
 finally{delete process.env.RAILWAY_PUBLIC_DOMAIN;}
});

test('healthy VIOP health still does not certify quotes or trading',async()=>{
 configured();globalThis.fetch=async()=>Response.json({ok:true,viopReady:true});
 const r=await handleRequest(req('/v1/health?market=VIOP'));const body=await r.json();
 assert.equal(r.status,200);assert.equal(body.markets.VIOP.healthOk,true);
 assert.equal(body.markets.BIST.attempted,false);assert.equal(body.marketDataReady,false);
 assert.equal(body.quotesVerified,false);assert.equal(body.liveTradingVerified,false);
});
test('BIST health cannot borrow an available VIOP provider',async()=>{
 configured();let requests=0;globalThis.fetch=async()=>{requests++;return Response.json({ok:true,viopReady:true});};
 const r=await handleRequest(req('/v1/health?market=BIST'));const body=await r.json();
 assert.equal(r.status,503);assert.equal(body.detail.code,'LICENSED_BIST_PROVIDER_NOT_CONNECTED');
 assert.equal(requests,0);assert.equal(body.markets.VIOP.attempted,false);
});
