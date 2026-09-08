// Progressive enhancement of one landing-page document. The browser owns the
// scroll position, animation, scrollbar, touch gestures and fragment navigation.
const DESKTOP = '(min-width: 64rem) and (hover: hover) and (pointer: fine)';
const QUIET_MS = 220;
const INTENT_PX = 28;
const EPSILON = 2;
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

export function mountPresentation(main) {
  if (!main) return () => {};
  const doc = main.ownerDocument;
  const win = doc.defaultView;
  const panels = [...main.querySelectorAll(':scope > [data-presentation-section]')];
  if (panels.length < 2 || !win.matchMedia) return () => {};
  const desktop = win.matchMedia(DESKTOP);
  const reduced = win.matchMedia('(prefers-reduced-motion: reduce)');
  let gesture = null;
  let lastWheel = -Infinity;
  let intent = 0;
  let quietTimer;
  let frame;
  let destination = null;
  let disposed = false;
  const listeners = [];
  const listen = (target, type, handler, options) => {
    target.addEventListener(type, handler, options);
    listeners.push(() => target.removeEventListener(type, handler, options));
  };

  // Measure at input time: designs, zoom, open details and hydration can change
  // inner geometry. Header and footer belong to the first and last chapters.
  function geometry() {
    const viewport = win.innerHeight;
    const max = Math.max(0, doc.scrollingElement.scrollHeight - viewport);
    const starts = panels.map((panel, i) => i === 0 ? 0 : clamp(panel.getBoundingClientRect().top + win.scrollY, 0, max));
    return { starts, max, viewport };
  }
  function indexAt(starts) {
    let index = 0;
    while (index + 1 < starts.length && starts[index + 1] <= win.scrollY + EPSILON) index++;
    return index;
  }
  function resetGesture() {
    gesture = null;
    intent = 0;
    lastWheel = -Infinity;
    win.clearTimeout(quietTimer);
  }
  function finish() {
    win.cancelAnimationFrame(frame);
    destination = null;
  }
  function cancel(stop = true) {
    if (destination !== null && stop) destination.scroller.scrollTo({ top: scrollPosition(destination.scroller), behavior: 'instant' });
    finish();
    resetGesture();
  }
  const scrollPosition = scroller => scroller === win ? win.scrollY : scroller.scrollTop;
  function move(top, scroller = win) {
    if (Math.abs(top - scrollPosition(scroller)) <= EPSILON) return;
    destination = { top, scroller };
    const began = win.performance.now();
    scroller.scrollTo({ top, behavior: reduced.matches ? 'instant' : 'smooth' });
    // scrollend is preferred; the bounded position check supports older engines
    // and a reduced-motion jump that might not emit a scroll event at all.
    const settle = () => {
      if (disposed || destination === null) return;
      if (Math.abs(scrollPosition(scroller) - top) <= EPSILON || win.performance.now() - began > 1800) finish();
      else frame = win.requestAnimationFrame(settle);
    };
    frame = win.requestAnimationFrame(settle);
  }
  function otherOwner(event, keyboard = false) {
    if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey || event.isComposing) return true;
    if (doc.querySelector('dialog[open], [aria-modal="true"]')) return true;
    if (win.getSelection()?.toString()) return true;
    const target = event.target?.nodeType === 1 ? event.target : event.target?.parentElement;
    if (target?.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"]), [role="slider"], [role="listbox"], [data-presentation-native]')) return true;
    if (keyboard && target?.closest('a, button, summary, [tabindex], [role="button"], [role="tab"]')) return true;
    // A nested code/table/editor scroller keeps its entire gesture, even at its
    // boundary. Horizontal wheel input never becomes a vertical chapter turn.
    for (let node = target; node && node !== doc.body; node = node.parentElement) {
      if (panels.includes(node)) break;
      const style = win.getComputedStyle(node);
      if ((/(auto|scroll)/.test(style.overflowY) && node.scrollHeight > node.clientHeight + EPSILON) ||
          (/(auto|scroll)/.test(style.overflowX) && node.scrollWidth > node.clientWidth + EPSILON)) return true;
    }
    return false;
  }
  const readingEnd = panel => Math.max(0, panel.scrollHeight - panel.clientHeight);
  function canRead(i, direction) {
    return direction > 0 ? panels[i].scrollTop < readingEnd(panels[i]) - EPSILON : panels[i].scrollTop > EPSILON;
  }
  function turnTarget(g, i, direction) {
    if (direction > 0) return i + 1 < panels.length ? g.starts[i + 1] : g.max;
    return g.starts[Math.max(0, i - 1)];
  }
  function wheel(event) {
    if (!desktop.matches || (win.visualViewport?.scale ?? 1) > 1 || event.shiftKey || event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented || !event.cancelable) return;
    if (!event.deltaY || Math.abs(event.deltaX) >= Math.abs(event.deltaY)) return;
    const now = win.performance.now();
    if (now - lastWheel > QUIET_MS && destination === null) resetGesture();
    // A turn's inertia belongs to that turn, even if scrolling brought a code
    // block under the pointer. A new gesture in that block stays native.
    if (!gesture && destination === null && otherOwner(event)) return;
    lastWheel = now;
    win.clearTimeout(quietTimer);
    quietTimer = win.setTimeout(() => {
      // Keep the gesture consumed through animation AND the last inertial event.
      if (destination === null) resetGesture();
    }, QUIET_MS);
    event.preventDefault();
    if (destination !== null || gesture?.kind === 'turn') { gesture = { kind: 'turn' }; return; }
    const g = geometry();
    const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? g.viewport : 1);
    const direction = Math.sign(delta);
    const i = gesture?.kind === 'read' ? gesture.index : indexAt(g.starts);
    if (gesture?.kind === 'read' || canRead(i, direction)) {
      gesture = { kind: 'read', index: i };
      // Preserve pixel deltas inside tall chapters, but contain the burst at its
      // edge. A fresh gesture is required to leave; no content is skipped.
      const panel = panels[i];
      panel.scrollTo({ top: clamp(panel.scrollTop + delta, 0, readingEnd(panel)), behavior: 'instant' });
      return;
    }
    if (Math.sign(intent) !== direction) intent = 0;
    intent += delta;
    if (Math.abs(intent) < INTENT_PX) return;
    gesture = { kind: 'turn' };
    move(turnTarget(g, i, direction));
  }
  function keydown(event) {
    if (['Tab', 'Escape', 'Home', 'End'].includes(event.key)) { cancel(); return; }
    if (!desktop.matches || otherOwner(event, true)) return;
    const directions = { ArrowDown: 1, PageDown: 1, ArrowUp: -1, PageUp: -1, ' ': event.shiftKey ? -1 : 1 };
    const direction = directions[event.key];
    if (!direction || (event.shiftKey && event.key !== ' ')) return;
    event.preventDefault();
    if (destination !== null || event.repeat) return;
    resetGesture();
    const g = geometry();
    const i = indexAt(g.starts);
    if (canRead(i, direction)) {
      // Reading pages overlap to keep context; Arrow keys use smaller steps.
      const step = event.key.startsWith('Arrow') ? 80 : g.viewport * .85;
      const panel = panels[i];
      move(clamp(panel.scrollTop + direction * step, 0, readingEnd(panel)), panel);
    } else move(turnTarget(g, i, direction));
  }
  listen(win, 'wheel', wheel, { passive: false });
  listen(doc, 'keydown', keydown);
  listen(doc, 'scrollend', () => {
    if (destination !== null && Math.abs(scrollPosition(destination.scroller) - destination.top) <= EPSILON) finish();
  }, true);
  listen(doc, 'pointerdown', () => cancel(), { passive: true });
  listen(doc, 'touchstart', () => cancel(), { passive: true });
  listen(doc, 'focusin', () => cancel());
  listen(win, 'hashchange', () => cancel(false));
  listen(win, 'popstate', () => cancel(false));
  listen(win, 'resize', () => cancel());
  listen(desktop, 'change', () => cancel());
  listen(reduced, 'change', () => {
    if (reduced.matches && destination !== null) destination.scroller.scrollTo({ top: destination.top, behavior: 'instant' });
    finish();
  });
  const resize = win.ResizeObserver ? new win.ResizeObserver(() => cancel()) : null;
  for (const panel of panels) resize?.observe(panel);
  const dispose = () => {
    disposed = true;
    cancel();
    resize?.disconnect();
    for (const remove of listeners) remove();
  };
  listen(win, 'pagehide', event => { if (event.persisted) cancel(false); else dispose(); });
  return dispose;
}
