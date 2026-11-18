import { test } from 'node:test';
import assert from 'node:assert/strict';
import { STRINGS, makeT, resolveLanguage } from '../js/i18n.js';

const placeholders = (text) => [...text.matchAll(/\{(\d+)\}/g)].map((m) => m[1]).sort().join(',');

test('English and Hebrew have the same words, with the same placeholders', () => {
  assert.deepEqual(Object.keys(STRINGS.he).sort(), Object.keys(STRINGS.en).sort());
  for (const key of Object.keys(STRINGS.en)) {
    assert.equal(placeholders(STRINGS.he[key]), placeholders(STRINGS.en[key]), key);
    assert.ok(STRINGS.he[key].trim(), `${key} is empty in Hebrew`);
  }
});

test('the language follows the browser unless one is chosen', () => {
  assert.equal(resolveLanguage('system', ['he-IL', 'en-US']), 'he');
  assert.equal(resolveLanguage('system', ['iw']), 'he');
  assert.equal(resolveLanguage('system', ['fr-FR', 'en-GB']), 'en');
  assert.equal(resolveLanguage('system', ['ru']), 'en');
  assert.equal(resolveLanguage('he', ['en-US']), 'he');
  assert.equal(resolveLanguage('en', ['he-IL']), 'en');
});

test('placeholders are filled in', () => {
  assert.equal(makeT('en')('sleep_stops_in', '4:59'), 'Stops in 4:59');
  assert.equal(makeT('he')('sleep_minutes', 30), '30 דק׳');
  assert.equal(makeT('he')('no_such_key'), 'no_such_key');
});
