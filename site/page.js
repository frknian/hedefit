/* Alt sayfalar: yıl + (gizlilik sayfasında) TR/EN geçişi. Satır içi betik yok → sıkı CSP. */
(() => {
  const yr = document.getElementById('yr');
  if (yr) yr.textContent = new Date().getFullYear();
  const btns = document.querySelectorAll('.langs button');
  if (!btns.length) return;
  const set = l => {
    document.documentElement.lang = l;
    document.querySelectorAll('[data-tr]').forEach(e => { e.hidden = l !== 'tr'; });
    document.querySelectorAll('[data-en]').forEach(e => { e.hidden = l !== 'en'; });
    btns.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.lang === l)));
  };
  btns.forEach(b => b.addEventListener('click', () => set(b.dataset.lang)));
  if (/^#.*-en$/.test(location.hash) || /[?&]lang=en/.test(location.search)) set('en');
})();
