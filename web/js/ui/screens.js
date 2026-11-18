// The screens: Home, Browse, Favorites, Search and Settings.
import { useEffect, useMemo, useRef, useState } from '../../vendor/preact-hooks.js';
import { collectionName, existing, stationName, stationsIn } from '../catalog.js';
import { ANDROID_URL, BUILD, PRIVACY_URL, REPOSITORY_URL, suggestStationUrl } from '../actions.js';
import { goBack } from '../router.js';
import { search } from '../search.js';
import { ACCENT_PRESETS } from '../seed-scheme.js';
import { clearRecents, dismissBanner, moveFavorite, setSetting, toggleFavorite } from '../store.js';
import {
  Group,
  LinkRow,
  Segmented,
  Shelf,
  StationList,
  SwitchRow,
  html,
  useApp,
} from './components.js';
import { Icon } from './icons.js';

const byId = (catalog, ids) => existing(catalog, ids).map((id) => catalog.byId.get(id));

function isAndroidBrowser() {
  return /Android/i.test(navigator.userAgent) && !matchMedia('(display-mode: standalone)').matches;
}

function AndroidCard() {
  const { t } = useApp();
  return html`<aside class="card card--android" aria-labelledby="android-title">
    <${Icon} name="phoneAndroid" />
    <div>
      <h2 class="card__title title-medium" id="android-title">${t('app_banner_title')}</h2>
      <p class="card__body body-medium">${t('app_banner_body')}</p>
      <div class="card__actions">
        <a class="btn btn--filled state" href=${ANDROID_URL} target="_blank" rel="noopener">${t('action_get_app')}</a>
        <button class="btn btn--text state" type="button" onClick=${dismissBanner}>${t('action_not_now')}</button>
      </div>
    </div>
  </aside>`;
}

function EmptyFavorites() {
  const { t } = useApp();
  return html`<div class="card">
    <p class="card__body body-medium">${t('favorites_empty_body')}</p>
    <div class="card__actions">
      <a class="btn btn--filled state" href="#/browse">${t('action_browse_stations')}</a>
    </div>
  </div>`;
}

export function HomeView() {
  const { t, lang, catalog, favorites, recents, bannerDismissed } = useApp();
  const favoriteStations = byId(catalog, favorites);
  const recentStations = byId(catalog, recents);
  const shelves = useMemo(
    () =>
      catalog.collections
        .map((collection) => ({ collection, stations: stationsIn(catalog, collection.id) }))
        .filter((shelf) => shelf.stations.length > 0),
    [catalog],
  );
  return html`<div class="screen">
    <h1 class="visually-hidden">${t('app_name')}</h1>
    <div class="home-top">
      <a class="search-field state" href="#/search">
        <${Icon} name="search" />
        <span class="body-large ellipsis">${t('search_hint')}</span>
      </a>
      <a class="icon-btn state" href="#/settings" aria-label=${t('settings_title')} title=${t('settings_title')}>
        <${Icon} name="settings" />
      </a>
    </div>
    ${!bannerDismissed && isAndroidBrowser() && html`<${AndroidCard} />`}
    ${favoriteStations.length
      ? html`<${Shelf} title=${t('section_favorites')} href="#/favorites" stations=${favoriteStations} />`
      : html`<section>
          <div class="shelf__header"><h2 class="shelf__title">${t('section_favorites')}</h2></div>
          <${EmptyFavorites} />
        </section>`}
    ${recentStations.length > 0 && html`<${Shelf} title=${t('section_recent')} stations=${recentStations} />`}
    ${shelves.map(
      ({ collection, stations }) =>
        html`<${Shelf}
          key=${collection.id}
          title=${collectionName(collection, lang)}
          href=${`#/browse/${collection.id}`}
          stations=${stations}
        />`,
    )}
    <div class="center">
      <a class="btn btn--tonal state" href="#/browse">${t('home_all_stations')}</a>
    </div>
  </div>`;
}

const SORTS = {
  recommended: () => 0,
  name: (lang) => {
    const collator = new Intl.Collator(lang);
    return (a, b) => collator.compare(stationName(a, lang), stationName(b, lang));
  },
  frequency: () => {
    const frequency = (s) => s.primaryFrequency ?? (s.frequencies.length ? Math.min(...s.frequencies) : Infinity);
    return (a, b) => frequency(a) - frequency(b);
  },
};

export function BrowseView({ collection }) {
  const { t, lang, catalog } = useApp();
  const [sort, setSort] = useState('recommended');
  const known = collection && catalog.collections.some((c) => c.id === collection) ? collection : null;
  const stations = useMemo(() => {
    const list = [...stationsIn(catalog, known)];
    return sort === 'recommended' ? list : list.sort(SORTS[sort](lang));
  }, [catalog, known, sort, lang]);
  return html`<div class="screen screen--narrow">
    <header class="top-bar">
      <h1 class="top-bar__title headline-small">${t('nav_browse')}</h1>
      <label class="chip state">
        <${Icon} name="sort" size=${18} />
        <select aria-label=${t('action_sort')} value=${sort} onChange=${(event) => setSort(event.currentTarget.value)}>
          <option value="recommended">${t('sort_recommended')}</option>
          <option value="name">${t('sort_name')}</option>
          <option value="frequency">${t('sort_frequency')}</option>
        </select>
      </label>
    </header>
    <nav class="chips" aria-label=${t('nav_browse')}>
      <a class="chip state" href="#/browse" aria-current=${known ? undefined : 'page'}>
        ${!known && html`<${Icon} name="check" size=${18} />`}${t('collection_all')}
      </a>
      ${catalog.collections.map(
        (c) => html`<a key=${c.id} class="chip state" href=${`#/browse/${c.id}`} aria-current=${known === c.id ? 'page' : undefined}>
          ${known === c.id && html`<${Icon} name="check" size=${18} />`}${collectionName(c, lang)}
        </a>`,
      )}
    </nav>
    <${StationList} stations=${stations} />
  </div>`;
}

export function FavoritesView() {
  const { t, catalog, favorites } = useApp();
  const [editing, setEditing] = useState(false);
  const stations = byId(catalog, favorites);
  useEffect(() => {
    if (!stations.length) setEditing(false);
  }, [stations.length]);
  const editControls = (station) => {
    const index = favorites.indexOf(station.id);
    return html`<span style=${{ display: 'flex' }}>
      <button class="icon-btn state" type="button" aria-label=${t('action_move_up')} title=${t('action_move_up')}
        disabled=${index <= 0} onClick=${() => moveFavorite(station.id, -1)}>
        <${Icon} name="arrowUpward" />
      </button>
      <button class="icon-btn state" type="button" aria-label=${t('action_move_down')} title=${t('action_move_down')}
        disabled=${index >= favorites.length - 1} onClick=${() => moveFavorite(station.id, 1)}>
        <${Icon} name="arrowDownward" />
      </button>
      <button class="icon-btn state" type="button" aria-label=${t('action_remove')} title=${t('action_remove')}
        onClick=${() => toggleFavorite(station.id)}>
        <${Icon} name="close" />
      </button>
    </span>`;
  };
  return html`<div class="screen screen--narrow">
    <header class="top-bar">
      <h1 class="top-bar__title headline-small">${t('nav_favorites')}</h1>
      ${stations.length > 0 &&
      html`<button class="btn btn--text state" type="button" onClick=${() => setEditing(!editing)}>
        ${editing ? t('action_done') : t('action_edit')}
      </button>`}
    </header>
    ${stations.length
      ? html`<${StationList} stations=${stations} trailing=${editing ? editControls : undefined} />`
      : html`<div class="card">
          <h2 class="card__title title-medium">${t('favorites_empty_title')}</h2>
          <p class="card__body body-medium">${t('favorites_empty_body')}</p>
          <div class="card__actions">
            <a class="btn btn--filled state" href="#/browse">${t('action_browse_stations')}</a>
          </div>
        </div>`}
  </div>`;
}

let lastQuery = '';

export function SearchView() {
  const { t, catalog, recents } = useApp();
  const [query, setQuery] = useState(lastQuery);
  const input = useRef(null);
  useEffect(() => {
    input.current?.focus({ preventScroll: true });
  }, []);
  const results = useMemo(() => search(query, catalog.stations), [query, catalog]);
  const recentStations = byId(catalog, recents);
  const update = (value) => {
    lastQuery = value;
    setQuery(value);
  };
  return html`<div class="screen screen--narrow">
    <div class="search-top" role="search">
      <button class="icon-btn state" type="button" aria-label=${t('action_back')} title=${t('action_back')} onClick=${goBack}>
        <${Icon} name="arrowBack" />
      </button>
      <label class="search-field">
        <${Icon} name="search" />
        <input
          ref=${input}
          type="search"
          enterkeyhint="search"
          autocomplete="off"
          spellcheck="false"
          placeholder=${t('search_hint')}
          aria-label=${t('search_hint')}
          value=${query}
          onInput=${(event) => update(event.currentTarget.value)}
        />
        ${query &&
        html`<button class="icon-btn state" type="button" aria-label=${t('action_clear')} title=${t('action_clear')}
          onClick=${() => {
            update('');
            input.current?.focus();
          }}>
          <${Icon} name="close" />
        </button>`}
      </label>
    </div>
    ${!query.trim()
      ? html`<p class="body-medium muted" style=${{ margin: '16px 24px' }}>${t('search_empty')}</p>
          ${recentStations.length > 0 &&
          html`<h2 class="group-label title-small">${t('section_recent')}</h2>
            <${StationList} stations=${recentStations} />`}`
      : results.length
        ? html`<${StationList} stations=${results} />`
        : html`<p class="empty body-large muted" role="status">${t('search_no_results')}</p>`}
  </div>`;
}

export function SettingsView() {
  const { t, settings, catalog, installPrompt, actions } = useApp();
  return html`<div class="screen screen--narrow">
    <header class="top-bar top-bar--back">
      <button class="icon-btn state" type="button" aria-label=${t('action_back')} title=${t('action_back')} onClick=${goBack}>
        <${Icon} name="arrowBack" />
      </button>
      <h1 class="top-bar__title headline-small">${t('settings_title')}</h1>
    </header>

    <${Group} label=${t('settings_colors')}>
      <div class="setting setting--block">
        <span class="body-large">${t('settings_theme')}</span>
        <${Segmented}
          label=${t('settings_theme')}
          value=${settings.theme}
          onChange=${(value) => setSetting('theme', value)}
          options=${[
            { value: 'system', label: t('theme_system') },
            { value: 'light', label: t('theme_light') },
            { value: 'dark', label: t('theme_dark') },
          ]}
        />
      </div>
      <${SwitchRow}
        title=${t('settings_pure_black')}
        summary=${t('settings_pure_black_summary')}
        checked=${settings.pureBlack}
        onChange=${(value) => setSetting('pureBlack', value)}
      />
      <div class="setting setting--block">
        <span class="setting__text">
          <span class="body-large">${t('settings_accent')}</span>
          <span class="body-medium muted">${t('settings_accent_summary')}</span>
        </span>
        <div class="swatches" role="group" aria-label=${t('settings_accent')}>
          ${ACCENT_PRESETS.map(
            (color, index) => html`<button
              class="swatch"
              type="button"
              style=${{ '--swatch': color }}
              aria-label=${t('accent_option', index + 1)}
              title=${t('accent_option', index + 1)}
              aria-pressed=${String(settings.accent.toUpperCase() === color)}
              onClick=${() => setSetting('accent', color)}
            ></button>`,
          )}
        </div>
      </div>
      <${SwitchRow}
        title=${t('settings_station_tint')}
        summary=${t('settings_station_tint_summary')}
        checked=${settings.stationTint}
        onChange=${(value) => setSetting('stationTint', value)}
      />
    <//>

    <${Group} label=${t('settings_interface')}>
      <div class="setting setting--block">
        <span class="body-large">${t('settings_language')}</span>
        <${Segmented}
          label=${t('settings_language')}
          value=${settings.language}
          onChange=${(value) => setSetting('language', value)}
          options=${[
            { value: 'system', label: t('language_system') },
            { value: 'he', label: t('language_hebrew') },
            { value: 'en', label: t('language_english') },
          ]}
        />
      </div>
      <${SwitchRow}
        title=${t('settings_background_gradient')}
        summary=${t('settings_background_gradient_summary')}
        checked=${settings.gradient}
        onChange=${(value) => setSetting('gradient', value)}
      />
      <${SwitchRow}
        title=${t('settings_animations')}
        summary=${t('settings_animations_summary')}
        checked=${settings.animations}
        onChange=${(value) => setSetting('animations', value)}
      />
      <${SwitchRow}
        title=${t('settings_station_logos')}
        summary=${t('settings_station_logos_summary')}
        checked=${settings.logos}
        onChange=${(value) => setSetting('logos', value)}
      />
      ${installPrompt &&
      html`<${LinkRow} title=${t('settings_install')} summary=${t('settings_install_summary')} onClick=${actions.install} />`}
    <//>

    <${Group} label=${t('settings_playback')}>
      <${SwitchRow}
        title=${t('settings_sleep_fade')}
        checked=${settings.sleepFade}
        onChange=${(value) => setSetting('sleepFade', value)}
      />
    <//>

    <${Group} label=${t('settings_stations')}>
      <${LinkRow} title=${t('settings_suggest_station')} summary=${t('settings_suggest_station_summary')} href=${suggestStationUrl()} />
      <${LinkRow}
        title=${t('settings_clear_recents')}
        onClick=${() => {
          clearRecents();
          actions.notify(t('recents_cleared'));
        }}
      />
    <//>

    <${Group} label=${t('settings_about')}>
      <${LinkRow} title=${t('about_android')} summary=${t('about_android_summary')} href=${ANDROID_URL} />
      <${LinkRow} title=${t('app_name')} summary=${t('about_license')} href=${REPOSITORY_URL} />
      <${LinkRow} title=${t('about_privacy')} summary=${t('about_privacy_summary')} href=${PRIVACY_URL} />
    <//>
    <p class="about-note body-medium muted">${t('about_web_note')}</p>
    <p class="about-note body-small muted">${t('about_disclaimer')}</p>
    <p class="about-note body-small muted tabular">
      <span dir="ltr">web ${BUILD}</span> · ${t('settings_catalog_version', catalog.version)}
    </p>
  </div>`;
}
