// A small static server that works like GitHub Pages (the site lives under /shidur/), for trying the
// web app on this computer and for the smoke test. No packages needed:
//   node scripts/serve.mjs [folder] [port]     then open http://localhost:8080/shidur/
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const root = resolve(process.argv[2] ?? resolve(here, '..'));
const port = Number(process.argv[3] ?? 8080);
const BASE = '/shidur/';
// When trying the source folder, the station list comes straight from the Android app's copy.
const CATALOG = resolve(here, '../../app/src/main/assets/catalog.json');

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.txt': 'text/plain; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
};

function send(res, status, headers, body = '') {
  res.writeHead(status, headers);
  res.end(body);
}

createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', 'http://localhost');
  if (!url.pathname.startsWith(BASE)) return send(res, 302, { Location: BASE });
  let path;
  try {
    path = decodeURIComponent(url.pathname.slice(BASE.length));
  } catch {
    return send(res, 400, {}, 'Bad request');
  }
  if (path === '' || path.endsWith('/')) path += 'index.html';
  let file = resolve(root, path);
  if (!file.startsWith(root + sep)) return send(res, 403, {}, 'Forbidden');
  let info = await stat(file).catch(() => null);
  if (!info && path === 'catalog.json') {
    file = CATALOG;
    info = await stat(file).catch(() => null);
  }
  if (info?.isDirectory()) return send(res, 301, { Location: `${url.pathname}/` });
  if (!info) return send(res, 404, { 'Content-Type': 'text/plain' }, 'Not found');
  const body = await readFile(file);
  send(res, 200, { 'Content-Type': TYPES[extname(file)] ?? 'application/octet-stream', 'Cache-Control': 'no-cache' }, body);
}).listen(port, () => console.log(`Shidur web: http://localhost:${port}${BASE}`));
