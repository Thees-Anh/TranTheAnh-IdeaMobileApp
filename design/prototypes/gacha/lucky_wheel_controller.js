// Presentation adapter only: no payment, draw, ownership mutation or reward grants.
(() => {
    'use strict';
    const sectors = ['FOOD','COINS_50','PET_SHARDS_5','PET_SHARDS_10','LUCKY_TICKET_1','PET_SHARDS_20','PET_SHARDS_50','PET_UNLOCK'];
    const aliases = {rabbit:'bunny',puppy:'dog',capybara:'capy'};
    const rewardTypes = {FOOD:'FOOD',COINS_50:'COINS',PET_SHARDS_5:'PET_SHARDS',PET_SHARDS_10:'PET_SHARDS',
        LUCKY_TICKET_1:'TICKET',PET_SHARDS_20:'PET_SHARDS',PET_SHARDS_50:'PET_SHARDS'};
    let config = null, result = null, frame = 0, timer = 0, generation = 0, rotation = 0, complete = false;
    const el = id => document.getElementById(id);
    const reduce = () => config?.reducedMotion || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    function resize() {
        // Chromium in a Compose lazy layout can cache vh/% at zero even after innerHeight updates.
        // Use its actual local viewport; no fixed phone dimensions or economic state are involved.
        const pixels=Math.max(1,window.innerHeight || 1), height=pixels+'px';
        [document.documentElement,document.body,document.querySelector('main')].forEach(node=>{
            if(node.style.height!==height) node.style.height=height;
        });
        // Reserve the status caption's row so it cannot cover the original wheel stand.
        el('lucky-wheel-svg').style.height=Math.max(1,pixels-22)+'px';
    }
    function cancel() { generation++; cancelAnimationFrame(frame); clearTimeout(timer); frame = timer = 0; }
    function notify(event) {
        if (!result) return;
        window.PawQuestWheelEvents?.postMessage(JSON.stringify({version:1,event,playbackId:config.playbackId,
            transactionId:result.transactionId,sectorCode:result.sectorCode,selectedPetId:result.selectedPetId ?? null}));
    }
    function highlight(key) {
        ['bunny','dog','capy'].forEach(k => el(`roulette-card-${k}`).classList.toggle('roulette-active-card',k===key));
    }
    function targetAngle(index) {
        const target = (360-index*45)%360;
        let delta = (target-((rotation%360)+360)%360+360)%360;
        if (delta<90) delta+=360;
        return rotation+2160+delta;
    }
    function showFinal() {
        if (!result) return;
        const index = sectors.indexOf(result.sectorCode);
        rotation = ((360-index*45)%360+360)%360;
        el('lucky-wheel-rotor').style.transform=`rotate(${rotation}deg)`;
        el('wheel-top-pointer').style.transform='rotate(0deg)';
        el('wheel-status-banner').textContent=WHEEL_SLOTS[index].shortLabel;
        if (result.actualReward==='PET_UNLOCKED') {
            el('pet-unlock-stage').hidden=false;
            highlight(aliases[result.selectedPetId]);
            el('jackpot-final-message').textContent=`Unlocked: ${result.selectedPetId==='puppy' ? 'Dog' : result.selectedPetId}`;
        } else if (result.actualReward==='PET_UNLOCK_CONVERTED_TO_SHARDS') {
            el('pet-unlock-stage').hidden=false; highlight(null);
            el('jackpot-final-message').textContent=`All pets owned · +${result.petShardsEarned} Pet Shards`;
        }
    }
    function finish() {
        if (!result || complete) return;
        cancel(); showFinal(); complete=true; notify('complete');
    }
    function petStage(token) {
        el('pet-unlock-stage').hidden=false;
        el('jackpot-final-message').textContent='Your new companion…';
        let step=0;
        function cycle() {
            if(token!==generation || complete) return;
            highlight(['bunny','dog','capy'][step%3]); step++;
            if(step>=14) finish(); else timer=setTimeout(cycle,110);
        }
        timer=setTimeout(cycle,110);
    }
    const api = {
        initialize(value) {
            if (!value || value.version!==1 || typeof value.playbackId!=='string') return false;
            if(value.sectors!=null) {
                if(!Array.isArray(value.sectors) || value.sectors.length!==8 || new Set(value.sectors.map(s=>s.sectorCode)).size!==8 ||
                    value.sectors.some(s=>!sectors.includes(s.sectorCode) || !Number.isInteger(s.weight) || s.weight<1 || !Number.isInteger(s.amount) || s.amount<1) ||
                    value.sectors.reduce((n,s)=>n+s.weight,0)!==10000) return false;
                value.sectors.forEach(s=>{
                    const slot=WHEEL_SLOTS[sectors.indexOf(s.sectorCode)];
                    slot.probability=s.weight/100;
                    slot.shortLabel=s.sectorCode==='FOOD' ? `${s.amount} Food` : s.sectorCode==='COINS_50' ? `${s.amount} Coins` :
                        s.sectorCode==='LUCKY_TICKET_1' ? `${s.amount} Ticket` : s.sectorCode==='PET_UNLOCK' ? 'Pet Unlock' : `${s.amount} Shards`;
                });
            }
            config=value;resize();
            document.documentElement.classList.toggle('reduced-motion',Boolean(reduce()));
            renderWheelSVG(); return true;
        },
        play(value) {
            if(!config || !value || typeof value.transactionId!=='string' || !sectors.includes(value.sectorCode)) return false;
            const isPet=value.actualReward==='PET_UNLOCKED', isFallback=value.actualReward==='PET_UNLOCK_CONVERTED_TO_SHARDS';
            if((isPet || isFallback)!==(value.sectorCode==='PET_UNLOCK') || isPet && !Object.prototype.hasOwnProperty.call(aliases,value.selectedPetId) ||
                !isPet && value.selectedPetId!=null || isFallback && (!Number.isInteger(value.petShardsEarned) || value.petShardsEarned<1)) return false;
            if(value.sectorCode!=='PET_UNLOCK' && value.actualReward!==rewardTypes[value.sectorCode]) return false;
            if(result) return result.transactionId===value.transactionId && result.sectorCode===value.sectorCode && result.selectedPetId===value.selectedPetId;
            result={...value};complete=false;cancel();const token=generation;
            if(reduce()) {finish();return true;}
            const rotor=el('lucky-wheel-rotor'), pointer=el('wheel-top-pointer');
            const startAngle=rotation, finalAngle=targetAngle(sectors.indexOf(result.sectorCode));
            let start=null, previous=startAngle;
            function step(timestamp) {
                if(token!==generation || complete) return;
                resize();
                if(start===null) start=timestamp;
                const t=Math.min((timestamp-start)/5500,1);
                const angle=startAngle+(finalAngle-startAngle)*smoothDecelerationEaseOut(t);
                rotor.style.transform=`rotate(${angle.toFixed(2)}deg)`;
                pointer.style.transform=`rotate(${computePointerPinBendAngle(angle,angle-previous).toFixed(2)}deg)`;
                rotation=angle;previous=angle;
                el('wheel-status-banner').textContent=t<.62 ? 'Spinning…' : 'Slowing down…';
                if(t<1) frame=requestAnimationFrame(step);
                else {
                    pointer.style.transform='rotate(0deg)';notify('wheelStopped');
                    if(isPet) timer=setTimeout(()=>{if(token===generation) petStage(token);},300);
                    else timer=setTimeout(()=>{if(token===generation) finish();},300);
                }
            }
            frame=requestAnimationFrame(step); return true;
        },
        skip() {finish();return true;},
        reset() {cancel();result=null;complete=false;el('pet-unlock-stage').hidden=true;highlight(null);el('wheel-top-pointer').style.transform='rotate(0deg)';return true;},
        resize,
        pause() {finish();},
        dispose() {cancel();window.removeEventListener('resize',resize);config=null;result=null;}
    };
    ['bunny','dog','capy'].forEach(k=>{el(`modal-svg-${k}`).innerHTML=UNLOCKABLE_PETS[k].svg;});
    renderWheelSVG();
    window.addEventListener('resize',resize);resize();
    window.PawQuestLuckyWheel=Object.freeze(api);
    window.pawquestLuckyWheelReady=true;
})();
