import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { ACCENT_PRESETS, colorScheme, contrastRatio, lch, parseHex, schemeToCss, withPureBlack } from '../js/seed-scheme.js';
import { parseCatalog } from '../js/catalog.js';

const catalog = parseCatalog(JSON.parse(readFileSync(new URL('../../app/src/main/assets/catalog.json', import.meta.url), 'utf8')));
const seeds = [...ACCENT_PRESETS, ...catalog.stations.map((s) => s.color), '#000000', '#FFFFFF', '#808080'];

const PAIRS = [
  ['primary', 'onPrimary'],
  ['primaryContainer', 'onPrimaryContainer'],
  ['secondaryContainer', 'onSecondaryContainer'],
  ['surface', 'onSurface'],
  ['surfaceContainerHigh', 'onSurface'],
  ['surface', 'onSurfaceVariant'],
  ['errorContainer', 'onErrorContainer'],
];

test('every seed gives readable text in light and dark', () => {
  for (const seed of seeds) {
    for (const dark of [false, true]) {
      const scheme = colorScheme(seed, dark);
      for (const [background, content] of PAIRS) {
        const ratio = contrastRatio(scheme[background], scheme[content]);
        assert.ok(ratio >= 4.5, `${seed} ${dark ? 'dark' : 'light'}: ${content} on ${background} is ${ratio.toFixed(2)}`);
      }
    }
  }
});

test('roles sit at their Material tones', () => {
  const tone = (hex) => lch(parseHex(hex))[0];
  const light = colorScheme('#6750A4', false);
  const dark = colorScheme('#6750A4', true);
  assert.ok(Math.abs(tone(light.primary) - 40) < 1);
  assert.ok(Math.abs(tone(light.surface) - 98) < 1);
  assert.ok(Math.abs(tone(dark.primary) - 80) < 1);
  assert.ok(Math.abs(tone(dark.surface) - 6) < 1);
});

test('gray seeds give calm schemes', () => {
  const chroma = (hex) => lch(parseHex(hex))[1];
  for (const seed of ['#000000', '#FFFFFF', '#777777']) {
    assert.ok(chroma(colorScheme(seed, false).primary) < 2, `${seed} stays gray`);
  }
});

test('pure black keeps accents and blackens surfaces', () => {
  const scheme = withPureBlack(colorScheme('#0061A4', true));
  assert.equal(scheme.surface, '#000000');
  assert.equal(scheme.surfaceContainer, '#111114');
  assert.equal(scheme.primary, colorScheme('#0061A4', true).primary);
});

test('schemes become CSS custom properties', () => {
  const css = schemeToCss({ onPrimaryContainer: '#123456' });
  assert.equal(css, '--md-sys-color-on-primary-container: #123456;');
});
