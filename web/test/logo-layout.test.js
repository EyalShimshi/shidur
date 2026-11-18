// How station logos are laid out on their square artwork; the same cases as the app's
// LogoLayoutTest.kt.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DARK_BACKGROUND, WHITE, frameWidth, layoutOf, rgbaToArgb } from '../js/logo-layout.js';

const transparent = 0x00000000;
const black = 0xff000000;
const purple = 0xff51305f;
const blue = 0xff0a57a8;

/** A width x height image of background, with paint deciding any other pixel's color. */
function image(width, height, background, paint) {
  const pixels = new Uint32Array(width * height);
  for (let i = 0; i < pixels.length; i++) pixels[i] = paint(i % width, Math.floor(i / width)) ?? background;
  return pixels;
}

const within = (v, from, to) => v >= from && v <= to;

test('a solid tile fills the whole square with its color', () => {
  // A purple tile on a transparent canvas, like Galgalatz's logo.
  const pixels = image(120, 80, transparent, (x, y) => (within(x, 4, 115) && within(y, 4, 75) ? purple : null));
  const layout = layoutOf(pixels, 120, 80);
  assert.equal(layout.fill, purple);
  assert.deepEqual(layout.box, { left: 4, top: 4, right: 116, bottom: 76 });
  assert.ok(Math.abs(layout.margin - 0.06) < 0.001);
  assert.equal(layout.cropSquare, false);
  assert.equal(layout.clear, null);
});

test('a wordmark loses its empty margins and sits on white', () => {
  const pixels = image(200, 100, transparent, (x, y) => (within(y, 40, 59) && within(x, 50, 149) && x % 10 < 3 ? black : null));
  const layout = layoutOf(pixels, 200, 100);
  assert.equal(layout.fill, WHITE);
  assert.deepEqual(layout.box, { left: 50, top: 40, right: 143, bottom: 60 });
  assert.ok(Math.abs(layout.margin - 0.12) < 0.001);
  assert.equal(layout.clear, null);
});

test('a white wordmark gets a dark background', () => {
  const pixels = image(200, 100, transparent, (x, y) => (within(y, 40, 59) && within(x, 50, 149) && x % 10 < 3 ? WHITE : null));
  assert.equal(layoutOf(pixels, 200, 100).fill, DARK_BACKGROUND);
});

test('a logo on its own background keeps that background', () => {
  const pixels = image(200, 100, WHITE, (x, y) => (within(y, 30, 69) && within(x, 40, 159) && x % 8 < 2 ? blue : null));
  const layout = layoutOf(pixels, 200, 100);
  assert.equal(layout.fill, WHITE);
  assert.deepEqual(layout.box, { left: 40, top: 30, right: 154, bottom: 70 });
  assert.equal(layout.clear, null);
});

test('a badge on a background of another color is cut out', () => {
  // A blue tile with a white letter inside, on an opaque white picture.
  const pixels = image(100, 100, WHITE, (x, y) => {
    if (within(x, 45, 54) && within(y, 45, 54)) return WHITE;
    if (within(x, 20, 79) && within(y, 30, 69)) return blue;
    return null;
  });
  const layout = layoutOf(pixels, 100, 100);
  assert.equal(layout.fill, blue);
  assert.deepEqual(layout.box, { left: 20, top: 30, right: 80, bottom: 70 });
  assert.ok(layout.clear, 'clears the picture');
  assert.equal(layout.clear[0], 1, "the picture's own background is removed");
  assert.equal(layout.clear[35 * 100 + 25], 0, 'the tile stays');
  assert.equal(layout.clear[50 * 100 + 50], 0, 'white inside the tile stays');
});

test('a busy square picture is used as it is', () => {
  let seed = 1;
  const random = () => (seed = (seed * 1103515245 + 12345) % 2147483648) & 0xffffff;
  const pixels = new Uint32Array(100 * 100).map(() => (black | random()) >>> 0);
  assert.equal(layoutOf(pixels, 100, 100).cropSquare, true);
});

test('blank images have no layout', () => {
  assert.equal(layoutOf(new Uint32Array(50 * 50), 50, 50), null);
  assert.equal(layoutOf(new Uint32Array(50 * 50).fill(WHITE), 50, 50), null);
});

test('a thin border line is ignored', () => {
  assert.equal(frameWidth(400, 200), 2);
  assert.equal(frameWidth(60, 60), 1);
  assert.equal(frameWidth(17, 17), 0);
});

test('canvas bytes become ARGB pixels', () => {
  const pixels = rgbaToArgb(new Uint8ClampedArray([0x51, 0x30, 0x5f, 0xff, 0, 0, 0, 0]));
  assert.deepEqual([...pixels], [purple, transparent]);
});
