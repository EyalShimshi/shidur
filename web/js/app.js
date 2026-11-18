// The app shell: navigation (a bottom bar on phones, a rail on wider screens), the screens, the
// player (mini + sheet on phones, a side pane on wide screens), sheets and messages.
import { useEffect, useLayoutEffect, useMemo, useState } from '../vendor/preact-hooks.js';
import { stationName } from './catalog.js';
import { makeT, resolveLanguage } from './i18n.js';
import { onLogoReady } from './logos.js';
import { lastChangeWentBack, navigate, useRoute } from './router.js';
import { useStore } from './store.js';
import { applyTheme, onSystemAppearanceChange, schemeStyle, stationScheme, useMediaQuery } from './theme.js';
import { AppContext, html } from './ui/components.js';
import { Icon } from './ui/icons.js';
import { MiniPlayer, PlayerHero, PlayerSheet, SleepTimerSheet, StationInfoSheet } from './ui/player-ui.js';
import { BrowseView, FavoritesView, HomeView, SearchView, SettingsView } from './ui/screens.js';

// Chrome offers to install the site as an app; keep the offer for the Settings row.
let installEvent = null;
const installListeners = new Set();
addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault();
  installEvent = event;
  installListeners.forEach((listener) => listener(event));
});

const NAV = [
  { href: '#/', name: 'home', icon: 'home', iconOff: 'homeOutlined', label: 'nav_home' },
  { href: '#/browse', name: 'browse', icon: 'radio', iconOff: 'radioOutlined', label: 'nav_browse' },
  { href: '#/favorites', name: 'favorites', icon: 'favorite', iconOff: 'favoriteOutlined', label: 'nav_favorites' },
];

function NavItems({ route, t }) {
  return NAV.map((item) => {
    const selected = route.name === item.name;
    return html`<a key=${item.name} class="nav-item" href=${item.href} aria-current=${selected ? 'page' : undefined}>
      <span class="nav-item__indicator"><${Icon} name=${selected ? item.icon : item.iconOff} /></span>
      <span>${t(item.label)}</span>
    </a>`;
  });
}

function useOnline() {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    addEventListener('online', update);
    addEventListener('offline', update);
    return () => {
      removeEventListener('online', update);
      removeEventListener('offline', update);
    };
  }, []);
  return online;
}

export function App({ catalog, player }) {
  const settings = useStore((s) => s.settings);
  const favorites = useStore((s) => s.favorites);
  const recents = useStore((s) => s.recents);
  const bannerDismissed = useStore((s) => s.bannerDismissed);
  const [playerState, setPlayerState] = useState(player.get());
  const [appearance, setAppearance] = useState(0);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [modal, setModal] = useState(null);
  const [message, setMessage] = useState(null);
  const [installPrompt, setInstallPrompt] = useState(installEvent);
  const route = useRoute();
  const wide = useMediaQuery('(min-width: 1000px)');
  const online = useOnline();
  const lang = resolveLanguage(settings.language, navigator.languages ?? [navigator.language]);
  const t = useMemo(() => makeT(lang), [lang]);

  useEffect(() => player.subscribe(setPlayerState), []);
  useEffect(() => onSystemAppearanceChange(() => setAppearance((n) => n + 1)), []);
  useEffect(() => {
    installListeners.add(setInstallPrompt);
    return () => installListeners.delete(setInstallPrompt);
  }, []);
  useLayoutEffect(() => applyTheme(settings), [settings, appearance]);

  const station = playerState.stationId ? catalog.byId.get(playerState.stationId) ?? null : null;
  const playerStyle = useMemo(
    () => (station ? schemeStyle(stationScheme(station, settings)) : undefined),
    [station, settings, appearance],
  );

  useLayoutEffect(() => {
    const root = document.documentElement;
    root.lang = lang;
    root.dir = lang === 'he' ? 'rtl' : 'ltr';
    document.querySelector('meta[name="description"]')?.setAttribute('content', t('page_description'));
  }, [lang]);

  useEffect(() => {
    const playing = station && (playerState.status === 'playing' || playerState.status === 'connecting');
    document.title = playing ? `${stationName(station, lang)} · ${t('app_name')}` : t('page_title');
  }, [station, playerState.status, lang]);

  // The lock screen follows the language and shows the logo once it's ready.
  useEffect(() => player.refreshMetadata(), [lang, settings.logos]);
  useEffect(() => onLogoReady((id) => id === player.get().stationId && player.refreshMetadata()), []);

  // Overlays get a history entry, so the back button or gesture closes them.
  const openPlayer = () => {
    if (wide) return;
    setSheetOpen(true);
    if (history.state?.shidur !== 'player') history.pushState({ shidur: 'player' }, '');
  };
  const closePlayer = () => {
    if (history.state?.shidur === 'player') history.back();
    else setSheetOpen(false);
  };
  const openModal = (name) => {
    setModal(name);
    history.pushState({ shidur: 'modal' }, '');
  };
  const closeModal = () => {
    if (history.state?.shidur === 'modal') history.back();
    else setModal(null);
  };
  useEffect(() => {
    const onPop = () => {
      const overlay = history.state?.shidur;
      if (overlay !== 'modal') setModal(null);
      if (overlay !== 'player' && overlay !== 'modal') setSheetOpen(false);
    };
    addEventListener('popstate', onPop);
    return () => removeEventListener('popstate', onPop);
  }, []);

  useEffect(() => {
    document.body.classList.toggle('is-locked', sheetOpen && !wide);
  }, [sheetOpen, wide]);

  // A shared link (#/station/<id>) shows the station in the player, ready to play.
  useEffect(() => {
    if (route.name === 'station') {
      const id = route.station;
      navigate('/', { replace: true });
      if (catalog.byId.has(id)) {
        if (player.get().stationId !== id) player.select(id, favorites.includes(id) ? favorites : [id]);
        setTimeout(openPlayer);
      }
      return;
    }
    if (!lastChangeWentBack()) scrollTo(0, 0);
  }, [route]);

  // Last time's station waits in the mini player, one tap away.
  useEffect(() => {
    if (route.name === 'station' || player.get().stationId) return;
    const last = recents.find((id) => catalog.byId.has(id));
    if (last) player.select(last, favorites.includes(last) ? favorites : recents);
  }, []);

  useEffect(() => {
    if (!message) return undefined;
    const timer = setTimeout(() => setMessage(null), 4000);
    return () => clearTimeout(timer);
  }, [message]);

  // Space plays or pauses; Escape closes the player.
  useEffect(() => {
    const onKey = (event) => {
      if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return;
      const target = event.target instanceof Element ? event.target : null;
      if (event.key === 'Escape' && sheetOpen && !modal) {
        closePlayer();
        return;
      }
      if (event.code === 'Space' && !target?.closest('input, select, textarea, button, a, [role="switch"]')) {
        if (!player.get().stationId) return;
        event.preventDefault();
        player.toggle();
      }
    };
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [sheetOpen, modal]);

  const actions = {
    play: (target, queue) => player.play(target.id, queue.map((s) => s.id)),
    notify: (text) => setMessage({ text, key: Date.now() }),
    install: async () => {
      if (!installPrompt) return;
      installPrompt.prompt();
      try {
        await installPrompt.userChoice;
      } finally {
        installEvent = null;
        setInstallPrompt(null);
      }
    },
  };

  const context = {
    t,
    lang,
    settings,
    catalog,
    favorites,
    recents,
    bannerDismissed,
    player,
    playerState,
    installPrompt,
    actions,
  };

  let view;
  switch (route.name) {
    case 'browse':
      view = html`<${BrowseView} collection=${route.collection} />`;
      break;
    case 'favorites':
      view = html`<${FavoritesView} />`;
      break;
    case 'search':
      view = html`<${SearchView} />`;
      break;
    case 'settings':
      view = html`<${SettingsView} />`;
      break;
    default:
      view = html`<${HomeView} />`;
  }

  const overlay = sheetOpen && !wide;
  return html`<${AppContext.Provider} value=${context}>
    <a class="skip-link" href="#main" onClick=${(event) => {
      event.preventDefault();
      document.getElementById('main')?.focus();
    }}>${t('skip_to_content')}</a>
    <div class="app" data-mini=${String(Boolean(station) && !wide)} data-pane=${String(Boolean(station) && wide)}>
      <nav class="rail" aria-label=${t('app_name')} inert=${overlay}>
        <img class="rail__logo" src="icon.svg" alt="" width="40" height="40" />
        <${NavItems} route=${route} t=${t} />
      </nav>
      <main id="main" class="content" tabindex="-1" inert=${overlay}>
        ${!online && html`<div class="banner body-medium" role="status"><${Icon} name="info" />${t('offline_banner')}</div>`}
        ${view}
      </main>
      ${station && !wide && html`<${MiniPlayer} station=${station} style=${playerStyle} onOpen=${openPlayer} />`}
      ${station &&
      wide &&
      html`<aside class="pane" style=${playerStyle} aria-labelledby="pane-title">
        <${PlayerHero} station=${station} inPane onOpenSheet=${openModal} />
      </aside>`}
      ${station &&
      !wide &&
      html`<${PlayerSheet} open=${sheetOpen} style=${playerStyle} label=${stationName(station, lang)} onClose=${closePlayer}>
        <${PlayerHero} station=${station} onClose=${closePlayer} onOpenSheet=${openModal} />
      <//>`}
      <nav class="navbar" aria-label=${t('app_name')} inert=${overlay}>
        <${NavItems} route=${route} t=${t} />
      </nav>
      ${message && html`<div class="snackbar body-medium" role="status" key=${message.key}>${message.text}</div>`}
    </div>
    ${modal === 'sleep' && station && html`<${SleepTimerSheet} style=${playerStyle} onClose=${closeModal} />`}
    ${modal === 'info' && station && html`<${StationInfoSheet} station=${station} style=${playerStyle} onClose=${closeModal} />`}
  <//>`;
}
