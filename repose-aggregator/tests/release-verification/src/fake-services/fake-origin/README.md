# Fake Origin

A Node.js mock for release verification, listening on port 8000.
It returns `X-DEVICE-ID` as JSON (`id`) or XML (`deviceId`) when `Accept` contains `xml`.
Missing device IDs are returned as `UNDEFINED`.

Use Node.js 22.12 or later and npm 10 or later. Run `npm ci` in this directory, then `node app.js`.
