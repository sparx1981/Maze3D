# Marblix daily leaderboard

Two endpoints on Vercel serverless functions, no dependencies, no accounts and no personal data: just an arcade-style name (3-8 letters/digits) and a time for each day.

- `GET  /api/scores?day=YYYY-MM-DD` returns the top 20 `{name, ms}` for that UTC day.
- `POST /api/scores` with `{day, name, ms}` records a time (only ever improves a name's time for the day). Only today and yesterday (UTC) are accepted.

## Deploy
1. In Vercel, create a project from this repo with **Root Directory = `server`**.
2. Add storage: Marketplace -> **Upstash Redis** (or Vercel KV). It sets `KV_REST_API_URL` and `KV_REST_API_TOKEN` automatically.
3. Deploy, then put the project URL in `Leaderboard.BASE_URL` (`app/src/main/java/com/maze3d/core/Leaderboard.kt`) and rebuild the app.

Times are submitted by the app, so the board is for fun: a determined player can post a fake time. The server rejects impossible values (under 4 s or over an hour) and the data expires after 3 days.
