# NEON RUSH 3D - monorepo

An online full-stack neon endless runner for Android (true 3D perspective
renderer, three lanes, jump physics) with a server-authoritative backend
hosted on Vercel + Neon Postgres.

```
/                      Game backend: Vercel serverless functions (api/*) + status page (public/)
/android               NEON RUSH Android app (Jetpack Compose, 3D renderer)
/.github/workflows     CI (GitHub Actions build) + on-device E2E (your laptop)
/.maestro              Maestro smoke test flow
/lib                   Shared game rules (nicknames, achievements, daily key)
/schema.sql            Reference schema (the DB also self-provisions at runtime)
```

## Live backend

| Env | URL |
| --- | --- |
| Production | https://aass-api.vercel.app |
| Health check | https://aass-api.vercel.app/api/health |

Every push to `main` is deployed automatically by Vercel (project **aass-api**;
`.vercel-ignore.sh` skips builds for every other Vercel project linked to this
repository so their live sites are never touched).

### Database: self-provisioning

`DATABASE_URL` (Neon connection string) is configured in Vercel. The backend
creates its own tables on first request (`ensureSchema()` in `lib/db.ts` runs
idempotent `CREATE TABLE IF NOT EXISTS` statements), so there is no manual SQL
step. `schema.sql` is kept only as reference documentation.

## API surface

| Method | Route | Purpose |
| --- | --- | --- |
| GET | /api/health | service + database status |
| POST | /api/players | device-based auto account (upsert) |
| GET/PATCH | /api/players/{id} | stats + world ranks / rename |
| POST | /api/scores | server-validated score submit (anti-cheat caps) |
| GET | /api/leaderboard | classic / coinrush / daily (UTC date key) |

## Android app

* True 3D perspective renderer written from scratch on Jetpack Compose Canvas
  (pinhole camera projection, depth-sorted shaded boxes, billboard coins,
  fog, particles, speed lines, screen shake, vignette).
* Pure-Kotlin deterministic engine (30 unit tests) - Daily Challenge uses a
  UTC date seed, so everyone worldwide plays the identical run each day.
* Three modes: Classic Run (one hit ends it), Coin Rush (60 s), Daily.
* Offline-tolerant: the game is fully playable with no internet; scores sync
  when a server is reachable. The backend URL can be changed in Settings.

Build locally (JDK 17+ / Android SDK):

```bash
cd android
gradle assembleDebug testDebugUnitTest
```

The APK lands in `android/app/build/outputs/apk/debug/app-debug.apk`.

## CI + on-device E2E

* `android-build` - GitHub Actions ubuntu runner: unit tests + debug APK
  artifact on every push touching `android/**`.
* `android-e2e` - runs on your own Windows laptop as a self-hosted runner:
  boots an emulator, installs the APK and drives the real app with Maestro
  (`.maestro/neon-smoke.yaml`). See the workflow header for one-time setup.

> Note: the two workflow files live in `.github/workflows/` in this repo's
> staging copy. If they are missing on `main`, add them via the GitHub web UI
> (Add file), or grant the GitHub integration the **Workflows** permission
> (Settings > Applications) and push them.
