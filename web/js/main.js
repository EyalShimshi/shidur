// Starts the app: loads the station list, creates the player and draws the screens.
import { render } from '../vendor/preact.js';
import { App } from './app.js';
import { parseCatalog, stationName, stationSubtitle } from './catalog.js';
import { makeT, resolveLanguage } from './i18n.js';
import { monogramUrl, readyLogo } from './logos.js';
import { createPlayer } from './player.js';
import { store } from './store.js';
import { applyTheme } from './theme.js';
import { html } from './ui/components.js';

const root = document.getElementById('app');
applyTheme(store.get().settings);

const language = () => resolveLanguage(store.get().settings.language, navigator.languages ?? [navigator.language]);

/** What the lock screen, notification and media keys show for a station. */
function describe(station) {
  const lang = language();
  const t = makeT(lang);
  const logo = store.get().settings.logos ? readyLogo(station) : null;
  return {
    title: stationName(station, lang),
    artist: stationSubtitle(station, t),
    album: t('app_name'),
    artwork: logo
      ? [{ src: logo.src, sizes: logo.raw ? '512x512' : '384x384' }]
      : [{ src: monogramUrl(station), sizes: '256x256', type: 'image/png' }],
  };
}

function showError() {
  const t = makeT(language());
  render(
    html`<div class="boot">
      <div>
        <img class="boot__logo" src="icon.svg" alt="" width="96" height="96" />
        <p class="body-large">${t('catalog_error')}</p>
        <button class="btn btn--filled state" type="button" onClick=${() => location.reload()}>${t('action_retry')}</button>
      </div>
    </div>`,
    root,
  );
}

async function start() {
  let catalog;
  try {
    const response = await fetch('catalog.json');
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    catalog = parseCatalog(await response.json());
  } catch (error) {
    console.error('Shidur: the station list did not load', error);
    showError();
    return;
  }
  const player = createPlayer({ getCatalog: () => catalog, describe });
  root.textContent = '';
  render(html`<${App} catalog=${catalog} player=${player} />`, root);
  if ('serviceWorker' in navigator && location.protocol === 'https:') {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
}

start();
