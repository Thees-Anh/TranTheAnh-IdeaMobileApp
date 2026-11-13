import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
const pages=await(await fetch('http://127.0.0.1:9228/json')).json();
const ws=new WebSocket(pages[0].webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
let id=0;const pending=new Map();ws.onmessage=e=>{const v=JSON.parse(e.data);pending.get(v.id)?.(v)};
async function call(method,params={}){const n=++id;return await new Promise(r=>{pending.set(n,r);ws.send(JSON.stringify({id:n,method,params}))})}
async function evaluate(expression){const r=await call('Runtime.evaluate',{expression,returnByValue:true});assert(!r.result.exceptionDetails,JSON.stringify(r));return r.result.result.value}
let samples=0;
try {
for(const [key,prefix] of [['puppy','dog'],['capybara','capy'],['rabbit','bunny']]) {
 await call('Page.navigate',{url:'file:///'+path.resolve('docs/media/previews/'+key+'.html').replaceAll('\\','/')});
 await new Promise(r=>setTimeout(r,400));
 for(const height of [640,780,800,568]) {
  await evaluate(`window.pawquestResize(360,${height},60,100)`);
  for(const state of ['idle','eating','drinking','sleeping','laughing','sad']) {
   await evaluate(`window.pawquestRender({state:'${state}',equipment:['HEAD','FACE','NECK'],room:['BED','LAMP','CARPET']})`);
   for(let i=0;i<6;i++) {
    await new Promise(r=>setTimeout(r,120));
    const result=await evaluate(`(()=>{const s=document.querySelector('svg'),v=s.getBoundingClientRect(),h=document.getElementById('${prefix}-head-group');
      return ['${prefix}-whole','pq-head','pq-face','pq-neck'].map(id=>{const e=document.getElementById(id),r=e.getBoundingClientRect();return {id,visible:getComputedStyle(e).display!=='none',inside:r.left>=v.left-1&&r.right<=v.right+1&&r.top>=v.top-1&&r.bottom<=v.bottom+1,attached:id.startsWith('pq-')?h.contains(e):true}})})()`);
    assert(result.every(x=>x.visible&&x.inside&&x.attached),JSON.stringify({key,height,state,result}));samples++;
   }
  }
 }
 await evaluate("window.pawquestResize(800,660,0,0);window.pawquestRender({state:'idle',equipment:['HEAD','FACE','NECK']})");
 const capture=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync('build/'+key+'-equipment.png',Buffer.from(capture.result.data,'base64'));
 console.log('PASS',key,'six animation states with owned-slot presentation and four portrait ratios');
}
console.log('PASS',samples,'animation/framing samples');
} finally {ws.close()}
