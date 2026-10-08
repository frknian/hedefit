/* Hero telefonu: gerçek uygulamayı taklit eden, kaydırılabilir ve dokunulabilir demo.
   Giriş turu kendiliğinden gezer; kullanıcı dokununca durur, 15 sn sessizlikte sürer. */
(() => {
  const EN = document.documentElement.lang === 'en';
  const t = (tr, en) => (EN ? en : tr);
  // İngilizce arayüzlü ekran görüntüsü olanlar (site/assets/shots-en); diğerleri Türkçe arayüzlü kalır.
  const EN_SHOTS = new Set(['kesfet', 'challenge', 'challenge-detay', 'koc-challenge', 'topluluk', 'adim-yarisi', 'atlas', 'harita', 'beslenme', 'hareketler', 'istatistik', 'ogunler', 'rota', 'hedef', 'kardiyo-prog', 'koc', 'kosubandi', 'oyun', 'program', 'rotaplan', 'seans-detay', 'seans-hizli', 'sosyal-board', 'sosyal-challenges', 'sosyal-feed', 'sosyal-friends']);
  const app = document.getElementById('heroApp');
  if (!app) return;
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const views = app.querySelector('.app-views');
  const nav = app.querySelector('.app-nav');
  const back = document.getElementById('appBack');
  const tapEl = document.getElementById('appTap');
  const capT = document.getElementById('capT'), capD = document.getElementById('capD'), capL = document.getElementById('capL');

  // id → { img, başlık, açıklama, bölüm bağlantısı, sekme }
  const S = {
    home:      { t: t('Ana ekran', 'Home screen'), d: t('Bugünün antrenmanı, hedef yolculuğu ve günlük dengen tek bakışta.', 'Today\'s workout, goal journey and daily balance at a glance.'), tab: 'home' },
    kesfet:    { t: t('Keşfet', 'Explore'), d: t('Programlar, challenge’lar ve topluluk tek sekmede. Antrenmanın ve hareket kütüphanen Programlar’da.', 'Programs, challenges and community in one tab. Your workout and the exercise library live in Programs.'), a: '#challenge', tab: 'kesfet' },
    kocchal:   { img: 'koc-challenge', t: t('Fit Koç ile Challenge', 'Challenge with Fit Coach'), d: t('Hedefin, süren ve ekipmanına göre kişisel plan; her gün check-in’ine uyum sağlar.', 'A personal plan for your goal, time and equipment that adapts to your daily check-in.'), a: '#challenge' },
    challenge: { img: 'challenge-detay', t: t('Challenge', 'Challenge'), d: t('Gün gün plan, seri ve bugünkü görev. Görevi yap, XP kazan.', 'A day-by-day plan, your streak and today’s task. Do the task, earn XP.'), a: '#challenge' },
    beslenme:  { img: 'beslenme', t: t('Beslenme', 'Nutrition'), d: t('Kalori halkası, makrolar ve su. Öğünü yaz, kalorisi veritabanından hesaplansın; ya da fotoğrafla.', 'Calorie ring, macros and water. Type your meal and its calories are calculated from the database, or photograph it.'), a: '#f-beslenme', tab: 'beslenme' },
    ilerleme:  { img: 'istatistik', t: t('İlerleme', 'Progress'), d: t('Seviye, XP, seri ve rozetlerin; kilo ve performans grafiklerinle birlikte.', 'Your level, XP, streak and badges, alongside your weight and performance charts.'), a: '#f-ilerleme', tab: 'ilerleme' },
    koc:       { img: 'koc', t: t('Fit Koç', 'Fit Coach'), d: t('Antrenman, beslenme ve ilerleme sorularını senin verine göre yanıtlar.', 'Answers your training, nutrition and progress questions using your own data.'), a: '#f-koc', tab: 'koc' },
    seans:     { img: 'seans-hizli', t: t('Antrenman seansı', 'Workout session'), d: t('Setlere dokun, dinlenme sayacı kendiliğinden başlar. İstersen detaylı moda geç.', 'Tap sets and the rest timer starts on its own. Switch to detailed mode if you like.'), a: '#f-antrenman' },
    hedef:     { img: 'hedef', t: t('Hedef yolculuğu', 'Goal journey'), d: t('Hedef kilon için tempo seç; tarihin ve ara hedeflerin hesaplansın.', 'Pick a pace for your target weight; your date and milestones are calculated.'), a: '#hedef-yolculugu' },
    atlas:     { img: 'harita', t: t('Hareket Atlası', 'Exercise Atlas'), d: t('600’den fazla hareket. Kas haritasında bir bölgeye dokun, o kasın hareketleri listelensin.', '600+ exercises. Tap a spot on the muscle map and its exercises are listed.'), a: '#f-antrenman' },
    kardiyo:   { img: 'kardiyo-prog', t: t('Kardiyo', 'Cardio'), d: t('6 makine ve hazır programlar: HIIT, tepe tırmanışı, bisiklet sprintleri.', '6 machines and ready programs: HIIT, hill climb, bike sprints.'), a: '#f-kardiyo' },
    oyun:      { img: 'oyun', t: t('Oyun modu', 'Game mode'), d: t('Sanal rota, rekorunla yarış ve hedef görevleri.', 'A virtual route, racing your record and goal quests.'), a: '#galeri' },
    rota:      { img: 'rota', t: t('Hedefit Rota', 'Hedefit Routes'), d: t('GPS ile kaydet, rotanı planla ve tekrar kullan.', 'Record with GPS, plan your route and reuse it.'), a: '#rota' },
    ogunler:   { img: 'ogunler', t: t('Öğün ekle', 'Add meal'), d: t('Metinle ya da fotoğrafla akıllı öğün ekleme.', 'Smart meal add by text or photo.'), a: '#yapay-zeka' },
    arkadaslar:{ img: 'topluluk', t: t('Topluluk', 'Community'), d: t('Arkadaşların, meydan okumalar ve challenge sıralamaları.', 'Your friends, challenges and challenge rankings.'), a: '#challenge' },
  };
  const TABS = ['home', 'kesfet', 'koc', 'beslenme', 'ilerleme'];
  const home = views.querySelector('[data-v="home"]');
  let cur = 'home', lastTab = 'home', userActive = false, idleT = 0, tourT = 0, tourI = 0;

  // görünümleri üret
  Object.entries(S).forEach(([id, s]) => {
    if (!s.img) return;
    const v = document.createElement('section');
    v.className = 'view view-img' + (s.tab ? ' crop' : ''); v.dataset.v = id;
    v.innerHTML = `<img src="/assets/shots${EN && EN_SHOTS.has(s.img) ? '-en' : ''}/${s.img}.jpg" alt="${s.t}${t(' ekranı', ' screen')}" width="560" height="1244" loading="lazy">`;
    views.appendChild(v);
  });
  const viewOf = id => views.querySelector(`[data-v="${id}"]`);

  function go(id) {
    if (id === 'saat') { document.getElementById('akilli-saat').scrollIntoView({ behavior: reduce ? 'auto' : 'smooth' }); return; }
    const s = S[id]; if (!s || id === cur) return;
    views.querySelectorAll('.view').forEach(v => { v.classList.remove('on'); v.classList.toggle('pre', v.dataset.v !== id); });
    const v = viewOf(id); v.classList.remove('pre'); v.classList.add('on'); v.scrollTop = 0;
    cur = id; if (s.tab) lastTab = id;
    nav.querySelectorAll('button').forEach(b => b.classList.toggle('on', b.dataset.tab === s.tab));
    back.hidden = !!s.tab;
    capT.innerHTML = s.t.replace('Hedefit', 'Hedef<span lang="en">it</span>'); capD.textContent = s.d;
    if (s.a) { capL.hidden = false; capL.href = s.a; } else capL.hidden = true;
  }

  function ripple(el) {
    if (reduce || !el) return;
    const a = app.querySelector('.app').getBoundingClientRect(), b = el.getBoundingClientRect();
    tapEl.style.left = (b.left - a.left + b.width / 2) + 'px'; tapEl.style.top = (b.top - a.top + b.height / 2) + 'px';
    tapEl.classList.remove('go'); void tapEl.offsetWidth; tapEl.classList.add('go');
  }

  // etkileşim
  const touched = () => {
    userActive = true; clearTimeout(tourT); clearTimeout(idleT);
    idleT = setTimeout(() => { userActive = false; if (!reduce && visible) tour(); }, 15000);
  };
  // Yalnız gerçek dokunuş (düğme/bağlantı) turu durdurur; sayfayı kaydırırken tur sürsün.
  app.addEventListener('pointerdown', e => { if (e.target.closest('button, a, [data-go], [data-tab]')) touched(); }, { passive: true });
  app.addEventListener('wheel', touched, { passive: true });
  app.addEventListener('click', e => {
    const g = e.target.closest('[data-go]'); if (g) { go(g.dataset.go); return; }
    const t = e.target.closest('[data-tab]'); if (t) go(t.dataset.tab);
  });
  back.addEventListener('click', () => go(lastTab));

  // ---- Keşfet: Programlar · Challenge · Topluluk (gerçek uygulamadaki gibi dokunulabilir) ----
  const ex = viewOf('kesfet');
  const seg = id => {
    ex.querySelectorAll('.ex-seg button').forEach(b => b.setAttribute('aria-selected', String(b.dataset.seg === id)));
    ex.querySelectorAll('.ex-pane').forEach(p => p.classList.toggle('on', p.dataset.pane === id));
    ex.scrollTop = 0;
    const cap = { prog: [t('Keşfet · Programlar', 'Explore · Programs'), t('Antrenmanın, kategoriler ve hareket kütüphanesi tek yerde.', 'Your workout, categories and the exercise library in one place.')],
      chal: [t('Keşfet · Challenge', 'Explore · Challenge'), t('Bir challenge’a katıl ya da Fit Koç’a sana özel bir tane hazırlat.', 'Join a challenge or have Fit Coach build one just for you.')],
      top: [t('Keşfet · Topluluk', 'Explore · Community'), t('Arkadaşına meydan oku: birlikte tamamlayın ya da rekabet edin.', 'Challenge a friend: complete it together or compete.')] }[id];
    if (cur === 'kesfet') { capT.textContent = cap[0]; capD.textContent = cap[1]; }
  };
  ex.querySelector('.ex-seg').addEventListener('click', e => { const b = e.target.closest('[data-seg]'); if (b) seg(b.dataset.seg); });
  const CH = [
    { k: 'core', c: 'workout', ic: '🏋', tr: '14 Gün Core Challenge', en: '14-Day Core Challenge', dtr: 'Her gün kısa, kontrollü core seansları.', den: 'Short, controlled core sessions every day.', lv: [t('Başlangıç', 'Beginner')], d: 14, m: t('8–15 dk/gün', '8–15 min/day'), xp: 830, fy: 1 },
    { k: 'pil', c: 'pilates', ic: '🧘', tr: '21 Gün Pilates', en: '21-Day Pilates', dtr: 'Daha güçlü core, daha dik duruş.', den: 'A stronger core and a taller posture.', lv: [t('Orta', 'Intermediate')], d: 21, m: t('12–22 dk/gün', '12–22 min/day'), xp: 1105, fy: 1 },
    { k: 'step', c: 'steps', ic: '🚶', tr: '7 Gün Adım Challenge', en: '7-Day Step Challenge', dtr: 'Her gün 8.000 adım; arkadaşınla yarış.', den: '8,000 steps a day; race a friend.', lv: [t('Başlangıç', 'Beginner')], d: 7, m: null, xp: 555, fy: 1 },
    { k: 'home', c: 'workout', ic: '🏠', tr: '21 Gün Evde Güç', en: '21-Day Home Strength', dtr: 'Ekipmansız, kademeli tüm vücut.', den: 'Progressive full body, no equipment.', lv: [t('Orta', 'Intermediate')], d: 21, m: t('10–25 dk/gün', '10–25 min/day'), xp: 1105 },
    { k: 'flex', c: 'flex', ic: '🤸', tr: '10 Gün Esneklik', en: '10-Day Flexibility', dtr: 'Masa başı tutukluğuna iyi gelir.', den: 'Great for desk-bound stiffness.', lv: [t('Başlangıç', 'Beginner')], d: 10, m: t('8–12 dk/gün', '8–12 min/day'), xp: 630 },
    { k: 'water', c: 'nutrition', ic: '💧', tr: '7 Gün Su Challenge', en: '7-Day Hydration Challenge', dtr: 'Her gün en az 2 litre su.', den: 'At least 2 litres of water a day.', lv: [t('Başlangıç', 'Beginner')], d: 7, m: null, xp: 555 },
    { k: 'meals', c: 'nutrition', ic: '🍽', tr: '14 Gün Öğün Takibi', en: '14-Day Meal Tracking', dtr: 'Her gün en az 3 öğününü kaydet.', den: 'Log at least 3 meals every day.', lv: [t('Başlangıç', 'Beginner')], d: 14, m: null, xp: 830 },
  ];
  const FILTERS = [['fy', t('Sana Özel', 'For You')], ['pop', t('Popüler', 'Popular')], ['workout', t('Antrenman', 'Workout')], ['nutrition', t('Beslenme', 'Nutrition')], ['steps', t('Adım', 'Steps')], ['pilates', 'Pilates'], ['flex', t('Esneklik', 'Flexibility')]];
  const joined = new Map([['core', 5]]); // challenge → tamamlanan gün
  let filter = 'fy';
  const chips = ex.querySelector('#exChips'), cards = ex.querySelector('#exCards'), act = ex.querySelector('#exActive');
  const drawActive = () => {
    act.innerHTML = '';
    joined.forEach((done, k) => {
      const c = CH.find(x => x.k === k);
      const b = document.createElement('button'); b.type = 'button'; b.className = 'ex-row'; b.dataset.go = 'challenge';
      b.innerHTML = `<header><span><b></b><em></em></span>${done ? `<span class="fl">🔥 ${done}</span>` : ""}</header><div class="gbar"><span style="width:${Math.round(done / c.d * 100)}%"></span></div>`;
      b.querySelector('b').textContent = t(c.tr, c.en); b.querySelector('em').textContent = t(`Gün ${done + 1} / ${c.d}`, `Day ${done + 1} / ${c.d}`);
      act.appendChild(b);
    });
  };
  const drawCards = () => {
    chips.innerHTML = '';
    FILTERS.forEach(([k, label]) => { const b = document.createElement('button'); b.type = 'button'; b.role = 'tab'; b.textContent = label; b.setAttribute('aria-selected', String(k === filter)); b.addEventListener('click', () => { filter = k; drawCards(); }); chips.appendChild(b); });
    const list = filter === 'fy' ? CH.filter(c => c.fy) : filter === 'pop' ? CH.slice(0, 4) : CH.filter(c => c.c === filter);
    cards.innerHTML = '';
    list.forEach(c => {
      const el = document.createElement('article'); el.className = 'ex-card';
      el.innerHTML = `<header><span class="ic">${c.ic}</span><div><b></b><p></p></div></header><div class="tg"></div><footer><strong>+${c.xp} XP</strong><button type="button" class="ex-join"></button></footer>`;
      el.querySelector('b').textContent = t(c.tr, c.en); el.querySelector('p').textContent = t(c.dtr, c.den);
      [...c.lv, t(`${c.d} gün`, `${c.d} days`), c.m].filter(Boolean).forEach(x => { const i = document.createElement('i'); i.textContent = x; el.querySelector('.tg').appendChild(i); });
      const j = el.querySelector('.ex-join'); const on = joined.has(c.k);
      j.textContent = on ? t('Devam et', 'Continue') : t('Katıl', 'Join'); j.classList.toggle('on', on);
      j.addEventListener('click', () => {
        if (joined.has(c.k)) { go('challenge'); return; }
        if (joined.size >= 3) { capD.textContent = t('Aynı anda en fazla 3 challenge yürütebilirsin.', 'You can run up to 3 challenges at once.'); return; }
        joined.set(c.k, 0); drawActive(); drawCards();
        capD.textContent = t('Challenge başladı. Bugünkü görevin ana ekranda.', 'Challenge started. Today’s task is on your Today screen.');
      });
      cards.appendChild(el);
    });
    if (!list.length) cards.innerHTML = `<p class="ex-empty">${t('Bu kategoride challenge yok.', 'No challenges in this category.')}</p>`;
  };
  drawActive(); drawCards();
  // Topluluk: adım yarışı
  let mySteps = 39210;
  const meBar = ex.querySelector('#exMe'), meTxt = ex.querySelector('#exMeTxt');
  const fmt = n => n.toLocaleString(EN ? 'en-US' : 'tr-TR');
  ex.querySelector('#exWalk').addEventListener('click', () => {
    mySteps += 2000; meBar.style.width = Math.min(100, mySteps / 42840 * 100) + '%'; meTxt.textContent = fmt(mySteps);
    capD.textContent = mySteps > 42840 ? t('Öne geçtin! Mert’in 3 günü kaldı.', 'You took the lead! Mert has 3 days left.') : t('Mert’e yaklaşıyorsun.', 'You’re closing in on Mert.');
  });
  ex.querySelector('#exDare').addEventListener('click', () => { capD.textContent = t('Bir challenge seç; Birlikte Tamamla ya da Rekabet Et.', 'Pick a challenge; Complete Together or Compete.'); });
  ex.querySelector('#exMeTxt').textContent = fmt(mySteps); ex.querySelector('.ex-race .rr em').textContent = fmt(42840);

  // ana ekran animasyonları (kilo 95→80, halkalar)
  const kg = document.getElementById('apKg'), wk = document.getElementById('apWk'), bar = document.getElementById('apBar');
  const rings = home.querySelectorAll('.rg');
  const setRings = on => rings.forEach(r => r.querySelector('.fg').style.strokeDashoffset = on ? 138.2 * (1 - +r.dataset.p) : 138.2);
  const ease = t => t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
  // Üç hedef türü sırayla: kilo ver (95→80), kas kazan (70→74), formu koru (80).
  const PG = [
    { from: 95, to: 80, weeks: 20, text: w => w + t(' hafta kaldı', ' weeks left') },
    { from: 70, to: 74, weeks: 16, text: w => w + t(' hafta kaldı', ' weeks left') },
    { from: 80, to: 80, weeks: 0, text: () => t('Bakım', 'Maintain') },
  ];
  let pgi = 0;
  const apTo = document.getElementById('apTo');
  const goalAt = p => {
    const g = PG[pgi];
    kg.textContent = (g.from + (g.to - g.from) * p).toFixed(1) + ' kg'; apTo.textContent = g.to.toFixed(1) + ' kg';
    wk.textContent = g.text(Math.max(0, Math.round(g.weeks * (1 - p)))); bar.style.width = (p * 100) + '%';
  };
  let visible = false, raf = 0, t0 = 0, ringsOn = false;
  const frame = now => {
    if (!visible) return;
    if (!t0) t0 = now;
    const ms = now - t0, T = 5200, H = 2400;
    goalAt(ease(Math.min(1, ms / T)));
    if (ms > T + H) { t0 = 0; pgi = (pgi + 1) % PG.length; }
    raf = requestAnimationFrame(frame);
  };
  if (reduce) { goalAt(1); setRings(true); }
  new IntersectionObserver(es => es.forEach(e => {
    visible = e.isIntersecting; cancelAnimationFrame(raf);
    if (visible) {
      if (!ringsOn) { ringsOn = true; setTimeout(() => setRings(true), 300); }
      if (!reduce) { t0 = 0; raf = requestAnimationFrame(frame); if (!userActive && !tourT) tour(); }
    } else { clearTimeout(tourT); tourT = 0; }
  }), { threshold: .3 }).observe(app);

  // otomatik tur
  function smoothScroll(el, to, dur) {
    const from = el.scrollTop, t = performance.now();
    const step = n => { const p = Math.min(1, (n - t) / dur); el.scrollTop = from + (to - from) * ease(p); if (p < 1 && !userActive) requestAnimationFrame(step); };
    requestAnimationFrame(step);
  }
  const STEPS = ['home-scroll', 'kesfet', 'ex-chal', 'ex-top', 'koc', 'beslenme', 'ilerleme', 'home'];
  function tour() {
    if (userActive || reduce || !visible) return;
    const s = STEPS[tourI++ % STEPS.length];
    let wait = 3200;
    if (s === 'home-scroll') {
      go('home'); smoothScroll(home, 360, 2200);
      tourT = setTimeout(() => { smoothScroll(home, 0, 1200); tourT = setTimeout(tour, 1500); }, 2800); return;
    }
    if (s === 'ex-chal' || s === 'ex-top') {
      const b = ex.querySelector(`[data-seg="${s === 'ex-chal' ? 'chal' : 'top'}"]`); ripple(b);
      tourT = setTimeout(() => { if (userActive) return; seg(s === 'ex-chal' ? 'chal' : 'top'); tourT = setTimeout(tour, wait); }, 450); return;
    }
    if (s === 'kesfet') seg('prog');
    if (TABS.includes(s)) { ripple(nav.querySelector(`[data-tab="${s}"]`)); }
    else if (s === 'rota') { go('home'); const b = home.querySelector('[data-go="rota"]'); home.scrollTop = 300; ripple(b); }
    tourT = setTimeout(() => { if (userActive) return; go(s); if (s === 'home') home.scrollTop = 0; tourT = setTimeout(tour, wait); }, 450);
  }
})();
