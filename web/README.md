# Build and verify the Folio web UI

Use Node.js 22.12 or later. Run these commands from this directory.

1. Run `npm ci` to install the pinned dependencies.
2. Run `npm run build` to create `dist/index.html` and its bundled assets.
3. Run `npm test` to check the host codec, lifecycle, drafts, and original fenced-code indexes.
4. Run `npm run test:e2e` to check the real HeroUI DOM in headless Chromium.

A nonzero exit from any command is a failure. Test runs must report a nonzero test count with no failures or skips. Vitest writes `test-results/unit.xml`. Playwright writes `test-results/e2e.xml` and retains failing traces. Set `FOLIO_WEB_EVIDENCE` to an existing or new artifact directory to capture the four width and theme pairs during the browser checks.

`npm run typecheck` checks the application, browser fixtures, and tool configuration. `npm run dev` starts the local development server. A standalone page reports that the IDE host is unavailable. Browser tests substitute only the transport defined in [the v1 contract](../protocol/README.md).

The IntelliJ host serves the built files and supplies external `boot.js` before the module entry. That resource defines `window.folioHost`. The development server returns an empty `boot.js` response without creating a host. The production build excludes that resource. The production UI needs no CDN or development server.

Browser tests do not verify native JCEF, PasswordSafe, source-file writes, clipboard bytes, Git validation, or IDE installation. Check those through the Java session tests and the installed plugin.
