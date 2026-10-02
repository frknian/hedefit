(() => {
  const EN = document.documentElement.lang === 'en';
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  document.getElementById('yr').textContent = new Date().getFullYear();

  // Mağaza bağlantıları yayına çıkınca: href'i yaz, aria-disabled'ı kaldır.
  const STORES = { 'google-play': '', 'app-store': '' };
  document.querySelectorAll('.store[data-store]').forEach(el => {
    const url = STORES[el.dataset.store];
    if (!url) { el.addEventListener('click', e => e.preventDefault()); return; }
    el.href = url; el.removeAttribute('aria-disabled'); el.removeAttribute('role'); el.style.cursor = 'pointer';
    el.querySelector('small').textContent = EN ? 'GET' : 'İNDİR';
  });

  // Logoya basınca en üste çık (sabit başlık #top hedefi bazı tarayıcılarda kaydırmaz)
  document.querySelectorAll('a.brand[href="#top"]').forEach(a => a.addEventListener('click', e => {
    e.preventDefault();
    const from = scrollY, t0 = performance.now(), d = Math.min(900, 250 + from / 4);
    const h = document.documentElement; h.style.scrollBehavior = 'auto';
    if (reduce || from < 2 || document.hidden) { scrollTo(0, 0); h.style.scrollBehavior = ''; }
    else {
      const step = t => { const p = Math.min(1, (t - t0) / d); scrollTo(0, Math.round(from * (1 - (1 - Math.pow(1 - p, 3))))); if (p < 1) requestAnimationFrame(step); else h.style.scrollBehavior = ''; };
      requestAnimationFrame(step);
    }
    history.replaceState(null, '', location.pathname + location.search);
  }));

  // Scroll reveal
  const io = new IntersectionObserver(es => es.forEach(e => {
    if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
  }), { threshold: .14, rootMargin: '0px 0px -6% 0px' });
  document.querySelectorAll('.reveal').forEach((el, i) => { el.style.transitionDelay = `${(i % 4) * 70}ms`; io.observe(el); });

  // Sayaçlar
  const count = (el, to, suffix = '') => {
    if (reduce) { el.textContent = to + suffix; return; }
    const t0 = performance.now(), d = 1400;
    const tick = t => { const p = Math.min(1, (t - t0) / d); el.textContent = Math.round(to * (1 - Math.pow(1 - p, 3))) + suffix; if (p < 1) requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
  };
  const cio = new IntersectionObserver(es => es.forEach(e => {
    if (!e.isIntersecting) return;
    const el = e.target; count(el, +el.dataset.count, el.dataset.suffix || ''); cio.unobserve(el);
  }), { threshold: .6 });
  document.querySelectorAll('[data-count]').forEach(el => cio.observe(el));

  // AI demo: yazma + sonuç
  const text = EN ? '2 slices of whole wheat bread, omelette' : '2 dilim tam buğday ekmeği, omlet';
  const typed = document.getElementById('typed');
  const result = document.getElementById('aiResult');
  const kcal = result.querySelector('[data-to]');
  let started = false;
  const showResult = () => {
    result.classList.add('show');
    const to = +kcal.dataset.to;
    if (reduce) { kcal.textContent = to; return; }
    const t0 = performance.now();
    const tick = t => { const p = Math.min(1, (t - t0) / 1100); kcal.textContent = Math.round(to * p); if (p < 1) requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
  };
  const run = () => {
    typed.textContent = '';
    result.classList.remove('show');
    kcal.textContent = '0';
    let i = 0;
    const step = () => {
      typed.textContent = text.slice(0, ++i);
      if (i < text.length) setTimeout(step, 38 + Math.random() * 40);
      else setTimeout(() => { showResult(); setTimeout(run, 7000); }, 500);
    };
    step();
  };
  const demo = document.getElementById('aiDemo');
  new IntersectionObserver((es, o) => es.forEach(e => {
    if (e.isIntersecting && !started) { started = true; reduce ? (typed.textContent = text, showResult()) : run(); o.disconnect(); }
  }), { threshold: .4 }).observe(demo);

  // Hero telefonu: hafif imleç eğimi
  const tilt = document.querySelector('[data-tilt]');
  if (tilt && !reduce && matchMedia('(pointer:fine)').matches) {
    const stage = tilt.parentElement;
    stage.addEventListener('mousemove', e => {
      const r = stage.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
      tilt.style.transform = `rotate(${-3 + x * 4}deg) rotateY(${x * 10}deg) rotateX(${-y * 8}deg)`;
    });
    stage.addEventListener('mouseleave', () => { tilt.style.transform = ''; });
  }

  // Yayın bildirimi formu → /api/subscribe (Worker + KV)
  const form = document.getElementById('notify');
  if (form) {
    const msg = form.querySelector('.nf-msg'), btn = form.querySelector('button');
    const say = (t, ok) => { msg.textContent = t; msg.dataset.ok = ok ? '1' : '0'; };
    form.addEventListener('submit', async e => {
      e.preventDefault();
      const email = form.email.value.trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) { say(EN ? 'Enter a valid email address.' : 'Geçerli bir e-posta adresi yaz.', false); form.email.focus(); return; }
      if (!form.consent.checked) { say(EN ? 'Tick the consent box to continue.' : 'Devam etmek için onay kutusunu işaretle.', false); return; }
      btn.disabled = true; say(EN ? 'Sending…' : 'Gönderiliyor…', true);
      try {
        const r = await fetch('/api/subscribe', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email, consent: true, website: form.website.value }) });
        if (r.ok) { say(EN ? 'Thank you! We will send you a single email when Hedefit is published.' : 'Teşekkürler! Hedefit yayınlandığında sana tek bir e-posta göndereceğiz.', true); form.reset(); }
        else say(r.status === 429 ? (EN ? 'Too many attempts, please try again shortly.' : 'Çok fazla deneme yapıldı, biraz sonra tekrar dene.') : (EN ? 'Could not save, please try again.' : 'Kaydedilemedi, lütfen tekrar dene.'), false);
      } catch { say(EN ? 'Connection error, please try again.' : 'Bağlantı hatası, lütfen tekrar dene.', false); }
      btn.disabled = false;
    });
  }
})();
