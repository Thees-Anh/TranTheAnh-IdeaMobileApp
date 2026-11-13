// Deterministic renderer contract tests, no backend or financial operations.
import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const html=fs.readFileSync('shared/src/androidMain/assets/gacha/lucky_wheel.html','utf8');
assert(!/Math\.random|playerResources|onclick=|rollWeightedSlotIndex|craftPetWithShards|https:\/\/cdn|fonts.googleapis/.test(html));
assert(html.includes('connect-src &#')===false && html.includes("connect-src 'none'"));
const nodes=new Map();
function node(id) {if(!nodes.has(id)) nodes.set(id,{style:{},textContent:'',hidden:false,innerHTML:'',classes:new Set(),classList:{toggle(name,on){const n=node(id);on?n.classes.add(name):n.classes.delete(name);}}});return nodes.get(id);}
let clock=0,next=0;const work=new Map(),events=[];
const schedule=(callback,delay)=>{const id=++next;work.set(id,{callback,time:clock+delay});return id;};
const context=vm.createContext({console,document:{getElementById:node,documentElement:node('root'),body:node('body'),querySelector:()=>node('main')},
    window:{innerHeight:330,addEventListener(){},removeEventListener(){},matchMedia:()=>({matches:false}),PawQuestWheelEvents:{postMessage:data=>events.push(JSON.parse(data))}},
    requestAnimationFrame:callback=>schedule(()=>callback(clock),16),cancelAnimationFrame:id=>work.delete(id),
    setTimeout:(callback,delay)=>schedule(callback,delay),clearTimeout:id=>work.delete(id)});
vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],context);
const api=context.window.PawQuestLuckyWheel;
const codes=['FOOD','COINS_50','PET_SHARDS_5','PET_SHARDS_10','LUCKY_TICKET_1','PET_SHARDS_20','PET_SHARDS_50','PET_UNLOCK'];
const config={version:1,playbackId:'test-playback',sectors:codes.map((code,i)=>({sectorCode:code,weight:[2500,2000,1800,1200,1000,800,500,200][i],amount:[1,50,5,10,1,20,50,1][i]}))};
function advance(ms) {const target=clock+ms;let n=0;while(work.size) {const [id,w]=[...work.entries()].sort((a,b)=>a[1].time-b[1].time)[0];if(w.time>target)break;assert(n++<10000);work.delete(id);clock=w.time;w.callback();}clock=target;}
function result(i,pet=null) {return {transactionId:`transaction-${next}-${i}`,sectorCode:codes[i],selectedPetId:pet,
    actualReward:i===7 ? pet ? 'PET_UNLOCKED':'PET_UNLOCK_CONVERTED_TO_SHARDS' : ['FOOD','COINS','PET_SHARDS','PET_SHARDS','TICKET','PET_SHARDS','PET_SHARDS'][i],petShardsEarned:50};}
assert(api.initialize(config));
for(let i=0;i<8;i++) {
    api.reset();events.length=0;const r=result(i,i===7?'rabbit':null);
    assert(api.play(r));assert(api.play(r));assert(!api.play({...r,transactionId:'another'}));
    advance(7800);
    assert.equal(events.filter(e=>e.event==='complete').length,1);
    assert.equal(events.at(-1).sectorCode,codes[i]);
    const angle=Number(node('lucky-wheel-rotor').style.transform.match(/rotate\((.+)deg/)[1]);
    assert.equal(angle,(360-i*45)%360,'sector centered precisely at 12 o’clock');
    assert.equal(node('wheel-top-pointer').style.transform,'rotate(0deg)');
    api.skip();assert.equal(events.filter(e=>e.event==='complete').length,1);
}
console.log('PASS all 8 sectors, exact pointer centers, multiple openings and idempotent completion');
for(const [pet,alias] of [['rabbit','bunny'],['puppy','dog'],['capybara','capy']]) {
    api.reset();events.length=0;assert(api.play(result(7,pet)));advance(5900);
    assert(events.some(e=>e.event==='wheelStopped'));assert(!events.some(e=>e.event==='complete'),'pet stage has not completed early');
    advance(1800);assert.equal(events.at(-1).selectedPetId,pet);
    assert(node('roulette-card-'+alias).classes.has('roulette-active-card'));
    assert.equal(['bunny','dog','capy'].filter(k=>node('roulette-card-'+k).classes.has('roulette-active-card')).length,1);
}
api.reset();events.length=0;assert(api.play(result(7)));advance(6000);
assert(node('jackpot-final-message').textContent.includes('+50 Pet Shards'));
assert(!['bunny','dog','capy'].some(k=>node('roulette-card-'+k).classes.has('roulette-active-card')));
assert.equal(events.at(-1).sectorCode,'PET_UNLOCK');
console.log('PASS two-stage exact selected pets and all-owned replacement without false pet grant');
api.reset();events.length=0;assert(api.play(result(7,'puppy')));advance(100);api.skip();advance(9000);
assert.equal(events.filter(e=>e.event==='complete').length,1);assert(node('roulette-card-dog').classes.has('roulette-active-card'));
api.reset();events.length=0;assert(api.play(result(0)));advance(100);api.reset();advance(9000);assert.equal(events.length,0);
assert(!api.play({...result(7),selectedPetId:'mochi_cat',actualReward:'PET_UNLOCKED'}));
assert(!api.play({...result(7),selectedPetId:'toString',actualReward:'PET_UNLOCKED'}));
assert(!api.play({...result(0),actualReward:'PET_UNLOCKED'}));
assert(!api.play({...result(0),sectorCode:'UNKNOWN'}));
assert(api.initialize({...config,reducedMotion:true}));events.length=0;assert(api.play(result(7,'capybara')));
assert.equal(events.at(-1).event,'complete');assert.equal(work.size,0);
api.dispose();assert(!api.play(result(0)));
console.log('PASS skip, cancellation/reset, invalid IDs, reduced motion, disposal and offline/no-authority renderer');
