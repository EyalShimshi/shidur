// The app's state: settings, favorites and recently played, kept in this browser only
// (localStorage). Nothing leaves the device.
import { useEffect, useRef, useState } from '../vendor/preact-hooks.js';

const STORAGE_KEY = 'shidur:v1';
export const MAX_RECENTS = 20;

export const DEFAULT_SETTINGS = Object.freeze({
  theme: 'system',
  pureBlack: false,
  accent: '#6750A4',
  stationTint: true,
  gradient: true,
  animations: true,
  logos: true,
  language: 'system',
  sleepFade: true,
  volume: 1,
});

const DEFAULT_STATE = Object.freeze({
  settings: DEFAULT_SETTINGS,
  favorites: [],
  recents: [],
  /** The stream index that last worked, per station. */
  workingStream: {},
  bannerDismissed: false,
});

function read() {
  try {
    const data = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    if (!data || typeof data !== 'object') return DEFAULT_STATE;
    return {
      ...DEFAULT_STATE,
      ...data,
      settings: { ...DEFAULT_SETTINGS, ...data.settings },
      favorites: Array.isArray(data.favorites) ? data.favorites.filter((id) => typeof id === 'string') : [],
      recents: Array.isArray(data.recents) ? data.recents.filter((id) => typeof id === 'string') : [],
      workingStream: data.workingStream && typeof data.workingStream === 'object' ? data.workingStream : {},
    };
  } catch {
    return DEFAULT_STATE;
  }
}

function createStore() {
  let state = read();
  let saveQueued = false;
  const listeners = new Set();
  const notify = () => listeners.forEach((listener) => listener(state));
  const save = () => {
    saveQueued = false;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Private browsing or a full storage: the app keeps working for this visit.
    }
  };
  // Another tab changed favorites or settings.
  addEventListener('storage', (event) => {
    if (event.key !== STORAGE_KEY) return;
    state = read();
    notify();
  });
  return {
    get: () => state,
    update(change) {
      const next = change(state);
      if (next === state) return;
      state = next;
      notify();
      if (!saveQueued) {
        saveQueued = true;
        queueMicrotask(save);
      }
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

export const store = createStore();

/** The part of the state [select] picks; re-renders when it changes. */
export function useStore(select) {
  const selectRef = useRef(select);
  selectRef.current = select;
  const [value, setValue] = useState(() => select(store.get()));
  useEffect(() => {
    setValue(selectRef.current(store.get()));
    return store.subscribe((state) => setValue(selectRef.current(state)));
  }, []);
  return value;
}

export function setSetting(key, value) {
  store.update((s) => (s.settings[key] === value ? s : { ...s, settings: { ...s.settings, [key]: value } }));
}

export function toggleFavorite(id) {
  store.update((s) => ({
    ...s,
    favorites: s.favorites.includes(id) ? s.favorites.filter((f) => f !== id) : [...s.favorites, id],
  }));
}

export function moveFavorite(id, delta) {
  store.update((s) => {
    const list = [...s.favorites];
    const from = list.indexOf(id);
    const to = from + delta;
    if (from < 0 || to < 0 || to >= list.length) return s;
    list.splice(to, 0, list.splice(from, 1)[0]);
    return { ...s, favorites: list };
  });
}

export function recordRecent(id) {
  store.update((s) =>
    s.recents[0] === id ? s : { ...s, recents: [id, ...s.recents.filter((r) => r !== id)].slice(0, MAX_RECENTS) },
  );
}

export function clearRecents() {
  store.update((s) => (s.recents.length ? { ...s, recents: [] } : s));
}

export function rememberStream(stationId, index) {
  store.update((s) =>
    s.workingStream[stationId] === index ? s : { ...s, workingStream: { ...s.workingStream, [stationId]: index } },
  );
}

export function dismissBanner() {
  store.update((s) => ({ ...s, bannerDismissed: true }));
}
