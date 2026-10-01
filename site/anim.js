/* Hedef yolculuğu ve Hedefit Rota animasyonları (hero kartları + vitrin bölümleri).
   Ekranda değilken durur; "hareketi azalt" açıksa son kareyi sabit gösterir. */
(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const $ = id => document.getElementById(id);
  const ease = t => t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
  const fmt = (n, d = 2) => n.toFixed(d).replace('.', ',');
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

  /* ---------------- Hero: hedef kartı ---------------- */
  const hg = $('heroGoal');
  if (hg) {
    const line = $('hgLine'), dot = $('hgDot'), kg = $('hgKg'), L = line.getTotalLength();
    line.style.strokeDasharray = L;
    const draw = p => {
      line.style.strokeDashoffset = L * (1 - p);
      const pt = line.getPointAtLength(L * p); dot.setAttribute('cx', pt.x); dot.setAttribute('cy', pt.y);
      kg.textContent = (95 - 15 * p).toFixed(1);
    };
    if (reduce) draw(1); else loop(hg, ms => { const T = 4200, H = 2200; draw(ease(Math.min(1, ms / T))); return ms > T + H; });
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

  /* ---------------- Hedef yolculuğu paneli ---------------- */
  const jp = $('journey');
  if (jp) {
    const WEEKS = [24, 16, 12], RATE = ['0,51', '0,77', '1,02'];
    const START = new Date(2026, 9, 1), FROM = 102, TO = 90;
    const X0 = 30, X1 = 570, Y0 = 40, Y1 = 232;
    const line = $('jLine'), dot = $('jDot'), clip = $('jclipRect'), kg = $('jKg'), badge = $('jBadge');
    const miles = $('jMiles'), list = $('jMileList'), L = line.getTotalLength();
    const FR = [.25, .5, .75, 1];
    const NS = 'http://www.w3.org/2000/svg';
    const nodes = FR.map(f => {
      const c = document.createElementNS(NS, 'circle');
      c.setAttribute('class', 'mile'); c.setAttribute('r', 9);
      c.setAttribute('cx', X0 + (X1 - X0) * f); c.setAttribute('cy', Y0 + (Y1 - Y0) * f);
      miles.appendChild(c); return c;
    });
    const chips = FR.map(() => { const d = document.createElement('div'); list.appendChild(d); return d; });
    line.style.strokeDasharray = L;
    let tempo = 1, p = 0;
    const dateOf = (w, f) => new Date(START.getTime() + w * 7 * f * 86400000).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' });
    const paint = () => {
      line.style.strokeDashoffset = L * (1 - p);
      const pt = line.getPointAtLength(L * p); dot.setAttribute('cx', pt.x); dot.setAttribute('cy', pt.y);
      clip.setAttribute('width', pt.x);
      kg.textContent = (FROM - (FROM - TO) * p).toFixed(1);
      FR.forEach((f, i) => { const hit = p >= f - .002; nodes[i].classList.toggle('hit', hit); chips[i].classList.toggle('hit', hit); });
    };
    const labels = () => {
      const w = WEEKS[tempo];
      $('jRate').textContent = RATE[tempo] + ' kg'; $('jWeeks').textContent = w + ' hafta'; badge.textContent = w + ' hafta';
      badge.classList.add('bump'); setTimeout(() => badge.classList.remove('bump'), 260);
      FR.forEach((f, i) => {
        chips[i].innerHTML = `<small>${f === 1 ? 'HEDEF' : '%' + f * 100}</small><b>${(FROM - (FROM - TO) * f).toFixed(1)} kg</b><span>${dateOf(w, f)}</span>`;
      });
    };
    labels();
    if (reduce) { p = 1; paint(); } else {
      paint();
      const lp = loop(jp, ms => { const T = 4600, H = 3200; p = ease(Math.min(1, ms / T)); paint(); return ms > T + H; });
      $('tempoSeg').addEventListener('click', e => {
        const b = e.target.closest('button'); if (!b) return;
        tempo = +b.dataset.t;
        $('tempoSeg').querySelectorAll('button').forEach(x => x.setAttribute('aria-selected', String(x === b)));
        labels(); lp.restart();
      });
    }
    if (reduce) $('tempoSeg').addEventListener('click', e => {
      const b = e.target.closest('button'); if (!b) return;
      tempo = +b.dataset.t; $('tempoSeg').querySelectorAll('button').forEach(x => x.setAttribute('aria-selected', String(x === b))); labels(); paint();
    });
  }

  /* ---------------- Hedefit Rota vitrini ---------------- */
  const rd = $('routeDemo');
  if (rd) {
    const ACT = {
      run:  { path: 'M70 232 C58 164 88 112 150 102 C214 92 232 142 282 122 C342 98 362 152 332 202 C302 252 202 266 140 252 C102 244 76 244 70 232 Z', plan: ['Dönüşlü', '30 dk'], sec: 1680, km: 5.2, kcal: 62, metric: ['TEMPO', s => hms2(s)], start: [70, 232] },
      bike: { path: 'M52 226 C48 140 112 70 204 68 C304 66 364 122 352 192 C340 262 250 272 190 240 C140 214 98 272 52 226 Z', plan: ['Dönüşlü', '20 km'], sec: 3060, km: 18.4, kcal: 28, metric: ['HIZ', (s, km) => fmt(km / (s / 3600), 1) + ' <i>km/sa</i>'], start: [52, 226] },
      hike: { path: 'M48 252 C92 202 58 162 120 150 C182 138 152 92 212 80 C272 68 250 130 302 120 C344 112 342 72 362 48', plan: ['A → B', '8 km'], sec: 5700, km: 7.8, kcal: 45, metric: ['TEMPO', (s, km) => hms2(s / km) + ' <i>/km</i>'], start: [48, 252] },
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
