// Drives a headless Chrome or Edge over the DevTools protocol, so the web app can be tested and
// photographed without installing anything (Node 22+ has WebSocket built in). Just enough for
// scripts/smoke.mjs: open pages, size them like a phone or a desktop, run code, click, screenshot.
import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const env = process.env;
const CANDIDATES = {
  win32: [
    `${env.PROGRAMFILES}\\Google\\Chrome\\Application\\chrome.exe`,
    `${env['PROGRAMFILES(X86)']}\\Google\\Chrome\\Application\\chrome.exe`,
    `${env.LOCALAPPDATA}\\Google\\Chrome\\Application\\chrome.exe`,
    `${env['PROGRAMFILES(X86)']}\\Microsoft\\Edge\\Application\\msedge.exe`,
    `${env.PROGRAMFILES}\\Microsoft\\Edge\\Application\\msedge.exe`,
  ],
  linux: ['/usr/bin/google-chrome', '/usr/bin/google-chrome-stable', '/usr/bin/microsoft-edge', '/usr/bin/chromium'],
  darwin: [
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
  ],
};

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** The browser to use: $BROWSER_PATH, or an installed Chrome or Edge. */
export function findBrowser() {
  if (env.BROWSER_PATH) return env.BROWSER_PATH;
  return (CANDIDATES[process.platform] ?? []).find((path) => existsSync(path));
}

export async function launch({ path = findBrowser(), args = [] } = {}) {
  if (!path) throw new Error('No Chrome or Edge found. Set BROWSER_PATH to one.');
  const extra = (env.BROWSER_ARGS ?? '').split(/\s+/).filter(Boolean);
  const profile = mkdtempSync(join(tmpdir(), 'shidur-browser-'));
  const child = spawn(
    path,
    [
      '--headless=new',
      '--remote-debugging-port=0',
      `--user-data-dir=${profile}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-extensions',
      '--autoplay-policy=no-user-gesture-required',
      '--mute-audio',
      ...extra,
      ...args,
      'about:blank',
    ],
    { stdio: ['ignore', 'ignore', 'pipe'] },
  );
  const endpoint = await new Promise((resolve, reject) => {
    let output = '';
    const timer = setTimeout(() => reject(new Error(`The browser didn't start:\n${output}`)), 30000);
    child.stderr.on('data', (chunk) => {
      output += chunk;
      const match = output.match(/DevTools listening on (ws:\/\/\S+)/);
      if (match) {
        clearTimeout(timer);
        resolve(match[1]);
      }
    });
    child.once('exit', (code) => reject(new Error(`The browser stopped (${code}):\n${output}`)));
  });
  const connection = await connect(endpoint);
  return {
    newPage: () => newPage(connection),
    async close() {
      await Promise.race([connection.send('Browser.close').catch(() => {}), sleep(5000)]);
      connection.close();
      if (child.exitCode === null) await Promise.race([new Promise((resolve) => child.once('exit', resolve)), sleep(5000)]);
      if (child.exitCode === null) child.kill();
      rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
    },
  };
}

function connect(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    const pending = new Map();
    const listeners = new Set();
    let nextId = 1;
    socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id) {
        const call = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) call?.reject(new Error(`${call.method}: ${message.error.message}`));
        else call?.resolve(message.result);
      } else {
        listeners.forEach((listener) => listener(message));
      }
    };
    socket.onerror = () => reject(new Error(`Couldn't connect to the browser at ${url}`));
    socket.onopen = () =>
      resolve({
        send(method, params = {}, sessionId) {
          const id = nextId++;
          socket.send(JSON.stringify({ id, method, params, sessionId }));
          return new Promise((resolveCall, rejectCall) => pending.set(id, { resolve: resolveCall, reject: rejectCall, method }));
        },
        on(listener) {
          listeners.add(listener);
          return () => listeners.delete(listener);
        },
        close: () => socket.close(),
      });
  });
}

function describeException(details) {
  return details.exception?.description ?? details.text ?? 'Unknown error';
}

async function newPage(connection) {
  const { targetId } = await connection.send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await connection.send('Target.attachToTarget', { targetId, flatten: true });
  const send = (method, params) => connection.send(method, params, sessionId);
  const events = new Set();
  /** Everything the page reported as an error: { kind, text }. */
  const errors = [];
  connection.on((message) => {
    if (message.sessionId !== sessionId) return;
    const { method, params } = message;
    if (method === 'Runtime.exceptionThrown') errors.push({ kind: 'exception', text: describeException(params.exceptionDetails) });
    if (method === 'Runtime.consoleAPICalled' && params.type === 'error') {
      errors.push({ kind: 'console', text: params.args.map((arg) => arg.value ?? arg.description ?? '').join(' ') });
    }
    if (method === 'Log.entryAdded' && params.entry.level === 'error') {
      errors.push({ kind: params.entry.source, text: params.entry.text, url: params.entry.url });
    }
    events.forEach((listener) => listener(message));
  });
  const waitForEvent = (name, timeout) =>
    new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        events.delete(listener);
        reject(new Error(`Timed out waiting for ${name}`));
      }, timeout);
      const listener = (message) => {
        if (message.method !== name) return;
        clearTimeout(timer);
        events.delete(listener);
        resolve(message.params);
      };
      events.add(listener);
    });
  await Promise.all([send('Page.enable'), send('Runtime.enable'), send('Log.enable')]);

  const page = {
    errors,
    /** Any DevTools protocol command, for what the helpers below don't cover. */
    send,
    async goto(url, { timeout = 30000 } = {}) {
      const loaded = waitForEvent('Page.loadEventFired', timeout);
      const result = await send('Page.navigate', { url });
      if (result.errorText) throw new Error(`${url}: ${result.errorText}`);
      await loaded;
    },
    async evaluate(expression) {
      const { result, exceptionDetails } = await send('Runtime.evaluate', {
        expression,
        awaitPromise: true,
        returnByValue: true,
        userGesture: true,
      });
      if (exceptionDetails) throw new Error(describeException(exceptionDetails));
      return result.value;
    },
    /** The first truthy value of [expression], checked every [interval] ms; null after [timeout]. */
    async waitFor(expression, { timeout = 15000, interval = 250 } = {}) {
      const end = Date.now() + timeout;
      for (;;) {
        const value = await page.evaluate(expression);
        if (value) return value;
        if (Date.now() > end) return null;
        await sleep(interval);
      }
    },
    /** A real mouse click in the middle of the first element matching [selector]. */
    async click(selector) {
      const point = await page.evaluate(`(() => {
        const element = document.querySelector(${JSON.stringify(selector)});
        if (!element) return null;
        element.scrollIntoView({ block: 'center', inline: 'center' });
        const box = element.getBoundingClientRect();
        return { x: box.left + box.width / 2, y: box.top + box.height / 2 };
      })()`);
      if (!point) throw new Error(`Nothing on the page matches ${selector}`);
      for (const type of ['mousePressed', 'mouseReleased']) {
        await send('Input.dispatchMouseEvent', { type, ...point, button: 'left', buttons: 1, clickCount: 1 });
      }
    },
    async viewport({ width, height, mobile = false, scale = 1 }) {
      await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: scale, mobile });
      await send('Emulation.setTouchEmulationEnabled', { enabled: mobile });
    },
    async colorScheme(value) {
      await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value }] });
    },
    async screenshot(path) {
      const { data } = await send('Page.captureScreenshot', { format: 'png' });
      writeFileSync(path, Buffer.from(data, 'base64'));
    },
  };
  return page;
}
