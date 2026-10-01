(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  document.getElementById('yr').textContent = new Date().getFullYear();

  // Mağaza bağlantıları yayına çıkınca: href'i yaz, aria-disabled'ı kaldır.
  const STORES = { 'google-play': '', 'app-store': '' };
  document.querySelectorAll('.store[data-store]').forEach(el => {
    const url = STORES[el.dataset.store];
    if (!url) { el.addEventListener('click', e => e.preventDefault()); return; }
    el.href = url; el.removeAttribute('aria-disabled'); el.removeAttribute('role'); el.style.cursor = 'pointer';
    el.querySelector('small').textContent = 'İNDİR';
  });

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
  const text = '2 dilim tam buğday ekmeği, 2 yumurtalı omlet';
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
})();
