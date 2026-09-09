// Restore before paint, using a landing-only preference. No wheel or keyboard
// controller: the document scroller owns every gesture in both views.
const STORAGE_KEY = 'scalajs-jfx.landing.view';
const valid = value => value === 'reading' || value === 'presentation';

export const presentationBootstrap = `(() => {
  let view = new URL(location.href).searchParams.get('view');
  if (view !== 'reading' && view !== 'presentation') {
    try { view = localStorage.getItem('${STORAGE_KEY}'); } catch {}
  }
  document.documentElement.toggleAttribute('data-presentation', view === 'presentation');
})();`;

export function mountPresentation(doc, onStorageUnavailable = () => {}) {
  const win = doc.defaultView;
  const choice = doc.querySelector('#view-choice');
  const apply = value => {
    choice.value = value === 'presentation' ? value : 'reading';
    doc.documentElement.toggleAttribute('data-presentation', choice.value === 'presentation');
  };
  const restore = () => {
    let value = new URL(win.location.href).searchParams.get('view');
    if (!valid(value)) {
      try { value = win.localStorage.getItem(STORAGE_KEY); }
      catch { onStorageUnavailable(); }
    }
    apply(value);
  };
  const change = () => {
    apply(choice.value);
    const url = new URL(win.location.href);
    url.searchParams.set('view', choice.value);
    win.history.replaceState(win.history.state, '', url);
    for (const anchor of doc.querySelectorAll('[data-page-anchor]')) {
      const target = new URL(url);
      target.hash = anchor.dataset.pageAnchor;
      anchor.href = target.href;
    }
    try { win.localStorage.setItem(STORAGE_KEY, choice.value); }
    catch { onStorageUnavailable(); }
  };
  restore();
  choice.disabled = false;
  choice.addEventListener('change', change);
  win.addEventListener('popstate', restore);
  const dispose = () => {
    choice.removeEventListener('change', change);
    win.removeEventListener('popstate', restore);
    win.removeEventListener('pagehide', pagehide);
  };
  const pagehide = event => { if (!event.persisted) dispose(); };
  win.addEventListener('pagehide', pagehide);
  return dispose;
}
