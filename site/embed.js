/* /detay?embed=1#bölüm: yalnız o bölümü gösterir (ana sayfadaki açılır pencere için) */
(() => {
  if (!/[?&]embed=1/.test(location.search)) return;
  document.documentElement.classList.add('embed');
  addEventListener('DOMContentLoaded', () => {
    const id = location.hash.slice(1), target = id && document.getElementById(id);
    document.querySelectorAll('.reveal').forEach(e => e.classList.add('in'));
    if (!target) return;
    const keep = target.closest('main > section') || target;
    document.querySelectorAll('main > *').forEach(n => { if (n !== keep) n.hidden = true; });
    document.querySelectorAll('.nav, .footer, .cta').forEach(n => { n.hidden = true; });
    document.body.classList.add('embed-body');
  });
})();
