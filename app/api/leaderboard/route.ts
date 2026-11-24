import { db } from "@/lib/db";
import { isGameMode, todayKeyUtc } from "@/lib/game";

export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  const url = new URL(req.url);
  const mode = (url.searchParams.get("mode") ?? "classic").trim();
  if (!isGameMode(mode)) {
    return Response.json(
      { error: "mode must be classic, coinrush or daily" },
      { status: 400 }
    );
  }

  const limitRaw = Math.floor(Number(url.searchParams.get("limit") ?? 100));
  const limit = Math.min(Math.max(Number.isFinite(limitRaw) ? limitRaw : 100, 1), 100);

  // Daily boards are per-UTC-day; classic and coinrush are all-time.
  const key =
    mode === "daily" ? ((url.searchParams.get("key") ?? "").trim() || todayKeyUtc()) : null;

  try {
    const rows = await db()`
      SELECT s.player_id, p.nickname, s.score, s.coins,
             ROW_NUMBER() OVER (ORDER BY s.score DESC)::int AS rank
      FROM scores s JOIN players p ON p.id = s.player_id
      WHERE s.mode = ${mode}
        AND (${key}::text IS NULL OR s.daily_key = ${key})
      ORDER BY s.score DESC
      LIMIT ${limit}`;

    return Response.json({
      mode,
      key,
      entries: rows.map((r) => ({
        rank: Number(r.rank),
        playerId: String(r.player_id),
        nickname: String(r.nickname),
        score: Number(r.score),
        coins: Number(r.coins),
      })),
    });
  } catch (e) {
    return Response.json(
      { error: e instanceof Error ? e.message : "Leaderboard lookup failed" },
      { status: 500 }
    );
  }
}
