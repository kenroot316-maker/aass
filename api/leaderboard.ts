import { db } from "../lib/db";
import { isGameMode, todayKeyUtc } from "../lib/game";

/** GET /api/leaderboard?mode=classic|coinrush|daily[&key=YYYY-MM-DD][&limit=100] */
export default async function handler(req: any, res: any) {
  const mode = String(req.query.mode ?? "classic").trim();
  if (!isGameMode(mode)) {
    res.status(400).json({ error: "mode must be classic, coinrush or daily" });
    return;
  }

  const limitRaw = Math.floor(Number(req.query.limit ?? 100));
  const limit = Math.min(Math.max(Number.isFinite(limitRaw) ? limitRaw : 100, 1), 100);

  const key =
    mode === "daily" ? (String(req.query.key ?? "").trim() || todayKeyUtc()) : null;

  try {
    const rows = await db()`
      SELECT s.player_id, p.nickname, s.score, s.coins,
             ROW_NUMBER() OVER (ORDER BY s.score DESC)::int AS rank
      FROM scores s JOIN players p ON p.id = s.player_id
      WHERE s.mode = ${mode}
        AND (${key}::text IS NULL OR s.daily_key = ${key})
      ORDER BY s.score DESC
      LIMIT ${limit}`;

    res.status(200).json({
      mode,
      key,
      entries: rows.map((r: any) => ({
        rank: Number(r.rank),
        playerId: String(r.player_id),
        nickname: String(r.nickname),
        score: Number(r.score),
        coins: Number(r.coins),
      })),
    });
  } catch (e) {
    res.status(500).json({ error: e instanceof Error ? e.message : "Leaderboard lookup failed" });
  }
}
