// How a station logo is laid out on its square artwork, worked out from its pixels alone:
// - a logo that is a solid shape (a tile or badge, like Galgalatz's purple tile) fills the whole
//   square with its own color;
// - any other logo loses its empty margins and is centered on its own background color, or on white
//   (dark for white logos) when it has none;
// - a square picture with a busy edge (a photo, a full-bleed icon) is used as it is.
// A port of LogoNormalizer.LogoLayout in the Android app's StationLogos.kt; keep the two in step.
// Pixels are unsigned 32-bit ARGB numbers, row by row.

/** Raise whenever the artwork changes, so logos cached by older versions are made again. */
export const LOGO_VERSION = 2;

export const WHITE = 0xffffffff;
export const DARK_BACKGROUND = 0xff1c1b1f;

const BADGE_MARGIN = 0.06;
const LOGO_MARGIN = 0.12;
const SQUARE_MIN_ASPECT = 0.8;
const SQUARE_MAX_ASPECT = 1.25;
const FRAME_FRACTION = 0.01;
const MIN_INNER_PX = 16;
const TRANSPARENT_EDGE_SHARE = 0.02;
const SOLID_EDGE_SHARE = 0.9;
const BADGE_SHARE = 0.8;
const RING_INSET = 0.03;
const LIGHT_LOGO_LUMINANCE = 0.82;
// Squared distances between RGB colors.
const SAME_EDGE = 32 * 32;
const SAME_BACKGROUND = 48 * 48;
const SAME_FILL = 40 * 40;

const alpha = (c) => (c >>> 24) & 0xff;
const red = (c) => (c >>> 16) & 0xff;
const green = (c) => (c >>> 8) & 0xff;
const blue = (c) => c & 0xff;
const rgb = (r, g, b) => ((0xff << 24) | (r << 16) | (g << 8) | b) >>> 0;

function distance2(a, b) {
  const dr = red(a) - red(b);
  const dg = green(a) - green(b);
  const db = blue(a) - blue(b);
  return dr * dr + dg * dg + db * db;
}

/** Canvas RGBA bytes → ARGB pixels. */
export function rgbaToArgb(data) {
  const pixels = new Uint32Array(data.length / 4);
  for (let i = 0, j = 0; i < pixels.length; i++, j += 4) {
    pixels[i] = ((data[j + 3] << 24) | (data[j] << 16) | (data[j + 1] << 8) | data[j + 2]) >>> 0;
  }
  return pixels;
}

export function argbToCss(color) {
  return '#' + ((color >>> 0) & 0xffffff).toString(16).padStart(6, '0');
}

/** Pixels to ignore along each side of an image, so a thin border line doesn't count. */
export function frameWidth(width, height) {
  const frame = Math.max(1, Math.round(Math.min(width, height) * FRAME_FRACTION));
  return width - 2 * frame < MIN_INNER_PX || height - 2 * frame < MIN_INNER_PX ? 0 : frame;
}

/**
 * The layout for an image, or null when it is blank:
 * { cropSquare, box: { left, top, right, bottom } (right and bottom exclusive), fill (ARGB),
 *   margin (a share of the square on each side), clear (pixels to make transparent first, or null) }.
 */
export function layoutOf(pixels, width, height) {
  if (width <= 0 || height <= 0 || pixels.length < width * height) return null;
  const border = borderIndices(width, height);
  const outside = new Uint8Array(width * height);
  let background = WHITE;
  let edge;
  let transparent = 0;
  for (const i of border) if (alpha(pixels[i]) < 250) transparent++;
  if (transparent > border.length * TRANSPARENT_EDGE_SHARE) {
    for (let i = 0; i < outside.length; i++) outside[i] = alpha(pixels[i]) < 32 ? 1 : 0;
    edge = 'transparent';
  } else {
    background = medianColor(pixels, border);
    let same = 0;
    for (const i of border) if (distance2(pixels[i], background) <= SAME_EDGE) same++;
    if (same >= border.length * SOLID_EDGE_SHARE) {
      floodFill(pixels, width, border, background, outside);
      edge = 'solid';
    } else {
      edge = 'busy';
    }
  }
  const box = contentBox(outside, width, height);
  if (!box) return null;
  const ring = ringColor(pixels, width, outside, box);
  const whole = { left: 0, top: 0, right: width, bottom: height };
  if (edge === 'busy') {
    const aspect = width / height;
    return aspect >= SQUARE_MIN_ASPECT && aspect <= SQUARE_MAX_ASPECT
      ? { cropSquare: true, box: whole, fill: WHITE, margin: 0, clear: null }
      : { cropSquare: false, box: whole, fill: ring ? ring.color : WHITE, margin: LOGO_MARGIN, clear: null };
  }
  const badgeColor = ring && ring.share >= BADGE_SHARE ? ring.color : null;
  let fill;
  if (badgeColor !== null) fill = badgeColor;
  else if (edge === 'solid') fill = background;
  else fill = isLight(pixels, width, outside, box) ? DARK_BACKGROUND : WHITE;
  // A badge on a background of another color: only the badge goes on the square.
  const clear = edge === 'solid' && distance2(fill, background) > SAME_FILL ? outside : null;
  return { cropSquare: false, box, fill, margin: badgeColor !== null ? BADGE_MARGIN : LOGO_MARGIN, clear };
}

function borderIndices(width, height) {
  const indices = [];
  for (let x = 0; x < width; x++) {
    indices.push(x);
    if (height > 1) indices.push((height - 1) * width + x);
  }
  for (let y = 1; y < height - 1; y++) {
    indices.push(y * width);
    if (width > 1) indices.push(y * width + width - 1);
  }
  return indices;
}

function medianColor(pixels, indices) {
  const median = (channel) => {
    const values = indices.map((i) => channel(pixels[i])).sort((a, b) => a - b);
    return values[Math.floor(values.length / 2)];
  };
  return rgb(median(red), median(green), median(blue));
}

/** Marks the background reachable from the image's edges, so enclosed areas stay. */
function floodFill(pixels, width, seeds, background, outside) {
  const stack = new Int32Array(pixels.length);
  let size = 0;
  for (const i of seeds) {
    if (!outside[i] && distance2(pixels[i], background) <= SAME_BACKGROUND) {
      outside[i] = 1;
      stack[size++] = i;
    }
  }
  while (size > 0) {
    const i = stack[--size];
    const x = i % width;
    for (let direction = 0; direction < 4; direction++) {
      let j;
      if (direction === 0) j = x > 0 ? i - 1 : -1;
      else if (direction === 1) j = x < width - 1 ? i + 1 : -1;
      else if (direction === 2) j = i - width;
      else j = i + width;
      if (j < 0 || j >= outside.length || outside[j]) continue;
      if (distance2(pixels[j], background) > SAME_BACKGROUND) continue;
      outside[j] = 1;
      stack[size++] = j;
    }
  }
}

/** The bounds of the logo itself: rows and columns with more than a speck of it. */
function contentBox(outside, width, height) {
  const minInRow = Math.max(1, Math.round(width * 0.002));
  const minInColumn = Math.max(1, Math.round(height * 0.002));
  const inColumn = new Int32Array(width);
  let top = -1;
  let bottom = -1;
  for (let y = 0; y < height; y++) {
    let inRow = 0;
    for (let x = 0; x < width; x++) {
      if (!outside[y * width + x]) {
        inRow++;
        inColumn[x]++;
      }
    }
    if (inRow >= minInRow) {
      if (top < 0) top = y;
      bottom = y;
    }
  }
  let left = -1;
  let right = -1;
  for (let x = 0; x < width; x++) {
    if (inColumn[x] >= minInColumn) {
      if (left < 0) left = x;
      right = x;
    }
  }
  if (top < 0 || left < 0) return null;
  return { left, top, right: right + 1, bottom: bottom + 1 };
}

/**
 * The most common color on a line just inside [box], and the share of that line it covers: close
 * to 1 when the logo is a solid shape such as a tile or a circle.
 */
function ringColor(pixels, width, outside, box) {
  const boxWidth = box.right - box.left;
  const boxHeight = box.bottom - box.top;
  const inset = Math.max(1, Math.round(Math.min(boxWidth, boxHeight) * RING_INSET));
  const stride = Math.max(1, Math.round(Math.max(boxWidth, boxHeight) / 200));
  const left = box.left + inset;
  const right = box.right - 1 - inset;
  const top = box.top + inset;
  const bottom = box.bottom - 1 - inset;
  if (left > right || top > bottom) return null;
  const samples = [];
  for (let x = left; x <= right; x += stride) {
    samples.push(top * width + x, bottom * width + x);
  }
  for (let y = top; y <= bottom; y += stride) {
    samples.push(y * width + left, y * width + right);
  }
  const buckets = new Map();
  for (const i of samples) {
    const color = pixels[i];
    if (outside[i] || alpha(color) < 200) continue;
    const key = ((red(color) >> 4) << 8) | ((green(color) >> 4) << 4) | (blue(color) >> 4);
    let sum = buckets.get(key);
    if (!sum) buckets.set(key, (sum = [0, 0, 0, 0]));
    sum[0] += red(color);
    sum[1] += green(color);
    sum[2] += blue(color);
    sum[3]++;
  }
  let best = null;
  for (const sum of buckets.values()) if (!best || sum[3] > best[3]) best = sum;
  if (!best) return null;
  const color = rgb(Math.floor(best[0] / best[3]), Math.floor(best[1] / best[3]), Math.floor(best[2] / best[3]));
  let matching = 0;
  for (const i of samples) {
    if (!outside[i] && alpha(pixels[i]) >= 200 && distance2(pixels[i], color) <= SAME_FILL) matching++;
  }
  return { color, share: matching / samples.length };
}

/** Whether the visible part of the logo is mostly white (it needs a dark background). */
function isLight(pixels, width, outside, box) {
  let total = 0;
  let count = 0;
  for (let y = box.top; y < box.bottom; y += 2) {
    for (let x = box.left; x < box.right; x += 2) {
      const i = y * width + x;
      const color = pixels[i];
      if (outside[i] || alpha(color) < 128) continue;
      total += (0.299 * red(color) + 0.587 * green(color) + 0.114 * blue(color)) / 255;
      count++;
    }
  }
  return count > 0 && total / count > LIGHT_LOGO_LUMINANCE;
}
