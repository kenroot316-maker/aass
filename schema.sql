-- Neon Rush database schema (Neon Postgres)
-- Run this once in the Neon SQL editor, or: psql $DATABASE_URL -f schema.sql

CREATE TABLE IF NOT EXISTS players (
  id         TEXT PRIMARY KEY,
  device_id  TEXT UNIQUE NOT NULL,
  nickname   TEXT NOT NULL,
  -- uploaded avatar as a data URI (data:image/...), null = none
  avatar     TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS scores (
  id         BIGSERIAL PRIMARY KEY,
  player_id  TEXT NOT NULL REFERENCES players(id),
  mode       TEXT NOT NULL CHECK (mode IN ('classic', 'coinrush', 'daily')),
  daily_key  TEXT,
  score      INT NOT NULL CHECK (score >= 0),
  coins      INT NOT NULL DEFAULT 0 CHECK (coins >= 0),
  seconds    INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Leaderboard lookups: by mode (+ daily key), highest score first
CREATE INDEX IF NOT EXISTS idx_scores_board
  ON scores (mode, daily_key, score DESC);

-- Per-player history / stats
CREATE INDEX IF NOT EXISTS idx_scores_player
  ON scores (player_id, mode, score DESC);

CREATE TABLE IF NOT EXISTS achievements (
  player_id      TEXT NOT NULL REFERENCES players(id),
  achievement_id TEXT NOT NULL,
  unlocked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (player_id, achievement_id)
);
