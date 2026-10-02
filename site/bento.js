/* Ana sayfa "Dokun, dene" kartları: her biri uygulamanın küçük bir parçasını taklit eder.
   Hedef hesabı uygulamayla aynı oranları kullanır (lib/goal-plan.ts). Hiçbir veri gönderilmez. */
(() => {
  const root = document.querySelector('.bgrid');
  if (!root) return;
  const EN = document.documentElement.lang === 'en';
  const t = (tr, en) => (EN ? en : tr);
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const num = (n, d = 0) => Number(n).toFixed(d).replace('.', EN ? '.' : ',').replace(/\B(?=(\d{3})+(?!\d))/g, EN ? ',' : '.');
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const NS = 'http://www.w3.org/2000/svg';
  const el = (n, a = {}, p) => { const e = document.createElementNS(NS, n); for (const k in a) e.setAttribute(k, a[k]); p && p.appendChild(e); return e; };

  /* --- Set bitir --- */
  (() => {
    const sets = $$('#bSets .set'), cnt = $('#bSetCount'), rest = $('#bRest'), xp = $('#bXp');
    let timer = null;
    const stop = () => { clearInterval(timer); timer = null; rest.textContent = ''; };
    sets.forEach(b => b.addEventListener('click', () => {
      b.classList.toggle('done');
      const n = sets.filter(x => x.classList.contains('done')).length;
      cnt.textContent = `${n} / ${sets.length}`; xp.hidden = n < sets.length;
      stop();
      if (b.classList.contains('done') && n < sets.length) {
        let s = 60; rest.textContent = t(`Dinlenme ${s} sn`, `Rest ${s} s`);
        timer = setInterval(() => { s--; if (s <= 0 || document.hidden) { if (s <= 0) stop(); return; } rest.textContent = t(`Dinlenme ${s} sn`, `Rest ${s} s`); }, 1000);
      }
    }));
  })();

  /* --- Hedef --- */
  (() => {
    const START = 95, goal = $('#bGoal'), out = $('#bGoalOut'), chart = $('#bChart');
    const MODE = { lose: { min: 60, max: 94, v: 80, f: .0075 }, gain: { min: 96, max: 108, v: 102, f: .00375 }, keep: { min: 95, max: 95, v: 95, f: 0 } };
    let mode = 'lose';
    const render = () => {
      const g = +goal.value, M = MODE[mode];
      out.textContent = `${g} kg`;
      const rem = Math.abs(g - START), weekly = START * M.f, weeks = weekly ? Math.max(1, Math.round(rem / weekly)) : 0;
      $('#bWeeks').textContent = mode === 'keep' ? '∞' : num(weeks);
      $('#bWeeksL').textContent = mode === 'keep' ? t('sürdür', 'ongoing') : t('hafta', 'weeks');
      $('#bEta').textContent = mode === 'keep' ? t('her gün', 'every day') : new Date(Date.now() + weeks * 7 * 864e5).toLocaleDateString(EN ? 'en-GB' : 'tr-TR', { month: 'short', year: 'numeric' });
      $('#bPace').textContent = mode === 'keep' ? '±0' : (mode === 'lose' ? '−' : '+') + num(weekly, 2) + ' kg';
      chart.innerHTML = '';
      const W = 300, H = 60, n = Math.max(1, Math.min(weeks, 104)), pts = [];
      for (let i = 0; i <= n; i++) { const w = mode === 'keep' ? START : mode === 'lose' ? Math.max(g, START - weekly * i) : Math.min(g, START + weekly * i); const lo = Math.min(START, g), hi = Math.max(START, g); pts.push([6 + (W - 12) * i / n, mode === 'keep' ? H / 2 : 6 + (H - 12) * (1 - (w - lo) / (hi - lo || 1))]); }
      el('path', { d: 'M' + pts.map(p => p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join('L'), fill: 'none', stroke: 'var(--g)', 'stroke-width': 3, 'stroke-linecap': 'round' }, chart);
      el('circle', { cx: pts[0][0], cy: pts[0][1], r: 4, fill: '#fff' }, chart); el('circle', { cx: pts[pts.length - 1][0], cy: pts[pts.length - 1][1], r: 5, fill: 'var(--g)' }, chart);
    };
    goal.addEventListener('input', render);
    $('#bMode').addEventListener('click', e => {
      const b = e.target.closest('button[data-m]'); if (!b) return; mode = b.dataset.m; const M = MODE[mode];
      $$('#bMode button').forEach(x => x.setAttribute('aria-selected', String(x === b)));
      goal.min = M.min; goal.max = M.max; goal.value = M.v; goal.disabled = mode === 'keep'; render();
    });
    render();
  })();

  /* --- Seri --- */
  (() => {
    const wk = $('#bWeek'), msg = $('#bStreakMsg'), cnt = $('#bStreak');
    const D = EN ? ['M', 'T', 'W', 'T', 'F', 'S', 'S'] : ['P', 'S', 'Ç', 'P', 'C', 'C', 'P'];
    const st = [1, 1, 1, 0, 1, 1, 0];      // 1 yapıldı, 0 boş, 2 dinlenme hakkı
    let token = 1;
    const calc = () => { let n = 0; for (let i = 6; i >= 0; i--) { if (st[i] === 1) n++; else if (st[i] === 2) continue; else if (i === 6) continue; else break; } return n; };
    const draw = () => {
      wk.innerHTML = '';
      st.forEach((s, i) => {
        const b = document.createElement('button'); b.type = 'button'; b.className = 'bd d' + s;
        b.innerHTML = `<small>${D[i]}</small><b>${s === 1 ? '✓' : s === 2 ? '☾' : ''}</b>`;
        b.setAttribute('aria-label', `${D[i]}: ${s === 1 ? t('yapıldı', 'done') : s === 2 ? t('dinlenme günü', 'rest day') : t('boş', 'empty')}`);
        b.addEventListener('click', () => {
          if (i === 6) { st[6] = st[6] ? 0 : 1; msg.textContent = st[6] ? t('Bugün de tamam, seri büyüyor.', 'Done today too, the streak grows.') : t('Bugünü henüz yapmadın.', 'Not done yet today.'); }
          else if (s === 0) {
            if (token > 0) { token--; st[i] = 2; msg.textContent = t('Dinlenme hakkı kullandın, seri korundu.', 'Rest day used, your streak is safe.'); }
            else msg.textContent = t('Bu hafta dinlenme hakkın bitti.', 'No rest days left this week.');
          } else if (s === 2) { token++; st[i] = 0; msg.textContent = t('Hak geri alındı.', 'Rest day returned.'); }
          draw();
        });
        wk.appendChild(b);
      });
      cnt.textContent = calc();
    };
    draw();
  })();

  /* --- Su --- */
  (() => {
    let ml = 1000; const GOAL = 2500, ring = $('#bRing'), C = 2 * Math.PI * 48;
    ring.style.strokeDasharray = C;
    const draw = () => { ring.style.strokeDashoffset = C * (1 - Math.min(1, ml / GOAL)); $('#bMl').textContent = num(ml); };
    $('#bWp').addEventListener('click', () => { ml = Math.min(GOAL + 500, ml + 250); draw(); });
    $('#bWm').addEventListener('click', () => { ml = Math.max(0, ml - 250); draw(); });
    draw();
  })();

  /* --- Fit Koç --- */
  (() => {
    const chat = $('#bChat'), chips = $('#bChips');
    const QA = [
      [t('Bugünkü antrenmanım?', 'What is my workout today?'), t('Bugün itiş günü: 5 hareket, yaklaşık 45 dk. Isınma setiyle başla; son kaydında bench pressi 50 kg yapmıştın, bugün 52,5 deneyebilirsin.', 'Today is a push day: 5 exercises, about 45 min. Start with a warm-up set; you did 50 kg on bench last time, try 52.5 today.')],
      [t('Akşam ne yesem?', 'What should I eat tonight?'), t('Bugün 1.426 kcal aldın, hedefine 824 kcal var ve proteinde 8 g açığın var. Izgara tavuk, bulgur ve yoğurtlu bir salata iyi gider.', 'You have had 1,426 kcal today with 824 left, and you are 8 g short on protein. Grilled chicken, bulgur and a yoghurt salad would fit well.')],
      [t('Bu hafta nasıldım?', 'How was my week?'), t('3 antrenmanın 3’ünü tamamladın ve ortalama 8.100 adım attın. Su hedefini 2 gün kaçırdın; akşam bir bardak eklemek yeter.', 'You finished 3 of 3 workouts and averaged 8,100 steps. You missed your water goal on 2 days; an evening glass would fix it.')],
    ];
    let busy = false;
    const bubble = (cls, text) => { const d = document.createElement('div'); d.className = 'msg ' + cls; d.textContent = text; chat.appendChild(d); chat.scrollTop = chat.scrollHeight; return d; };
    chips.addEventListener('click', e => {
      const b = e.target.closest('button[data-q]'); if (!b || busy) return; busy = true;
      const [q, a] = QA[+b.dataset.q]; chat.innerHTML = ''; bubble('me', q);
      const r = bubble('ai', ''); r.classList.add('typing-dots'); r.textContent = '…';
      setTimeout(() => {
        r.classList.remove('typing-dots'); r.textContent = '';
        if (reduce) { r.textContent = a; busy = false; return; }
        let i = 0; const step = () => { r.textContent = a.slice(0, i += 2); chat.scrollTop = chat.scrollHeight; if (i < a.length) setTimeout(step, 16); else busy = false; }; step();
      }, reduce ? 0 : 450);
    });
    chips.firstElementChild.click();
  })();

  /* --- Rota --- */
  (() => {
    const km = $('#bKm'), svg = $('#bRoute');
    const draw = () => {
      const d = +km.value, lobes = 2 + Math.floor(d / 7), R = 34 + Math.min(48, d * 2.2); svg.innerHTML = '';
      for (let i = 1; i < 5; i++) { el('line', { x1: 0, x2: 300, y1: i * 40, y2: i * 40, class: 'rg' }, svg); el('line', { y1: 0, y2: 200, x1: i * 60, x2: i * 60, class: 'rg' }, svg); }
      const pts = []; for (let i = 0; i <= 90; i++) { const a = Math.PI * 2 * i / 90, r = R * (1 + .22 * Math.sin(lobes * a + 1) + .1 * Math.cos(2 * a)); pts.push([150 + r * Math.cos(a) * 1.4, 100 + r * Math.sin(a) * .95]); }
      const p = el('path', { d: 'M' + pts.map(q => q[0].toFixed(1) + ' ' + q[1].toFixed(1)).join('L') + 'Z', class: 'rl' }, svg);
      if (!reduce) { p.setAttribute('pathLength', '1'); p.style.animation = 'rdraw 1s ease-out'; }
      el('circle', { cx: pts[0][0], cy: pts[0][1], r: 6, fill: 'var(--g)' }, svg);
      const mins = Math.round(d / 10 * 60);
      $('#bKmOut').textContent = num(d, d % 1 ? 1 : 0) + ' km';
      $('#bRouteStat').textContent = t(`Koşu · yaklaşık ${mins} dk · ${num(Math.round(d * 62))} kcal`, `Run · about ${mins} min · ${num(Math.round(d * 62))} kcal`);
    };
    km.addEventListener('input', draw); draw();
  })();
  /* --- Hareket kütüphanesi --- */
  (() => {
    const L = [
      ['chest', 'Bench press', 'Bench press', ['Halter', 'Barbell'], ['Orta', 'Intermediate'], ['Kürek kemiklerini geride tut, bar göğsün alt kısmına insin.', 'Keep the shoulder blades back; lower the bar to the lower chest.']],
      ['chest', 'Şınav', 'Push-up', ['Vücut ağırlığı', 'Bodyweight'], ['Başlangıç', 'Beginner'], ['Gövdeyi düz bir çizgide tut, dirsekler 45° açıyla.', 'Keep the body in one line, elbows at about 45°.']],
      ['chest', 'Kablo crossover', 'Cable crossover', ['Kablo', 'Cable'], ['Orta', 'Intermediate'], ['Kolları hafif bükük tut, göğüsü sıkarak birleştir.', 'Keep a slight elbow bend and squeeze the chest together.']],
      ['back', 'Barfiks', 'Pull-up', ['Vücut ağırlığı', 'Bodyweight'], ['İleri', 'Advanced'], ['Dirsekleri cebe doğru çek, omuzları kulaktan uzak tut.', 'Drive the elbows toward your pockets, shoulders away from the ears.']],
      ['back', 'Lat pulldown', 'Lat pulldown', ['Makine', 'Machine'], ['Başlangıç', 'Beginner'], ['Barı göğsünün üstüne çek, gövdeyi sallama.', 'Pull the bar to the upper chest without swinging.']],
      ['back', 'Barbell row', 'Barbell row', ['Halter', 'Barbell'], ['Orta', 'Intermediate'], ['Belini nötr tut, barı göbeğe doğru çek.', 'Keep the spine neutral and row toward the navel.']],
      ['legs', 'Squat', 'Squat', ['Halter', 'Barbell'], ['Orta', 'Intermediate'], ['Dizler ayak ucuyla aynı hizada, topuklar yerde.', 'Knees track over the toes, heels stay down.']],
      ['legs', 'Romanian deadlift', 'Romanian deadlift', ['Halter', 'Barbell'], ['Orta', 'Intermediate'], ['Kalçayı geri it, bar bacağa yakın insin.', 'Push the hips back; keep the bar close to the legs.']],
      ['legs', 'Leg press', 'Leg press', ['Makine', 'Machine'], ['Başlangıç', 'Beginner'], ['Dizleri kilitleme, belini yastıktan kaldırma.', 'Do not lock the knees or lift the lower back off the pad.']],
      ['shoulders', 'Askıda omuz presi', 'Overhead press', ['Dumbbell', 'Dumbbell'], ['Orta', 'Intermediate'], ['Karnı sıkı tut, kolları kulak hizasında uzat.', 'Brace the core and press to full extension by the ears.']],
      ['shoulders', 'Yan omuz kaldırma', 'Lateral raise', ['Dumbbell', 'Dumbbell'], ['Başlangıç', 'Beginner'], ['Dirsekler hafif bükük, omuz hizasında dur.', 'Slight elbow bend; stop at shoulder height.']],
      ['arms', 'Hammer curl', 'Hammer curl', ['Dumbbell', 'Dumbbell'], ['Başlangıç', 'Beginner'], ['Dirsekleri gövdeye sabitle, yavaş indir.', 'Pin the elbows to your sides and lower slowly.']],
      ['arms', 'Triceps pushdown', 'Triceps pushdown', ['Kablo', 'Cable'], ['Başlangıç', 'Beginner'], ['Dirsekler sabit, alt noktada sık.', 'Elbows fixed; squeeze at the bottom.']],
      ['core', 'Plank', 'Plank', ['Vücut ağırlığı', 'Bodyweight'], ['Başlangıç', 'Beginner'], ['Kalça düşmesin, nefesi tutma.', 'Do not let the hips sag; keep breathing.']],
      ['core', 'Asılı bacak kaldırma', 'Hanging leg raise', ['Vücut ağırlığı', 'Bodyweight'], ['İleri', 'Advanced'], ['Sallanmadan, karından kontrol ederek kaldır.', 'Lift with the abs, no swinging.']],
    ];
    const G = { all: ['Tümü', 'All'], chest: ['Göğüs', 'Chest'], back: ['Sırt', 'Back'], legs: ['Bacak', 'Legs'], shoulders: ['Omuz', 'Shoulders'], arms: ['Kol', 'Arms'], core: ['Karın', 'Core'] };
    const q = $('#libQ'), chips = $('#libChips'), list = $('#libList');
    let grp = 'all', open = null;
    Object.keys(G).forEach(k => { const b = document.createElement('button'); b.type = 'button'; b.dataset.g = k; b.textContent = G[k][EN ? 1 : 0]; b.setAttribute('aria-pressed', String(k === 'all')); chips.appendChild(b); });
    const norm = s => s.toLocaleLowerCase(EN ? 'en' : 'tr').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/ı/g, 'i');
    const draw = () => {
      const f = norm(q.value.trim()); list.innerHTML = '';
      const rows = L.filter(x => (grp === 'all' || x[0] === grp) && (!f || norm(x[EN ? 2 : 1]).includes(f))).slice(0, 5);
      if (!rows.length) { const li = document.createElement('li'); li.className = 'lib-none'; li.textContent = t('Eşleşen hareket yok.', 'No matching exercise.'); list.appendChild(li); return; }
      rows.forEach(x => {
        const li = document.createElement('li'), b = document.createElement('button'); b.type = 'button';
        const name = x[EN ? 2 : 1]; b.setAttribute('aria-expanded', String(open === name));
        b.innerHTML = '<b></b><small></small>'; b.firstChild.textContent = name; b.lastChild.textContent = `${G[x[0]][EN ? 1 : 0]} · ${x[3][EN ? 1 : 0]} · ${x[4][EN ? 1 : 0]}`;
        b.addEventListener('click', () => { open = open === name ? null : name; draw(); });
        li.appendChild(b);
        if (open === name) { const p = document.createElement('p'); p.textContent = x[5][EN ? 1 : 0]; li.appendChild(p); }
        list.appendChild(li);
      });
    };
    chips.addEventListener('click', e => { const b = e.target.closest('button[data-g]'); if (!b) return; grp = b.dataset.g; open = null; $$('button', chips).forEach(x => x.setAttribute('aria-pressed', String(x === b))); draw(); });
    q.addEventListener('input', () => { open = null; draw(); });
    draw();
  })();

  /* --- Arkadaşlar --- */
  (() => {
    const body = $('#socBody');
    const me = { n: t('Sen', 'You'), xp: 320, me: true };
    const board = [{ n: 'Elif', xp: 540 }, { n: 'Mert', xp: 470 }, me, { n: 'Zeynep', xp: 290 }, { n: 'Can', xp: 210 }];
    const chal = { me: 14, fr: 17, goal: 30 };
    let tab = 'feed';
    const FEED = [
      ['E', 'Elif', t('5,2 km koştu', 'ran 5.2 km'), t('2 sa önce', '2 h ago')],
      ['M', 'Mert', t('Bacak antrenmanını tamamladı', 'finished a leg workout'), t('4 sa önce', '4 h ago')],
      ['Z', 'Zeynep', t('12 günlük seriye ulaştı', 'reached a 12-day streak'), t('dün', 'yesterday')],
    ];
    const draw = () => {
      body.innerHTML = '';
      if (tab === 'feed') {
        const ul = document.createElement('ul'); ul.className = 'feed';
        FEED.forEach(([a, n, w, ago]) => { const li = document.createElement('li'); li.innerHTML = '<i></i><div><b></b> <span></span><small></small></div>'; li.firstChild.textContent = a; li.querySelector('b').textContent = n; li.querySelector('span').textContent = w; li.querySelector('small').textContent = ago; ul.appendChild(li); });
        body.appendChild(ul);
      } else if (tab === 'board') {
        const rows = [...board].sort((x, y) => y.xp - x.xp), top = rows[0].xp;
        const ul = document.createElement('ul'); ul.className = 'lb';
        rows.forEach((r, i) => { const li = document.createElement('li'); if (r.me) li.className = 'me'; li.innerHTML = '<em></em><span></span><div class="mbar"><i></i></div><b></b>'; li.querySelector('em').textContent = i + 1; li.querySelector('span').textContent = r.n; li.querySelector('i').style.setProperty('--w', Math.round(r.xp / top * 100) + '%'); li.querySelector('b').textContent = r.xp + ' XP'; ul.appendChild(li); });
        const btn = document.createElement('button'); btn.type = 'button'; btn.className = 'sbtn'; btn.textContent = t('Antrenmanı bitir (+40 XP)', 'Finish a workout (+40 XP)');
        btn.addEventListener('click', () => { me.xp += 40; draw(); });
        body.append(ul, btn);
      } else {
        const wrap = document.createElement('div'); wrap.className = 'chal';
        const h = document.createElement('p'); h.className = 'bnote'; h.textContent = t('Haftalık 30 km · Elif ile', 'Weekly 30 km · vs Elif'); wrap.appendChild(h);
        [[t('Sen', 'You'), chal.me, true], ['Elif', chal.fr, false]].forEach(([n, v, mine]) => { const r = document.createElement('div'); r.className = 'cr' + (mine ? ' me' : ''); r.innerHTML = '<span></span><div class="mbar"><i></i></div><b></b>'; r.querySelector('span').textContent = n; r.querySelector('i').style.setProperty('--w', Math.min(100, Math.round(v / chal.goal * 100)) + '%'); r.querySelector('b').textContent = num(v, v % 1 ? 1 : 0) + ' km'; wrap.appendChild(r); });
        const btn = document.createElement('button'); btn.type = 'button'; btn.className = 'sbtn'; btn.textContent = t('+5 km koştum', 'I ran +5 km');
        btn.addEventListener('click', () => { chal.me = Math.min(chal.goal, chal.me + 5); draw(); });
        const res = document.createElement('p'); res.className = 'bnote'; res.textContent = chal.me >= chal.goal ? t('Meydan okumayı kazandın!', 'You won the challenge!') : chal.me > chal.fr ? t('Öndesin.', 'You are ahead.') : t('Elif önde, yetişebilirsin.', 'Elif leads; you can catch up.');
        body.append(wrap, btn, res);
      }
    };
    $('#socTabs').addEventListener('click', e => { const b = e.target.closest('button[data-s]'); if (!b) return; tab = b.dataset.s; $$('#socTabs button').forEach(x => x.setAttribute('aria-selected', String(x === b))); draw(); });
    draw();
  })();
  /* --- "Daha fazlası": ayrıntı sayfasını açılır pencerede göster --- */
  (() => {
    const dlg = $('#moreDlg'); if (!dlg || !dlg.showModal) return;
    const frame = $('iframe', dlg);
    const close = () => { dlg.close(); };
    $('.more-x', dlg).addEventListener('click', close);
    dlg.addEventListener('click', e => { if (e.target === dlg) close(); });
    dlg.addEventListener('close', () => { frame.src = 'about:blank'; document.documentElement.classList.remove('lock'); });
    document.addEventListener('click', e => {
      const a = e.target.closest('.mtile'); if (!a || e.metaKey || e.ctrlKey || e.shiftKey) return;
      e.preventDefault();
      const [path, hash] = a.getAttribute('href').split('#');
      frame.src = path + '?embed=1#' + (hash || '');
      const ttl = $('b', a).textContent; frame.title = ttl;
      dlg.setAttribute('aria-label', ttl);
      document.documentElement.classList.add('lock'); dlg.showModal();
    });
  })();
  /* --- Mobilde yana kaydırma noktaları --- */
  (() => {
    const cards = $$('.bcard', root); if (cards.length < 2) return;
    const dots = document.createElement('div'); dots.className = 'bdots'; dots.setAttribute('aria-hidden', 'true');
    cards.forEach((c, i) => { const b = document.createElement('button'); b.type = 'button'; b.tabIndex = -1; b.addEventListener('click', () => c.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', inline: 'center', block: 'nearest' })); dots.appendChild(b); });
    root.after(dots);
    const mark = i => $$('button', dots).forEach((b, k) => b.setAttribute('aria-current', String(k === i)));
    let raf = 0;
    root.addEventListener('scroll', () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        const mid = root.scrollLeft + root.clientWidth / 2;
        let best = 0, d = Infinity; cards.forEach((c, i) => { const x = Math.abs(c.offsetLeft + c.offsetWidth / 2 - mid); if (x < d) { d = x; best = i; } });
        mark(best);
      });
    }, { passive: true });
    mark(0);
  })();
  /* --- Açılır karşılaştırma ("Dört uygulama yerine bir") --- */
  (() => {
    const cmp = $('#cmp'), btn = $('#cmpToggle'); if (!cmp || !btn) return;
    const label = open => t(open ? 'Daha az göster' : 'Tüm farkları göster', open ? 'Show less' : 'Show all differences');
    btn.textContent = label(false);
    btn.addEventListener('click', () => {
      const open = cmp.classList.toggle('collapsed') === false;
      btn.setAttribute('aria-expanded', String(open)); btn.textContent = label(open);
    });
  })();
})();
