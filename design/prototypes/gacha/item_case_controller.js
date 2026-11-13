/* Presentation adapter for the preserved PawQuest chest/reel design.
 * No purchases, currency changes, ownership checks, grants or outcome RNG. */
(() => {
    'use strict';
    const VERSION = 1;
    const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
    const track = document.getElementById('cs2-reel-track');
    const viewport = document.getElementById('cs2-viewport-box');
    const chest = document.getElementById('chest-svg-container');
    const visuals = new Map(GACHA_ITEMS_POOL.map(item => [item.id, item]));
    let items = new Map(), config = null, current = null, phase = 'idle';
    let shakeTimer = null, finishTimer = null, frame = null, transitionDone = null;
    const escapeText = text => text.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

    function clean() {
        clearTimeout(shakeTimer); clearTimeout(finishTimer); cancelAnimationFrame(frame);
        if (transitionDone) track.removeEventListener('transitionend', transitionDone);
        shakeTimer = finishTimer = frame = transitionDone = null;
    }
    function emit(event, reason) {
        const message = {version: VERSION, event, reason, playbackId: config.playbackId,
            transactionId: current.transactionId, winningItemId: current.winningItemId};
        // Origin/main-frame validation is performed by the native message listener.
        if (window.PawQuestCaseEvents) window.PawQuestCaseEvents.postMessage(JSON.stringify(message));
    }
    function centerWinner() {
        const card = document.getElementById(`reel-card-${WINNING_CARD_INDEX}`);
        if (!card || !current || card.dataset.itemId !== current.winningItemId) throw Error('winner_missing');
        track.style.transition = 'none';
        track.style.transform = 'translateX(0px)';
        const target = card.getBoundingClientRect();
        const indicator = document.querySelector('.cs2-center-laser').getBoundingClientRect();
        return (indicator.left + indicator.width / 2) - (target.left + target.width / 2);
    }
    function finish(reason) {
        if (!current || phase === 'complete' || phase === 'error') return;
        clean();
        try {
            const position = centerWinner();
            track.style.transform = `translateX(${position}px)`;
            document.getElementById(`reel-card-${WINNING_CARD_INDEX}`).classList.add('winning-card-highlight');
            chest.classList.remove('chest-state-idle', 'chest-state-shaking');
            chest.classList.add('chest-state-opened');
            phase = 'complete';
            emit('complete', reason);
        } catch (_) { phase = 'error'; emit('error', 'layout_unavailable'); }
    }
    function initialize(value) {
        if (current || !value || value.version !== VERSION || !uuid.test(value.playbackId) ||
            !Array.isArray(value.items) || !value.items.length || value.items.length > 256) return false;
        const mapped = new Map();
        for (const item of value.items) {
            if (!item || !uuid.test(item.itemId) || mapped.has(item.itemId) || typeof item.name !== 'string' ||
                !item.name.length || item.name.length > 512 || typeof item.assetId !== 'string' ||
                !Object.hasOwn(RARITY_CONFIG, item.rarity)) return false;
            const art = visuals.get(item.assetId);
            mapped.set(item.itemId, {id: item.itemId, assetId: item.assetId, name: escapeText(item.name),
                icon: art?.icon || '☆', category: art?.category || 'unknown', rarity: item.rarity});
        }
        config = {playbackId: value.playbackId, fast: value.fast === true, reducedMotion: value.reducedMotion === true};
        items = mapped;
        CASE_DISPLAY_ITEMS = [...mapped.values()];
        return true;
    }
    function play(result) {
        if (!config || !result || !uuid.test(result.transactionId) || !uuid.test(result.winningItemId)) return false;
        if (current) return current.transactionId === result.transactionId && current.winningItemId === result.winningItemId;
        if (!items.has(result.winningItemId)) return false;
        current = {transactionId: result.transactionId, winningItemId: result.winningItemId};
        phase = 'shaking';
        clean();
        // Existing 65-card renderer, with the confirmed item forced at slot 52.
        buildReelCards(items.get(current.winningItemId));
        document.getElementById(`reel-card-${WINNING_CARD_INDEX}`).dataset.itemId = current.winningItemId;
        chest.classList.remove('chest-state-idle', 'chest-state-opened');
        chest.classList.add('chest-state-shaking');
        if (config.reducedMotion || window.matchMedia('(prefers-reduced-motion: reduce)').matches) { finish('reduced_motion'); return true; }
        shakeTimer = setTimeout(() => {
            shakeTimer = null;
            phase = 'rolling';
            chest.classList.remove('chest-state-shaking'); chest.classList.add('chest-state-opened');
            const position = centerWinner();
            track.offsetHeight; // Commit the initial position before applying the original easing.
            const duration = config.fast ? 1800 : 4800;
            transitionDone = event => { if (event.target === track && event.propertyName === 'transform') finish('finished'); };
            track.addEventListener('transitionend', transitionDone);
            track.style.transition = `transform ${duration}ms cubic-bezier(0.10, 0.82, 0.08, 1)`;
            track.style.transform = `translateX(${position}px)`;
            // Timer is a visual recovery fallback, never an economic operation.
            finishTimer = setTimeout(() => finish('transition_fallback'), duration + 350);
        }, config.fast ? 450 : 1100);
        return true;
    }
    function reset() {
        if (current && phase !== 'complete' && phase !== 'error') finish('cancelled');
        clean(); current = null; phase = 'idle'; config = null; items.clear();
        CASE_DISPLAY_ITEMS = GACHA_ITEMS_POOL;
        track.style.transition = 'none'; track.style.transform = 'translateX(0px)';
        chest.classList.remove('chest-state-opened', 'chest-state-shaking'); chest.classList.add('chest-state-idle');
        buildReelCards();
        return true;
    }
    function fit() {
        const height = window.innerHeight;
        if (height > 0) document.querySelectorAll('html,body,main,#gacha-stage-container').forEach(e => e.style.height = height + 'px');
        if (current && phase === 'complete') track.style.transform = `translateX(${centerWinner()}px)`;
        else if (current && (phase === 'rolling' || phase === 'shaking')) finish('resized');
    }
    window.PawQuestCase = Object.freeze({
        initialize, play, reset,
        skip: () => { if (!current) return false; finish('skipped'); return true; },
        pause: paused => {
            document.querySelectorAll('*').forEach(e => e.style.animationPlayState = paused ? 'paused' : 'running');
            if (paused) finish('interrupted');
        },
        status: () => ({version: VERSION, phase, transactionId: current?.transactionId, winningItemId: current?.winningItemId})
    });
    window.addEventListener('resize', fit);
    window.addEventListener('pagehide', () => { finish('interrupted'); clean(); });
    document.addEventListener('visibilitychange', () => { if (document.hidden) finish('interrupted'); });
    window.onload = () => { fit(); buildReelCards(); window.pawquestCasePreviewReady = true; };
    window.pawquestCasePreviewPause = paused => window.PawQuestCase.pause(paused);
})();
