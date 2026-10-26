import {test,expect} from 'bun:test';
import {handleRequest,isAuthorized} from '../railway_gateway_function.ts';
import {gatewayServerOptions} from '../market_proxy.ts';

test('actual websocket handshake relays a market event and refuses an order action',async()=>{
  const names=['BORSA_GATEWAY_APP_KEY','BORSA_BIST_WSS_URL','BORSA_BIST_WSS_TOKEN'];
  const old=Object.fromEntries(names.map(k=>[k,process.env[k]]));
  process.env.BORSA_GATEWAY_APP_KEY='test-app';
  process.env.BORSA_BIST_WSS_URL='wss://provider.invalid/v1/live';
  process.env.BORSA_BIST_WSS_TOKEN='test-upstream';
  let seenAuth='';let client:WebSocket|undefined;
  const upstream=Bun.serve({port:0,fetch(req,server){seenAuth=req.headers.get('authorization')||'';
    if(server.upgrade(req))return;return new Response('upgrade required',{status:426});},
    websocket:{message(ws,message){const input=JSON.parse(message.toString());
      if(input.action==='subscribe')ws.send(JSON.stringify({type:'tick',symbol:'TEST',price:110,exchangeTimestamp:Date.now()}));}}
  });
  const options=gatewayServerOptions(handleRequest,isAuthorized,(url,config)=>{
    expect(url).toBe('wss://provider.invalid/v1/live');
    // Only the test transport points to local cleartext; production configuration remains WSS-only.
    return new WebSocket(`ws://127.0.0.1:${upstream.port}`,config);
  });
  const gateway=Bun.serve({...options,port:0});
  try {
    const received=await new Promise<any>((resolve,reject)=>{
      const timer=setTimeout(()=>reject(new Error('websocket event timeout')),5000);
      client=new WebSocket(`ws://127.0.0.1:${gateway.port}/v1/live`,{headers:{Authorization:'Bearer test-app'}});
      client.onopen=()=>client!.send(JSON.stringify({action:'subscribe',symbols:['TEST']}));
      client.onmessage=e=>{clearTimeout(timer);resolve(JSON.parse(e.data.toString()));};
      client.onerror=()=>{clearTimeout(timer);reject(new Error('handshake failed'));};
    });
    expect(received.symbol).toBe('TEST');expect(received.price).toBe(110);expect(seenAuth).toBe('Bearer test-upstream');
    const close=await new Promise<number>((resolve,reject)=>{
      const timer=setTimeout(()=>reject(new Error('read-only rejection timeout')),5000);
      client!.onclose=e=>{clearTimeout(timer);resolve(e.code);};client!.send(JSON.stringify({action:'order'}));
    });
    expect(close).toBe(1008);
  } finally {
    client?.close();gateway.stop(true);upstream.stop(true);
    for(const k of names){if(old[k]===undefined)delete process.env[k];else process.env[k]=old[k];}
  }
},15000);
