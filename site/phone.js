/* Hero telefonu: gerçek uygulamayı taklit eden, kaydırılabilir ve dokunulabilir demo.
   Giriş turu kendiliğinden gezer; kullanıcı dokununca durur, 15 sn sessizlikte sürer. */
(() => {
  const EN = document.documentElement.lang === 'en';
  const t = (tr, en) => (EN ? en : tr);
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
    antrenman: { img: 'hareketler', t: t('Antrenman', 'Workout'), d: t('Her hareket animasyonlu önizlemeyle gelir; set ve tekrarı dokunarak ayarla.', 'Every exercise comes with an animated preview; adjust sets and reps with a tap.'), a: '#f-antrenman', tab: 'antrenman' },
    beslenme:  { img: 'beslenme', t: t('Beslenme', 'Nutrition'), d: t('Kalori halkası, makrolar ve su. Öğünü yaz, kalorisi veritabanından hesaplansın; ya da fotoğrafla.', 'Calorie ring, macros and water. Type your meal and its calories are calculated from the database, or photograph it.'), a: '#f-beslenme', tab: 'beslenme' },
    ilerleme:  { img: 'istatistik', t: t('İlerleme', 'Progress'), d: t('Aktivite takvimi, seri ve haftalık süre. 7G’den Tümü’ne.', 'Activity calendar, streak and weekly time. From 7D to All.'), a: '#f-ilerleme', tab: 'ilerleme' },
    koc:       { img: 'koc', t: t('Fit Koç', 'Fit Coach'), d: t('Antrenman, beslenme ve ilerleme sorularını senin verine göre yanıtlar.', 'Answers your training, nutrition and progress questions using your own data.'), a: '#f-koc', tab: 'koc' },
    seans:     { img: 'seans-hizli', t: t('Antrenman seansı', 'Workout session'), d: t('Setlere dokun, dinlenme sayacı kendiliğinden başlar. İstersen detaylı moda geç.', 'Tap sets and the rest timer starts on its own. Switch to detailed mode if you like.'), a: '#f-antrenman' },
    hedef:     { img: 'hedef', t: t('Hedef yolculuğu', 'Goal journey'), d: t('Hedef kilon için tempo seç; tarihin ve ara hedeflerin hesaplansın.', 'Pick a pace for your target weight; your date and milestones are calculated.'), a: '#hedef-yolculugu' },
    atlas:     { img: 'atlas', t: t('Hareket Atlası', 'Exercise Atlas'), d: t('600’den fazla hareket; kas, ekipman ve seviyeye göre filtrele.', '600+ exercises; filter by muscle, equipment and level.'), a: '#f-antrenman' },
    kardiyo:   { img: 'kardiyo-prog', t: t('Kardiyo', 'Cardio'), d: t('6 makine ve hazır programlar: HIIT, tepe tırmanışı, bisiklet sprintleri.', '6 machines and ready programs: HIIT, hill climb, bike sprints.'), a: '#f-kardiyo' },
    oyun:      { img: 'oyun', t: t('Oyun modu', 'Game mode'), d: t('Sanal rota, rekorunla yarış ve hedef görevleri.', 'A virtual route, racing your record and goal quests.'), a: '#galeri' },
    rota:      { img: 'rota', t: t('Hedefit Rota', 'Hedefit Routes'), d: t('GPS ile kaydet, rotanı planla ve tekrar kullan.', 'Record with GPS, plan your route and reuse it.'), a: '#rota' },
    ogunler:   { img: 'ogunler', t: t('Öğün ekle', 'Add meal'), d: t('Metinle ya da fotoğrafla akıllı öğün ekleme.', 'Smart meal add by text or photo.'), a: '#yapay-zeka' },
    arkadaslar:{ img: 'sosyal-friends', t: t('Arkadaşlar', 'Friends'), d: t('Sıralama, akış ve ortak meydan okumalar.', 'Leaderboard, feed and shared challenges.'), a: '#sosyal' },
  };
  const TABS = ['home', 'antrenman', 'beslenme', 'ilerleme', 'koc'];
  const home = views.querySelector('[data-v="home"]');
  let cur = 'home', userActive = false, idleT = 0, tourT = 0, tourI = 0;

  // görünümleri üret
  Object.entries(S).forEach(([id, s]) => {
    if (!s.img) return;
    const v = document.createElement('section');
    v.className = 'view view-img' + (s.tab ? ' crop' : ''); v.dataset.v = id;
    v.innerHTML = `<img src="/assets/shots/${s.img}.jpg" alt="${s.t}${t(' ekranı', ' screen')}" width="560" height="1244" loading="lazy">`;
    views.appendChild(v);
  });
  const viewOf = id => views.querySelector(`[data-v="${id}"]`);

  function go(id) {
    if (id === 'saat') { document.getElementById('akilli-saat').scrollIntoView({ behavior: reduce ? 'auto' : 'smooth' }); return; }
    const s = S[id]; if (!s || id === cur) return;
    views.querySelectorAll('.view').forEach(v => { v.classList.remove('on'); v.classList.toggle('pre', v.dataset.v !== id); });
    const v = viewOf(id); v.classList.remove('pre'); v.classList.add('on'); v.scrollTop = 0;
    cur = id;
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
  back.addEventListener('click', () => go('home'));

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
  const STEPS = ['home-scroll', 'antrenman', 'beslenme', 'ilerleme', 'koc', 'home', 'rota', 'home'];
  function tour() {
    if (userActive || reduce || !visible) return;
    const s = STEPS[tourI++ % STEPS.length];
    let wait = 3200;
    if (s === 'home-scroll') {
      go('home'); smoothScroll(home, 360, 2200);
      tourT = setTimeout(() => { smoothScroll(home, 0, 1200); tourT = setTimeout(tour, 1500); }, 2800); return;
    }
    if (TABS.includes(s)) { ripple(nav.querySelector(`[data-tab="${s}"]`)); }
    else if (s === 'rota') { go('home'); const b = home.querySelector('[data-go="rota"]'); home.scrollTop = 300; ripple(b); }
    tourT = setTimeout(() => { if (userActive) return; go(s); if (s === 'home') home.scrollTop = 0; tourT = setTimeout(tour, wait); }, 450);
  }
})();
