// The station list: the same catalog.json the Android app uses (copied next to the page when the
// site is published). Mirrors CatalogDto.kt, plus one web rule: browsers block plain-http audio on
// an https page, so only https streams are kept, and a station without one is left out.

export const SUPPORTED_SCHEMA_VERSION = 1;
const DEFAULT_COLOR = '#6750A4';

export function parseCatalog(dto) {
  if (!dto || typeof dto !== 'object') throw new Error('The station list is not valid JSON');
  if ((dto.schemaVersion ?? SUPPORTED_SCHEMA_VERSION) !== SUPPORTED_SCHEMA_VERSION) {
    throw new Error(`Unsupported station list schema ${dto.schemaVersion}`);
  }
  const collections = (dto.collections ?? [])
    .map((c) => ({ id: c.id, nameHe: c.nameHe, nameEn: c.nameEn, order: c.order ?? 0 }))
    .sort((a, b) => a.order - b.order);
  const stations = (dto.stations ?? [])
    .filter((s) => (s.status ?? 'active') === 'active')
    .map(toStation)
    .filter((s) => s.streams.length > 0)
    .sort((a, b) => a.rank - b.rank);
  return {
    version: dto.catalogVersion ?? 0,
    collections,
    stations,
    byId: new Map(stations.map((s) => [s.id, s])),
  };
}

function toStation(s) {
  return {
    id: s.id,
    nameHe: s.nameHe,
    nameEn: s.nameEn,
    shortName: s.shortName ?? s.nameEn,
    color: parseColor(s.color),
    network: s.network ?? 'other',
    collections: s.collections ?? [],
    frequencies: s.fm ?? [],
    primaryFrequency: s.primaryFm ?? null,
    aliases: s.aliases ?? [],
    website: s.website ?? null,
    logo: s.logo ?? null,
    rank: s.rank ?? 1000,
    streams: (s.streams ?? [])
      .filter((stream) => isWebPlayable(stream.url))
      .map((stream) => ({
        url: stream.url,
        format: stream.format ?? null,
        bitrate: stream.bitrate ?? null,
        introSeconds: stream.introSeconds ?? null,
      })),
  };
}

export function isWebPlayable(url) {
  return /^https:\/\//i.test(String(url ?? ''));
}

export function isHls(stream) {
  return stream.format === 'hls' || /\.m3u8(\?|$)/i.test(stream.url);
}

/** `#RRGGBB` from "#RRGGBB" or "#AARRGGBB"; Material's baseline purple otherwise. */
export function parseColor(hex) {
  const clean = String(hex ?? '').trim().replace(/^#/, '');
  if (/^[0-9a-f]{6}$/i.test(clean)) return '#' + clean.toUpperCase();
  if (/^[0-9a-f]{8}$/i.test(clean)) return '#' + clean.slice(2).toUpperCase();
  return DEFAULT_COLOR;
}

export function stationName(station, lang) {
  return lang === 'he' ? station.nameHe : station.nameEn;
}

export function collectionName(collection, lang) {
  return lang === 'he' ? collection.nameHe : collection.nameEn;
}

export function formatFrequency(mhz) {
  return `${Number(mhz).toFixed(1)} FM`;
}

/** "88.0 FM", "FM · varies by region" or "Online only". */
export function stationSubtitle(station, t) {
  if (station.primaryFrequency != null) return formatFrequency(station.primaryFrequency);
  if (station.frequencies.length > 0) return t('fm_varies');
  return t('digital_station');
}

/** Stations of a collection (or all of them), best first. */
export function stationsIn(catalog, collectionId) {
  if (!collectionId) return catalog.stations;
  return catalog.stations.filter((s) => s.collections.includes(collectionId));
}

/** Station ids that still exist, in their original order. */
export function existing(catalog, ids) {
  return ids.filter((id) => catalog.byId.has(id));
}
