// Colors: the scheme generated from the accent (or a station's color) as CSS custom properties.
import { useEffect, useState } from '../vendor/preact-hooks.js';
import { colorScheme, withPureBlack } from './seed-scheme.js';

const darkQuery = matchMedia('(prefers-color-scheme: dark)');
const reducedMotionQuery = matchMedia('(prefers-reduced-motion: reduce)');
const schemes = new Map();

export function useMediaQuery(query) {
  const [matches, setMatches] = useState(() => matchMedia(query).matches);
  useEffect(() => {
    const list = matchMedia(query);
    const onChange = () => setMatches(list.matches);
    onChange();
    list.addEventListener('change', onChange);
    return () => list.removeEventListener('change', onChange);
  }, [query]);
  return matches;
}

export function isDark(settings) {
  return settings.theme === 'dark' || (settings.theme === 'system' && darkQuery.matches);
}

export function schemeFor(seed, dark, pureBlack) {
  const key = `${seed}|${dark}|${pureBlack}`;
  if (!schemes.has(key)) {
    const scheme = colorScheme(seed, dark);
    schemes.set(key, dark && pureBlack ? withPureBlack(scheme) : scheme);
  }
  return schemes.get(key);
}

/** { '--md-sys-color-primary': '#…', … } for an element's style. */
export function schemeStyle(scheme) {
  const style = {};
  for (const [role, value] of Object.entries(scheme)) {
    style[`--md-sys-color-${role.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase())}`] = value;
  }
  return style;
}

export function stationScheme(station, settings) {
  const dark = isDark(settings);
  return schemeFor(settings.stationTint ? station.color : settings.accent, dark, settings.pureBlack);
}

/** Applies the app's colors and appearance settings to the whole page. */
export function applyTheme(settings) {
  const dark = isDark(settings);
  const scheme = schemeFor(settings.accent, dark, settings.pureBlack);
  const root = document.documentElement;
  for (const [name, value] of Object.entries(schemeStyle(scheme))) root.style.setProperty(name, value);
  root.style.colorScheme = dark ? 'dark' : 'light';
  root.dataset.theme = dark ? 'dark' : 'light';
  root.dataset.gradient = String(settings.gradient && !(dark && settings.pureBlack));
  root.dataset.motion = String(settings.animations && !reducedMotionQuery.matches);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', scheme.surface);
}

/** Calls [listener] when the system switches between light and dark, or reduced motion changes. */
export function onSystemAppearanceChange(listener) {
  darkQuery.addEventListener('change', listener);
  reducedMotionQuery.addEventListener('change', listener);
  return () => {
    darkQuery.removeEventListener('change', listener);
    reducedMotionQuery.removeEventListener('change', listener);
  };
}
