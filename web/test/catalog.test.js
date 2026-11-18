import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { isHls, parseCatalog, parseColor, stationSubtitle, stationsIn } from '../js/catalog.js';

const raw = JSON.parse(readFileSync(new URL('../../app/src/main/assets/catalog.json', import.meta.url), 'utf8'));
const catalog = parseCatalog(raw);
const t = (key) => key;

test('every station in the list plays in a browser', () => {
  const active = raw.stations.filter((s) => (s.status ?? 'active') === 'active' && s.streams?.length);
  const missing = active.filter((s) => !catalog.byId.has(s.id)).map((s) => s.id);
  assert.deepEqual(missing, [], 'stations without an https stream are hidden on the web');
});

test('only https streams are kept', () => {
  for (const station of catalog.stations) {
    for (const stream of station.streams) assert.match(stream.url, /^https:\/\//, station.id);
  }
});

test('stations are valid', () => {
  const collections = new Set(catalog.collections.map((c) => c.id));
  for (const station of catalog.stations) {
    assert.match(station.color, /^#[0-9A-F]{6}$/, station.id);
    assert.ok(station.nameHe && station.nameEn && station.shortName, station.id);
    for (const id of station.collections) assert.ok(collections.has(id), `${station.id}: unknown collection ${id}`);
    if (station.logo) assert.match(station.logo, /^https:\/\//, `${station.id}: logos load over https`);
  }
  assert.equal(new Set(catalog.stations.map((s) => s.id)).size, catalog.stations.length, 'ids are unique');
});

test('stations come best first, and collections in order', () => {
  const ranks = catalog.stations.map((s) => s.rank);
  assert.deepEqual(ranks, [...ranks].sort((a, b) => a - b));
  const kan = stationsIn(catalog, 'kan');
  assert.ok(kan.length > 0 && kan.every((s) => s.collections.includes('kan')));
  assert.equal(stationsIn(catalog, null), catalog.stations);
});

test('colors and subtitles follow the app', () => {
  assert.equal(parseColor('#ff7a1f'), '#FF7A1F');
  assert.equal(parseColor('#80FF7A1F'), '#FF7A1F');
  assert.equal(parseColor('nope'), '#6750A4');
  assert.equal(stationSubtitle({ primaryFrequency: 91.8, frequencies: [91.8] }, t), '91.8 FM');
  assert.equal(stationSubtitle({ primaryFrequency: null, frequencies: [91.8, 96.6] }, t), 'fm_varies');
  assert.equal(stationSubtitle({ primaryFrequency: null, frequencies: [] }, t), 'digital_station');
});

test('HLS streams are recognized', () => {
  assert.equal(isHls({ url: 'https://example.com/live.m3u8?x=1', format: null }), true);
  assert.equal(isHls({ url: 'https://example.com/stream', format: 'hls' }), true);
  assert.equal(isHls({ url: 'https://example.com/stream.mp3', format: 'mp3' }), false);
});

test('an unsupported list is rejected', () => {
  assert.throws(() => parseCatalog({ schemaVersion: 99, stations: [] }));
  assert.throws(() => parseCatalog(null));
});
