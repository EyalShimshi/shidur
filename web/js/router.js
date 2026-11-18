// Screens live in the address's #hash, so the page works as static files and links can be shared:
// #/  #/browse  #/browse/<collection>  #/favorites  #/search  #/settings  #/station/<id>
import { useEffect, useState } from '../vendor/preact-hooks.js';

export function parseHash(hash) {
  const parts = String(hash ?? '')
    .replace(/^#\/?/, '')
    .split(/[?#]/)[0]
    .split('/')
    .filter(Boolean)
    .map((part) => {
      try {
        return decodeURIComponent(part);
      } catch {
        return part;
      }
    });
  switch (parts[0]) {
    case 'browse':
      return { name: 'browse', collection: parts[1] ?? null };
    case 'favorites':
      return { name: 'favorites' };
    case 'search':
      return { name: 'search' };
    case 'settings':
      return { name: 'settings' };
    case 'station':
      return parts[1] ? { name: 'station', station: parts[1] } : { name: 'home' };
    default:
      return { name: 'home' };
  }
}

export function useRoute() {
  const [route, setRoute] = useState(() => parseHash(location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseHash(location.hash));
    addEventListener('hashchange', onChange);
    return () => removeEventListener('hashchange', onChange);
  }, []);
  return route;
}

// The screens visited in this tab, so the in-app back button never leaves the site.
const visits = typeof location === 'undefined' ? [] : [location.hash];
let wentBack = false;
let replacing = false;
if (typeof location !== 'undefined') {
  addEventListener('hashchange', () => {
    if (replacing) {
      replacing = false;
      wentBack = false;
      visits[visits.length - 1] = location.hash;
      return;
    }
    wentBack = visits.length > 1 && visits[visits.length - 2] === location.hash;
    if (wentBack) visits.pop();
    else visits.push(location.hash);
  });
}

/** Whether the latest screen change went back (the browser then restores the scroll position). */
export function lastChangeWentBack() {
  return wentBack;
}

/** Back to the previous screen, or to Home when this screen was the first one opened. */
export function goBack() {
  if (visits.length > 1) history.back();
  else navigate('/', { replace: true });
}

export function navigate(path, { replace = false } = {}) {
  const hash = '#' + path;
  if (location.hash === hash) return;
  if (replace) {
    replacing = true;
    location.replace(hash);
  } else {
    location.hash = hash;
  }
}

/** The address that opens [stationId] in the player. */
export function stationLink(stationId) {
  return `${location.origin}${location.pathname}#/station/${encodeURIComponent(stationId)}`;
}
