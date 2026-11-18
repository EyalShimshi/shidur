// Smoke test for the web app in a real headless Chrome or Edge: the screens load without errors,
// and stations actually play. Saves screenshots and, on GitHub, a summary table.
//   node scripts/smoke.mjs [site folder] [--all] [--shots=<folder>]
// Fails on page errors or Content Security Policy violations, or when none of the stations play.
// A single station that doesn't play is only reported: streams come and go.
import { spawn } from 'node:child_process';
import { appendFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { launch, sleep } from './browser.mjs';

const here = fileURLToPath(new URL('.', import.meta.url));
const args = process.argv.slice(2);
const option = (name) => args.find((arg) => arg.startsWith(`--${name}=`))?.split('=')[1];
const root = resolve(args.find((arg) => !arg.startsWith('--')) ?? join(here, '..'));
const shots = resolve(option('shots') ?? join(tmpdir(), 'shidur-shots'));
const testAll = args.includes('--all');
// One station per kind of stream: Kan's HLS, then MP3 and AAC from each streaming company.
const SAMPLE = ['kan-bet', 'glglz', '103fm', 'eco99', '102fm-tlv', 'radio-jerusalem'];
const PHONE = { width: 412, height: 915, mobile: true, scale: 2 };
const DESKTOP = { width: 1280, height: 800, mobile: false, scale: 1 };

mkdirSync(shots, { recursive: true });
const port = 8100 + Math.floor(Math.random() * 800);
const base = `http://localhost:${port}/shidur/`;
const server = spawn(process.execPath, [join(here, 'serve.mjs'), root, String(port)], { stdio: 'ignore' });
let browser;
const failures = [];
const warnings = [];

async function open(page, url) {
  await page.goto('about:blank');
  await page.goto(url);
}

async function tryStation(page, id) {
  await open(page, `${base}#/station/${id}`);
  const ready = await page.waitFor(`!!document.querySelector('.player-sheet[data-open="true"] .play-btn')`, { timeout: 10000 });
  if (!ready) return { id, ok: false, note: "the player didn't open" };
  const started = Date.now();
  await page.click('.player-sheet .play-btn');
  const playing = await page.waitFor(
    `(() => {
      const audio = document.getElementById('shidur-audio');
      return document.documentElement.dataset.playback === 'playing' && audio.currentTime > 0;
    })()`,
    { timeout: 40000 },
  );
  const ms = Date.now() - started;
  const audioState = `({ time: document.getElementById('shidur-audio').currentTime, src: document.getElementById('shidur-audio').currentSrc, status: document.documentElement.dataset.playback })`;
  if (!playing) {
    const state = await page.evaluate(audioState);
    return { id, ok: false, note: `status ${state.status} after ${Math.round(ms / 1000)} s` };
  }
  const before = await page.evaluate(audioState);
  await sleep(3000);
  const after = await page.evaluate(audioState);
  const advanced = after.time - before.time;
  const via = after.src.startsWith('blob:') ? 'HLS (hls.js)' : new URL(after.src).host;
  return { id, ok: advanced > 1.5, ms, via, note: advanced > 1.5 ? '' : `stalled (${advanced.toFixed(1)} s in 3 s)` };
}

function report(lines) {
  console.log(lines.join('\n'));
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${lines.join('\n')}\n`);
}

try {
  for (let i = 0; ; i++) {
    const ok = await fetch(base).then((response) => response.ok, () => false);
    if (ok) break;
    if (i > 50) throw new Error('The local server did not start');
    await sleep(100);
  }
  const catalog = await (await fetch(`${base}catalog.json`)).json();
  const known = new Set(catalog.stations.map((station) => station.id));
  const stations = testAll ? [...known] : SAMPLE.filter((id) => known.has(id));

  browser = await launch();
  const page = await browser.newPage();
  await page.viewport(PHONE);
  await page.colorScheme('light');

  // The same screenshots on every computer: English and light first, Hebrew and dark later.
  await open(page, base);
  await page.evaluate(`localStorage.setItem('shidur:v1', JSON.stringify({ settings: { language: 'en', theme: 'light' } }))`);
  await open(page, base);
  const tiles = await page.waitFor(`document.querySelectorAll('.tile').length`, { timeout: 20000 });
  if (!tiles) failures.push('Home did not show any stations');
  await sleep(2500);
  await page.screenshot(join(shots, '1-home.png'));

  const results = [];
  for (const id of stations) {
    const result = await tryStation(page, id);
    results.push(result);
    console.log(`${result.ok ? 'plays ' : 'FAILED'} ${id} ${result.via ?? ''} ${result.ms ? `${(result.ms / 1000).toFixed(1)} s` : ''} ${result.note}`);
    if (result.ok && results.filter((r) => r.ok).length === 1) await page.screenshot(join(shots, '2-player.png'));
  }
  const played = results.filter((result) => result.ok).length;
  if (played === 0) failures.push('None of the stations played');
  else if (played < results.length) warnings.push(`${results.length - played} of ${results.length} stations did not play`);

  await page.viewport(DESKTOP);
  await open(page, base);
  await page.waitFor(`document.querySelectorAll('.tile').length`);
  if (!(await page.waitFor(`!!document.querySelector('.pane .play-btn')`, { timeout: 5000 }))) {
    failures.push('The wide layout did not show the player pane');
  }
  await sleep(1500);
  await page.screenshot(join(shots, '3-wide.png'));

  await page.evaluate(`(() => {
    const saved = JSON.parse(localStorage.getItem('shidur:v1') || '{}');
    saved.settings = { ...saved.settings, language: 'he', theme: 'dark' };
    localStorage.setItem('shidur:v1', JSON.stringify(saved));
  })()`);
  await page.viewport(PHONE);
  await open(page, base);
  await page.waitFor(`document.querySelectorAll('.tile').length`);
  if ((await page.evaluate('document.documentElement.dir')) !== 'rtl') failures.push('Hebrew did not switch the page to right-to-left');
  await sleep(1500);
  await page.screenshot(join(shots, '4-home-hebrew-dark.png'));
  await open(page, `${base}#/settings`);
  await page.waitFor(`!!document.querySelector('.switch')`);
  await sleep(500);
  await page.screenshot(join(shots, '5-settings.png'));

  for (const error of page.errors) {
    const line = `${error.kind}: ${error.text}${error.url ? ` (${error.url})` : ''}`;
    // Streams and logos live on other sites: a refused or failed download there is only a note.
    const elsewhere = error.kind === 'network' || /blocked by CORS policy|directive: "media-src/i.test(error.text);
    const serious =
      !elsewhere && (['exception', 'console', 'javascript', 'security'].includes(error.kind) || /Content Security Policy/i.test(error.text));
    (serious ? failures : warnings).push(line);
  }

  const unique = (lines) => [...new Set(lines)];
  report([
    '### Web app smoke test',
    '',
    '| Station | Result | Stream | Time to sound |',
    '| --- | --- | --- | --- |',
    ...results.map((r) => `| ${r.id} | ${r.ok ? '✅ plays' : `❌ ${r.note}`} | ${r.via ?? ''} | ${r.ms ? `${(r.ms / 1000).toFixed(1)} s` : ''} |`),
    '',
    ...(failures.length ? ['**Failed:**', ...unique(failures).map((failure) => `- ${failure}`), ''] : []),
    ...(warnings.length
      ? [`<details><summary>${unique(warnings).length} notes</summary>`, '', ...unique(warnings).map((warning) => `- ${warning}`), '', '</details>', '']
      : []),
    `Screenshots: ${shots}`,
  ]);
} catch (error) {
  failures.push(error.stack ?? String(error));
  console.error(error);
} finally {
  await browser?.close().catch(() => {});
  server.kill();
}
process.exit(failures.length ? 1 : 0);
