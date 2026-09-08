import assert from 'node:assert/strict';
import { test } from 'node:test';
import { JSDOM } from 'jsdom';
import { mountPresentation } from '../src/presentation.mjs';

function fixture({ reduced = false, desktop = true, animated = false } = {}) {
  const dom = new JSDOM('<html><body><main><section data-presentation-section><button>Action</button><input><select><option>A</option></select><pre tabindex="0">Code</pre><div contenteditable="true">Edit</div></section><section data-presentation-section></section><section data-presentation-section></section></main></body></html>', { pretendToBeVisual: true });
  const win = dom.window, doc = win.document;
  let time = 0, y = 0, nextId = 0, height = 800, scale = 1;
  const calls = [], scheduled = new Map(), frames = new Map(), media = new Map();
  let positions = [0, 800, 1600], total = 2400;
  const contents = [800, 1600, 1000];
  Object.defineProperty(win, 'innerHeight', { get: () => height });
  Object.defineProperty(win, 'scrollY', { get: () => y });
  Object.defineProperty(win, 'visualViewport', { value: { get scale() { return scale; } } });
  Object.defineProperty(doc, 'scrollingElement', { value: { get scrollHeight() { return total; } } });
  win.performance.now = () => time;
  win.matchMedia = query => {
    if (!media.has(query)) { const m = new win.EventTarget(); m.matches = query.includes('reduced') ? reduced : desktop; media.set(query, m); }
    return media.get(query);
  };
  win.setTimeout = (fn, delay) => { const id = ++nextId; scheduled.set(id, { fn, at: time + delay }); return id; };
  win.clearTimeout = id => scheduled.delete(id);
  win.requestAnimationFrame = fn => { const id = ++nextId; frames.set(id, fn); return id; };
  win.cancelAnimationFrame = id => frames.delete(id);
  win.scrollTo = options => { calls.push(options); if (!animated || options.behavior === 'instant') y = options.top; };
  const panels = [...doc.querySelectorAll('section')];
  panels.forEach((panel, i) => {
    panel.getBoundingClientRect = () => ({ top: positions[i] - y });
    Object.defineProperty(panel, 'clientHeight', { get: () => height });
    Object.defineProperty(panel, 'scrollHeight', { get: () => contents[i] });
    panel.scrollTo = options => { calls.push({ ...options, panel: i }); if (!animated || options.behavior === 'instant') panel.scrollTop = options.top; };
  });
  const dispose = mountPresentation(doc.querySelector('main'));
  const dispatch = (type, options = {}, target = doc.body) => {
    const event = type === 'wheel' ? new win.WheelEvent(type, { bubbles: true, cancelable: true, deltaY: 120, ...options }) : new win.KeyboardEvent(type, { bubbles: true, cancelable: true, ...options });
    target.dispatchEvent(event); return event;
  };
  return {
    win, doc, calls, dispose, media, panels,
    wheel: (options, target) => dispatch('wheel', options, target),
    key: (key, options, target) => dispatch('keydown', { key, ...options }, target),
    tick(ms) { time += ms; for (const [id, timer] of [...scheduled]) if (timer.at <= time) { scheduled.delete(id); timer.fn(); } for (const [id, fn] of [...frames]) { frames.delete(id); fn(time); } },
    complete() { const last = calls.at(-1); if (last.panel === undefined) y = last.top; else panels[last.panel].scrollTop = last.top; doc.dispatchEvent(new win.Event('scrollend')); },
    set y(value) { y = value; }, get y() { return y; },
    set scale(value) { scale = value; },
    resize(newPositions, newHeight = height, newTotal = total) { positions = newPositions; height = newHeight; total = newTotal; win.dispatchEvent(new win.Event('resize')); },
    close() { dispose(); dom.window.close(); }
  };
}

test('one mouse impulse advances once; animation and continuing inertia are consumed', () => {
  const f = fixture({ animated: true });
  f.wheel(); assert.equal(f.calls[0].top, 800);
  for (let i = 0; i < 12; i++) { f.tick(80); f.wheel(); }
  assert.equal(f.calls.length, 1);
  f.complete(); f.tick(80); f.wheel(); assert.equal(f.calls.length, 1);
  f.tick(221); f.wheel(); assert.equal(f.calls.at(-1).top, 120); // Tall section: read, don't skip.
  f.close();
});

test('small trackpad deltas accumulate intent and cannot skip several chapters', () => {
  const f = fixture();
  for (let i = 0; i < 3; i++) { f.wheel({ deltaY: 7 }); f.tick(16); }
  assert.equal(f.calls.length, 0);
  f.wheel({ deltaY: 7 }); assert.equal(f.calls[0].top, 800);
  for (let i = 0; i < 40; i++) { f.tick(16); f.wheel({ deltaY: 9 }); }
  assert.equal(f.calls.length, 1); f.close();
});

test('inertia stays with its chapter when a nested scroller moves under the pointer', () => {
  const f = fixture();
  const pre = f.doc.querySelector('pre'); pre.style.overflowX = 'auto';
  Object.defineProperty(pre, 'scrollWidth', { value: 900 }); Object.defineProperty(pre, 'clientWidth', { value: 300 });
  f.wheel(); f.tick(100);
  assert.equal(f.wheel({}, pre).defaultPrevented, true); assert.equal(f.calls.length, 1);
  f.tick(221); assert.equal(f.wheel({}, pre).defaultPrevented, false);
  f.y = 800; f.wheel(); f.tick(16);
  assert.equal(f.wheel({ deltaY: 5000 }, pre).defaultPrevented, true); assert.equal(f.panels[1].scrollTop, 800);
  f.close();
});

test('tall chapters keep all content reachable and require a fresh gesture at their edge', () => {
  const f = fixture(); f.y = 800;
  f.wheel({ deltaY: 5000 }); assert.equal(f.panels[1].scrollTop, 800); assert.equal(f.y, 800);
  f.wheel({ deltaY: 5000 }); assert.equal(f.y, 800);
  f.tick(221); f.wheel(); assert.equal(f.y, 1600);
  f.tick(221); f.wheel({ deltaY: -120 }); assert.equal(f.y, 800);
  assert.equal(f.panels[1].scrollTop, 800); // Previous reading position is retained.
  f.tick(221); f.wheel({ deltaY: -120 }); assert.equal(f.panels[1].scrollTop, 680);
  f.close();
});

test('direction changes reset pending intent; wheel line and page units are normalized', () => {
  const f = fixture(); f.y = 800;
  f.wheel({ deltaY: -10 }); f.wheel({ deltaY: 10 }); assert.equal(f.panels[1].scrollTop, 10);
  f.tick(221); f.wheel({ deltaMode: 1, deltaY: 3 }); assert.equal(f.panels[1].scrollTop, 58);
  f.tick(221); f.wheel({ deltaMode: 2, deltaY: 1 }); assert.equal(f.panels[1].scrollTop, 800);
  f.close();
});

test('keyboard pages overlap inside long chapters; repeat and animation do not queue moves', () => {
  const f = fixture();
  f.key('PageDown'); assert.equal(f.y, 800);
  f.key('PageDown', { repeat: true }); assert.equal(f.calls.length, 1);
  f.tick(250); f.key('PageDown'); assert.equal(f.panels[1].scrollTop, 680);
  f.tick(250); f.key('ArrowDown'); assert.equal(f.panels[1].scrollTop, 760);
  f.tick(250); f.key(' ', { shiftKey: true }); assert.equal(f.panels[1].scrollTop, 80);
  f.tick(250); f.key('ArrowUp'); assert.equal(f.panels[1].scrollTop, 0);
  f.tick(250); f.key('PageUp'); assert.equal(f.y, 0);
  f.close();
});

test('forms, editable content, nested scrolling and horizontal/zoom gestures retain native input', () => {
  const f = fixture();
  for (const selector of ['input', 'select', '[contenteditable]']) {
    const target = f.doc.querySelector(selector);
    assert.equal(f.wheel({}, target).defaultPrevented, false);
    assert.equal(f.key('ArrowDown', {}, target).defaultPrevented, false);
  }
  for (const selector of ['button', 'pre']) assert.equal(f.key(' ', {}, f.doc.querySelector(selector)).defaultPrevented, false);
  const pre = f.doc.querySelector('pre'); pre.style.overflowX = 'auto';
  Object.defineProperty(pre, 'scrollWidth', { value: 900 }); Object.defineProperty(pre, 'clientWidth', { value: 300 });
  assert.equal(f.wheel({}, pre).defaultPrevented, false);
  for (const options of [{ ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { deltaX: 200 }, { cancelable: false }]) assert.equal(f.wheel(options).defaultPrevented, false);
  f.scale = 2; assert.equal(f.wheel().defaultPrevented, false);
  assert.equal(f.calls.length, 0); f.close();
});

test('mobile uses native input and reduced motion uses instant chapter moves', () => {
  const mobile = fixture({ desktop: false });
  assert.equal(mobile.wheel().defaultPrevented, false); assert.equal(mobile.key('PageDown').defaultPrevented, false); mobile.close();
  const reduced = fixture({ reduced: true }); reduced.wheel(); assert.equal(reduced.calls[0].behavior, 'instant'); reduced.close();
});

test('resize remeasures without repositioning; footer and document endpoints are reachable', () => {
  const f = fixture();
  f.resize([0, 600, 1200], 600, 1800); assert.equal(f.calls.length, 0);
  f.wheel(); assert.equal(f.y, 0); assert.equal(f.panels[0].scrollTop, 120);
  f.resize([0, 800, 1600], 800, 2400); f.panels[0].scrollTop = 0;
  f.wheel(); assert.equal(f.y, 800);
  f.tick(221); f.y = 1600; f.wheel({ deltaY: 5000 }); assert.equal(f.panels[2].scrollTop, 200);
  f.tick(221); f.wheel(); assert.equal(f.y, 1600);
  f.close();
});

test('Escape, focus and native fragment navigation cancel an animation without moving focus', () => {
  const f = fixture({ animated: true }); f.wheel();
  f.key('Escape'); assert.equal(f.calls.at(-1).behavior, 'instant'); assert.equal(f.y, 0);
  f.tick(250); f.wheel(); f.doc.querySelector('input').focus();
  assert.equal(f.doc.activeElement, f.doc.querySelector('input')); assert.equal(f.calls.at(-1).top, 0);
  f.tick(250); f.wheel(); const count = f.calls.length;
  f.win.dispatchEvent(new f.win.HashChangeEvent('hashchange')); assert.equal(f.calls.length, count);
  f.close();
});

test('disposal and bfcache lifecycle leave no intercepting listeners', () => {
  const f = fixture();
  f.win.dispatchEvent(new f.win.PageTransitionEvent('pagehide', { persisted: true }));
  assert.equal(f.wheel().defaultPrevented, true);
  f.dispose(); assert.equal(f.wheel().defaultPrevented, false); assert.equal(f.key('PageDown').defaultPrevented, false);
  f.close();
});
