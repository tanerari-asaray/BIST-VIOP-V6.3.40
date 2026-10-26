import {test} from 'node:test';
import assert from 'node:assert/strict';
import {failureBody, marketHealth} from '../gateway_diagnostics.mjs';

const response = (status, body) => ({status, async json() { return body; }});
const healthy = () => Promise.resolve(response(200, {ok:true}));
const missing = market => () => Promise.resolve(response(503, failureBody(503, 'LICENSED_'+market+'_PROVIDER_NOT_CONNECTED')));

test('VIOP health cannot be rescued by healthy BIST', async () => {
  let called = false;
  const r = await marketHealth('VIOP', {VIOP:missing('VIOP'), BIST:() => {called=true; return healthy();}});
  assert.equal(r.status,503); assert.equal(called,false);
  assert.equal(r.body.markets.BIST.state,'NOT_TESTED');
  assert.equal(r.body.gatewayReady,true); assert.equal(r.body.gatewayAuthenticated,true);
});
test('BIST works independently when VIOP is absent', async () => {
  const r=await marketHealth('BIST',{BIST:healthy,VIOP:missing('VIOP')});
  assert.equal(r.status,200); assert.equal(r.body.markets.BIST.healthOk,true);
  assert.equal(r.body.markets.VIOP.attempted,false);
});
test('VIOP works independently when BIST is absent', async () => {
  const r=await marketHealth('VIOP',{BIST:missing('BIST'),VIOP:healthy});
  assert.equal(r.status,200); assert.equal(r.body.markets.BIST.attempted,false);
});
test('ALL records both missing providers rather than stopping at the first', async () => {
  const r=await marketHealth('ALL',{BIST:missing('BIST'),VIOP:missing('VIOP')});
  assert.equal(r.status,503); assert.equal(r.body.markets.BIST.attempted,true);
  assert.equal(r.body.markets.VIOP.attempted,true);
  assert.equal(r.body.markets.BIST.code,'LICENSED_BIST_PROVIDER_NOT_CONNECTED');
  assert.equal(r.body.markets.VIOP.code,'LICENSED_VIOP_PROVIDER_NOT_CONNECTED');
});
test('ALL preserves successful BIST alongside failed VIOP', async () => {
  const r=await marketHealth('ALL',{BIST:healthy,VIOP:missing('VIOP')});
  assert.equal(r.status,503); assert.equal(r.body.ok,false);
  assert.equal(r.body.markets.BIST.healthOk,true); assert.equal(r.body.markets.VIOP.healthOk,false);
});
test('ALL preserves successful VIOP alongside failed BIST', async () => {
  const r=await marketHealth('ALL',{BIST:missing('BIST'),VIOP:healthy});
  assert.equal(r.body.markets.BIST.healthOk,false); assert.equal(r.body.markets.VIOP.healthOk,true);
});
test('health success never certifies quotes, market readiness or trading', async () => {
  const r=await marketHealth('ALL',{BIST:healthy,VIOP:healthy},()=>1792516633398);
  assert.equal(r.status,200); assert.equal(r.body.serverTime,1792516633398);
  assert.equal(r.body.marketDataReady,false); assert.equal(r.body.quotesVerified,false);
  assert.equal(r.body.liveTradingVerified,false);
});
test('HTTP 200 without explicit health success fails closed', async () => {
  const r=await marketHealth('VIOP',{VIOP:()=>response(200,{})});
  assert.equal(r.status,502); assert.equal(r.body.ok,false);
});
test('provider not ready remains failed despite HTTP 200', async () => {
  const r=await marketHealth('BIST',{BIST:()=>response(200,{ok:false})});
  assert.equal(r.status,502); assert.equal(r.body.markets.BIST.healthOk,false);
});
test('bad JSON is a provider failure and does not suppress the other result', async () => {
  const r=await marketHealth('ALL',{BIST:()=>({status:200,json:async()=>{throw new SyntaxError('bad');}}),VIOP:healthy});
  assert.equal(r.body.markets.BIST.state,'ERROR'); assert.equal(r.body.markets.VIOP.healthOk,true);
});
test('network failure records a bounded error code without exception text', async () => {
  const r=await marketHealth('VIOP',{VIOP:()=>{throw new Error('Bearer PRIVATE_TEST_SECRET');}});
  assert.equal(r.body.detail.code,'VIOP_UPSTREAM_UNAVAILABLE');
  assert.equal(JSON.stringify(r).includes('PRIVATE_TEST_SECRET'),false);
});
test('invalid scope is rejected before any provider is called', async () => {
  let called=false;
  const r=await marketHealth('SECRET',{BIST:()=>{called=true;},VIOP:()=>{called=true;}});
  assert.equal(r.status,400); assert.equal(called,false);
});
test('arbitrary provider error code and message cannot enter reports', async () => {
  const r=await marketHealth('VIOP',{VIOP:()=>response(503,{detail:{code:'PRIVATE_TOKEN_123',message:'Bearer PRIVATE_TEST_SECRET'}})});
  assert.equal(r.body.detail.code,'VIOP_UPSTREAM_NOT_READY');
  assert.equal(JSON.stringify(r).includes('PRIVATE_TEST_SECRET'),false);
  assert.equal(JSON.stringify(r).includes('PRIVATE_TOKEN_123'),false);
});
for (const status of [401,403,429,500,502,503,504]) {
  test('upstream HTTP '+status+' is retained separately from gateway status', async () => {
    const body=failureBody(502,'VIOP_UPSTREAM_HTTP_'+status);
    assert.equal(body.detail.httpStatus,502); assert.equal(body.detail.upstreamHttpStatus,status);
    assert.equal(body.detail.stage,[401,403].includes(status)?'PROVIDER_AUTH':'PROVIDER');
  });
}
test('gateway authentication and provider authentication remain distinct', () => {
  assert.equal(failureBody(401,'INVALID_GATEWAY_APP_TOKEN').detail.stage,'GATEWAY_AUTH');
  assert.equal(failureBody(401,'BIST_UPSTREAM_HTTP_401').detail.stage,'PROVIDER_AUTH');
});
test('missing credentials are configuration failure rather than data verification', () => {
  assert.equal(failureBody(503,'PRIVATE_GATEWAY_KEY_NOT_CONFIGURED').detail.stage,'GATEWAY_CONFIG');
  assert.equal(failureBody(503,'LICENSED_BIST_PROVIDER_NOT_CONNECTED').detail.stage,'PROVIDER_CONFIG');
});
test('HTTP 503 provider body preserves stage and safe explanation', async () => {
  const r=await marketHealth('VIOP',{VIOP:missing('VIOP')});
  assert.equal(r.body.detail.stage,'PROVIDER_CONFIG');
  assert.match(r.body.detail.message,/VİOP/);
});
test('invalid provider URL is not labeled as wrong application password', () => {
  assert.equal(failureBody(503,'VIOP_UPSTREAM_URL_INVALID').detail.stage,'PROVIDER_CONFIG');
});
test('gateway self-reference is explained as provider configuration', () => {
  const r=failureBody(503,'VIOP_UPSTREAM_SELF_REFERENCE');
  assert.equal(r.detail.stage,'PROVIDER_CONFIG'); assert.match(r.detail.message,/kendi adresi/);
});
test('invalid or oversized error code is replaced', () => {
  assert.equal(failureBody(503,'Bearer SECRET').detail.code,'UPSTREAM_UNAVAILABLE');
  assert.equal(failureBody(503,'A'.repeat(97)).detail.code,'UPSTREAM_UNAVAILABLE');
});

test('provider authentication failure still proves gateway authentication passed',()=>{
  assert.equal(failureBody(401,'VIOP_UPSTREAM_HTTP_401').gatewayAuthenticated,true);
  assert.equal(failureBody(401,'INVALID_GATEWAY_APP_TOKEN').gatewayAuthenticated,false);
});
