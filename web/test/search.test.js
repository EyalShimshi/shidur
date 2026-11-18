import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { normalize, parseFrequency, search } from '../js/search.js';
import { parseCatalog } from '../js/catalog.js';

const catalog = parseCatalog(JSON.parse(readFileSync(new URL('../../app/src/main/assets/catalog.json', import.meta.url), 'utf8')));
const first = (query) => search(query, catalog.stations)[0]?.id;

test('normalizing ignores case, quotes, spaces, niqqud and final letters', () => {
  assert.equal(normalize('צה"ל'), 'צהל');
  assert.equal(normalize('גלגל״צ'), 'גלגלצ');
  assert.equal(normalize('שָׁלוֹם'), 'שלומ');
  assert.equal(normalize('Kan  Bet!'), 'kanbet');
});

test('frequencies parse in the FM band only', () => {
  assert.equal(parseFrequency('88'), 88);
  assert.equal(parseFrequency('107,5'), 107.5);
  assert.equal(parseFrequency('103FM'), 103);
  assert.equal(parseFrequency('91.8 mhz'), 91.8);
  assert.equal(parseFrequency('12'), null);
  assert.equal(parseFrequency('150'), null);
  assert.equal(parseFrequency('galgalatz'), null);
});

test('names, nicknames and frequencies find the right station first', () => {
  assert.equal(first('גלגלצ'), 'glglz');
  assert.equal(first('88'), 'kan-88');
  assert.equal(first('103'), '103fm');
  assert.equal(first('Kan Bet'), 'kan-bet');
});

test('nothing matches an empty query', () => {
  assert.deepEqual(search('  ', catalog.stations), []);
  assert.deepEqual(search('zzzzqqq', catalog.stations), []);
});
