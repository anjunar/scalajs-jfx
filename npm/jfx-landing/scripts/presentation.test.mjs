import assert from 'node:assert/strict';
import { test } from 'node:test';
import { JSDOM } from 'jsdom';
import { mountPresentation, presentationBootstrap } from '../src/presentation.mjs';

function fixture({ url = 'https://jfx.example/de/?design=terra#main', saved, blocked = false } = {}) {
  const dom = new JSDOM('<select id="view-choice" disabled><option value="reading">Reading</option><option value="presentation">Presentation</option></select><a data-page-anchor="main" href="#main">Main</a><main id="main"><details open><summary>Starter</summary>Source</details></main>', { url, runScripts: 'outside-only' });
  const win = dom.window, doc = win.document;
  if (saved) win.localStorage.setItem('scalajs-jfx.landing.view', saved);
  if (blocked) Object.defineProperty(win, 'localStorage', { get() { throw new Error('Storage blocked'); } });
  win.eval(presentationBootstrap);
  const bootstrapped = doc.documentElement.hasAttribute('data-presentation');
  let warnings = 0;
  const dispose = mountPresentation(doc, () => warnings++);
  const choice = doc.querySelector('select');
  return { win, doc, choice, bootstrapped, get warnings() { return warnings; },
    change(value) { choice.value = value; choice.dispatchEvent(new win.Event('change')); },
    close() { dispose(); win.close(); } };
}

test('first visit reads naturally and leaves wheel, keyboard and starter state to the browser', () => {
  const f = fixture();
  assert.equal(f.bootstrapped, false);
  assert.equal(f.choice.value, 'reading');
  assert.equal(f.choice.disabled, false);
  for (const view of ['reading', 'presentation']) {
    f.change(view);
    const wheel = new f.win.WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY: 5000 });
    const key = new f.win.KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'PageDown' });
    f.doc.body.dispatchEvent(wheel); f.doc.body.dispatchEvent(key);
    assert.equal(wheel.defaultPrevented, false); assert.equal(key.defaultPrevented, false);
    assert(f.doc.querySelector('details').open);
  }
  f.close();
});

test('presentation is restored before paint; valid URL choices override storage', () => {
  for (const [url, saved, expected] of [
    ['https://jfx.example/', 'presentation', true],
    ['https://jfx.example/?view=reading', 'presentation', false],
    ['https://jfx.example/?view=presentation', 'reading', true],
    ['https://jfx.example/?view=unknown', 'unknown', false],
  ]) {
    const f = fixture({ url, saved });
    assert.equal(f.bootstrapped, expected);
    assert.equal(f.doc.documentElement.hasAttribute('data-presentation'), expected);
    assert.equal(f.choice.value, expected ? 'presentation' : 'reading');
    f.close();
  }
});

test('view changes persist and retain locale, appearance and fragment links', () => {
  const f = fixture(); f.change('presentation');
  assert.equal(f.win.localStorage.getItem('scalajs-jfx.landing.view'), 'presentation');
  const url = new URL(f.win.location.href);
  assert.equal(url.pathname, '/de/'); assert.equal(url.hash, '#main');
  assert.equal(url.searchParams.get('design'), 'terra');
  assert.equal(url.searchParams.get('view'), 'presentation');
  assert.equal(f.doc.querySelector('a').href, url.href);
  f.change('reading'); assert.equal(f.win.localStorage.getItem('scalajs-jfx.landing.view'), 'reading');
  f.close();
});

test('blocked storage still permits both views and URL state', () => {
  const f = fixture({ blocked: true });
  assert.equal(f.bootstrapped, false); assert.equal(f.warnings, 1);
  f.change('presentation'); assert.equal(f.warnings, 2);
  assert(f.doc.documentElement.hasAttribute('data-presentation'));
  assert.equal(new URL(f.win.location.href).searchParams.get('view'), 'presentation');
  f.close();
});

test('history navigation restores the view and bfcache retains the control', () => {
  const f = fixture();
  f.win.history.replaceState(null, '', '?view=presentation');
  f.win.dispatchEvent(new f.win.PopStateEvent('popstate'));
  assert.equal(f.choice.value, 'presentation');
  f.win.dispatchEvent(new f.win.PageTransitionEvent('pagehide', { persisted: true }));
  f.change('reading'); assert.equal(f.doc.documentElement.hasAttribute('data-presentation'), false);
  f.close();
});
