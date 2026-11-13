// Installed local-fixture debug WebView only, forwarded on 9229 by the native driver.
import assert from 'node:assert/strict';
const pages=await (await fetch('http://127.0.0.1:9229/json')).json();
const page=pages.find(p=>p.url.includes('pawquest.invalid'))||pages[0];
assert(page,'No offline pet renderer');
const ws=new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});
let id=0;const pending=new Map();
ws.onmessage=e=>{const v=JSON.parse(e.data);pending.get(v.id)?.(v)};
async function evaluate(expression){
 const n=++id;
 const r=await new Promise(resolve=>{pending.set(n,resolve);ws.send(JSON.stringify({id:n,method:'Runtime.evaluate',params:{expression,returnByValue:true}}))});
 pending.delete(n);assert(!r.result.exceptionDetails,JSON.stringify(r));return r.result.result.value;
}
try {
 assert(await evaluate(`!!document.getElementById('pawquest-dog-svg')`));
 const saved=await evaluate(`document.querySelector('svg').classList[0].replace('state-','')`);
 const nativeHeight=Number(process.argv[2]||10000),hudTop=Number(process.argv[3]||10000);
 let samples=0;
 for(const state of ['idle','eating','drinking','sleeping','laughing','sad']){
  await evaluate(`window.pawquestRender({state:${JSON.stringify(state)},room:['BED','LAMP','CARPET']})`);
  for(let i=0;i<16;i++){
   await new Promise(r=>setTimeout(r,250));
   const geometry=await evaluate(`(()=>{
    const svg=document.querySelector('svg'),r=svg.getBoundingClientRect(),v=svg.viewBox.baseVal;
    const ids=['dog-whole','dog-head-group','dog-body-group','dog-tail','pq-bed','pq-lamp','pq-carpet'];
    const puppy=document.getElementById('dog-whole').getBoundingClientRect();
    const extras=[...document.querySelectorAll('.prop-group,.fx-overlay')].filter(e=>getComputedStyle(e).display!=='none');
    const boxes=[...ids.map(id=>document.getElementById(id)),...extras].map(e=>{const b=e.getBoundingClientRect();return {id:e.id||e.getAttribute('class'),x:b.left,y:b.top,right:b.right,bottom:b.bottom}});
    const visible=[...document.querySelectorAll('.face-expr')].filter(e=>getComputedStyle(e).display!=='none').map(e=>e.getAttribute('class'));
    return {width:r.width,height:r.height,ratio:v.width/v.height,boxes,puppyBottom:puppy.bottom,visible,
     safeTop:r.width/400*Number(svg.dataset.safeTop||0),safeBottom:r.height-r.width/400*Number(svg.dataset.safeBottom||0)};
   })()`);
   assert(Math.abs(geometry.width/geometry.height-geometry.ratio)<.02,'Uniform aspect ratio');
   assert(geometry.visible.length===1&&geometry.visible[0].includes('face-'+state),'Exactly one correct expression');
   for(const b of geometry.boxes)assert(b.x>=0&&b.y>=0&&b.right<=geometry.width&&b.bottom<=Math.min(geometry.height,nativeHeight),`${state}: clipped ${JSON.stringify(b)}`);
   for(const b of geometry.boxes)assert(b.y>=geometry.safeTop&&b.bottom<=geometry.safeBottom,`${state}: speech/action overlay covers ${JSON.stringify(b)}`);
   assert(geometry.puppyBottom<hudTop,`${state}: Puppy overlaps native controls`);
   samples++;
  }
 }
 await evaluate(`window.pawquestRender({state:'invalid',room:[]})`);
 assert(await evaluate(`document.querySelector('svg').classList.contains('state-idle')`));
 assert(await evaluate(`['pq-bed','pq-lamp','pq-carpet'].every(id=>document.getElementById(id).classList.contains('hidden'))`));
 await evaluate(`window.pawquestRender({state:${JSON.stringify(saved)},room:[]})`);
 console.log(`PASS ${samples} Puppy animation/framing samples, six exact expressions, no stretching/cropping/control overlap, fallback and hidden furniture`);
}finally{ws.close()}
