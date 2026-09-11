import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await page.waitForFunction(() => window.fixtures);
});

test('physical moves preserve hosts, focus, backward selection and later child insertion', async ({ page }) => {
  expect(await page.evaluate(() => {
    fixtures.mountMove(document.querySelector('#root'));
    const element = document.querySelector('#moving');
    const text = element.firstChild;
    element.focus();
    getSelection().setBaseAndExtent(text, 5, text, 1);
    fixtures.move();
    const selection = getSelection();
    const result = {
      host: document.querySelector('#right').firstChild === element,
      text: element.firstChild === text, focus: document.activeElement === element,
      anchor: selection.anchorNode === text && selection.anchorOffset === 5,
      extent: selection.focusNode === text && selection.focusOffset === 1,
    };
    fixtures.appendMoved();
    return { ...result, value: element.textContent };
  })).toEqual({ host: true, text: true, focus: true, anchor: true, extent: true, value: 'A😀BC!' });
});

test('UTF-16 splices preserve Text identity and no-op writes produce no mutations', async ({ page }) => {
  expect(await page.evaluate(() => {
    fixtures.mountMove(document.querySelector('#root'));
    const element = document.querySelector('#moving');
    const text = element.firstChild;
    getSelection().setBaseAndExtent(text, 4, text, 4);
    const observer = new MutationObserver(() => {});
    observer.observe(element, { subtree: true, characterData: true, childList: true });
    fixtures.splice(1, 2, '😀');
    const noOp = observer.takeRecords().length;
    const offset = getSelection().anchorOffset;
    fixtures.splice(1, 2, 'x');
    const changes = observer.takeRecords();
    observer.disconnect();
    return { noOp, offset, same: text === element.firstChild, value: text.data,
      types: changes.map(record => record.type) };
  })).toEqual({ noOp: 0, offset: 4, same: true, value: 'AxBC', types: ['characterData'] });
});

for (const control of [false, true]) {
  test(`insertBefore fallback preserves ${control ? 'form control' : 'external toolbar'} focus`, async ({ page }) => {
    expect(await page.evaluate(control => {
      fixtures.mountMove(document.querySelector('#root'));
      Object.defineProperty(document.querySelector('#right'), 'moveBefore', { value: undefined });
      const moving = document.querySelector('#moving');
      let active;
      if (control) {
        fixtures.addMovingField();
        active = document.querySelector('textarea'); active.focus(); active.setSelectionRange(2, 7, 'backward');
      } else {
        moving.focus(); getSelection().setBaseAndExtent(moving.firstChild, 4, moving.firstChild, 1);
        active = document.createElement('button'); document.body.append(active); active.focus();
      }
      // Focusing a toolbar may itself change the document range, depending on the browser.
      const before = [getSelection().anchorOffset, getSelection().focusOffset];
      fixtures.move();
      return { focus: document.activeElement === active, value: control ? active.value : moving.textContent,
        selection: control ? [active.selectionStart, active.selectionEnd, active.selectionDirection] :
          [getSelection().anchorOffset === before[0], getSelection().focusOffset === before[1]] };
    }, control)).toEqual({ focus: true, value: control ? 'control text' : 'A😀BC',
      selection: control ? [2, 7, 'backward'] : [true, true] });
  });
}

test('textarea retains values assigned before mounting with a separate baseline', async ({ page }) => {
  expect(await page.evaluate(() => {
    fixtures.mountPendingArea(document.querySelector('#root'));
    return [fixtures.areaValue(), fixtures.areaDefault()];
  })).toEqual(['pending draft', 'baseline']);
});

test('mutation protection rejects all structural paths and permits retry after release', async ({ page }) => {
  expect(await page.evaluate(() => {
    fixtures.mountMove(document.querySelector('#root'));
    const original = document.querySelector('#root').innerHTML;
    fixtures.protect();
    const statuses = ['text', 'move', 'remove', 'property', 'mount'].map(action => fixtures.attempt(action));
    const unchanged = document.querySelector('#root').innerHTML === original;
    fixtures.release();
    fixtures.move();
    fixtures.splice(0, 1, 'Z');
    return { statuses, unchanged, value: document.querySelector('#right').textContent };
  })).toEqual({ statuses: Array(5).fill('blocked'), unchanged: true, value: 'Z😀BC' });
});

test('keyed reconciliation updates and reorders the same elements', async ({ page }) => {
  expect(await page.evaluate(() => {
    fixtures.mountKeyed(document.querySelector('#root'));
    const a = document.querySelector('#a'), b = document.querySelector('#b');
    fixtures.updateKeyed();
    return { a: a === document.querySelector('#a'), b: b === document.querySelector('#b'),
      order: [...document.querySelectorAll('p')].map(node => [node.id, node.textContent]) };
  })).toEqual({ a: true, b: true, order: [['b', 'TWO'], ['a', 'one'], ['c', 'three']] });
});

test('textarea SSR survives the HTML parser and participates in native forms', async ({ page }) => {
  for (const input of ['', '\nabc', '\n\nabc', '&</textarea><script>bad()</script>', 'a\r\nb\rc']) {
    const expected = input.replace(/\r\n?/g, '\n');
    expect(await page.evaluate(value => {
      const html = fixtures.renderArea(value);
      document.querySelector('#root').innerHTML = html;
      const field = document.querySelector('textarea');
      return { value: field.value, submitted: new FormData(field.form).get('body'),
        comments: html.includes('<!--'), scripts: document.querySelector('#root script') !== null };
    }, input)).toEqual({ value: expected, submitted: expected, comments: false, scripts: false });
  }
});

test('server-rendered textarea works with JavaScript disabled', async ({ page, browser }) => {
  const html = await page.evaluate(() => fixtures.renderArea('\nsource & text'));
  const context = await browser.newContext({ javaScriptEnabled: false });
  try {
    const nativePage = await context.newPage();
    await nativePage.setContent(html.replace('<form', '<form action="http://127.0.0.1:4187/submit" method="post"')
      .replace('</form>', '<button>Save</button></form>'));
    await expect(nativePage.locator('textarea')).toHaveValue('\nsource & text');
    await nativePage.locator('textarea').fill('edited without JS');
    await nativePage.getByRole('button', { name: 'Save' }).click();
    await expect(nativePage.locator('body')).toHaveText('body=edited+without+JS');
  } finally { await context.close(); }
});

test('textarea hydration preserves live value, focus and selection; input and reset stay synchronized', async ({ page }) => {
  expect(await page.evaluate(() => {
    const root = document.querySelector('#root');
    root.innerHTML = fixtures.renderArea('server');
    const field = root.querySelector('textarea');
    field.value = 'user draft'; field.focus(); field.setSelectionRange(1, 6, 'backward');
    fixtures.hydrateArea(root, 'different client initial');
    return { same: root.querySelector('textarea') === field, value: fixtures.areaValue(),
      observed: fixtures.areaObserved(), baseline: fixtures.areaDefault(),
      focused: document.activeElement === field, selection: [field.selectionStart, field.selectionEnd, field.selectionDirection] };
  })).toEqual({ same: true, value: 'user draft', observed: 'user draft', baseline: 'server', focused: true,
    selection: [1, 6, 'backward'] });
  await page.locator('textarea').fill('later edit');
  expect(await page.evaluate(() => fixtures.areaObserved())).toBe('later edit');
  await page.evaluate(() => document.querySelector('form').reset());
  await expect.poll(() => page.evaluate(() => fixtures.areaObserved())).toBe('server');
  await page.locator('textarea').fill('cancelled reset');
  await page.evaluate(() => {
    const form = document.querySelector('form');
    form.addEventListener('reset', event => event.preventDefault(), { once: true });
    form.reset();
  });
  expect(await page.evaluate(() => fixtures.areaObserved())).toBe('cancelled reset');
  await page.evaluate(() => {
    const field = document.querySelector('textarea');
    fixtures.dispose();
    field.value = 'detached input'; field.dispatchEvent(new Event('input'));
  });
  expect(await page.evaluate(() => fixtures.areaObserved())).toBe('cancelled reset');
});

for (const mismatch of ['none', 'preflight', 'text', 'tag', 'extra']) {
  test(`hydration boundary handles ${mismatch} without touching the adjacent fallback`, async ({ page }) => {
    expect(await page.evaluate(mismatch => {
      const root = document.querySelector('#root');
      root.innerHTML = fixtures.renderBoundary();
      const source = root.querySelector('textarea'), section = root.querySelector('section');
      const first = section.firstChild;
      if (mismatch === 'preflight') section.dataset.reject = 'yes';
      if (mismatch === 'text') section.lastChild.textContent = 'wrong text';
      if (mismatch === 'tag') section.lastChild.outerHTML = '<div>second</div>';
      if (mismatch === 'extra') section.append(document.createElement('aside'));
      source.value = 'unsaved draft'; source.focus(); source.setSelectionRange(2, 7, 'backward');
      fixtures.hydrateBoundary(root, true);
      fixtures.complete();
      return { sameSource: root.querySelector('textarea') === source,
        sameBoundary: root.querySelector('section') === section, sameFirst: section.firstChild === first,
        value: source.value, focus: document.activeElement === source,
        selection: [source.selectionStart, source.selectionEnd, source.selectionDirection],
        texts: [...section.children].map(child => child.textContent),
        ready: fixtures.readyCount(), repairs: fixtures.repairCount(), captured: fixtures.capturedValue() };
    }, mismatch)).toEqual({ sameSource: true, sameBoundary: true, sameFirst: mismatch === 'none',
      value: 'unsaved draft', focus: true, selection: [2, 7, 'backward'], texts: ['first', 'second'],
      ready: 2, repairs: mismatch === 'none' ? 0 : 1, captured: 'unsaved draft' });
  });
}

for (const recovery of [false, true]) {
  test(`disposing a ${recovery ? 'recovered' : 'claimed'} boundary cancels deferred activation`, async ({ page }) => {
    expect(await page.evaluate(recovery => {
      const root = document.querySelector('#root'); root.innerHTML = fixtures.renderBoundary();
      if (recovery) root.querySelector('section').dataset.reject = 'yes';
      fixtures.hydrateBoundary(root, false);
      const before = fixtures.readyCount();
      fixtures.dispose(); fixtures.complete();
      return { before, after: fixtures.readyCount(), empty: root.childNodes.length === 0 };
    }, recovery)).toEqual({ before: 0, after: 0, empty: true });
  });
}

test('DOM creation and moves use the owning iframe document', async ({ page }) => {
  expect(await page.evaluate(() => {
    const frame = document.createElement('iframe'); document.body.append(frame);
    const doc = frame.contentDocument;
    fixtures.mountMove(doc.body);
    const element = doc.querySelector('#moving'), text = element.firstChild;
    element.focus(); doc.getSelection().setBaseAndExtent(text, 4, text, 1);
    fixtures.move();
    return { document: element.ownerDocument === doc, focus: doc.activeElement === element,
      anchor: doc.getSelection().anchorOffset, extent: doc.getSelection().focusOffset };
  })).toEqual({ document: true, focus: true, anchor: 4, extent: 1 });
});

test('moves reject a host adopted into another document even when detached', async ({ page }) => {
  expect(await page.evaluate(() => {
    fixtures.mountMove(document.querySelector('#root'));
    const element = document.querySelector('#moving');
    const foreign = document.implementation.createHTMLDocument();
    foreign.adoptNode(element);
    let rejected = false;
    try { fixtures.move(); } catch (error) { rejected = String(error).includes('across DOM documents'); }
    return { rejected, detached: element.parentNode === null, sameDocument: element.ownerDocument === foreign,
      destinationEmpty: document.querySelector('#right').childNodes.length === 0 };
  })).toEqual({ rejected: true, detached: true, sameDocument: true, destinationEmpty: true });
});
