# NEON RUSH 3D - monorepo

An online full-stack neon endless runner for Android (true 3D perspective
renderer, three lanes, jump physics) with a server-authoritative backend
hosted on Vercel + Neon Postgres.

```
/                      Next.js game backend  ->  auto-deploys to Vercel (aass-api)
/android               NEON RUSH Android app (Jetpack Compose, 3D renderer)
/.github/workflows     CI (GitHub Actions build) + on-device E2E (your laptop)
/.maestro              Maestro smoke test flow
```

## Live backend

| Env | URL |
| --- | --- |
| Production | https://aass-api.vercel.app |
| Health check | https://aass-api.vercel.app/api/health |

Every push to `main` is built and deployed automatically by Vercel
(`.vercel-ignore.sh` makes sure only the `aass-api` project builds from this
repo, so other Vercel projects linked to this repository are never touched).

### One required secret: `DATABASE_URL`

The backend is deployed, but the database is only connected once you add the
Neon connection string:

1. Create a free project at https://neon.tech (free tier, no card needed).
2. Copy the pooled connection string (`postgresql://...neon.tech/neondb`).
3. Vercel dashboard -> project **aass-api** -> Settings -> Environment
   Variables -> add `DATABASE_URL` (all environments) -> Redeploy.
4. Run `schema.sql` once in the Neon SQL editor (creates `players`, `scores`,
   `achievements` tables).
5. Verify: open `/api/health` - it must say `"database": "connected"`.

## API surface

| Method | Route | Purpose |
| --- | --- | --- |
| GET | /api/health | service + database status |
| POST | /api/players | device-based auto account (upsert) |
| GET/PATCH | /api/players/[id] | stats + ranks / rename |
| POST | /api/scores | server-validated score submit (anti-cheat caps) |
| GET | /api/leaderboard | classic / coinrush / daily (UTC date key) |

## Android app

* True 3D perspective renderer written from scratch on Jetpack Compose Canvas
  (pinhole camera projection, depth-sorted shaded boxes, billboard coins,
  fog, particles, speed lines, screen shake, vignette).
* Pure-Kotlin deterministic engine (17 unit tests) - Daily Challenge uses a
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
