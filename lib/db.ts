import { neon } from "@neondatabase/serverless";

/**
 * Lazily-created Neon SQL client. We build it on first use (not at import)
 * so a missing DATABASE_URL never breaks the Vercel build itself —
 * routes return a clear 500 instead.
 *
 * The driver's default generic resolves to a union that makes every call
 * site need narrowing, so we present the simpler shape we actually use:
 * a template-tag function that resolves to an array of row objects.
 */
type Row = Record<string, any>;
export type SqlFn = (
  strings: TemplateStringsArray,
  ...values: any[]
) => Promise<Row[]>;

let client: SqlFn | null = null;

export function db(): SqlFn {
  if (client === null) {
    const url = process.env.DATABASE_URL;
    if (!url) {
      throw new Error("DATABASE_URL is not configured");
    }
    client = neon(url) as unknown as SqlFn;
  }
  return client;
}

let schemaReady = false;

/**
 * Idempotent bootstrap: creates players/scores/achievements on first use so
 * the database self-provisions on the Neon free tier — no manual SQL step.
 * Safe under concurrent lambda instances (IF NOT EXISTS + already-exists catch).
 */
export async function ensureSchema(): Promise<void> {
  if (schemaReady) return;
  try {
    const sql = db();
    await sql`
      CREATE TABLE IF NOT EXISTS players (
        id         TEXT PRIMARY KEY,
        device_id  TEXT UNIQUE NOT NULL,
        nickname   TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      )`;
    await sql`
      CREATE TABLE IF NOT EXISTS scores (
        id         BIGSERIAL PRIMARY KEY,
        player_id  TEXT NOT NULL REFERENCES players(id),
        mode       TEXT NOT NULL CHECK (mode IN ('classic', 'coinrush', 'daily')),
        daily_key  TEXT,
        score      INT NOT NULL CHECK (score >= 0),
        coins      INT NOT NULL DEFAULT 0 CHECK (coins >= 0),
        seconds    INT NOT NULL DEFAULT 0,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      )`;
    await sql`CREATE INDEX IF NOT EXISTS idx_scores_board ON scores (mode, daily_key, score DESC)`;
    await sql`CREATE INDEX IF NOT EXISTS idx_scores_player ON scores (player_id, mode, score DESC)`;
    await sql`
      CREATE TABLE IF NOT EXISTS achievements (
        player_id      TEXT NOT NULL REFERENCES players(id),
        achievement_id TEXT NOT NULL,
        unlocked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
        PRIMARY KEY (player_id, achievement_id)
      )`;
    schemaReady = true;
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (/already exists/i.test(msg)) {
      schemaReady = true;
      return;
    }
    throw e;
  }
}
