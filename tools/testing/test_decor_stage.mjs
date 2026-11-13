import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';
const items=JSON.parse(fs.readFileSync('design/assets/decor/catalog.json','utf8')),accessories=items.filter(i=>i.category==='ACCESSORY'),room=items.filter(i=>i.category==='FURNITURE').map(i=>i.id);
const pages=await(await fetch('http://127.0.0.1:9228/json')).json();const ws=new WebSocket(pages[0].webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
let id=0;const pending=new Map();ws.onmessage=e=>{const v=JSON.parse(e.data);pending.get(v.id)?.(v)};
async function call(method,params={}){const n=++id;return await new Promise(r=>{pending.set(n,r);ws.send(JSON.stringify({id:n,method,params}))})}
async function evaluate(expression){const r=await call('Runtime.evaluate',{expression,returnByValue:true});assert(!r.result.exceptionDetails,JSON.stringify(r));return r.result.result.value}
let samples=0;
try {
for(const [key,prefix] of [['prototype','cat'],['puppy','dog'],['capybara','capy'],['rabbit','bunny']]){
 await call('Page.navigate',{url:'file:///'+path.resolve('shared/src/androidMain/assets/pet/'+key+'_stage.html').replaceAll('\\','/')});
 for(let n=0;n<40;n++){if(await evaluate(`!!window.pawquestRender && !!document.getElementById('acc-crown')`))break;await new Promise(r=>setTimeout(r,50));}
 for(const [width,height] of [[360,640],[360,780],[360,800],[320,568]]) {
  await call('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:true});
  await evaluate(`window.pawquestResize(${width},${height},60,100)`);
  for(const raw of ['idle','eating','drinking','sleeping','laughing','sad']) {
   const state=key==='prototype'?({idle:'IDLE',eating:'EATING',drinking:'DRINKING',sleeping:'SLEEPING',laughing:'HAPPY',sad:'PLAYING'})[raw]:raw;
   for(const item of accessories) {
    await evaluate(`window.pawquestRender(${JSON.stringify({state,equipment:[item.id],room})})`);await new Promise(r=>setTimeout(r,35));
    const result=await evaluate(`(()=>{
      const s=document.querySelector('svg'),v=s.getBoundingClientRect();
      const ids=${JSON.stringify([item.id,...room,prefix+'-whole'])};
      return ids.map(id=>{const e=document.getElementById(id),r=e.getBoundingClientRect();return {id,visible:getComputedStyle(e).display!=='none',inside:r.left>=v.left-2&&r.right<=v.right+2&&r.top>=v.top+58&&r.bottom<=v.bottom-98}});
    })()`);
    assert(result.every(i=>i.visible&&i.inside),JSON.stringify({key,width,height,state,item:item.id,result}));samples++;
    assert(await evaluate(`document.getElementById('${prefix}-${['HEAD','FACE'].includes(item.slot)?'head':'body'}-group').contains(document.getElementById('${item.id}'))`));
   }
  }
 }
 await call('Emulation.setDeviceMetricsOverride',{width:360,height:800,deviceScaleFactor:2,mobile:true});
 await evaluate(`window.pawquestResize(360,800,60,100);window.pawquestRender(${JSON.stringify({state:key==='prototype'?'IDLE':'idle',equipment:['acc-wizard-hat','acc-glasses','acc-bowtie','acc-wings'],room})})`);
 const image=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync('build/decor-'+key+'.png',Buffer.from(image.result.data,'base64'));
 console.log('PASS',key,'10 accessories, 10 furniture, six states, four portrait profiles');
}
console.log('PASS',samples,'scene cases with HUD-safe bounds and rig attachment');
}finally{ws.close()}
