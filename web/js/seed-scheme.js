// Material 3 color schemes from one seed color, generated in CIELAB, whose lightness L* is the same
// "tone" scale Material uses, so every role keeps Material's contrast guarantees whatever the seed.
// A port of the Android app's SeedScheme.kt; keep the two in step.

const XN = 0.95047;
const YN = 1.0;
const ZN = 1.08883;
const EPSILON = 216 / 24389;
const KAPPA = 24389 / 27;
const GAMUT_TOLERANCE = 0.0005;
const GAMUT_SEARCH_STEPS = 14;
const TERTIARY_HUE_SHIFT = 60;
const ERROR_HUE = 33;
/** Seeds at least this colorful (CIELAB chroma) get fully colored schemes. */
const FULL_COLOR_CHROMA = 16;

/** Accent colors offered in Settings. The first one is Material's baseline purple (the default). */
export const ACCENT_PRESETS = ['#6750A4', '#0061A4', '#006A6A', '#386A20', '#8B5000', '#9C4146', '#B4236C', '#0038B8'];

export const PURE_BLACK = {
  background: '#000000',
  surface: '#000000',
  surfaceDim: '#000000',
  surfaceContainerLowest: '#000000',
  surfaceContainerLow: '#0A0A0C',
  surfaceContainer: '#111114',
  surfaceContainerHigh: '#18181B',
  surfaceContainerHighest: '#1F1F23',
};

/** `#rrggbb` → [r, g, b] in 0–255, or null. */
export function parseHex(hex) {
  const match = /^#?([0-9a-f]{6})$/i.exec(String(hex ?? '').trim());
  if (!match) return null;
  const value = parseInt(match[1], 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

export function toHex([r, g, b]) {
  return '#' + [r, g, b].map((c) => c.toString(16).padStart(2, '0')).join('').toUpperCase();
}

function linear(channel) {
  const c = channel / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function encode(value) {
  const clamped = Math.min(1, Math.max(0, value));
  const c = clamped <= 0.0031308 ? 12.92 * clamped : 1.055 * clamped ** (1 / 2.4) - 0.055;
  return Math.min(255, Math.max(0, Math.floor(c * 255 + 0.5)));
}

const f = (t) => (t > EPSILON ? Math.cbrt(t) : (KAPPA * t + 16) / 116);

function fInverse(t) {
  const cube = t * t * t;
  return cube > EPSILON ? cube : (116 * t - 16) / KAPPA;
}

const inGamut = (v) => v >= -GAMUT_TOLERANCE && v <= 1 + GAMUT_TOLERANCE;

/** [lightness, chroma, hue in degrees] of an sRGB color. */
export function lch(rgb) {
  const r = linear(rgb[0]);
  const g = linear(rgb[1]);
  const b = linear(rgb[2]);
  const x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b;
  const y = 0.2126729 * r + 0.7151522 * g + 0.072175 * b;
  const z = 0.0193339 * r + 0.119192 * g + 0.9503041 * b;
  const fx = f(x / XN);
  const fy = f(y / YN);
  const fz = f(z / ZN);
  const lightness = 116 * fy - 16;
  const a = 500 * (fx - fy);
  const bb = 200 * (fy - fz);
  let hue = (Math.atan2(bb, a) * 180) / Math.PI;
  if (hue < 0) hue += 360;
  return [lightness, Math.hypot(a, bb), hue];
}

/** The sRGB color at (lightness, chroma, hue in radians), or null when it's outside sRGB. */
function toRgbOrNull(lightness, chroma, hueRadians) {
  const a = chroma * Math.cos(hueRadians);
  const b = chroma * Math.sin(hueRadians);
  const fy = (lightness + 16) / 116;
  const fx = fy + a / 500;
  const fz = fy - b / 200;
  const x = XN * fInverse(fx);
  const y = YN * (lightness > KAPPA * EPSILON ? fy * fy * fy : lightness / KAPPA);
  const z = ZN * fInverse(fz);
  const r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z;
  const g = -0.969266 * x + 1.8760108 * y + 0.041556 * z;
  const bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z;
  if (!inGamut(r) || !inGamut(g) || !inGamut(bl)) return null;
  return [encode(r), encode(g), encode(bl)];
}

/** Colors of one hue and chroma at any tone (0–100), clipped to the sRGB gamut. */
export function tonalPalette(hueDegrees, chroma) {
  const hue = (hueDegrees * Math.PI) / 180;
  const cache = new Map();
  const at = (lightness) => {
    if (lightness <= 0) return [0, 0, 0];
    if (lightness >= 100) return [255, 255, 255];
    const exact = toRgbOrNull(lightness, chroma, hue);
    if (exact) return exact;
    // Out of gamut: keep the lightness (and therefore the contrast) and lower the chroma.
    let low = 0;
    let high = chroma;
    let best = toRgbOrNull(lightness, 0, hue) ?? toRgbOrNull(lightness, 0, 0) ?? [0, 0, 0];
    for (let i = 0; i < GAMUT_SEARCH_STEPS; i++) {
      const mid = (low + high) / 2;
      const rgb = toRgbOrNull(lightness, mid, hue);
      if (rgb) {
        best = rgb;
        low = mid;
      } else {
        high = mid;
      }
    }
    return best;
  };
  return (tone) => {
    if (!cache.has(tone)) cache.set(tone, toHex(at(tone)));
    return cache.get(tone);
  };
}

/** A full Material 3 color scheme (role → `#RRGGBB`) generated from [seedHex]. */
export function colorScheme(seedHex, dark) {
  const rgb = parseHex(seedHex) ?? parseHex(ACCENT_PRESETS[0]);
  const [, seedChroma, seedHue] = lch(rgb);
  // Gray-ish seeds (black, white or gray brands) give calm schemes rather than a random hue.
  const colorfulness = Math.min(1, Math.max(0, seedChroma / FULL_COLOR_CHROMA));
  const primary = tonalPalette(seedHue, Math.min(56, Math.max(32, seedChroma)) * colorfulness);
  const secondary = tonalPalette(seedHue, 18 * colorfulness);
  const tertiary = tonalPalette(seedHue + TERTIARY_HUE_SHIFT, 28 * colorfulness);
  const neutral = tonalPalette(seedHue, 5 * colorfulness);
  const neutralVariant = tonalPalette(seedHue, 10 * colorfulness);
  const error = tonalPalette(ERROR_HUE, 62);
  const accents = (palette, name) =>
    dark
      ? {
          [name]: palette(80),
          ['on' + cap(name)]: palette(20),
          [name + 'Container']: palette(30),
          ['on' + cap(name) + 'Container']: palette(90),
        }
      : {
          [name]: palette(40),
          ['on' + cap(name)]: palette(100),
          [name + 'Container']: palette(90),
          ['on' + cap(name) + 'Container']: palette(10),
        };
  return {
    ...accents(primary, 'primary'),
    ...accents(secondary, 'secondary'),
    ...accents(tertiary, 'tertiary'),
    ...accents(error, 'error'),
    inversePrimary: primary(dark ? 40 : 80),
    background: neutral(dark ? 6 : 98),
    onBackground: neutral(dark ? 90 : 10),
    surface: neutral(dark ? 6 : 98),
    onSurface: neutral(dark ? 90 : 10),
    surfaceVariant: neutralVariant(dark ? 30 : 90),
    onSurfaceVariant: neutralVariant(dark ? 80 : 30),
    inverseSurface: neutral(dark ? 90 : 20),
    inverseOnSurface: neutral(dark ? 20 : 95),
    outline: neutralVariant(dark ? 60 : 50),
    outlineVariant: neutralVariant(dark ? 30 : 80),
    scrim: '#000000',
    surfaceBright: neutral(dark ? 24 : 98),
    surfaceDim: neutral(dark ? 6 : 87),
    surfaceContainerLowest: neutral(dark ? 4 : 100),
    surfaceContainerLow: neutral(dark ? 10 : 96),
    surfaceContainer: neutral(dark ? 12 : 94),
    surfaceContainerHigh: neutral(dark ? 17 : 92),
    surfaceContainerHighest: neutral(dark ? 22 : 90),
  };
}

function cap(name) {
  return name[0].toUpperCase() + name.slice(1);
}

export function withPureBlack(scheme) {
  return { ...scheme, ...PURE_BLACK };
}

/** The scheme as CSS custom properties: `--md-sys-color-on-primary: #FFFFFF;` and so on. */
export function schemeToCss(scheme) {
  return Object.entries(scheme)
    .map(([role, hex]) => `--md-sys-color-${role.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase())}: ${hex};`)
    .join(' ');
}

/** WCAG relative luminance (0–1). */
export function luminance(hex) {
  const rgb = parseHex(hex);
  if (!rgb) return 0;
  return 0.2126 * linear(rgb[0]) + 0.7152 * linear(rgb[1]) + 0.0722 * linear(rgb[2]);
}

export function contrastRatio(a, b) {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}
