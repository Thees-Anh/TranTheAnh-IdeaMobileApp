// Node 22+; run against the debug WebView forwarded to localhost:9229.
// Visual renderer checks only: no backend, rewards, or user data mutations.
import assert from 'node:assert/strict';
const pages = await (await fetch('http://127.0.0.1:9229/json')).json();
const socket = new WebSocket(pages[0].webSocketDebuggerUrl);
await new Promise((resolve, reject) => {socket.onopen=resolve;socket.onerror=reject;});
let id=0;
const pending=new Map();
socket.onmessage=event=>{const response=JSON.parse(event.data);pending.get(response.id)?.(response);};
async function evaluate(expression) {
  const request=++id;
  const result=await new Promise(resolve=>{
    pending.set(request,resolve);
    socket.send(JSON.stringify({id:request,method:'Runtime.evaluate',params:{expression,returnByValue:true,awaitPromise:true}}));
  });
  pending.delete(request);
  assert(!result.result.exceptionDetails,JSON.stringify(result));
  return result.result.result.value;
}
try {
  const saved=await evaluate(`JSON.stringify({state:document.querySelector('svg').classList[0],
    equipment:['HEAD','FACE','NECK'].filter((s,i)=>!document.getElementById(['pq-head','pq-face','pq-neck'][i]).classList.contains('hidden')),
    room:['BED','LAMP','CARPET'].filter((s,i)=>!document.getElementById(['pq-bed','pq-lamp','pq-carpet'][i]).classList.contains('hidden'))})`);
  const source=await evaluate('document.documentElement.outerHTML');
  assert(!source.includes('cdn.tailwindcss'));
  assert(source.includes("connect-src 'none'"));
  assert(!source.includes('addJavascriptInterface'));
  let samples=0;
  for(const state of ['IDLE','HAPPY','EATING','SLEEPING','PLAYING','DRINKING']) {
    await evaluate(`window.pawquestRender(${JSON.stringify({state,equipment:['HEAD','FACE','NECK'],room:['BED','LAMP','CARPET']})})`);
    for(let sample=0;sample<8;sample++) {
      await new Promise(resolve=>setTimeout(resolve,125));
      const result=JSON.parse(await evaluate(`JSON.stringify((()=>{
        const svg=document.querySelector('svg').getBoundingClientRect();
        const ids=['cat-head-group','cat-body-group','cat-tail','pq-head','pq-face','pq-neck','pq-bed','pq-lamp','pq-carpet'];
        const vb=document.querySelector('svg').viewBox.baseVal;
        const cat=document.getElementById('cat-whole').getBoundingClientRect();
        return {ratio:vb.width/vb.height,catAboveControls:cat.bottom<=svg.bottom-svg.width/400*100,width:svg.width,height:svg.height,rootHeight:document.documentElement.getBoundingClientRect().height,bodyHeight:document.body.getBoundingClientRect().height,contained:ids.every(id=>{
          const b=document.getElementById(id).getBoundingClientRect();
          return b.width>0&&b.height>0&&b.left>=svg.left+2&&b.top>=svg.top+2&&b.right<=svg.right-2&&b.bottom<=svg.bottom-2;
        }),headParts:['pq-head','pq-face'].every(id=>document.getElementById('cat-head-group').contains(document.getElementById(id))),
        bodyPart:document.getElementById('cat-body-group').contains(document.getElementById('pq-neck'))};
      })())`));
      assert(result.width>0&&result.height>0, 'SVG must have a nonzero viewport');
      assert(result.rootHeight>=result.height-1&&result.bodyHeight>=result.height-1,'HTML roots must not clip the SVG');
      assert(Math.abs(result.width/result.height-result.ratio)<0.03,'Artwork proportions must be preserved');
      assert(result.catAboveControls,`${state}: cat must stay above the in-room action bar`);
      assert(result.contained,`${state}: cat and equipment/furniture should stay inside stage`);
      assert(result.headParts&&result.bodyPart,'Equipment must inherit animated parent transforms');
      samples++;
    }
  }
  await evaluate(`window.pawquestRender({state:'UNKNOWN',equipment:[],room:[]})`);
  assert(await evaluate(`document.querySelector('svg').classList.contains('state-idle')`));
  assert(await evaluate(`['pq-head','pq-face','pq-neck','pq-bed','pq-lamp','pq-carpet'].every(id=>document.getElementById(id).classList.contains('hidden'))`));
  const original=JSON.parse(saved);
  const states={'state-idle':'IDLE','state-eating':'EATING','state-laughing':'HAPPY','state-sleeping':'SLEEPING','state-drinking':'DRINKING'};
  await evaluate(`window.pawquestRender(${JSON.stringify({...original,state:states[original.state]||'IDLE'})})`);
  console.log(`PASS: ${samples} animation/framing samples, 6 states, inherited equipment transforms, unknown-state fallback, hidden unowned art, offline CSP.`);
} finally { socket.close(); }
