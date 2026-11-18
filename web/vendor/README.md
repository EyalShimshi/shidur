# Third-party files

Copied from npm and renamed from `.mjs` to `.js`, which GitHub Pages always serves as JavaScript.
Two changes: `hls.light.min.esm.js` lost its source-map comment, and `preact-hooks.js` imports
`./preact.js` instead of the bare `preact`, so the page needs no import map (which a strict content
security policy would block). Each package's integrity was checked against the npm registry
before copying.

| File | Package | License | npm integrity |
|---|---|---|---|
| `preact.js` | [preact](https://preactjs.com) 11.2.0, `dist/preact.mjs` | MIT | `sha512-/HfHI/7N84e6GrWBJ3J280z3mLaRJiDcSvj/A+y/4AbLnND/1UlGHe1sR6oYuTGwId0nP7dqDU9OnLxMPZ7xBg==` |
| `preact-hooks.js` | preact 11.2.0, `hooks/dist/hooks.mjs` | MIT | (same package) |
| `htm.js` | [htm](https://github.com/developit/htm) 3.1.1, `dist/htm.mjs` | Apache-2.0 | `sha512-983Vyg8NwUE7JkZ6NmOqpCZ+sh1bKv2iYTlUkzlWmA5JD2acKoxd4KVxbMmxX/85mtfdnDmTFoNKcg5DGAvxNQ==` |
| `hls.light.min.esm.js` | [hls.js](https://github.com/video-dev/hls.js) 1.7.4, `dist/hls.light.min.mjs` | Apache-2.0 | `sha512-3GlxGdPvT2YHvQYOt+yOzT/DEior/qJVNIylHA77kgeoBTdQQqi70GwefNEUmHQeNGyjC8U62+bruqW/Tpm+tg==` |

The license texts are in [`licenses/`](licenses).

To update one: download `https://registry.npmjs.org/<package>/-/<package>-<version>.tgz`, compare
its SHA-512 (base64) with `dist.integrity` from `https://registry.npmjs.org/<package>/<version>`,
copy the same file, and update this table.
