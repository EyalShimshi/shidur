// The player: the mini player above the navigation bar, the full player (a sheet on phones, a side
// pane on wide screens), and its sleep timer and station info sheets. Colors come from the station.
import { useEffect, useRef, useState } from '../../vendor/preact-hooks.js';
import { formatFrequency, stationName, stationSubtitle } from '../catalog.js';
import { copyText, reportProblemUrl, shareStation } from '../actions.js';
import { stationLink } from '../router.js';
import { setSetting } from '../store.js';
import { Artwork, FavoriteButton, ModalSheet, formatRemaining, html, useApp, useSwipe } from './components.js';
import { Icon } from './icons.js';

const SLEEP_PRESETS = [15, 30, 45, 60, 90, 120];

/** The status line: an error, connecting, or a hint to tap play; empty while playing. */
function statusText(playerState, t) {
  switch (playerState.status) {
    case 'error':
      return t('player_error_short');
    case 'connecting':
      return t('player_connecting');
    case 'paused':
      return t('player_tap_to_play');
    default:
      return '';
  }
}

function playLabel(playerState, t) {
  if (playerState.status === 'error') return t('action_retry');
  return playerState.status === 'playing' || playerState.status === 'connecting' ? t('action_pause') : t('action_play');
}

function PlayIcon({ playerState, size }) {
  if (playerState.status === 'error') return html`<${Icon} name="refresh" size=${size} />`;
  if (playerState.status === 'connecting') {
    return html`<span class="spinner" style=${{ '--spinner-size': `${Math.round(size * 0.9)}px` }} aria-hidden="true"></span>`;
  }
  if (playerState.status === 'playing') return html`<${Icon} name="pause" size=${size} />`;
  return html`<${Icon} name="playArrow" size=${size} />`;
}

/** Re-renders every second while [active]. */
function useTick(active) {
  const [, setNow] = useState(0);
  useEffect(() => {
    if (!active) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [active]);
}

export function MiniPlayer({ station, style, onOpen }) {
  const { t, lang, playerState, player } = useApp();
  const ref = useRef(null);
  useSwipe(
    ref,
    (direction) => {
      if (direction === 'up') onOpen();
      else if (direction === 'next') player.next();
      else player.previous();
    },
    { up: true },
  );
  const status = playerState.status === 'paused' ? '' : statusText(playerState, t);
  return html`<div class="mini" style=${style} ref=${ref}>
    <button class="mini__open state" type="button" aria-label=${`${t('action_open_player')}: ${stationName(station, lang)}`} onClick=${onOpen}></button>
    <span class="mini__art"><${Artwork} station=${station} size=${44} /></span>
    <span class="mini__text" aria-hidden="true">
      <span class="title-medium ellipsis">${stationName(station, lang)}</span>
      <span class="body-medium ellipsis">${status || stationSubtitle(station, t)}</span>
    </span>
    <button class="icon-btn play-mini state" type="button" aria-label=${playLabel(playerState, t)} onClick=${player.toggle}>
      <${PlayIcon} playerState=${playerState} size=${28} />
    </button>
  </div>`;
}

export function PlayerHero({ station, inPane = false, onClose, onOpenSheet }) {
  const { t, lang, playerState, player, settings } = useApp();
  const art = useRef(null);
  useSwipe(art, (direction) => (direction === 'next' ? player.next() : player.previous()));
  useTick(Boolean(playerState.sleepEnd));
  const [shared, setShared] = useState(null);
  const active = playerState.status === 'playing' || playerState.status === 'connecting';
  const canStep = playerState.queue.length > 1;
  const share = async () => {
    const result = await shareStation(station, lang, t);
    if (result === 'copied') setShared(Date.now());
  };
  useEffect(() => {
    if (!shared) return undefined;
    const timer = setTimeout(() => setShared(null), 2500);
    return () => clearTimeout(timer);
  }, [shared]);
  return html`<div class="hero" data-active=${String(playerState.status === 'playing')}>
    <div class="hero__top" data-drag-handle>
      ${inPane
        ? html`<span></span>`
        : html`<button class="icon-btn state" type="button" aria-label=${t('action_close_player')} title=${t('action_close_player')} onClick=${onClose}>
            <${Icon} name="keyboardArrowDown" />
          </button>`}
      <button class="icon-btn state" type="button" aria-label=${t('action_station_info')} title=${t('action_station_info')} onClick=${() => onOpenSheet('info')}>
        <${Icon} name="info" />
      </button>
    </div>
    <div class="hero__art" ref=${art} data-drag-handle>
      <${Artwork} station=${station} corner=${0.22} />
    </div>
    <h2 class="hero__title" id=${inPane ? 'pane-title' : 'player-title'}>${stationName(station, lang)}</h2>
    <div class="hero__sub body-medium">
      <span class="tabular">${stationSubtitle(station, t)}</span>
      ${playerState.status === 'playing' && html`<span class="live">● ${t('player_live')}</span>`}
    </div>
    <p class="hero__status title-medium clamp-2" role="status">${shared ? t('link_copied') : statusText(playerState, t)}</p>
    <div class="controls">
      <button class="icon-btn state" type="button" aria-label=${t('action_previous_station')} title=${t('action_previous_station')}
        disabled=${!canStep} onClick=${player.previous}>
        <${Icon} name="skipPrevious" size=${32} />
      </button>
      <button class="play-btn state" type="button" data-active=${String(active)} aria-label=${playLabel(playerState, t)} onClick=${player.toggle}>
        <${PlayIcon} playerState=${playerState} size=${40} />
      </button>
      <button class="icon-btn state" type="button" aria-label=${t('action_next_station')} title=${t('action_next_station')}
        disabled=${!canStep} onClick=${player.next}>
        <${Icon} name="skipNext" size=${32} />
      </button>
    </div>
    <div class="toolbar">
      <${FavoriteButton} station=${station} />
      <button class="icon-btn state" type="button" data-on=${String(Boolean(playerState.sleepEnd))}
        aria-label=${t('sleep_timer_title')} title=${t('sleep_timer_title')} onClick=${() => onOpenSheet('sleep')}>
        <${Icon} name="bedtime" />
      </button>
      <button class="icon-btn state" type="button" aria-label=${t('action_share')} title=${t('action_share')} onClick=${share}>
        <${Icon} name="share" />
      </button>
    </div>
    ${playerState.sleepEnd &&
    html`<p class="sleep-left label-large tabular">${t('sleep_stops_in', formatRemaining(playerState.sleepEnd - Date.now()))}</p>`}
    <label class="volume">
      <${Icon} name="volumeUp" size=${20} />
      <input
        class="range"
        type="range"
        min="0"
        max="1"
        step="0.02"
        aria-label=${t('volume')}
        value=${settings.volume}
        onInput=${(event) => {
          const volume = Number(event.currentTarget.value);
          player.setVolume(volume);
          setSetting('volume', volume);
        }}
      />
    </label>
    ${playerState.status === 'error' &&
    html`<div class="error-card" role="alert">
      <p class="body-medium">${t('player_error')}</p>
      <div class="card__actions">
        <button class="btn btn--text state" type="button" onClick=${player.resume}>${t('action_retry')}</button>
        <a class="btn btn--text state" href=${reportProblemUrl(station, playerState.streamIndex)} target="_blank" rel="noopener">
          ${t('action_report')}
        </a>
      </div>
    </div>`}
  </div>`;
}

/**
 * The full player on phones: slides up over everything. Dragging its top or its artwork down
 * closes it, like the app's player sheet.
 */
export function PlayerSheet({ open, style, label, onClose, children }) {
  const ref = useRef(null);
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const sheet = ref.current;
    let start = null;
    let dragging = false;
    let lastY = 0;
    let lastTime = 0;
    let velocity = 0;
    let suppressClick = false;
    const down = (event) => {
      if (!event.target.closest('[data-drag-handle]') || sheet.scrollTop > 0) return;
      if (event.pointerType === 'mouse' && event.button !== 0) return;
      start = { x: event.clientX, y: event.clientY };
      dragging = false;
      lastY = event.clientY;
      lastTime = performance.now();
      velocity = 0;
    };
    const move = (event) => {
      if (!start) return;
      const dx = event.clientX - start.x;
      const dy = event.clientY - start.y;
      if (!dragging) {
        if (dy > 10 && dy > Math.abs(dx) * 1.2) {
          dragging = true;
          sheet.classList.add('is-dragging');
          sheet.setPointerCapture?.(event.pointerId);
        } else if (Math.abs(dx) > 10 || dy < -10) {
          start = null;
          return;
        } else {
          return;
        }
      }
      const now = performance.now();
      velocity = (event.clientY - lastY) / Math.max(1, now - lastTime);
      lastY = event.clientY;
      lastTime = now;
      sheet.style.transform = `translateY(${Math.max(0, dy)}px)`;
    };
    const up = (event) => {
      if (!start) return;
      const dy = event.clientY - start.y;
      start = null;
      if (!dragging) return;
      dragging = false;
      suppressClick = true;
      sheet.classList.remove('is-dragging');
      sheet.style.transform = '';
      if (dy > sheet.clientHeight * 0.25 || velocity > 1.2) close.current();
    };
    const click = (event) => {
      if (!suppressClick) return;
      suppressClick = false;
      event.stopPropagation();
      event.preventDefault();
    };
    sheet.addEventListener('pointerdown', down);
    sheet.addEventListener('pointermove', move);
    sheet.addEventListener('pointerup', up);
    sheet.addEventListener('pointercancel', up);
    sheet.addEventListener('click', click, true);
    return () => {
      sheet.removeEventListener('pointerdown', down);
      sheet.removeEventListener('pointermove', move);
      sheet.removeEventListener('pointerup', up);
      sheet.removeEventListener('pointercancel', up);
      sheet.removeEventListener('click', click, true);
    };
  }, []);
  useEffect(() => {
    if (open) ref.current?.querySelector('.hero__top button')?.focus({ preventScroll: true });
    else ref.current?.scrollTo(0, 0);
  }, [open]);
  return html`<div
    class="player-sheet"
    data-open=${String(open)}
    role="dialog"
    aria-modal="true"
    aria-label=${label}
    aria-hidden=${String(!open)}
    inert=${!open}
    style=${style}
    ref=${ref}
  >
    ${children}
  </div>`;
}

export function SleepTimerSheet({ style, onClose }) {
  const { t, playerState, player } = useApp();
  useTick(Boolean(playerState.sleepEnd));
  return html`<${ModalSheet} labelledBy="sleep-title" style=${style} onClose=${onClose}>
    <h2 class="title-large" id="sleep-title">${t('sleep_timer_title')}</h2>
    ${playerState.sleepEnd &&
    html`<p class="headline-small tabular" style=${{ margin: '8px 0' }}>
        ${t('sleep_stops_in', formatRemaining(playerState.sleepEnd - Date.now()))}
      </p>
      <div class="card__actions" style=${{ marginTop: 0 }}>
        <button class="btn btn--outlined state" type="button" onClick=${player.extendSleepTimer}>${t('sleep_extend')}</button>
        <button class="btn btn--text state" type="button" onClick=${() => {
          player.cancelSleepTimer();
          onClose();
        }}>${t('sleep_cancel')}</button>
      </div>`}
    <div class="swatches" style=${{ marginTop: '16px' }}>
      ${SLEEP_PRESETS.map(
        (minutes) => html`<button class="chip state" type="button" onClick=${() => {
          player.setSleepTimer(minutes);
          onClose();
        }}>${t('sleep_minutes', minutes)}</button>`,
      )}
    </div>
  <//>`;
}

export function StationInfoSheet({ station, style, onClose }) {
  const { t, lang, playerState } = useApp();
  const [copied, setCopied] = useState(false);
  return html`<${ModalSheet} labelledBy="info-title" style=${style} onClose=${onClose}>
    <div class="modal-sheet__header">
      <${Artwork} station=${station} size=${56} />
      <div>
        <h2 class="title-large" id="info-title">${stationName(station, lang)}</h2>
        <p class="body-medium muted" style=${{ margin: 0 }}>${stationSubtitle(station, t)}</p>
      </div>
    </div>
    ${station.frequencies.length > 0 &&
    html`<p class="label-large" style=${{ margin: '16px 0 0' }}>${t('info_frequencies')}</p>
      <p class="body-medium tabular" style=${{ margin: '4px 0 0' }}>${station.frequencies.map(formatFrequency).join(' · ')}</p>`}
    <div class="sheet-actions">
      ${station.website &&
      html`<a class="btn btn--text state" href=${station.website} target="_blank" rel="noopener">
        ${t('info_website')}<${Icon} name="openInNew" size=${18} />
      </a>`}
      <button class="btn btn--text state" type="button" onClick=${async () => setCopied(await copyText(stationLink(station.id)))}>
        <${Icon} name=${copied ? 'check' : 'contentCopy'} size=${18} />${copied ? t('link_copied') : t('info_copy_link')}
      </button>
      <a class="btn btn--text state" href=${reportProblemUrl(station, playerState.streamIndex)} target="_blank" rel="noopener">
        ${t('action_report')}
      </a>
    </div>
    <hr class="divider" />
    <p class="body-small muted" style=${{ margin: 0 }}>${t('info_disclaimer')}</p>
  <//>`;
}
