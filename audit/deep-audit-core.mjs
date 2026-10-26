// Reproducible audit of actual JS logic and selected Kotlin source contracts.
// This file does not compile or execute Kotlin/Android code.
const B = 'android-v6323/app/src/main/java/tr/borsatakip/v5/';
export async function runDeepAudit(readSource, diagnostics) {
  const {failureBody, marketHealth} = diagnostics;
  const matrixFailures = [];
  const check = (ok, scenario, rule) => { if (!ok) matrixFailures.push({scenario, rule}); };
  const now = 1792516633398;
  const modes = ['HEALTHY','MISSING','AUTH','NETWORK','BAD_JSON','FALSE_OK'];
  function fixture(market, mode) { return async () => {
    if (mode === 'NETWORK') throw Error('Bearer SYNTHETIC_AUDIT_SECRET');
    if (mode === 'BAD_JSON') return {status:200, json:async()=>{throw Error('SYNTHETIC_AUDIT_SECRET');}};
    if (mode === 'HEALTHY') return {status:200, json:async()=>({ok:true})};
    if (mode === 'FALSE_OK') return {status:200, json:async()=>({ok:false})};
    const status = mode === 'AUTH' ? 401 : 503;
    return {status, json:async()=>failureBody(status, mode === 'AUTH' ?
      market + '_UPSTREAM_HTTP_401' : 'LICENSED_' + market + '_PROVIDER_NOT_CONNECTED')};
  }; }
  let marketCases = 0;
  for (const scope of ['BIST','VIOP','ALL']) for (const bm of modes) for (const vm of modes) {
    marketCases++;
    const calls={BIST:0,VIOP:0};
    const probes={
      BIST:async()=>{calls.BIST++;return fixture('BIST',bm)();},
      VIOP:async()=>{calls.VIOP++;return fixture('VIOP',vm)();}
    };
    const result=await marketHealth(scope,probes,()=>now);
    const selected=scope==='ALL'?['BIST','VIOP']:[scope];
    const healthy=selected.every(m=>(m==='BIST'?bm:vm)==='HEALTHY');
    const scenario=[scope,bm,vm].join('/');
    check(result.body.ok===healthy,scenario,'health');
    check(result.body.marketDataReady===false && result.body.quotesVerified===false &&
      result.body.liveTradingVerified===false,scenario,'no quote/trading inference');
    check(result.body.serverTime===now,scenario,'injected time');
    check(!JSON.stringify(result).includes('SYNTHETIC_AUDIT_SECRET'),scenario,'no exception secrets');
    for(const m of ['BIST','VIOP']) {
      check(calls[m]===(selected.includes(m)?1:0),scenario,'scope call '+m);
      check(result.body.markets[m].attempted===selected.includes(m),scenario,'scope evidence '+m);
    }
    check(healthy?result.status===200:result.status>=400,scenario,'HTTP result');
  }
  let upstreamCases=0;
  for(const market of ['BIST','VIOP']) for(let status=400;status<=599;status++) {
    upstreamCases++;
    const value=failureBody(502,market+'_UPSTREAM_HTTP_'+status);
    check(value.detail.httpStatus===502,'upstream/'+market+'/'+status,'gateway HTTP');
    check(value.detail.upstreamHttpStatus===status,'upstream/'+market+'/'+status,'original HTTP');
    check(value.detail.stage===([401,403].includes(status)?'PROVIDER_AUTH':'PROVIDER'),
      'upstream/'+market+'/'+status,'failure stage');
    check(value.gatewayAuthenticated===true,'upstream/'+market+'/'+status,'gateway auth');
  }
  // Redaction is now a structured JSONObject/JSONArray transform.
  // Its native tests are authoritative; no JavaScript imitation of the Kotlin parser.
  function evaluateShortExpressions(path) {
    const source=readSource(B+path);
    const expressions=[...source.matchAll(/val (favorable|fav) = if \(isLong\) [^\n]+ else ([^\n]+)/g)];
    if(!expressions.length)throw Error('Short excursion changed; manually review the expression adapter');
    return expressions.map(match=>{
      const js=match[2].replaceAll('plan.entry','entry').replaceAll('c.low','low').replaceAll('b.low','low');
      const actual=new Function('entry','low','return '+js)(100,80);
      return {path,site:match[1],entry:100,low:80,expectedPct:20,actualPct:actual,
        conventionalEntryNotionalCorrect:Math.abs(actual-20)<1e-9};
    });
  }
  const runner=readSource(B+'worker/AutomaticScanRunner.kt');
  const compact=readSource(B+'ui/SettingsDetailActivity.kt');
  const policy=readSource(B+'worker/AutoScanUiPolicy.kt');
  const reportExpected=policy.match(/lastStatus ===? AutomaticScanRunner\.Outcome\.([A-Z_]+)\.name/)?.[1];
  const reportUsesSharedPolicy=compact.includes('AutoScanUiPolicy.isCompleted(runtime.lastAutoScanStatus)') &&
    readSource(B+'ui/SystemReportV2.kt').includes('AutoScanUiPolicy.isCompleted(runtime.lastAutoScanStatus)');
  const workerOutcomes=runner.match(/enum class Outcome\s*\{([^}]+)}/)?.[1].split(',').map(x=>x.trim())??[];
  return {
    jsDiagnostics:{marketScenarios:marketCases,upstreamHttpScenarios:upstreamCases,failures:matrixFailures},
    kotlinSourceExpressions:{execution:'V8 expression reproduction only; NOT Kotlin/JVM execution',
      redactionVerification:'NATIVE_JVM_TESTS_REQUIRED_NOT_EXECUTED_BY_THIS_SCRIPT',shortExcursion:[
        ...evaluateShortExpressions('analysis/TradeForwardEngine.kt'),
        ...evaluateShortExpressions('analysis/v540/backtest/V540BacktestEngine.kt')]},
    kotlinSourceContracts:{automaticSuccess:{reportExpected,workerOutcomes,
      reportUsesSharedPolicy,reportAcceptsAWorkerOutcome:reportUsesSharedPolicy && workerOutcomes.includes(reportExpected)}},
    nativeAndroidTests:'NOT_EXECUTED_BY_THIS_SCRIPT',
    apkBuildRequested:false
  };
}
