import { db } from "../lib/db";
import {
  COINS_CAP,
  SECONDS_CAP,
  SCORE_CAP,
  evaluateAchievements,
  isGameMode,
  isValidDailyKey,
  todayKeyUtc,
} from "../lib/game";

/** POST /api/scores — server-validated score submission + achievements. */
export default async function handler(req: any, res: any) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Use POST" });
    return;
  }

  let body: any = {};
  try {
    body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : (req.body ?? {});
  } catch {
    body = {};
  }

  const playerId = String(body.playerId ?? "").trim();
  const mode = String(body.mode ?? "").trim();
  const score = Math.floor(Number(body.score ?? 0));
  const coins = Math.floor(Number(body.coins ?? 0));
  const seconds = Math.floor(Number(body.seconds ?? 0));

  if (!playerId) {
    res.status(400).json({ error: "playerId is required" });
    return;
  }
  if (!isGameMode(mode)) {
    res.status(400).json({ error: "mode must be classic, coinrush or daily" });
    return;
  }
  if (
    !Number.isFinite(score) || score < 0 || score > SCORE_CAP ||
    !Number.isFinite(coins) || coins < 0 || coins > COINS_CAP ||
    !Number.isFinite(seconds) || seconds < 0 || seconds > SECONDS_CAP
  ) {
    res.status(400).json({ error: "score/coins/seconds out of allowed range" });
    return;
  }

  let dailyKey: string | null = null;
  if (mode === "daily") {
    dailyKey = String(body.dailyKey ?? "").trim() || todayKeyUtc();
    if (!isValidDailyKey(dailyKey)) {
      res.status(400).json({ error: "dailyKey must be YYYY-MM-DD" });
      return;
    }
  }

  try {
    const ownerRows = await db()`
      SELECT id FROM players WHERE id = ${playerId}`;
    if (ownerRows.length === 0) {
      res.status(404).json({ error: "Player not found" });
      return;
    }

    await db()`
      INSERT INTO scores (player_id, mode, daily_key, score, coins, seconds)
      VALUES (${playerId}, ${mode}, ${dailyKey}, ${score}, ${coins}, ${seconds})`;

    const gamesRows = await db()`
      SELECT COUNT(*)::int AS games FROM scores WHERE player_id = ${playerId}`;
    const gamesPlayed = Number(gamesRows[0].games);

    const haveRows = await db()`
      SELECT achievement_id FROM achievements WHERE player_id = ${playerId}`;
    const have = new Set(haveRows.map((r: any) => String(r.achievement_id)));
    const candidate = evaluateAchievements({ mode, score, coins } as any, gamesPlayed);
    const newAchievements = candidate.filter((a) => !have.has(a));
    for (const a of newAchievements) {
      await db()`
        INSERT INTO achievements (player_id, achievement_id)
        VALUES (${playerId}, ${a})
        ON CONFLICT (player_id, achievement_id) DO NOTHING`;
    }

    let rank: number | null = null;
    if (score > 0) {
      const rankRows = dailyKey
        ? await db()`
            SELECT COUNT(*)::int AS better FROM scores
            WHERE mode = 'daily' AND daily_key = ${dailyKey} AND score > ${score}`
        : await db()`
            SELECT COUNT(*)::int AS better FROM scores
            WHERE mode = ${mode} AND score > ${score}`;
      rank = Number(rankRows[0].better) + 1;
    }

    const bestRows = await db()`
      SELECT COALESCE(MAX(score), 0) AS best FROM scores
      WHERE player_id = ${playerId} AND mode = ${mode}`;
    const bestScore = Number(bestRows[0].best);

    const countRows = await db()`SELECT COUNT(*)::int AS total FROM players`;
    const totalPlayers = Number(countRows[0].total);

    res.status(200).json({
      accepted: true,
      score,
      rank,
      bestScore,
      newAchievements,
      totalPlayers,
    });
  } catch (e) {
    res.status(500).json({ error: e instanceof Error ? e.message : "Score submission failed" });
  }
}
