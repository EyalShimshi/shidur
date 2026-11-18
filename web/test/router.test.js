import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseHash } from '../js/router.js';

test('an empty or unknown address is Home', () => {
  assert.deepEqual(parseHash(''), { name: 'home' });
  assert.deepEqual(parseHash('#/'), { name: 'home' });
  assert.deepEqual(parseHash('#/nowhere'), { name: 'home' });
  assert.deepEqual(parseHash('#/station'), { name: 'home' });
});

test('screens, collections and shared station links', () => {
  assert.deepEqual(parseHash('#/browse'), { name: 'browse', collection: null });
  assert.deepEqual(parseHash('#/browse/kan'), { name: 'browse', collection: 'kan' });
  assert.deepEqual(parseHash('#/favorites'), { name: 'favorites' });
  assert.deepEqual(parseHash('#/settings/'), { name: 'settings' });
  assert.deepEqual(parseHash('#/station/kan-bet'), { name: 'station', station: 'kan-bet' });
});

test('ignores a query, decodes ids and survives broken escapes', () => {
  assert.deepEqual(parseHash('#/station/glglz?utm_source=x'), { name: 'station', station: 'glglz' });
  assert.deepEqual(parseHash('#/station/a%20b'), { name: 'station', station: 'a b' });
  assert.deepEqual(parseHash('#/station/%E0%A4%A'), { name: 'station', station: '%E0%A4%A' });
});
