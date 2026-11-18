// Station search tolerant of Hebrew spelling variants, transliterations and frequencies.
// A port of the Android app's SearchEngine.kt; keep the two in step.

const FREQUENCY = /^(\d{2,3})(?:[.,](\d))?\s*(?:fm|mhz)?$/i;
const FINALS = { 'ך': 'כ', 'ם': 'מ', 'ן': 'נ', 'ף': 'פ', 'ץ': 'צ' };
const MARK = /\p{Mn}/u;
const LETTER_OR_DIGIT = /[\p{L}\p{Nd}]/u;

/**
 * Lowercases, strips niqqud, punctuation, quotes (צה"ל → צהל) and spaces, and maps Hebrew final
 * letters to their regular forms.
 */
export function normalize(input) {
  const decomposed = String(input ?? '').toLowerCase().normalize('NFKD');
  let out = '';
  for (const ch of decomposed) {
    const code = ch.codePointAt(0);
    if (code >= 0x0591 && code <= 0x05c7) continue;
    if (FINALS[ch]) {
      out += FINALS[ch];
      continue;
    }
    if (MARK.test(ch)) continue;
    if (LETTER_OR_DIGIT.test(ch)) out += ch;
  }
  return out;
}

/** Parses "88", "88.0", "107,5" or "103FM" into a frequency in the FM band, or null. */
export function parseFrequency(query) {
  const match = FREQUENCY.exec(String(query ?? '').trim());
  if (!match) return null;
  const value = Number(match[2] ? `${match[1]}.${match[2]}` : match[1]);
  return value >= 87 && value <= 108.9 ? value : null;
}

/** Stations matching [query], best first. */
export function search(query, stations) {
  const normalized = normalize(query);
  if (!normalized) return [];
  const frequency = parseFrequency(query);
  const wholeNumber = frequency !== null && !query.includes('.') && !query.includes(',');
  return stations
    .map((station) => ({ station, score: score(station, normalized, frequency, wholeNumber) }))
    .filter((it) => it.score > 0)
    .sort((a, b) => b.score - a.score || a.station.rank - b.station.rank)
    .map((it) => it.station);
}

function score(station, query, frequency, wholeNumber) {
  let best = 0;
  if (frequency !== null) {
    if (station.frequencies.some((f) => Math.abs(f - frequency) < 0.05)) best = 100;
    else if (wholeNumber && station.frequencies.some((f) => Math.floor(f) === frequency)) best = 90;
  }
  const names = [station.nameHe, station.nameEn, station.shortName].map(normalize);
  const aliases = station.aliases.map(normalize);
  let text = 0;
  if (names.some((n) => n === query)) text = 95;
  else if (aliases.some((a) => a === query)) text = 85;
  else if (names.some((n) => n.startsWith(query))) text = 80;
  else if (aliases.some((a) => a.startsWith(query))) text = 70;
  else if (names.some((n) => n.includes(query))) text = 50;
  else if (aliases.some((a) => a.includes(query))) text = 40;
  return Math.max(best, text);
}
