/* Hedef yolculuğu ve Hedefit Rota animasyonları (hero kartları + vitrin bölümleri).
   Ekranda değilken durur; "hareketi azalt" açıksa son kareyi sabit gösterir. */
(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const EN = document.documentElement.lang === 'en';
  const t = (tr, en) => (EN ? en : tr);
  const DEC = EN ? '.' : ',';
  const $ = id => document.getElementById(id);
  const ease = t => t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
  const fmt = (n, d = 2) => n.toFixed(d).replace('.', DEC);
  const hms = s => [Math.floor(s / 3600), Math.floor(s / 60) % 60, Math.floor(s) % 60].map(v => String(v).padStart(2, '0')).join(':');

  /** Bir öğe görünürken çalışan, zaman tabanlı döngü. tick(ms) → bitti ise true. */
  function loop(el, tick) {
    let visible = false, raf = 0, t0 = 0, token = 0;
    const frame = now => {
      if (!visible) return;
      if (!t0) t0 = now;
      if (tick(now - t0, now)) { t0 = 0; }
      raf = requestAnimationFrame(frame);
    };
    new IntersectionObserver(es => es.forEach(e => {
      visible = e.isIntersecting;
      cancelAnimationFrame(raf);
      if (visible) { t0 = 0; raf = requestAnimationFrame(frame); }
    }), { threshold: .15 }).observe(el);
    return { restart: () => { t0 = 0; token++; } };
  }

  /* ---------------- Hero: hedef kartı (üç hedef türü sırayla) ---------------- */
  const hg = $('heroGoal');
  if (hg) {
    const line = $('hgLine'), dot = $('hgDot'), kg = $('hgKg'), to = $('hgTo'), lab = $('hgLabel'), note = $('hgNote');
    const HG = [
      { label: t('KİLO VERME', 'WEIGHT LOSS'), from: 95, to: 80, d: 'M6 8 L174 48', note: t('20 hafta · Dengeli tempo', '20 weeks · Balanced pace') },
      { label: t('KAS KAZANMA', 'MUSCLE GAIN'), from: 70, to: 74, d: 'M6 48 L174 8', note: t('16 hafta · Kontrollü artış', '16 weeks · Controlled gain') },
      { label: t('FORMU KORUMA', 'MAINTENANCE'), from: 80, to: 80, d: 'M6 28 L174 28', note: t('Bakım kalorisi · Düzenli antrenman', 'Maintenance calories · Regular training') },
    ];
    let gi = 0, L = 0, cur = HG[0];
    const set = () => {
      cur = HG[gi]; line.setAttribute('d', cur.d); L = line.getTotalLength(); line.style.strokeDasharray = L;
      lab.textContent = cur.label; note.textContent = cur.note; to.textContent = cur.to.toFixed(1);
    };
    const draw = p => {
      line.style.strokeDashoffset = L * (1 - p);
      const pt = line.getPointAtLength(L * p); dot.setAttribute('cx', pt.x); dot.setAttribute('cy', pt.y);
      kg.textContent = (cur.from + (cur.to - cur.from) * p).toFixed(1);
    };
    set();
    if (reduce) draw(1); else loop(hg, ms => {
      const T = 4200, H = 2200; draw(ease(Math.min(1, ms / T)));
      if (ms > T + H) { gi = (gi + 1) % HG.length; set(); return true; }
      return false;
    });
  }

  /* ---------------- Hero: rota kartı ---------------- */
  const hr = $('heroRoute');
  if (hr) {
    const run = $('hrRun'), dot = $('hrDot'), pulse = $('hrPulse'), km = $('hrKm'), L = run.getTotalLength();
    run.style.strokeDasharray = L;
    const draw = p => {
      run.style.strokeDashoffset = L * (1 - p);
      const pt = run.getPointAtLength(L * p);
      [dot, pulse].forEach(c => { c.setAttribute('cx', pt.x); c.setAttribute('cy', pt.y); });
      km.textContent = fmt(5.2 * p);
    };
    if (reduce) { draw(1); pulse.style.display = 'none'; } else loop(hr, ms => { const T = 5200, H = 2000; draw(Math.min(1, ms / T)); return ms > T + H; });
  }

  /* ---------------- Hedef yolculuğu paneli (kilo ver / kas kazan / formu koru) ---------------- */
  const jp = $('journey');
  if (jp) {
    const START = new Date(2026, 9, 1), HEIGHT = 1.8;
    // Hız oranları uygulamadaki GoalPace ile aynı (haftalık vücut ağırlığı oranı).
    const GOALS = {
      lose: { label: t('KİLO VERME', 'WEIGHT LOSS'), from: 102, to: 90, pct: [.005, .0075, .01], note: t('Örnek: 102 kg, 180 cm. Haftada vücut ağırlığının %0,5–1\'i kas kaybını en aza indirir.', 'Example: 102 kg, 180 cm. Losing 0.5–1% of body weight per week minimizes muscle loss.') },
      gain: { label: t('KAS KAZANMA', 'MUSCLE GAIN'), from: 70, to: 74, pct: [.0025, .00375, .005], note: t('Örnek: 70 kg, 180 cm. Haftada %0,25–0,5 kilo alımı kas kazanımını yağ artışına göre en iyi dengeler.', 'Example: 70 kg, 180 cm. Gaining 0.25–0.5% per week best balances muscle gain against fat gain.') },
      keep: { label: t('FORMU KORUMA', 'MAINTENANCE'), from: 80, to: 80, pct: null, note: t('Örnek: 80 kg, 180 cm. Kilonu korurken beslenme ve antrenman düzenini sürdürürsün.', 'Example: 80 kg, 180 cm. You keep your weight while sustaining your nutrition and training routine.') },
    };
    const KEEP_CHIPS = EN ? [['CALORIES', 'Maintenance', 'calorie level'], ['STEPS', '7,000', 'daily goal'], ['WATER', 'Per your goal', 'every day'], ['WORKOUTS', 'Regular', 'weekly plan']] : [['KALORİ', 'Bakım', 'kalori düzeyi'], ['ADIM', '7.000', 'günlük hedef'], ['SU', 'Hedefine göre', 'her gün'], ['ANTRENMAN', 'Düzenli', 'haftalık plan']];
    const X0 = 30, X1 = 570, Y0 = 40, Y1 = 232, YM = 136;
    const line = $('jLine'), dot = $('jDot'), clip = $('jclipRect'), kg = $('jKg'), badge = $('jBadge');
    const miles = $('jMiles'), list = $('jMileList');
    const plan = jp.querySelector('.jplan'), area = jp.querySelector('.jchart [clip-path] path');
    const FR = [.25, .5, .75, 1], NS = 'http://www.w3.org/2000/svg';
    const nodes = FR.map(() => { const c = document.createElementNS(NS, 'circle'); c.setAttribute('class', 'mile'); c.setAttribute('r', 9); miles.appendChild(c); return c; });
    const chips = FR.map(() => { const d = document.createElement('div'); list.appendChild(d); return d; });
    let goal = 'lose', tempo = 1, p = 0, L = 1;
    const fmtKg = n => n.toFixed(1), fmtC = n => n.toFixed(2).replace('.', DEC);
    const bmi = w => (w / (HEIGHT * HEIGHT)).toFixed(1).replace('.', DEC);
    const yAt = f => goal === 'lose' ? Y0 + (Y1 - Y0) * f : goal === 'gain' ? Y1 - (Y1 - Y0) * f : YM;
    const weeksFor = () => { const g = GOALS[goal]; const rate = Math.max(.1, g.from * g.pct[tempo]); return Math.ceil(Math.abs(g.to - g.from) / rate); };
    const dateOf = (w, f) => new Date(START.getTime() + w * 7 * f * 86400000).toLocaleDateString(EN ? 'en-GB' : 'tr-TR', { day: 'numeric', month: 'short' });
    const geometry = () => {
      const d = `M${X0} ${yAt(0)} L${X1} ${yAt(1)}`;
      line.setAttribute('d', d); plan.setAttribute('d', d); area.setAttribute('d', `${d} L${X1} 250 L${X0} 250 Z`);
      L = line.getTotalLength(); line.style.strokeDasharray = L;
      FR.forEach((f, i) => { nodes[i].setAttribute('cx', X0 + (X1 - X0) * f); nodes[i].setAttribute('cy', yAt(f)); });
    };
    const paint = () => {
      const g = GOALS[goal];
      line.style.strokeDashoffset = L * (1 - p);
      const pt = line.getPointAtLength(L * p); dot.setAttribute('cx', pt.x); dot.setAttribute('cy', pt.y);
      clip.setAttribute('width', pt.x);
      kg.textContent = fmtKg(g.from + (g.to - g.from) * p);
      FR.forEach((f, i) => { const hit = p >= f - .002; nodes[i].classList.toggle('hit', hit); chips[i].classList.toggle('hit', hit); });
    };
    const labels = () => {
      const g = GOALS[goal], keep = goal === 'keep';
      $('jLabel').textContent = g.label; $('jTo').textContent = fmtKg(g.to); $('jNote').textContent = g.note;
      jp.querySelector('.jchart').setAttribute('aria-label', keep ? t(`Form koruma: ${fmtKg(g.from)} kilo`, `Maintenance: ${fmtKg(g.from)} kg`) : t(`Planlanan kilo yolu: ${g.from} kilodan ${g.to} kiloya`, `Planned weight path: from ${g.from} kg to ${g.to} kg`));
      $('tempoSeg').classList.toggle('off', keep);
      $('tempoSeg').setAttribute('aria-disabled', String(keep));
      [0, 1, 2].forEach(i => { $('tm' + i).textContent = keep ? '—' : fmtC(g.from * g.pct[i]) + t(' kg/hf', ' kg/wk'); });
      if (keep) {
        $('jRate').textContent = '0 kg'; $('jWeeks').textContent = t('Sürekli', 'Ongoing'); badge.textContent = t('Bakım', 'Maintain');
        $('jBmi').innerHTML = bmi(g.from);
        KEEP_CHIPS.forEach((c, i) => { chips[i].innerHTML = `<small>${c[0]}</small><b>${c[1]}</b><span>${c[2]}</span>`; });
      } else {
        const w = weeksFor();
        $('jRate').textContent = fmtC(g.from * g.pct[tempo]) + ' kg'; $('jWeeks').textContent = w + t(' hafta', ' weeks'); badge.textContent = w + t(' hafta', ' weeks');
        $('jBmi').innerHTML = `${bmi(g.from)} <i>→ ${bmi(g.to)}</i>`;
        FR.forEach((f, i) => { chips[i].innerHTML = `<small>${f === 1 ? t('HEDEF', 'GOAL') : (EN ? f * 100 + '%' : '%' + f * 100)}</small><b>${fmtKg(g.from + (g.to - g.from) * f)} kg</b><span>${dateOf(w, f)}</span>`; });
      }
      badge.classList.add('bump'); setTimeout(() => badge.classList.remove('bump'), 260);
    };
    const refresh = () => { geometry(); labels(); paint(); };
    const pick = (segId, attr, set) => $(segId).addEventListener('click', e => {
      const b = e.target.closest('button'); if (!b || (segId === 'tempoSeg' && goal === 'keep')) return;
      set(b.dataset[attr]);
      $(segId).querySelectorAll('button').forEach(x => x.setAttribute('aria-selected', String(x === b)));
      refresh(); if (lp) lp.restart();
    });
    let lp = null;
    refresh();
    if (reduce) { p = 1; paint(); } else {
      lp = loop(jp, ms => { const T = 4600, H = 3200; p = ease(Math.min(1, ms / T)); paint(); return ms > T + H; });
    }
    if (reduce) { p = 1; }
    pick('goalSeg', 'g', v => { goal = v; });
    pick('tempoSeg', 't', v => { tempo = +v; });
  }

  /* ---------------- Hedefit Rota vitrini ---------------- */
  const rd = $('routeDemo');
  if (rd) {
    const ACT = {
      run:  { path: 'M70 232 C58 164 88 112 150 102 C214 92 232 142 282 122 C342 98 362 152 332 202 C302 252 202 266 140 252 C102 244 76 244 70 232 Z', plan: [t('Dönüşlü', 'Loop'), t('30 dk', '30 min')], sec: 1680, km: 5.2, kcal: 62, metric: [t('TEMPO', 'PACE'), s => hms2(s)], start: [70, 232] },
      bike: { path: 'M52 226 C48 140 112 70 204 68 C304 66 364 122 352 192 C340 262 250 272 190 240 C140 214 98 272 52 226 Z', plan: [t('Dönüşlü', 'Loop'), '20 km'], sec: 3060, km: 18.4, kcal: 28, metric: [t('HIZ', 'SPEED'), (s, km) => fmt(km / (s / 3600), 1) + t(' <i>km/sa</i>', ' <i>km/h</i>')], start: [52, 226] },
      hike: { path: 'M48 252 C92 202 58 162 120 150 C182 138 152 92 212 80 C272 68 250 130 302 120 C344 112 342 72 362 48', plan: ['A → B', '8 km'], sec: 5700, km: 7.8, kcal: 45, metric: [t('TEMPO', 'PACE'), (s, km) => hms2(s / km) + ' <i>/km</i>'], start: [48, 252] },
    };
    function hms2(sPerKm) { const m = Math.floor(sPerKm / 60), s = Math.round(sPerKm % 60); return `${m}:${String(s).padStart(2, '0')}`; }
    ACT.run.metric[1] = (s, km) => hms2(s / km) + ' <i>/km</i>';
    const plan = $('rPlan'), live = $('rLive'), dot = $('rDot'), pulse = $('rPulse'), start = $('rStart');
    const card = $('rPlanCard'), done = $('rDone');
    const steps = rd.querySelectorAll('.r-step'), listSteps = document.querySelectorAll('.steps li');
    let cur = 'run', Lp = 0;
    const setStage = s => {
      steps.forEach((el, i) => el.classList.toggle('on', i === s));
      listSteps.forEach((el, i) => el.classList.toggle('on', i === s));
    };
    const load = key => {
      cur = key; const a = ACT[key];
      plan.setAttribute('d', a.path); live.setAttribute('d', a.path);
      Lp = live.getTotalLength();
      plan.style.strokeDasharray = '2 9'; live.style.strokeDasharray = Lp;
      start.setAttribute('cx', a.start[0]); start.setAttribute('cy', a.start[1]);
      card.querySelector('b').innerHTML = `${a.plan[0]} · <span id="rPlanGoal">${a.plan[1]}</span>`;
      $('rPaceLbl').textContent = a.metric[0];
    };
    const stats = p => {
      const a = ACT[cur], s = a.sec * p, km = a.km * p;
      $('rTime').textContent = hms(s); $('rKm').textContent = fmt(km);
      $('rKcal').textContent = Math.round(km * a.kcal);
      $('rPace').innerHTML = p > .04 ? a.metric[1](s, km) : '–';
    };
    const moveDot = p => {
      const pt = live.getPointAtLength(Lp * p);
      [dot, pulse].forEach(c => { c.setAttribute('cx', pt.x); c.setAttribute('cy', pt.y); });
    };
    const PLAN = 2400, REC = 6200, DONE = 3400;
    const frame = ms => {
      const a = ACT[cur];
      if (ms < PLAN) {
        setStage(0); const q = ease(ms / PLAN);
        live.style.strokeDashoffset = Lp; card.classList.toggle('show', ms > 250); done.classList.remove('show');
        plan.style.opacity = Math.min(1, q * 1.4); stats(0); moveDot(0); pulse.style.opacity = 0;
      } else if (ms < PLAN + REC) {
        setStage(1); const p = (ms - PLAN) / REC;
        card.classList.remove('show'); done.classList.remove('show'); plan.style.opacity = 1; pulse.style.opacity = 1;
        live.style.strokeDashoffset = Lp * (1 - p); moveDot(p); stats(p);
      } else {
        setStage(2); live.style.strokeDashoffset = 0; moveDot(1); stats(1); pulse.style.opacity = 0; done.classList.add('show');
      }
      return ms > PLAN + REC + DONE;
    };
    load('run');
    if (reduce) { setStage(2); plan.style.opacity = 1; live.style.strokeDashoffset = 0; moveDot(1); stats(1); pulse.style.display = 'none'; done.classList.add('show'); }
    const lp = reduce ? null : loop(rd, frame);
    rd.querySelector('.r-chips').addEventListener('click', e => {
      const b = e.target.closest('button'); if (!b) return;
      rd.querySelectorAll('.r-chips button').forEach(x => x.setAttribute('aria-selected', String(x === b)));
      load(b.dataset.a); if (lp) lp.restart(); else { live.style.strokeDashoffset = 0; moveDot(1); stats(1); }
    });
  }
})();
