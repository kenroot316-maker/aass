/**
 * Neon Rush shared game rules — must stay in sync with the Android app's
 * game/ package (Nicknames, Achievements, DailyChallenge).
 */

export const NICKNAME_MIN = 3;
export const NICKNAME_MAX = 16;

export function isValidNickname(raw: string): boolean {
  const t = (raw ?? "").trim();
  if (t.length < NICKNAME_MIN || t.length > NICKNAME_MAX) return false;
  return [...t].every((c) => /[a-zA-Z0-9_ ]/.test(c));
}

export const GAME_MODES = ["classic", "coinrush", "daily"] as const;
export type GameMode = (typeof GAME_MODES)[number];

export function isGameMode(v: string): v is GameMode {
  return (GAME_MODES as readonly string[]).includes(v);
}

/** UTC date key, e.g. "2026-11-24" — identical to the Android DailyChallenge. */
export function todayKeyUtc(now: Date = new Date()): string {
  const y = now.getUTCFullYear();
  const m = String(now.getUTCMonth() + 1).padStart(2, "0");
  const d = String(now.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

const DAILY_KEY_RE = /^\d{4}-\d{2}-\d{2}$/;
export function isValidDailyKey(key: string): boolean {
  return DAILY_KEY_RE.test(key);
}

/** Default nickname when the app does not send one, e.g. "Runner-7f3a". */
export function defaultNickname(): string {
  const suffix = Math.random().toString(16).slice(2, 6);
  return `Runner-${suffix}`;
}

export interface AchievementDef {
  id: string;
  title: string;
  description: string;
}

export const ACHIEVEMENTS: AchievementDef[] = [
  { id: "first_run", title: "First Light", description: "Complete your first run" },
  { id: "classic_500", title: "Warming Up", description: "Score 500+ in one Classic Run" },
  { id: "classic_1500", title: "Blazing", description: "Score 1,500+ in one Classic Run" },
  { id: "classic_3000", title: "Neon Legend", description: "Score 3,000+ in one Classic Run" },
  { id: "rush_20", title: "Coin Snatcher", description: "Collect 20+ coins in one Coin Rush" },
  { id: "rush_45", title: "Coin Storm", description: "Collect 45+ coins in one Coin Rush" },
  { id: "daily_player", title: "Daily Spark", description: "Play a Daily Challenge" },
  { id: "regular", title: "Regular", description: "Play 5 total runs" },
];

/** Server-authoritative unlock logic (called after a score insert). */
export function evaluateAchievements(
  run: { mode: GameMode; score: number; coins: number },
  gamesPlayed: number
): string[] {
  const unlocked: string[] = [];
  if (gamesPlayed >= 1) unlocked.push("first_run");
  if (gamesPlayed >= 5) unlocked.push("regular");
  if (run.mode === "classic") {
    if (run.score >= 500) unlocked.push("classic_500");
    if (run.score >= 1500) unlocked.push("classic_1500");
    if (run.score >= 3000) unlocked.push("classic_3000");
  }
  if (run.mode === "coinrush") {
    if (run.coins >= 20) unlocked.push("rush_20");
    if (run.coins >= 45) unlocked.push("rush_45");
  }
  if (run.mode === "daily") unlocked.push("daily_player");
  return unlocked;
}

/** Plausibility caps so a tampered client cannot post absurd values. */
export const SCORE_CAP = 10_000_000;
export const COINS_CAP = 100_000;
export const SECONDS_CAP = 7200;
