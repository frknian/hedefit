/* Akıllı saat vitrini: Wear OS (yuvarlak) ve Apple Watch (kare) için 6'şar ekran.
   Kendiliğinden gezer; dokununca durur. Butonlar gerçekten çalışır (su ekle, seti bitir, ...). */
(() => {
  const EN = document.documentElement.lang === 'en';
  const DEC = EN ? '.' : ',';
  const stage = document.getElementById('watchStage');
  if (!stage) return;
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const dev = document.getElementById('wDev'), face = document.getElementById('wFace');
  const list = document.getElementById('wList'), dots = document.getElementById('wDots');
  const plat = document.getElementById('wPlat');

  const ORDER = {
    wear:  ['ozet', 'su', 'set', 'dinlenme', 'kardiyo', 'kadran'],
    apple: ['ozet', 'su', 'baslat', 'kardiyo', 'dinlenme', 'kadran'],
  };
  const TRMAP = [["Digital Crown ile 50–1000 ml", "50–1000 ml with Digital Crown"], ["Adım, su, protein halkaları", "Steps, water, protein rings"], ["Tek dokunuş, taçla miktar", "One tap, amount via crown"], ["Taç ile kg/tekrar ayarı", "Adjust kg/reps with the crown"], ["Taçla miktarı değiştir", "Change amount with the crown"], ["Üç halka, dikey sayfa", "Three rings, vertical page"], ["Mesafe, tempo, nabız", "Distance, pace, heart rate"], ["Kadran complication", "Watch face complication"], ["Bitince titreşim", "Vibrates when done"], ["Antrenman başlat", "Start workout"], ["Üç complication", "Three complications"], ["Dinlenme sayacı", "Rest timer"], ["Adım, seri, su", "Steps, streak, water"], ["Carousel liste", "Carousel list"], ["Antrenman seti", "Workout set"], ["Sıradaki: Set", "Next: Set"], ["Doğa yürüyüşü", "Hiking"], ["Sonraki: Set", "Next: Set"], ["Günlük özet", "Daily summary"], ["12 gün seri", "12-day streak"], ["Koşu · GPS", "Run · GPS"], ["Seti bitir", "Finish set"], ["Tüm Vücut", "Full Body"], ["Çar 1 Eki", "Wed 1 Oct"], ["Duraklat", "Pause"], ["Dinlenme", "Rest"], ["Bisiklet", "Bike"], ["Su ekle", "Add water"], ["Yürüyüş", "Walk"], ["Kardiyo", "Cardio"], ["Durdur", "Stop"], ["Kadran", "Watch face"], ["+15 sn", "+15 s"], ["12 gün", "12 days"], ["Bölge", "Zone"], ["hedef", "goal"], ["2,4 L", "2.4 L"], ["2,5 L", "2.5 L"], ["Atla", "Skip"], ["Koşu", "Run"], ["adım", "steps"], ["seri", "streak"], ["Özet", "Summary"], ["7,4K", "7.4K"], ["1,2L", "1.2L"], ["Su", "Water"], ["su", "water"]];
  const WB = '[A-Za-zÇĞİÖŞÜçğıöşü]';
  const L = html => EN ? TRMAP.reduce((acc, [a, b]) => acc.replace(new RegExp('(^|[^A-Za-zÇĞİÖŞÜçğıöşü])' + a.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '(?![A-Za-zÇĞİÖŞÜçğıöşü])', 'g'), '$1' + b), html) : html;
  const INFO = {
    wear:  { ozet: ['Günlük özet', 'Adım, su, protein halkaları'], su: ['Su ekle', 'Tek dokunuş, taçla miktar'], set: ['Antrenman seti', 'Taç ile kg/tekrar ayarı'], dinlenme: ['Dinlenme sayacı', 'Bitince titreşim'], kardiyo: ['Kardiyo', 'Mesafe, tempo, nabız'], kadran: ['Kadran complication', 'Adım, seri, su'] },
    apple: { ozet: ['Özet', 'Üç halka, dikey sayfa'], su: ['Su', 'Digital Crown ile 50–1000 ml'], baslat: ['Antrenman başlat', 'Carousel liste'], kardiyo: ['Kardiyo', 'Mesafe, tempo, nabız'], dinlenme: ['Dinlenme sayacı', 'Bitince titreşim'], kadran: ['Kadran', 'Üç complication'] },
  };

  const st = { water: 1.2, rest: 48, rate: 154, km: 5.42, sec: 30 * 60 + 34, paused: false, set: 2 };
  let P = 'wear', cur = 0, userActive = false, tour = 0, idle = 0, visible = false, tick = 0;

  const C = r => 2 * Math.PI * r;
  const ring = (r, p, col, w = 9) => `<circle class="rb" cx="100" cy="100" r="${r}" stroke-width="${w}"/><circle class="rf" cx="100" cy="100" r="${r}" stroke="${col}" stroke-width="${w}" stroke-dasharray="${C(r)}" stroke-dashoffset="${C(r)}" data-c="${C(r)}" data-p="${p}"/>`;
  const svg = inner => `<svg class="wring" viewBox="0 0 200 200" aria-hidden="true">${inner}</svg>`;
  const fmtL = v => v.toFixed(1).replace('.', DEC);
  const mmss = s => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  const hms = s => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

  const SCREENS = {
    wear: {
      ozet: () => svg(ring(92, .8, '#5bc46a', 10) + ring(78, .66, '#5bbcf8', 10) + ring(64, .55, '#f07272', 10)) + `<div class="wc"><span class="wbig xl">7.412</span><span class="sm">adım</span><span class="c-y">12 gün seri</span></div>`,
      su: () => svg(ring(92, st.water / 2.4, '#5bbcf8', 10)) + `<div class="wc"><span class="lbl">Su</span><span class="wbig xl c-b" data-b="water">${fmtL(st.water)} L</span><span class="sm">hedef 2,4 L</span><button type="button" class="wbtn blue" data-act="water">+250 ml</button></div>`,
      set: () => svg(ring(92, st.set / 4, '#5bc46a', 7)) + `<div class="wc"><span class="lbl">Set ${st.set}/4 · Bench Press</span><span class="wbig">8 <small style="font-size:.5em;color:#9aa4b2">x</small> 60<small style="font-size:.4em;color:#9aa4b2">kg</small></span><span class="hr">♥ 128 bpm</span><button type="button" class="wbtn green" data-act="setdone">Seti bitir</button></div>`,
      dinlenme: () => svg(ring(92, st.rest / 60, '#f2c14e', 10)) + `<div class="wc"><span class="lbl">Dinlenme</span><span class="wbig xl" data-b="rest">${mmss(st.rest)}</span><span class="sm">Sıradaki: Set ${Math.min(4, st.set + 1)}, 8 x 60 kg</span><span class="sm"><button type="button" class="wbtn pill" data-act="plus15">+15 sn</button><button type="button" class="wbtn pill" data-act="skip">Atla</button></span></div>`,
      kardiyo: () => `<div class="wc"><span class="c-g lbl">Koşu · GPS</span><span class="wbig xl" data-b="km">${st.km.toFixed(2).replace('.', DEC)}<small style="font-size:.45em;color:#9aa4b2"> km</small></span><span class="wrow2"><span>5:38 <small style="color:#9aa4b2;font-size:.7em">/km</small></span><span data-b="time">${hms(st.sec)}</span></span><span class="hr" data-b="hr">♥ ${st.rate} · Bölge 3</span><span class="wrow"><button type="button" class="wbtn dark" data-act="pause" aria-label="Duraklat">${st.paused ? '▶' : '❚❚'}</button><button type="button" class="wbtn red" data-act="stop" aria-label="Durdur">■</button></span></div>`,
      kadran: () => svg(ring(92, .82, '#5bc46a', 7)) + `<div class="wc"><span class="wbig xl">10:09</span><span class="lbl">Çar 1 Eki</span><span class="wcomp"><span class="c-g">7,4K</span><span class="c-y">12 gün</span><span class="c-b">1,2L</span></span></div>`,
    },
    apple: {
      ozet: () => `<div class="ahd"><span></span><span class="atm">10:09</span></div>` + svg(ring(82, .8, '#5bc46a', 12) + ring(66, .66, '#5bbcf8', 12) + ring(50, .55, '#f07272', 12)) + `<div class="wc" style="margin-top:34px"><span class="wbig" style="font-size:1.9rem">7.412</span><span class="c-r" style="font-size:.8rem">adım</span><span class="c-y" style="font-size:.8rem">12 gün</span></div>`,
      su: () => `<div class="ahd b"><span class="at">Su</span><span class="atm">10:09</span></div><div class="wc" style="margin-top:6px"><span class="wbig xl c-b" data-b="water">${fmtL(st.water)} L</span><span class="sm">hedef 2,5 L</span><button type="button" class="wbtn blue" data-act="water" style="width:84%;border-radius:20px;margin-top:10px;padding:11px">+250 ml</button><span class="sm" style="font-size:.78rem">Taçla miktarı değiştir</span></div>`,
      baslat: () => `<div class="ahd"><span class="at">Tüm Vücut</span><span class="atm">10:09</span></div><div class="alist"><button type="button" data-act="pick"><span>🏃</span>Koşu</button><button type="button" data-act="pick"><span>🚶</span>Yürüyüş</button><button type="button" data-act="pick"><span>⛰</span>Doğa yürüyüşü</button><button type="button" class="dim" data-act="pick"><span style="color:#7d8794">🚴</span>Bisiklet</button></div>`,
      kardiyo: () => `<div class="ahd"><span class="at">Koşu</span><span class="atm">10:39</span></div><div class="wc" style="margin-top:20px"><span class="wbig xl" data-b="km">${st.km.toFixed(2).replace('.', DEC)}<small style="font-size:.45em;color:#9aa4b2"> km</small></span><span class="wrow2"><span>5:38 <small style="color:#9aa4b2;font-size:.7em">/km</small></span><span data-b="time">${hms(st.sec)}</span></span><span class="hr" data-b="hr">♡ ${st.rate}</span><span class="wrow"><button type="button" class="wbtn dark" data-act="pause" aria-label="Duraklat">${st.paused ? '▶' : '❚❚'}</button><button type="button" class="wbtn red" data-act="stop" aria-label="Durdur">■</button></span></div>`,
      dinlenme: () => `<div class="ahd y"><span class="at">Dinlenme</span><span class="atm">10:21</span></div>` + svg(ring(70, st.rest / 60, '#f2c14e', 10)) + `<div class="wc" style="margin-top:10px"><span class="wbig xl c-y" data-b="rest">${mmss(st.rest)}</span><span class="sm" style="font-size:.8rem">Sonraki: Set ${Math.min(4, st.set + 1)}</span><span class="wrow" style="gap:6px"><button type="button" class="wbtn pill" data-act="plus15">+15 sn</button><button type="button" class="wbtn pill" data-act="skip">Atla</button></span></div>`,
      kadran: () => `<div class="ahd g2"><span></span><span class="atm">10:09</span></div><div class="adate"><div class="ad">Çar 1 Eki</div><div class="at2">10:09</div></div><div class="acomp"><div class="c-g">7,4K<small>adım</small></div><div class="c-y">12<small>seri</small></div><div class="c-b">1,2L<small>su</small></div></div>`,
    },
  };

  // İngilizce sayfada ekran metinleri ve bilgi başlıkları İngilizceye çevrilir.
  for (const p of Object.keys(SCREENS)) for (const k of Object.keys(SCREENS[p])) { const f = SCREENS[p][k]; SCREENS[p][k] = () => L(f()); }
  if (EN) for (const p of Object.keys(INFO)) for (const k of Object.keys(INFO[p])) INFO[p][k] = INFO[p][k].map(L);

  function mount(p) {
    P = p; dev.classList.toggle('round', p === 'wear'); dev.classList.toggle('square', p === 'apple');
    face.innerHTML = ORDER[p].map((id, i) => `<div class="wsc" data-i="${i}">${SCREENS[p][id]()}</div>`).join('');
    list.innerHTML = ORDER[p].map((id, i) => `<li><button type="button" data-i="${i}"><i>${i + 1}</i><div><b>${INFO[p][id][0]}</b><span>${INFO[p][id][1]}</span></div></button></li>`).join('');
    dots.innerHTML = ORDER[p].map((id, i) => `<button type="button" data-i="${i}" aria-label="${INFO[p][id][0]}"></button>`).join('');
    show(Math.min(cur, 5), true);
  }
  function fill(sc) {
    sc.querySelectorAll('.rf').forEach(c => { const C0 = +c.dataset.c; c.style.strokeDashoffset = C0; requestAnimationFrame(() => requestAnimationFrame(() => { c.style.strokeDashoffset = C0 * (1 - Math.min(1, +c.dataset.p)); })); });
  }
  function show(i) {
    cur = i;
    face.querySelectorAll('.wsc').forEach(s => s.classList.toggle('on', +s.dataset.i === i));
    list.querySelectorAll('button').forEach(b => b.classList.toggle('on', +b.dataset.i === i));
    dots.querySelectorAll('button').forEach(b => b.classList.toggle('on', +b.dataset.i === i));
    const sc = face.querySelector(`.wsc[data-i="${i}"]`); if (sc) fill(sc);
  }
  const rerender = () => { // mevcut ekranı yeni değerlerle yeniden çiz
    const id = ORDER[P][cur], sc = face.querySelector(`.wsc[data-i="${cur}"]`);
    sc.innerHTML = SCREENS[P][id](); fill(sc);
  };
  const goId = id => { const i = ORDER[P].indexOf(id); if (i >= 0) show(i); };

  // etkileşim
  const touched = () => { userActive = true; clearTimeout(tour); clearTimeout(idle); idle = setTimeout(() => { userActive = false; if (visible && !reduce) next(); }, 15000); };
  stage.addEventListener('pointerdown', e => { if (e.target.closest('button, a')) touched(); }, { passive: true });
  list.addEventListener('pointerdown', touched, { passive: true });
  list.addEventListener('click', e => { const b = e.target.closest('button'); if (b) show(+b.dataset.i); });
  dots.addEventListener('click', e => { const b = e.target.closest('button'); if (b) { touched(); show(+b.dataset.i); } });
  plat.addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    plat.querySelectorAll('button').forEach(x => x.setAttribute('aria-selected', String(x === b)));
    touched(); mount(b.dataset.p);
  });
  face.addEventListener('click', e => {
    const b = e.target.closest('[data-act]'); if (!b) return;
    const a = b.dataset.act;
    if (a === 'water') { st.water = Math.min(2.4, +(st.water + .25).toFixed(2)); if (st.water >= 2.4) st.water = 1.2; rerender(); }
    else if (a === 'setdone') { st.set = Math.min(4, st.set + 1); st.rest = 48; goId('dinlenme'); face.querySelector(`.wsc[data-i="${ORDER[P].indexOf('dinlenme')}"]`).innerHTML = SCREENS[P].dinlenme(); fill(face.querySelector(`.wsc[data-i="${ORDER[P].indexOf('dinlenme')}"]`)); }
    else if (a === 'plus15') { st.rest = Math.min(120, st.rest + 15); rerender(); }
    else if (a === 'skip') { st.rest = 0; if (P === 'wear') { st.set = Math.min(4, st.set); goId('set'); const i = ORDER[P].indexOf('set'); const sc = face.querySelector(`.wsc[data-i="${i}"]`); sc.innerHTML = SCREENS[P].set(); fill(sc); } else goId('kadran'); st.rest = 48; }
    else if (a === 'pause') { st.paused = !st.paused; rerender(); }
    else if (a === 'stop') { st.sec = 0; st.km = 0; st.paused = true; rerender(); setTimeout(() => { st.km = 5.42; st.sec = 1834; st.paused = false; }, 2500); }
    else if (a === 'pick') { goId('kardiyo'); }
  });

  // canlı değerler
  const live = () => {
    if (!visible) return;
    const set = (k, html) => { const el = face.querySelector(`.wsc.on [data-b="${k}"]`); if (el) el.innerHTML = html; };
    const id = ORDER[P][cur];
    if (id === 'kardiyo' && !st.paused) {
      st.sec += 1; st.km += .0029; st.rate = Math.max(148, Math.min(160, st.rate + Math.round((Math.random() - .5) * 4)));
      set('km', `${st.km.toFixed(2).replace('.', DEC)}<small style="font-size:.45em;color:#9aa4b2"> km</small>`);
      set('time', hms(st.sec)); set('hr', (P === 'wear' ? '♥ ' : '♡ ') + st.rate + (P === 'wear' ? L(' · Bölge 3') : ''));
    }
    if (id === 'dinlenme' && st.rest > 0) {
      st.rest -= 1; set('rest', mmss(st.rest));
      const rf = face.querySelector('.wsc.on .rf'); if (rf) rf.style.strokeDashoffset = (+rf.dataset.c) * (1 - st.rest / 60);
      if (st.rest === 0) { if (!userActive) next(); else st.rest = 48; }
    }
  };

  // otomatik tur
  function next() {
    if (userActive || reduce || !visible) return;
    clearTimeout(tour);
    const n = (cur + 1) % 6;
    if (ORDER[P][n] === 'dinlenme') st.rest = 48;
    show(n);
    tour = setTimeout(next, ORDER[P][n] === 'dinlenme' ? 6000 : 4600);
  }
  new IntersectionObserver(es => es.forEach(e => {
    visible = e.isIntersecting; clearInterval(tick); clearTimeout(tour);
    if (visible) { tick = setInterval(live, 1000); if (!userActive && !reduce) tour = setTimeout(next, 3600); }
  }), { threshold: .35 }).observe(stage);

  mount('wear');
})();
