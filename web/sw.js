// Keeps the app's own files for offline use: always the newest from the network, and the saved
// copy when there's no connection. Radio streams and logos (other sites) are never touched.
const CACHE = 'shidur-shell-v1';

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      for (const name of await caches.keys()) {
        if (name.startsWith('shidur-shell-') && name !== CACHE) await caches.delete(name);
      }
      await self.clients.claim();
    })(),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || !url.pathname.startsWith(new URL('./', self.location).pathname)) return;
  if (request.headers.has('range')) return;
  event.respondWith(networkFirst(request));
});

async function networkFirst(request) {
  const cache = await caches.open(CACHE);
  try {
    // GitHub Pages lets browsers reuse files for 10 minutes. Checking each file with the server
    // keeps the modules from one publish together; page loads are left to the browser.
    const response = await (request.mode === 'navigate' ? fetch(request) : fetch(request, { cache: 'no-cache' }));
    if (response.ok && response.type === 'basic') cache.put(request, response.clone());
    return response;
  } catch (error) {
    const cached = await cache.match(request, { ignoreSearch: true });
    if (cached) return cached;
    if (request.mode === 'navigate') {
      const page = await cache.match(new URL('./', self.location).href);
      if (page) return page;
    }
    throw error;
  }
}
