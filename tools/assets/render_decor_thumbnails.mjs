import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';
const items=JSON.parse(fs.readFileSync('design/assets/decor/catalog.json','utf8'));
const pages=await(await fetch('http://127.0.0.1:9228/json')).json();const ws=new WebSocket(pages[0].webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
let id=0;const pending=new Map();ws.onmessage=e=>{const v=JSON.parse(e.data);pending.get(v.id)?.(v)};
async function call(method,params={}){const n=++id;return await new Promise(r=>{pending.set(n,r);ws.send(JSON.stringify({id:n,method,params}))})}
try {
await call('Emulation.setDeviceMetricsOverride',{width:224,height:192,deviceScaleFactor:2,mobile:false});
for(const i of items){
 await call('Page.navigate',{url:'file:///'+path.resolve('build/decor-thumbnails/'+i.code+'.html').replaceAll('\\','/')});
 for(let n=0;n<40;n++){const ready=await call('Runtime.evaluate',{expression:`!!document.getElementById('${i.id}')`,returnByValue:true});if(ready.result?.result?.value)break;await new Promise(r=>setTimeout(r,50));}
 const r=await call('Runtime.evaluate',{expression:`(()=>{const svg=document.querySelector('svg'),b=document.getElementById('${i.id}').getBBox();svg.setAttribute('viewBox',[b.x-12,b.y-12,b.width+24,b.height+24].join(' '));return b.width>0&&b.height>0})()`,returnByValue:true});assert(r.result.result.value,i.id);
 await new Promise(r=>setTimeout(r,30));const p=await call('Page.captureScreenshot',{format:'png'});
 fs.writeFileSync('shared/src/commonMain/composeResources/drawable/decor_'+i.code+'.png',Buffer.from(p.result.data,'base64'));
}
console.log('PASS 20 supplied-art thumbnails rendered');
}finally{ws.close()}
