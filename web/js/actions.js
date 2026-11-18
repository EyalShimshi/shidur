// Links and actions that leave the page: sharing, reporting a problem, the Android app.
import { stationLink } from './router.js';
import { stationName } from './catalog.js';

export const REPOSITORY_URL = 'https://github.com/EyalShimshi/shidur';
export const ANDROID_URL = `${REPOSITORY_URL}/releases/latest`;
export const PRIVACY_URL = `${REPOSITORY_URL}/blob/main/PRIVACY.md`;

/** The build, filled in when the site is published ("dev" when served from the repository). */
export const BUILD = document.querySelector('meta[name="shidur-build"]')?.content || 'dev';

const encode = (value) => encodeURIComponent(value);

/** Shares the station's link with the system's share sheet, or copies it. Returns 'shared', 'copied' or null. */
export async function shareStation(station, lang, t) {
  const url = stationLink(station.id);
  const text = t('share_text', stationName(station, lang));
  if (navigator.share) {
    try {
      await navigator.share({ title: stationName(station, lang), text, url });
      return 'shared';
    } catch (error) {
      if (error?.name === 'AbortError') return null;
    }
  }
  return (await copyText(url)) ? 'copied' : null;
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

/** A pre-filled GitHub issue for a problem with a station (or the site when [station] is null). */
export function reportProblemUrl(station, streamIndex = 0) {
  const title = station ? `Problem with ${station.nameEn} (${station.id}) on the web` : 'Problem report (web)';
  const lines = ['**What happened?**', '', '', '---', `Web version: ${BUILD}`, `Browser: ${navigator.userAgent}`];
  if (station) {
    lines.push(`Station: ${station.id}`);
    const stream = station.streams[streamIndex];
    if (stream) lines.push(`Stream: ${stream.url}`);
  }
  return `${REPOSITORY_URL}/issues/new?title=${encode(title)}&body=${encode(lines.join('\n') + '\n')}`;
}

export function suggestStationUrl() {
  return `${REPOSITORY_URL}/issues/new?title=${encode('Station suggestion: ')}&body=${encode('Station name:\nWebsite:\nStream URL (if known):\n')}`;
}
