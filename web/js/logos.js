// Station logos: downloaded from their public sources in the browser (like the app does on the
// phone), laid out on a square by logo-layout.js, and kept in the browser's Cache Storage so later
// visits are instant. A logo whose host doesn't allow reading its pixels is shown as it is.
import { useEffect, useState } from '../vendor/preact-hooks.js';
import { LOGO_VERSION, argbToCss, frameWidth, layoutOf, rgbaToArgb } from './logo-layout.js';
import { luminance } from './seed-scheme.js';

const OUTPUT_PX = 384;
const ANALYSIS_MAX_PX = 512;
const MIN_SIDE_PX = 96;
// Most logos come from Wikimedia, which turns away bursts (HTTP 429): download them gently.
const PARALLEL = 2;
const SPACING_MS = 200;
const BACKOFF_MS = [3000, 10000, 30000];
const RETRY_AFTER_MS = 5 * 60 * 1000;
const CACHE_NAME = 'shidur-logos';
/** Marks a cached entry for a logo whose host doesn't share its pixels: show the original. */
const AS_IS_HEADER = 'X-Shidur-As-Is';

const results = new Map();
/** Logos already made, by the same key, so a screen that opens again shows them at once. */
const ready = new Map();
const waiting = [];
let running = 0;
let nextStart = 0;
let timer = 0;
const listeners = new Set();

/** Calls [listener] whenever another logo is ready. */
export function onLogoReady(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

const keyOf = (station) => `${station.id}|${station.logo}`;

/** { src, raw } for the station's logo, or null when it has none (keep the text artwork). */
export function loadLogo(station) {
  if (!station?.logo) return Promise.resolve(null);
  const key = keyOf(station);
  if (!results.has(key)) {
    const promise = makeLogo(station).then((logo) => {
      if (logo) {
        ready.set(key, logo);
        listeners.forEach((listener) => listener(station.id));
      } else {
        setTimeout(() => results.get(key) === promise && results.delete(key), RETRY_AFTER_MS);
      }
      return logo;
    });
    results.set(key, promise);
  }
  return results.get(key);
}

/** The logo, if it has already been made. */
export function readyLogo(station) {
  return station?.logo ? ready.get(keyOf(station)) ?? null : null;
}

/** The logo once it's ready; null while loading, when turned off, or when there's none. */
export function useLogo(station, enabled) {
  const [logo, setLogo] = useState(() => (enabled ? readyLogo(station) : null));
  useEffect(() => {
    if (!enabled || !station?.logo) {
      setLogo(null);
      return undefined;
    }
    setLogo(readyLogo(station));
    let active = true;
    loadLogo(station).then((value) => active && setLogo(value));
    return () => {
      active = false;
    };
  }, [station?.id, station?.logo, enabled]);
  return logo;
}

function enqueue(task) {
  return new Promise((resolve) => {
    waiting.push({ task, resolve });
    pump();
  });
}

function pump() {
  if (timer) return;
  while (running < PARALLEL && waiting.length) {
    const wait = nextStart - Date.now();
    if (wait > 0) {
      timer = setTimeout(() => {
        timer = 0;
        pump();
      }, wait);
      return;
    }
    nextStart = Date.now() + SPACING_MS;
    const { task, resolve } = waiting.shift();
    running++;
    task()
      .catch(() => null)
      .then((value) => {
        running--;
        resolve(value);
        pump();
      });
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * The logo's bytes; 'blocked' when its host doesn't let other sites read them (no CORS), or null
 * when it didn't download (a later screen or visit tries again).
 */
async function download(url) {
  for (let attempt = 0; ; attempt++) {
    let response;
    try {
      response = await fetch(url, { mode: 'cors', credentials: 'omit' });
    } catch {
      return navigator.onLine ? 'blocked' : null;
    }
    if (response.ok) return response.blob();
    const busy = response.status === 429 || response.status === 503;
    if (!busy || attempt >= BACKOFF_MS.length) return null;
    // The host asked to slow down: hold back every download, not just this one.
    nextStart = Math.max(nextStart, Date.now() + BACKOFF_MS[attempt]);
    await sleep(BACKOFF_MS[attempt]);
  }
}

function cacheUrl(station) {
  let hash = 0;
  for (const ch of station.logo) hash = (Math.imul(hash, 31) + ch.charCodeAt(0)) | 0;
  return new URL(`logo-cache/v${LOGO_VERSION}/${station.id}-${(hash >>> 0).toString(16)}.png`, location.href).href;
}

async function openCache() {
  try {
    return 'caches' in self ? await caches.open(CACHE_NAME) : null;
  } catch {
    return null;
  }
}

async function makeLogo(station) {
  const cache = await openCache();
  const key = cacheUrl(station);
  const cached = await cache?.match(key).catch(() => null);
  if (cached?.headers.get(AS_IS_HEADER)) return { src: station.logo, raw: true };
  if (cached) return { src: URL.createObjectURL(await cached.blob()), raw: false };

  const bytes = await enqueue(() => download(station.logo));
  if (!bytes) return null;
  if (bytes === 'blocked') {
    // The pixels can't be read, so show the picture as it is (and skip trying next time).
    try {
      const plain = await loadImage(station.logo);
      if (Math.min(plain.naturalWidth, plain.naturalHeight) < MIN_SIDE_PX) return null;
      await cache?.put(key, new Response('', { headers: { [AS_IS_HEADER]: '1' } })).catch(() => {});
      return { src: station.logo, raw: true };
    } catch {
      return null;
    }
  }
  const image = await decode(bytes);
  if (!image || Math.min(image.width, image.height) < MIN_SIDE_PX) return null;
  const blob = await normalize(image);
  if (!blob) return null;
  try {
    await cache?.put(key, new Response(blob, { headers: { 'Content-Type': 'image/png' } }));
  } catch {
    // Storage full or unavailable: the logo still shows for this visit.
  }
  return { src: URL.createObjectURL(blob), raw: false };
}

function loadImage(url) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.decoding = 'async';
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('Image failed to load'));
    image.src = url;
  });
}

/** A drawable picture with its pixel size as width and height, or null. */
async function decode(blob) {
  try {
    if ('createImageBitmap' in self) return await createImageBitmap(blob);
  } catch {
    // Older browsers: decode through an image element below.
  }
  const url = URL.createObjectURL(blob);
  try {
    const image = await loadImage(url);
    return { image, width: image.naturalWidth, height: image.naturalHeight };
  } catch {
    return null;
  } finally {
    URL.revokeObjectURL(url);
  }
}

function canvas(width, height) {
  const element = document.createElement('canvas');
  element.width = width;
  element.height = height;
  return element;
}

/** Square PNG artwork, or null for a blank picture. */
async function normalize(picture) {
  const scale = Math.min(1, ANALYSIS_MAX_PX / Math.max(picture.width, picture.height));
  const width = Math.max(1, Math.round(picture.width * scale));
  const height = Math.max(1, Math.round(picture.height * scale));
  const work = canvas(width, height);
  const workContext = work.getContext('2d', { willReadFrequently: true });
  workContext.drawImage(picture.image ?? picture, 0, 0, width, height);
  picture.close?.();
  // Some logos come with a thin border line; it shouldn't decide the background.
  const frame = frameWidth(width, height);
  const innerWidth = width - 2 * frame;
  const innerHeight = height - 2 * frame;
  const data = workContext.getImageData(frame, frame, innerWidth, innerHeight);
  const layout = layoutOf(rgbaToArgb(data.data), innerWidth, innerHeight);
  if (!layout) return null;
  if (layout.clear) {
    for (let i = 0; i < layout.clear.length; i++) if (layout.clear[i]) data.data[i * 4 + 3] = 0;
  }
  const source = canvas(innerWidth, innerHeight);
  source.getContext('2d').putImageData(data, 0, 0);

  const output = canvas(OUTPUT_PX, OUTPUT_PX);
  const context = output.getContext('2d');
  context.imageSmoothingQuality = 'high';
  if (layout.cropSquare) {
    const side = Math.min(innerWidth, innerHeight);
    context.drawImage(source, (innerWidth - side) / 2, (innerHeight - side) / 2, side, side, 0, 0, OUTPUT_PX, OUTPUT_PX);
  } else {
    context.fillStyle = argbToCss(layout.fill);
    context.fillRect(0, 0, OUTPUT_PX, OUTPUT_PX);
    const box = layout.box;
    const boxWidth = box.right - box.left;
    const boxHeight = box.bottom - box.top;
    const room = OUTPUT_PX * (1 - 2 * layout.margin);
    const fit = Math.min(room / boxWidth, room / boxHeight);
    const drawWidth = boxWidth * fit;
    const drawHeight = boxHeight * fit;
    context.drawImage(
      source,
      box.left,
      box.top,
      boxWidth,
      boxHeight,
      (OUTPUT_PX - drawWidth) / 2,
      (OUTPUT_PX - drawHeight) / 2,
      drawWidth,
      drawHeight,
    );
  }
  return new Promise((resolve) => output.toBlob(resolve, 'image/png'));
}

/** Font size of the monogram text, as a share of the artwork's size; the same steps as the app. */
export function monogramScale(text) {
  const length = [...String(text)].length;
  if (length <= 2) return 0.42;
  if (length === 3) return 0.34;
  if (length === 4) return 0.28;
  if (length === 5) return 0.24;
  return 0.2;
}

export function monogramTextColor(background) {
  return luminance(background) > 0.5 ? '#1D1B20' : '#FFFFFF';
}

const monograms = new Map();

/** The station's text artwork as a PNG address, for the lock screen and notifications. */
export function monogramUrl(station) {
  if (monograms.has(station.id)) return monograms.get(station.id);
  const size = 256;
  const element = canvas(size, size);
  const context = element.getContext('2d');
  context.fillStyle = station.color;
  context.fillRect(0, 0, size, size);
  context.fillStyle = monogramTextColor(station.color);
  context.font = `bold ${Math.round(size * monogramScale(station.shortName))}px system-ui, sans-serif`;
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.direction = /[\u0590-\u05FF]/.test(station.shortName) ? 'rtl' : 'ltr';
  context.fillText(station.shortName, size / 2, size / 2, size * 0.9);
  const url = element.toDataURL('image/png');
  monograms.set(station.id, url);
  return url;
}
