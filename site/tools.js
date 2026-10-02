/* "Kendin dene" araçları (hedef hesabı, kas atlası, seri takvimi, rota), plan bulucu, SSS araması, paylaş.
   Formüller uygulamayla aynıdır: lib/goal-plan.ts + lib/nutrition-goals.ts. Hiçbir veri gönderilmez/saklanmaz. */
(() => {
  const EN = document.documentElement.lang === 'en';
  const t = (tr, en) => (EN ? en : tr);
  const DEC = EN ? '.' : ',';
  const num = (n, d = 0) => Number(n).toFixed(d).replace('.', DEC).replace(/\B(?=(\d{3})+(?!\d))/g, EN ? ',' : '.');
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const NS = 'http://www.w3.org/2000/svg';
  const el = (name, attrs = {}, parent) => { const e = document.createElementNS(NS, name); for (const k in attrs) e.setAttribute(k, attrs[k]); parent && parent.appendChild(e); return e; };
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------- SSS araması ---------- */
  const fq = $('#faqQ');
  if (fq) {
    const items = $$('.faq details'), none = $('#faqNone');
    const norm = s => s.toLocaleLowerCase(EN ? 'en' : 'tr').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/ı/g, 'i');
    fq.addEventListener('input', () => {
      const q = norm(fq.value.trim()); let shown = 0;
      items.forEach(d => { const hit = !q || norm(d.textContent).includes(q); d.hidden = !hit; if (hit) shown++; if (q && hit) d.open = true; });
      none.hidden = shown > 0;
    });
  }

  /* ---------- Paylaş ---------- */
  $$('.share').forEach(btn => btn.addEventListener('click', async () => {
    const data = { title: 'Hedefit', text: t('Hedefit: antrenman, öğün ve yapay zekâ koçu tek uygulamada.', 'Hedefit: workouts, meals and an AI coach in one app.'), url: location.origin + (EN ? '/en/' : '/') };
    const msg = btn.parentElement.querySelector('.share-msg');
    try { if (navigator.share) { await navigator.share(data); return; } } catch { return; }
    try { await navigator.clipboard.writeText(data.url); msg.textContent = t('Bağlantı kopyalandı.', 'Link copied.'); }
    catch { msg.textContent = data.url; }
  }));

  /* ---------- Plan bulucu (planlar sayfası) ---------- */
  const fin = $('#finder');
  if (fin) {
    const NAMES = ['Free', 'Plus', 'Premium'];
    const out = $('#finderOut');
    const run = () => {
      const val = n => { const c = $(`input[name="${n}"]:checked`, fin); return c ? +c.value : 0; };
      const need = $$('input[name="need"]:checked', fin).map(i => +i.value);
      const tier = Math.max(val('coach'), val('photo'), val('prog'), ...need, 0);
      const why = [];
      const L = (g, a, b, c) => [a, b, c][g];
      why.push(t(`Fit Koç: günde ${L(val('coach'), 5, 20, 40)} soruya kadar`, `AI coach: up to ${L(val('coach'), 5, 20, 40)} questions a day`));
      why.push(t(`Fotoğraftan kalori: günde ${L(val('photo'), 1, 3, 15)} fotoğrafa kadar`, `Photo calories: up to ${L(val('photo'), 1, 3, 15)} photos a day`));
      why.push(val('prog') === 0 ? t('Kendi programı: 2’ye kadar', 'Own programs: up to 2') : val('prog') === 1 ? t('Kendi programı: 5’e kadar', 'Own programs: up to 5') : t('Kendi programı: sınırsız', 'Own programs: unlimited'));
      if (need.length) why.push(t('İstediğin özellikler bu planda var', 'The features you picked are included'));
      out.innerHTML = '';
      const card = document.createElement('div'); card.className = 'fo-card t' + tier;
      const h = document.createElement('p'); h.className = 'fo-h'; h.textContent = t('Sana uygun plan', 'Your best fit');
      const b = document.createElement('b'); b.textContent = NAMES[tier];
      const ul = document.createElement('ul'); why.forEach(w => { const li = document.createElement('li'); li.textContent = w; ul.appendChild(li); });
      const p = document.createElement('p'); p.className = 'fo-n';
      p.textContent = tier === 0 ? t('Ücretsiz hesap ihtiyaçlarına yetiyor. İstediğin zaman yükseltebilirsin.', 'A free account covers your needs. You can upgrade any time.') : t('Fiyat, mağaza sayfalarında yayınlanınca açıklanacak.', 'Pricing will be shown on the store pages at launch.');
      card.append(h, b, ul, p); out.appendChild(card);
    };
    fin.addEventListener('change', run); run();
  }

  const tabs = $('#tryTabs');
  if (!tabs) return;

  /* ---------- Sekmeler ---------- */
  const panels = $$('.tpanel');
  const init = {};
  const show = k => {
    $$('button', tabs).forEach(b => b.setAttribute('aria-selected', String(b.dataset.t === k)));
    panels.forEach(p => { p.hidden = p.dataset.p !== k; });
    if (!init[k]) { init[k] = true; ({ calc, atlas, streak, route })[k](); }
  };
  tabs.addEventListener('click', e => { const b = e.target.closest('button[data-t]'); if (b) show(b.dataset.t); });

  /* ---------- Hedef hesabı ---------- */
  function calc() {
    const form = $('#calcForm'), out = $('#calcOut');
    const FR = { easy: .005, steady: .0075, hard: .01 }, GR = { easy: .0025, steady: .00375, hard: .005 }, MET = { easy: 4.5, steady: 6, hard: 7.5 };
    const compute = () => {
      const f = Object.fromEntries(new FormData(form));
      const age = clamp(+f.age || 0, 14, 90), h = clamp(+f.height || 0, 120, 230), w = clamp(+String(f.weight).replace(',', '.') || 0, 35, 250), tg = clamp(+String(f.target).replace(',', '.') || 0, 35, 250);
      const days = +f.days, mins = +f.mins, pace = f.pace;
      const bmr = Math.round(10 * w + 6.25 * h - 5 * age + ({ m: 5, f: -161, x: -78 }[f.sex]));
      const burn = Math.round((MET[pace] * 3.5 * w / 200 * mins * days) / 7);
      const maintenance = Math.round(bmr * 1.2 + burn);
      const diff = tg - w, rem = Math.abs(diff), losing = diff < 0;
      let mode = 'keep', weeks = 0, rate = 0, intake = maintenance, delta = 0, clamped = false;
      if (rem >= .5) {
        mode = losing ? 'lose' : 'gain';
        const req = w * (losing ? FR : GR)[pace], cap = w * .01, mag = Math.min(req, cap); clamped = req > cap + 1e-9;
        rate = mag; weeks = Math.max(1, Math.round(rem / mag));
        delta = Math.round(mag * 7700 / 7);
        intake = Math.round(losing ? maintenance - Math.max(0, delta - burn) : maintenance + delta);
      }
      const protein = Math.round(clamp(clamp(w, 45, 90) * (days <= 3 ? 1.2 : 1.4), 50, Math.max(50, Math.min(140, Math.floor(intake * .275 / 4)))));
      const fat = Math.round(intake * .275 / 9);
      const carbs = Math.round(Math.max(0, intake - protein * 4 - fat * 9) / 4);
      return { w, tg, bmr, burn, maintenance, mode, weeks, rate, intake, protein, fat, carbs, clamped, low: intake < bmr, capped: weeks > 104 };
    };
    const render = () => {
      const r = compute();
      const eta = new Date(Date.now() + Math.min(r.weeks, 104) * 7 * 864e5).toLocaleDateString(EN ? 'en-GB' : 'tr-TR', { month: 'long', year: 'numeric' });
      const title = { lose: t('Kilo verme planı', 'Weight-loss plan'), gain: t('Kas / kilo alma planı', 'Muscle / weight-gain plan'), keep: t('Formu koruma planı', 'Maintenance plan') }[r.mode];
      const head = r.mode === 'keep' ? t('Bugünkü kilonu koru', 'Hold your current weight') : t(`Yaklaşık ${num(r.weeks)} hafta · ${eta}`, `About ${num(r.weeks)} weeks · ${eta}`);
      out.innerHTML = '';
      const top = document.createElement('div'); top.className = 'co-top';
      top.innerHTML = '<p class="eyebrow"></p><h3></h3>'; top.firstChild.textContent = title; top.lastChild.textContent = head; out.appendChild(top);
      const grid = document.createElement('div'); grid.className = 'co-grid';
      const cell = (v, l, cls) => { const d = document.createElement('div'); if (cls) d.className = cls; const b = document.createElement('b'); b.textContent = v; const s = document.createElement('small'); s.textContent = l; d.append(b, s); grid.appendChild(d); };
      cell(num(r.intake) + ' kcal', t('günlük hedef', 'daily target'), 'big');
      cell(num(r.protein) + ' g', t('protein', 'protein'));
      cell(num(r.carbs) + ' g', t('karbonhidrat', 'carbs'));
      cell(num(r.fat) + ' g', t('yağ', 'fat'));
      cell(r.mode === 'keep' ? '±0' : (r.mode === 'lose' ? '−' : '+') + num(r.rate, 2) + ' kg', t('haftalık hız', 'weekly pace'));
      cell(num(r.burn) + ' kcal', t('antrenmandan günlük yakım', 'daily training burn'));
      out.appendChild(grid);
      if (r.mode !== 'keep') {
        const W = 300, H = 70, n = Math.min(r.weeks, 104), svg = el('svg', { viewBox: `0 0 ${W} ${H}`, class: 'co-chart', role: 'img', 'aria-label': t('Tahmini kilo eğrisi', 'Projected weight curve') });
        const pts = []; for (let i = 0; i <= n; i++) { const x = 6 + (W - 12) * i / n; const wt = r.mode === 'lose' ? Math.max(r.tg, r.w - r.rate * i) : Math.min(r.tg, r.w + r.rate * i); const lo = Math.min(r.w, r.tg), hi = Math.max(r.w, r.tg); pts.push([x, 8 + (H - 16) * (1 - (wt - lo) / (hi - lo || 1))]); }
        el('path', { d: 'M' + pts.map(p => p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join('L'), fill: 'none', stroke: 'var(--g)', 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, svg);
        el('circle', { cx: pts[0][0], cy: pts[0][1], r: 4, fill: '#fff' }, svg); el('circle', { cx: pts[n][0], cy: pts[n][1], r: 5, fill: 'var(--g)' }, svg);
        const lab = document.createElement('div'); lab.className = 'co-lab'; lab.innerHTML = '<span></span><span></span>';
        lab.firstChild.textContent = num(r.w, 1) + ' kg'; lab.lastChild.textContent = num(r.tg, 1) + ' kg';
        out.append(svg, lab);
      }
      const notes = [];
      if (r.clamped) notes.push(t('Seçtiğin tempo güvenli sınırı (haftada kilonun %1’i) aştığı için sınırlandı.', 'Your chosen pace exceeded the safe limit (1% of body weight a week), so it was capped.'));
      if (r.low) notes.push(t('Bu hızda günlük alım bazal metabolizma hızının altına iniyor. Daha rahat bir tempo seç.', 'At this pace daily intake drops below your basal metabolic rate. Pick an easier pace.'));
      if (r.capped) notes.push(t('Bu hedef 2 yıldan uzun sürer; daha yakın bir ara hedef koymak daha gerçekçi olur.', 'This goal takes over two years; a closer milestone is more realistic.'));
      if (r.mode === 'gain') notes.push(t('Kas yapımında hedef, haftada kilonun %0,5’ini geçmeyen yavaş bir artıştır; fazlası çoğunlukla yağ olarak eklenir.', 'For muscle gain the aim is a slow increase, under 0.5% of body weight a week; faster mostly adds fat.'));
      notes.forEach(n => { const p = document.createElement('p'); p.className = 'co-note'; p.textContent = n; out.appendChild(p); });
    };
    form.addEventListener('input', render); form.addEventListener('submit', e => e.preventDefault()); render();
  }

  /* ---------- Kas atlası ---------- */
  function atlas() {
    const svg = $('#atlasSvg'), info = $('#atlasInfo');
    const M = {
      shoulders: { n: ['Omuz', 'Shoulders'], ex: [['Askılı omuz presi', 'Overhead press'], ['Yan omuz kaldırma', 'Lateral raise'], ['Face pull', 'Face pull'], ['Arnold press', 'Arnold press']], tip: ['Omuzlar hem ön hem arka yüzde çalışır; yan ve arka başı ihmal etme.', 'Shoulders work from the front and the back; do not skip side and rear delts.'] },
      chest: { n: ['Göğüs', 'Chest'], ex: [['Bench press', 'Bench press'], ['Eğimli dumbbell press', 'Incline dumbbell press'], ['Kablo crossover', 'Cable crossover'], ['Şınav', 'Push-up']], tip: ['Skapulayı geride ve aşağıda tutmak göğsü daha iyi yükler.', 'Keeping the shoulder blades back and down loads the chest better.'] },
      abs: { n: ['Karın', 'Abs'], ex: [['Plank', 'Plank'], ['Asılı bacak kaldırma', 'Hanging leg raise'], ['Kablo crunch', 'Cable crunch'], ['Ab wheel', 'Ab wheel']], tip: ['Karın, sıkı bir gövde için her antrenmanın sonunda kısa çalışılabilir.', 'Abs can be trained briefly at the end of any session.'] },
      biceps: { n: ['Pazı (biceps)', 'Biceps'], ex: [['Barbell curl', 'Barbell curl'], ['Hammer curl', 'Hammer curl'], ['Incline dumbbell curl', 'Incline dumbbell curl'], ['Chin-up', 'Chin-up']], tip: ['Dirseği sabit tut; sallanmak yükü kastan alır.', 'Keep the elbow fixed; swinging takes load off the muscle.'] },
      quads: { n: ['Ön bacak (quadriceps)', 'Quadriceps'], ex: [['Squat', 'Squat'], ['Leg press', 'Leg press'], ['Bulgarian split squat', 'Bulgarian split squat'], ['Leg extension', 'Leg extension']], tip: ['Dizler ayak ucuyla aynı hizada ilerlesin.', 'Let the knees track in line with the toes.'] },
      calves: { n: ['Baldır', 'Calves'], ex: [['Ayakta baldır kaldırma', 'Standing calf raise'], ['Oturarak baldır kaldırma', 'Seated calf raise'], ['İp atlama', 'Jump rope']], tip: ['Tam hareket genişliği ve alt noktada kısa bekleme işe yarar.', 'Full range of motion with a short pause at the bottom works well.'] },
      traps: { n: ['Trapez', 'Traps'], ex: [['Barbell shrug', 'Barbell shrug'], ['Farmer’s walk', 'Farmer’s walk'], ['Face pull', 'Face pull']], tip: ['Omuzları kulağa değil, dik yukarı çek.', 'Shrug straight up, not rolling the shoulders.'] },
      lats: { n: ['Sırt (lat)', 'Lats'], ex: [['Barfiks', 'Pull-up'], ['Lat pulldown', 'Lat pulldown'], ['Barbell row', 'Barbell row'], ['Tek kol dumbbell row', 'One-arm dumbbell row']], tip: ['Çekişi kollarla değil, dirsekleri cebe götürür gibi yap.', 'Pull by driving the elbows toward your pockets, not with the hands.'] },
      lowerback: { n: ['Bel', 'Lower back'], ex: [['Hiperekstansiyon', 'Back extension'], ['Romanian deadlift', 'Romanian deadlift'], ['Good morning', 'Good morning']], tip: ['Belini nötr tut; ağırlıktan önce formu oturt.', 'Keep the spine neutral; lock in form before adding load.'] },
      triceps: { n: ['Arka kol (triceps)', 'Triceps'], ex: [['Triceps pushdown', 'Triceps pushdown'], ['Skull crusher', 'Skull crusher'], ['Dips', 'Dips'], ['Close-grip bench', 'Close-grip bench press']], tip: ['Kolun üçte ikisini triceps oluşturur; itişlerin yanında ayrıca çalış.', 'Triceps make up most of the upper arm; train them besides pressing.'] },
      glutes: { n: ['Kalça (gluteus)', 'Glutes'], ex: [['Hip thrust', 'Hip thrust'], ['Deadlift', 'Deadlift'], ['Yürüyüş lunge', 'Walking lunge'], ['Kablo kickback', 'Cable kickback']], tip: ['Üst noktada kalçayı sıkıp bir saniye bekle.', 'Squeeze the glutes and pause for a second at the top.'] },
      hamstrings: { n: ['Arka bacak (hamstring)', 'Hamstrings'], ex: [['Romanian deadlift', 'Romanian deadlift'], ['Lying leg curl', 'Lying leg curl'], ['Nordic curl', 'Nordic curl']], tip: ['Kalçayı geriye iterek yükle, dizi kilitleme.', 'Load by pushing the hips back; do not lock the knees.'] },
    };
    // şematik gövde: [bölge, şekil, [cx'e göre x, y, genişlik, yükseklik], görünüm]
    const FRONT = [['shoulders', -58, 84, 17, 17, 'c'], ['shoulders', 58, 84, 17, 17, 'c'], ['chest', -23, 118, 21, 20, 'r'], ['chest', 23, 118, 21, 20, 'r'], ['abs', 0, 172, 17, 28, 'r'], ['biceps', -70, 128, 9, 24, 'r'], ['biceps', 70, 128, 9, 24, 'r'], ['quads', -22, 270, 18, 40, 'r'], ['quads', 22, 270, 18, 40, 'r'], ['calves', -22, 350, 12, 28, 'r'], ['calves', 22, 350, 12, 28, 'r']];
    const BACK = [['shoulders', -58, 84, 17, 17, 'c'], ['shoulders', 58, 84, 17, 17, 'c'], ['traps', 0, 88, 24, 14, 'r'], ['lats', -24, 135, 18, 28, 'r'], ['lats', 24, 135, 18, 28, 'r'], ['lowerback', 0, 190, 20, 16, 'r'], ['triceps', -70, 128, 9, 24, 'r'], ['triceps', 70, 128, 9, 24, 'r'], ['glutes', -20, 238, 19, 18, 'r'], ['glutes', 20, 238, 19, 18, 'r'], ['hamstrings', -22, 290, 17, 26, 'r'], ['hamstrings', 22, 290, 17, 26, 'r'], ['calves', -22, 350, 12, 28, 'r'], ['calves', 22, 350, 12, 28, 'r']];
    const body = (cx, parts) => {
      const g = el('g', {}, svg);
      el('circle', { cx, cy: 36, r: 21, class: 'bd' }, g); el('rect', { x: cx - 8, y: 54, width: 16, height: 14, class: 'bd' }, g);
      el('path', { d: `M${cx - 56} 72 Q${cx} 62 ${cx + 56} 72 L${cx + 40} 214 Q${cx} 224 ${cx - 40} 214 Z`, class: 'bd' }, g);
      [-1, 1].forEach(s => {
        el('rect', { x: cx + s * 73 - 11, y: 76, width: 22, height: 90, rx: 10, class: 'bd' }, g);
        el('rect', { x: cx + s * 74 - 9, y: 164, width: 18, height: 66, rx: 9, class: 'bd' }, g);
        el('rect', { x: cx + s * 22 - 19, y: 220, width: 38, height: 108, rx: 16, class: 'bd' }, g);
        el('rect', { x: cx + s * 22 - 14, y: 326, width: 28, height: 66, rx: 12, class: 'bd' }, g);
      });
      parts.forEach(([m, dx, y, a, b, k]) => {
        const node = k === 'c' ? el('circle', { cx: cx + dx, cy: y, r: a }, g) : el('rect', { x: cx + dx - a, y: y - b, width: a * 2, height: b * 2, rx: 8 }, g);
        node.setAttribute('class', 'mz'); node.dataset.m = m; node.setAttribute('tabindex', '0'); node.setAttribute('role', 'button'); node.setAttribute('aria-label', M[m].n[EN ? 1 : 0]);
      });
    };
    body(110, FRONT); body(330, BACK);
    const pick = m => {
      $$('.mz', svg).forEach(n => n.classList.toggle('on', n.dataset.m === m));
      const d = M[m]; info.innerHTML = '';
      const h = document.createElement('h3'); h.textContent = d.n[EN ? 1 : 0];
      const ul = document.createElement('ul'); d.ex.forEach(x => { const li = document.createElement('li'); li.textContent = x[EN ? 1 : 0]; ul.appendChild(li); });
      const p = document.createElement('p'); p.textContent = d.tip[EN ? 1 : 0];
      const lab = document.createElement('p'); lab.className = 'eyebrow'; lab.textContent = t('Örnek hareketler', 'Example exercises');
      info.append(h, lab, ul, p);
    };
    svg.addEventListener('click', e => { const n = e.target.closest('.mz'); if (n) pick(n.dataset.m); });
    svg.addEventListener('keydown', e => { if ((e.key === 'Enter' || e.key === ' ') && e.target.classList.contains('mz')) { e.preventDefault(); pick(e.target.dataset.m); } });
    pick('chest');
  }

  /* ---------- Seri takvimi ---------- */
  function streak() {
    const grid = $('#skGrid'), day = $('#skDay');
    const REST = new Set([12, 15, 19, 22, 26, 28]);
    const WK = [['Üst vücut', 'Upper body'], ['Bacak', 'Legs'], ['İtiş', 'Push'], ['Çekiş', 'Pull'], ['Tüm vücut', 'Full body']];
    const dow = EN ? ['M', 'T', 'W', 'T', 'F', 'S', 'S'] : ['P', 'S', 'Ç', 'P', 'C', 'C', 'P'];
    const today = new Date(), days = [];
    for (let a = 29; a >= 0; a--) { const d = new Date(today); d.setDate(d.getDate() - a); days.push({ a, d, on: !REST.has(a) }); }
    let run = 0; for (let a = 0; a < 30 && !REST.has(a); a++) run++;
    $('#skStreak').textContent = run; $('#skActive').textContent = days.filter(x => x.on).length;
    const lead = (days[0].d.getDay() + 6) % 7;
    dow.forEach(c => { const h = document.createElement('span'); h.className = 'sk-h'; h.textContent = c; h.setAttribute('aria-hidden', 'true'); grid.appendChild(h); });
    for (let i = 0; i < lead; i++) grid.appendChild(document.createElement('span'));
    const fmt = d => d.toLocaleDateString(EN ? 'en-GB' : 'tr-TR', { weekday: 'long', day: 'numeric', month: 'long' });
    const sel = x => {
      $$('.sk-c', grid).forEach(b => b.setAttribute('aria-pressed', String(b.dataset.a == x.a)));
      const i = x.a, w = WK[i % 5], mins = 38 + (i * 7) % 16, kcal = 250 + (i * 23) % 120;
      day.innerHTML = '';
      const h = document.createElement('h3'); h.textContent = fmt(x.d); day.appendChild(h);
      const rows = x.on ? [[t('Antrenman', 'Workout'), `${w[EN ? 1 : 0]} · ${mins} ${t('dk', 'min')} · ${kcal} kcal`], [t('Öğünler', 'Meals'), `${num(1900 + (i * 41) % 380)} kcal · ${112 + (i * 5) % 30} g ${t('protein', 'protein')}`], [t('Adım', 'Steps'), num(6200 + (i * 313) % 4600)]] : [[t('Dinlenme günü', 'Rest day'), t('Seri bu gün için dinlenme hakkıyla korunur.', 'Your streak is kept with a rest day.')]];
      rows.forEach(([k, v]) => { const r = document.createElement('p'); const b = document.createElement('b'); b.textContent = k; r.append(b, ' ' + v); day.appendChild(r); });
    };
    days.forEach(x => {
      const b = document.createElement('button'); b.type = 'button'; b.className = 'sk-c' + (x.on ? ' on' : ''); b.dataset.a = x.a; b.textContent = x.d.getDate();
      b.setAttribute('aria-label', fmt(x.d)); b.setAttribute('aria-pressed', 'false'); b.addEventListener('click', () => sel(x)); grid.appendChild(b);
    });
    sel(days[days.length - 1]);
  }

  /* ---------- Rota ---------- */
  function route() {
    const km = $('#rtKm'), svg = $('#rtSvg');
    let kind = 'run';
    const K = { run: { spd: 10, kcal: 62 }, walk: { spd: 5, kcal: 48 }, bike: { spd: 22, kcal: 28 } };
    const draw = () => {
      const d = +km.value, lobes = 2 + Math.floor(d / 7), R = 38 + Math.min(52, d * 2.4);
      svg.innerHTML = '';
      const pts = [];
      for (let i = 0; i <= 90; i++) { const a = Math.PI * 2 * i / 90, r = R * (1 + .22 * Math.sin(lobes * a + 1) + .1 * Math.cos(2 * a)); pts.push([150 + r * Math.cos(a) * 1.35, 110 + r * Math.sin(a) * .95]); }
      for (let i = 1; i < 5; i++) { el('line', { x1: 0, x2: 300, y1: i * 44, y2: i * 44, class: 'rg' }, svg); el('line', { y1: 0, y2: 220, x1: i * 60, x2: i * 60, class: 'rg' }, svg); }
      const path = el('path', { d: 'M' + pts.map(p => p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join('L') + 'Z', class: 'rl' }, svg);
      if (!reduce) { path.setAttribute('pathLength', '1'); path.style.animation = 'rdraw 1.1s ease-out'; }
      el('circle', { cx: pts[0][0], cy: pts[0][1], r: 6, fill: 'var(--g)' }, svg);
      const k = K[kind], mins = d / k.spd * 60, h = Math.floor(mins / 60), m = Math.round(mins % 60);
      $('#rtKmOut').textContent = num(d, d % 1 ? 1 : 0) + ' km';
      $('#rtTime').textContent = h ? `${h}${t('s', 'h')} ${String(m).padStart(2, '0')}${t('dk', 'm')}` : `${m} ${t('dk', 'min')}`;
      if (kind === 'bike') { $('#rtPace').textContent = num(k.spd) + ' km/h'; $('#rtPaceL').textContent = t('Hız', 'Speed'); }
      else { const pm = 60 / k.spd; $('#rtPace').textContent = `${Math.floor(pm)}:${String(Math.round((pm % 1) * 60)).padStart(2, '0')} /km`; $('#rtPaceL').textContent = t('Tempo', 'Pace'); }
      $('#rtKcal').textContent = num(Math.round(d * k.kcal)) + ' kcal';
    };
    km.addEventListener('input', draw);
    $('#rtKind').addEventListener('click', e => { const b = e.target.closest('button[data-k]'); if (!b) return; kind = b.dataset.k; $$('#rtKind button').forEach(x => x.setAttribute('aria-selected', String(x === b))); draw(); });
    draw();
  }

  calc(); init.calc = true;
})();
