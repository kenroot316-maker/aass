import { db } from "@/lib/db";
import {
  COINS_CAP,
  SECONDS_CAP,
  SCORE_CAP,
  evaluateAchievements,
  isGameMode,
  isValidDailyKey,
  todayKeyUtc,
} from "@/lib/game";

export const dynamic = "force-dynamic";

export async function POST(req: Request) {
  let body: {
    playerId?: string;
    mode?: string;
    score?: number;
    coins?: number;
    seconds?: number;
    dailyKey?: string | null;
  };
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  const playerId = (body.playerId ?? "").trim();
  const mode = (body.mode ?? "").trim();
  const score = Math.floor(Number(body.score ?? 0));
  const coins = Math.floor(Number(body.coins ?? 0));
  const seconds = Math.floor(Number(body.seconds ?? 0));

  if (!playerId) {
    return Response.json({ error: "playerId is required" }, { status: 400 });
  }
  if (!isGameMode(mode)) {
    return Response.json({ error: "mode must be classic, coinrush or daily" }, { status: 400 });
  }
  if (
    !Number.isFinite(score) || score < 0 || score > SCORE_CAP ||
    !Number.isFinite(coins) || coins < 0 || coins > COINS_CAP ||
    !Number.isFinite(seconds) || seconds < 0 || seconds > SECONDS_CAP
  ) {
    return Response.json({ error: "score/coins/seconds out of allowed range" }, { status: 400 });
  }

  let dailyKey: string | null = null;
  if (mode === "daily") {
    dailyKey = (body.dailyKey ?? "").trim() || todayKeyUtc();
    if (!isValidDailyKey(dailyKey)) {
      return Response.json({ error: "dailyKey must be YYYY-MM-DD" }, { status: 400 });
    }
  }

  try {
    const ownerRows = await db()`
      SELECT id FROM players WHERE id = ${playerId}`;
    if (ownerRows.length === 0) {
      return Response.json({ error: "Player not found" }, { status: 404 });
    }

    await db()`
      INSERT INTO scores (player_id, mode, daily_key, score, coins, seconds)
      VALUES (${playerId}, ${mode}, ${dailyKey}, ${score}, ${coins}, ${seconds})`;

    const gamesRows = await db()`
      SELECT COUNT(*)::int AS games FROM scores WHERE player_id = ${playerId}`;
    const gamesPlayed = Number(gamesRows[0].games);

    // Achievements: diff against what the player already has.
    const haveRows = await db()`
      SELECT achievement_id FROM achievements WHERE player_id = ${playerId}`;
    const have = new Set(haveRows.map((r) => String(r.achievement_id)));
    const candidate = evaluateAchievements({ mode, score, coins }, gamesPlayed);
    const newAchievements = candidate.filter((a) => !have.has(a));
    for (const a of newAchievements) {
      await db()`
        INSERT INTO achievements (player_id, achievement_id)
        VALUES (${playerId}, ${a})
        ON CONFLICT (player_id, achievement_id) DO NOTHING`;
    }

    // World rank of this run (1-based) among strictly better scores.
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

    return Response.json({
      accepted: true,
      score,
      rank,
      bestScore,
      newAchievements,
      totalPlayers,
    });
  } catch (e) {
    return Response.json(
      { error: e instanceof Error ? e.message : "Score submission failed" },
      { status: 500 }
    );
  }
}
