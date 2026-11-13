/* Display only. Native code confirms purchases; JS never changes economy or pet stats. */
(()=>{'use strict';
const assets=new Set(['food-bone-biscuit','food-fresh-carrot','food-warm-milk','food-taiyaki','food-onigiri','food-boba-tea','food-cat-pudding','food-ramen-bowl','food-salmon-sushi','food-berry-parfait','food-wagyu-steak','food-royal-hotpot']);
const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
let config=null,current=null,timer=null,phase='idle',items=new Map(),menuKey='';
const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const wrapper=document.getElementById('pet-stage-wrapper'),svg=document.getElementById('bistro-stage-svg');
function state(value){wrapper.setAttribute('class','state-'+value);['idle','eating','drinking','happy'].forEach(s=>svg.classList.remove('state-'+s));svg.classList.add('state-'+value);phase=value;}
function emit(event,itemId){if(config&&window.PawQuestBistroEvents)window.PawQuestBistroEvents.postMessage(JSON.stringify({version:1,viewId:config.viewId,event,itemId,transactionId:current?.transactionId}));}
function finish(){if(!current||phase==='complete')return;clearTimeout(timer);state('idle');phase='complete';document.getElementById('floating-buff-container').replaceChildren();emit('complete',current.itemId);}
function initialize(value){
 if(!value||value.version!==1||!uuid.test(value.viewId)||!Array.isArray(value.items)||value.items.length>12)return false;
 const next=new Map();for(const item of value.items){if(!uuid.test(item.itemId)||next.has(item.itemId)||!assets.has(item.assetId)||!['food','drink'].includes(item.type)||typeof item.name!=='string'||item.price<=0)return false;next.set(item.itemId,item);}
 config=value;items=next;
 const key=JSON.stringify([value.items,value.species,value.labels]);
 if(key!==menuKey){menuKey=key;document.getElementById('pet-actor-root').innerHTML=PET_TEMPLATES[value.species]||PET_TEMPLATES.cat;
  document.getElementById('menu').innerHTML=value.items.map(item=>`<article class="meal"><p>${escape(item.tier)}</p><svg viewBox="0 0 120 120" aria-hidden="true"><use href="#${item.assetId}"/></svg><h3>${escape(item.name)}</h3><p>+${item.hunger} ${escape(value.labels.fullness)} · +${item.happiness} ${escape(value.labels.joy)} · +${item.xp} EXP</p><button type="button" data-item="${item.itemId}">${escape(value.labels.buy)} · ${item.price} ${escape(value.labels.coins)}</button></article>`).join('');
 }
 document.querySelectorAll('button[data-item]').forEach(b=>{b.disabled=!value.canOrder;});return true;
}
function play(result){
 if(!config||!result||!uuid.test(result.transactionId)||!uuid.test(result.itemId)||!assets.has(result.assetId)||!['food','drink'].includes(result.type))return false;
 if(current?.transactionId===result.transactionId)return true;
 const item=result;clearTimeout(timer);current={transactionId:result.transactionId,itemId:result.itemId};
 const slot=document.getElementById('stage-food-slot');slot.innerHTML=`<use href="#${item.assetId}"/>`;
 document.getElementById('meal-status').textContent=item.name;
 const buff=document.createElement('span');buff.className='floating-buff-badge buff';
 buff.textContent=`+${Number(result.hunger)||0} ${config.labels.fullness} · +${Number(result.happiness)||0} ${config.labels.joy} · +${Number(result.xp)||0} EXP`;
 document.getElementById('floating-buff-container').replaceChildren(buff);
 document.getElementById('stage').scrollIntoView({behavior:'smooth',block:'start'});
 state(item.type==='drink'?'drinking':'eating');
 if(config.reducedMotion||window.matchMedia('(prefers-reduced-motion: reduce)').matches){finish();return true;}
 timer=setTimeout(()=>{if(item.type==='drink'){finish();return;}state('happy');timer=setTimeout(finish,2000);},3200);return true;
}
document.getElementById('menu').addEventListener('click',event=>{const button=event.target.closest('button[data-item]');if(button&&!button.disabled&&items.has(button.dataset.item))emit('select',button.dataset.item);});
window.PawQuestBistro=Object.freeze({initialize,play,skip:finish,pause:()=>{document.documentElement.classList.add('pq-paused');finish();},resume:()=>document.documentElement.classList.remove('pq-paused'),dispose:()=>{clearTimeout(timer);config=null;current=null;items.clear();},getState:()=>phase});
document.getElementById('stage-food-slot').replaceChildren();state('idle');window.pawquestBistroReady=true;
})();
