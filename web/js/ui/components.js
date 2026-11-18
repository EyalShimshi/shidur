// Shared pieces of the UI: artwork, tiles, rows, shelves, settings rows, bottom sheets and swipes.
import { createContext, createPortal, h } from '../../vendor/preact.js';
import { useContext, useEffect, useRef } from '../../vendor/preact-hooks.js';
import htm from '../../vendor/htm.js';
import { stationName, stationSubtitle } from '../catalog.js';
import { monogramScale, monogramTextColor, useLogo } from '../logos.js';
import { warmUp } from '../player.js';
import { toggleFavorite } from '../store.js';
import { Icon } from './icons.js';

export const html = htm.bind(h);

/** What every screen needs: words, settings, the station list, the player and the actions. */
export const AppContext = createContext(null);
export const useApp = () => useContext(AppContext);

/** The station's artwork: its logo once loaded, otherwise its short name on its brand color. */
export function Artwork({ station, size, corner = 0.28, class: className = '' }) {
  const { settings } = useApp();
  const logo = useLogo(station, settings.logos);
  const style = { '--art-corner': corner };
  if (size != null) style['--art-size'] = typeof size === 'number' ? `${size}px` : size;
  if (logo) {
    return html`<span class=${`art${logo.raw ? ' art--raw' : ''} ${className}`} style=${style}>
      <img src=${logo.src} alt="" draggable="false" decoding="async" referrerpolicy="no-referrer" />
    </span>`;
  }
  return html`<span class=${`art ${className}`} style=${style}>
    <span
      class="art__mono"
      dir="auto"
      style=${{
        background: station.color,
        color: monogramTextColor(station.color),
        fontSize: `calc(var(--art-size, 48px) * ${monogramScale(station.shortName)})`,
      }}
      >${station.shortName}</span
    >
  </span>`;
}

/** "Galgalatz, 91.8 FM, now playing": what a screen reader says for a station. */
export function stationLabel(station, app) {
  const { t, lang, playerState } = app;
  const parts = [stationName(station, lang), stationSubtitle(station, t)];
  if (playerState.stationId === station.id && playerState.status === 'playing') parts.push(t('now_playing_indicator'));
  return parts.join(', ');
}

export function StationTile({ station, queue, large = false }) {
  const app = useApp();
  const { lang, playerState, actions } = app;
  const current = playerState.stationId === station.id;
  return html`<li>
    <button
      class=${`tile state${large ? ' tile--large' : ''}`}
      type="button"
      aria-label=${stationLabel(station, app)}
      onPointerDown=${() => warmUp(station)}
      onClick=${() => actions.play(station, queue)}
    >
      <span class="tile__art">
        <${Artwork} station=${station} size=${large ? 112 : 72} />
        ${current && html`<span class="badge"><${Icon} name="graphicEq" size=${14} /></span>`}
      </span>
      <span class="tile__label label-medium clamp-2" aria-hidden="true">${stationName(station, lang)}</span>
    </button>
  </li>`;
}

export function FavoriteButton({ station }) {
  const { t, favorites } = useApp();
  const on = favorites.includes(station.id);
  const label = t(on ? 'favorite_remove' : 'favorite_add');
  return html`<button
    class="icon-btn state"
    type="button"
    data-on=${String(on)}
    aria-label=${label}
    title=${label}
    onClick=${() => toggleFavorite(station.id)}
  >
    <${Icon} name=${on ? 'favorite' : 'favoriteBorder'} />
  </button>`;
}

export function StationRow({ station, queue, trailing }) {
  const app = useApp();
  const { t, lang, playerState, actions } = app;
  const current = playerState.stationId === station.id;
  const playing = current && playerState.status === 'playing';
  return html`<li class=${`row${current ? ' is-current' : ''}`}>
    <button
      class="row__main state"
      type="button"
      aria-label=${stationLabel(station, app)}
      onPointerDown=${() => warmUp(station)}
      onClick=${() => actions.play(station, queue)}
    >
      <${Artwork} station=${station} size=${48} />
      <span class="row__text" aria-hidden="true">
        <span class="body-large ellipsis">${stationName(station, lang)}</span>
        <span class="body-medium muted ellipsis">${stationSubtitle(station, t)}</span>
      </span>
    </button>
    ${playing && html`<${Icon} name="graphicEq" class="row__eq" />`}
    ${trailing ?? html`<${FavoriteButton} station=${station} />`}
  </li>`;
}

export function StationList({ stations, trailing }) {
  return html`<ul class="list">
    ${stations.map(
      (station) =>
        html`<${StationRow} key=${station.id} station=${station} queue=${stations} trailing=${trailing?.(station)} />`,
    )}
  </ul>`;
}

/** A shelf's title (with an arrow to its full list) above a sideways-scrolling row of big tiles. */
export function Shelf({ title, href, stations }) {
  return html`<section class="shelf">
    <div class="shelf__header">
      <h2 class="shelf__title">
        ${href
          ? html`<a class="shelf__link state" href=${href}>
              <span class="ellipsis">${title}</span>
              <${Icon} name="arrowForward" size=${22} />
            </a>`
          : title}
      </h2>
    </div>
    <ul class="shelf__row">
      ${stations.map((station) => html`<${StationTile} key=${station.id} station=${station} queue=${stations} large />`)}
    </ul>
  </section>`;
}

export function Group({ label, children }) {
  return html`<section>
    <h2 class="group-label title-small">${label}</h2>
    <div class="group">${children}</div>
  </section>`;
}

export function SwitchRow({ title, summary, checked, disabled = false, onChange }) {
  return html`<button
    class="setting state"
    type="button"
    role="switch"
    aria-checked=${String(checked)}
    aria-disabled=${String(disabled)}
    onClick=${() => !disabled && onChange(!checked)}
  >
    <span class="setting__text">
      <span class="body-large">${title}</span>
      ${summary && html`<span class="body-medium muted">${summary}</span>`}
    </span>
    <span class="switch" aria-hidden="true"></span>
  </button>`;
}

export function LinkRow({ title, summary, href, onClick }) {
  const content = html`<span class="setting__text">
      <span class="body-large">${title}</span>
      ${summary && html`<span class="body-medium muted">${summary}</span>`}
    </span>
    ${href && html`<${Icon} name="openInNew" size=${20} class="muted" />`}`;
  return href
    ? html`<a class="setting state" href=${href} target="_blank" rel="noopener">${content}</a>`
    : html`<button class="setting state" type="button" onClick=${onClick}>${content}</button>`;
}

export function Segmented({ label, options, value, onChange }) {
  return html`<div class="segmented" role="group" aria-label=${label}>
    ${options.map(
      (option) => html`<button
        class="state"
        type="button"
        aria-pressed=${String(option.value === value)}
        onClick=${() => onChange(option.value)}
      >
        ${option.value === value && html`<${Icon} name="check" size=${18} />`}
        <span>${option.label}</span>
      </button>`,
    )}
  </div>`;
}

/**
 * A bottom sheet over everything, closed by the scrim, Escape or the back gesture (the app pushes a
 * history entry for it). Focus moves into it and back.
 */
export function ModalSheet({ labelledBy, style, onClose, children }) {
  const sheet = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    sheet.current?.querySelector('button, a, input, select')?.focus({ preventScroll: true });
    const onKey = (event) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
      }
    };
    addEventListener('keydown', onKey, true);
    return () => {
      removeEventListener('keydown', onKey, true);
      previous?.focus?.({ preventScroll: true });
    };
  }, []);
  return createPortal(
    html`<div style=${style}>
      <div class="scrim" onClick=${onClose}></div>
      <div class="modal-sheet" role="dialog" aria-modal="true" aria-labelledby=${labelledBy} ref=${sheet}>
        <div class="modal-sheet__handle" aria-hidden="true"></div>
        ${children}
      </div>
    </div>`,
    document.body,
  );
}

/**
 * Horizontal (and optionally upward) swipes on [ref]'s element. A swipe cancels the click it would
 * otherwise cause. [onSwipe] gets 'next', 'previous' or 'up'; next follows the reading direction.
 */
export function useSwipe(ref, onSwipe, { up = false } = {}) {
  const handler = useRef(onSwipe);
  handler.current = onSwipe;
  useEffect(() => {
    const element = ref.current;
    if (!element) return undefined;
    let start = null;
    let swiped = false;
    const down = (event) => {
      if (event.pointerType === 'mouse' && event.button !== 0) return;
      start = { x: event.clientX, y: event.clientY, time: performance.now() };
      swiped = false;
    };
    const upHandler = (event) => {
      if (!start) return;
      const dx = event.clientX - start.x;
      const dy = event.clientY - start.y;
      const elapsed = Math.max(1, performance.now() - start.time);
      start = null;
      const rtl = getComputedStyle(element).direction === 'rtl';
      if (Math.abs(dx) > 56 && Math.abs(dx) > Math.abs(dy) * 1.5) {
        swiped = true;
        const towardStart = rtl ? dx > 0 : dx < 0;
        handler.current(towardStart ? 'next' : 'previous');
      } else if (up && dy < -40 && Math.abs(dy) > Math.abs(dx) && -dy / elapsed > 0.25) {
        swiped = true;
        handler.current('up');
      }
    };
    const cancel = () => {
      start = null;
    };
    const click = (event) => {
      if (!swiped) return;
      swiped = false;
      event.stopPropagation();
      event.preventDefault();
    };
    element.addEventListener('pointerdown', down);
    element.addEventListener('pointerup', upHandler);
    element.addEventListener('pointercancel', cancel);
    element.addEventListener('click', click, true);
    return () => {
      element.removeEventListener('pointerdown', down);
      element.removeEventListener('pointerup', upHandler);
      element.removeEventListener('pointercancel', cancel);
      element.removeEventListener('click', click, true);
    };
  }, []);
}

/** "4:05" or "1:02:05". */
export function formatRemaining(ms) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = String(total % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${seconds}` : `${minutes}:${seconds}`;
}
