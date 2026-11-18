// The audio engine: one <audio> element, the station's streams in order (the one that worked last
// time first), hls.js for Kan's HLS streams where the browser can't play HLS itself, the lock
// screen and media keys through the Media Session API, and the sleep timer.
import { isHls } from './catalog.js';
import { rememberStream, recordRecent, store } from './store.js';

const START_TIMEOUT_MS = 15000;
const STALL_RECONNECT_MS = 12000;
const SLEEP_FADE_MS = 10000;
const SLEEP_EXTEND_MS = 10 * 60 * 1000;

/**
 * status: 'idle' (nothing loaded), 'paused', 'connecting', 'playing' or 'error'.
 * queue: the station ids that previous and next move through (the shelf or list it came from).
 */
const INITIAL = Object.freeze({ stationId: null, queue: [], status: 'idle', sleepEnd: null, streamIndex: 0 });

export function createPlayer({ getCatalog, describe }) {
  const audio = document.createElement('audio');
  audio.id = 'shidur-audio';
  audio.preload = 'none';
  document.body.append(audio);

  let state = INITIAL;
  const listeners = new Set();
  let hls = null;
  let attempt = 0;
  let tried = 0;
  let wantsToPlay = false;
  let startTimer = 0;
  let stallTimer = 0;
  let sleepTimer = 0;
  let hlsModule = null;

  const station = () => (state.stationId ? getCatalog()?.byId.get(state.stationId) ?? null : null);

  function set(patch) {
    state = { ...state, ...patch };
    document.documentElement.dataset.playback = state.status;
    updateMediaSession();
    listeners.forEach((listener) => listener(state));
  }

  function clearTimers() {
    clearTimeout(startTimer);
    clearTimeout(stallTimer);
  }

  /** Stops downloading: a paused radio stream would only fall behind the live broadcast. */
  function teardown() {
    clearTimers();
    if (hls) {
      hls.destroy();
      hls = null;
    }
    if (audio.hasAttribute('src')) {
      audio.removeAttribute('src');
      audio.load();
    }
  }

  function canPlayHlsNatively() {
    return audio.canPlayType('application/vnd.apple.mpegurl') !== '';
  }

  async function loadHls() {
    if (!hlsModule) hlsModule = import('../vendor/hls.light.min.esm.js').then((m) => m.default);
    return hlsModule;
  }

  async function load() {
    const current = station();
    if (!current) return;
    const index = state.streamIndex;
    const stream = current.streams[index];
    const token = ++attempt;
    teardown();
    audio.volume = store.get().settings.volume;
    startTimer = setTimeout(() => token === attempt && failStream(), START_TIMEOUT_MS);
    try {
      if (isHls(stream) && !canPlayHlsNatively()) {
        const Hls = await loadHls();
        if (token !== attempt) return;
        if (!Hls?.isSupported()) {
          failStream();
          return;
        }
        hls = new Hls({ enableWorker: true, lowLatencyMode: false, backBufferLength: 30 });
        hls.on(Hls.Events.ERROR, (event, data) => {
          if (data.fatal && token === attempt) failStream();
        });
        hls.attachMedia(audio);
        hls.loadSource(stream.url);
      } else {
        audio.src = stream.url;
      }
      await audio.play();
    } catch (error) {
      if (token !== attempt) return;
      if (error?.name === 'AbortError') return;
      if (error?.name === 'NotAllowedError') {
        // The browser wants a tap before sound plays (opened from a link).
        clearTimers();
        wantsToPlay = false;
        teardown();
        set({ status: 'paused' });
        return;
      }
      failStream();
    }
  }

  /** This stream failed: try the station's next one, or give up after trying them all. */
  function failStream() {
    const current = station();
    if (!current) return;
    tried++;
    if (tried < current.streams.length) {
      set({ streamIndex: (state.streamIndex + 1) % current.streams.length });
      load();
      return;
    }
    attempt++;
    teardown();
    set({ status: 'error' });
  }

  function start() {
    const current = station();
    if (!current) return;
    const remembered = store.get().workingStream[current.id];
    const index = Number.isInteger(remembered) && remembered >= 0 && remembered < current.streams.length ? remembered : 0;
    tried = 0;
    wantsToPlay = true;
    set({ status: 'connecting', streamIndex: index });
    load();
  }

  /** Reconnects to the same stream, for example after the network came back. */
  function reconnect() {
    if (!wantsToPlay || !station()) return;
    tried = 0;
    set({ status: 'connecting' });
    load();
  }

  audio.addEventListener('playing', () => {
    clearTimers();
    const current = station();
    if (!current) return;
    rememberStream(current.id, state.streamIndex);
    recordRecent(current.id);
    set({ status: 'playing' });
  });

  for (const type of ['waiting', 'stalled']) {
    audio.addEventListener(type, () => {
      if (state.status !== 'playing') return;
      set({ status: 'connecting' });
      clearTimeout(stallTimer);
      stallTimer = setTimeout(reconnect, STALL_RECONNECT_MS);
    });
  }

  audio.addEventListener('pause', () => {
    // Paused from outside (headphones unplugged, another app took the audio): stay paused.
    if (state.status !== 'playing' || !audio.paused || !audio.hasAttribute('src')) return;
    wantsToPlay = false;
    teardown();
    set({ status: 'paused' });
  });

  audio.addEventListener('ended', () => {
    // A live stream "ends" when the server drops the connection.
    if (wantsToPlay) reconnect();
  });

  audio.addEventListener('error', () => {
    if (!audio.hasAttribute('src') || hls || state.status !== 'connecting') {
      if (state.status === 'playing' && wantsToPlay) reconnect();
      return;
    }
    failStream();
  });

  addEventListener('online', () => {
    if (wantsToPlay && state.status !== 'playing') reconnect();
  });

  // Sleep timer

  function runSleepTimer() {
    clearInterval(sleepTimer);
    if (!state.sleepEnd) return;
    sleepTimer = setInterval(() => {
      const remaining = state.sleepEnd - Date.now();
      const volume = store.get().settings.volume;
      if (remaining <= 0) {
        clearInterval(sleepTimer);
        pause();
        audio.volume = volume;
        set({ sleepEnd: null });
      } else if (store.get().settings.sleepFade && remaining < SLEEP_FADE_MS) {
        audio.volume = volume * (remaining / SLEEP_FADE_MS);
      }
    }, 250);
  }

  function restoreVolume() {
    audio.volume = store.get().settings.volume;
  }

  // Media Session: lock screen, notification, headset and keyboard media keys.

  let describedId = null;
  function updateMediaSession() {
    if (!('mediaSession' in navigator)) return;
    const current = station();
    if (!current) {
      navigator.mediaSession.metadata = null;
      navigator.mediaSession.playbackState = 'none';
      describedId = null;
      return;
    }
    if (describedId !== current.id) {
      describedId = current.id;
      refreshMetadata();
    }
    navigator.mediaSession.playbackState = state.status === 'playing' || state.status === 'connecting' ? 'playing' : 'paused';
  }

  function refreshMetadata() {
    const current = station();
    if (!current || !('mediaSession' in navigator) || typeof MediaMetadata === 'undefined') return;
    const { title, artist, album, artwork } = describe(current);
    navigator.mediaSession.metadata = new MediaMetadata({ title, artist, album, artwork });
  }

  if ('mediaSession' in navigator) {
    const actions = {
      play: () => resume(),
      pause: () => pause(),
      stop: () => pause(),
      nexttrack: () => next(),
      previoustrack: () => previous(),
    };
    for (const [action, handler] of Object.entries(actions)) {
      try {
        navigator.mediaSession.setActionHandler(action, handler);
      } catch {
        // Not supported by this browser.
      }
    }
  }

  // Commands

  function play(stationId, queue = [stationId]) {
    if (!getCatalog()?.byId.has(stationId)) return;
    const unique = [...new Set(queue)].filter((id) => getCatalog().byId.has(id));
    set({ stationId, queue: unique.includes(stationId) ? unique : [stationId] });
    start();
  }

  /** Shows a station in the player without playing it (a shared link, or last time's station). */
  function select(stationId, queue = [stationId]) {
    if (!getCatalog()?.byId.has(stationId)) return;
    attempt++;
    wantsToPlay = false;
    teardown();
    set({ stationId, queue: queue.includes(stationId) ? queue : [stationId], status: 'paused' });
  }

  function pause() {
    if (!station()) return;
    attempt++;
    wantsToPlay = false;
    teardown();
    set({ status: 'paused' });
  }

  function resume() {
    if (station()) start();
  }

  function toggle() {
    if (state.status === 'playing' || state.status === 'connecting') pause();
    else resume();
  }

  function step(delta) {
    const { queue, stationId } = state;
    if (queue.length < 2) return;
    const index = queue.indexOf(stationId);
    play(queue[(index + delta + queue.length) % queue.length], queue);
  }

  const next = () => step(1);
  const previous = () => step(-1);

  function setSleepTimer(minutes) {
    restoreVolume();
    set({ sleepEnd: Date.now() + minutes * 60 * 1000 });
    runSleepTimer();
  }

  function extendSleepTimer() {
    restoreVolume();
    set({ sleepEnd: Math.max(state.sleepEnd ?? Date.now(), Date.now()) + SLEEP_EXTEND_MS });
    runSleepTimer();
  }

  function cancelSleepTimer() {
    clearInterval(sleepTimer);
    restoreVolume();
    set({ sleepEnd: null });
  }

  function setVolume(volume) {
    audio.volume = volume;
  }

  return {
    audio,
    get: () => state,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    play,
    select,
    pause,
    resume,
    toggle,
    next,
    previous,
    setSleepTimer,
    extendSleepTimer,
    cancelSleepTimer,
    setVolume,
    /** The language or artwork changed: update the lock screen. */
    refreshMetadata,
  };
}

const warmedOrigins = new Set();

/**
 * Opens the connection to a station's server when a finger or pointer lands on it, so the stream
 * starts sooner after the tap.
 */
export function warmUp(station) {
  const stream = station?.streams[0];
  if (!stream) return;
  let origin;
  try {
    origin = new URL(stream.url).origin;
  } catch {
    return;
  }
  if (warmedOrigins.has(origin)) return;
  warmedOrigins.add(origin);
  const link = document.createElement('link');
  link.rel = 'preconnect';
  link.href = origin;
  // HLS segments are fetched with CORS, plain streams without it; each needs its own connection.
  if (isHls(stream)) link.crossOrigin = 'anonymous';
  document.head.append(link);
}
