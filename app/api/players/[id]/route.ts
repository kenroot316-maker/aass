import { db } from "@/lib/db";
import { isValidNickname } from "@/lib/game";

export const dynamic = "force-dynamic";

/** GET /api/players/{id}/stats — full profile: bests, ranks, achievements. */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  try {
    const playerRows = await db()`
      SELECT id, nickname, created_at FROM players WHERE id = ${id}`;
    if (playerRows.length === 0) {
      return Response.json({ error: "Player not found" }, { status: 404 });
    }
    const p = playerRows[0];

    const statRows = await db()`
      SELECT
        (SELECT COALESCE(MAX(score), 0) FROM scores WHERE player_id = ${id} AND mode = 'classic')   AS best_classic,
        (SELECT COALESCE(MAX(score), 0) FROM scores WHERE player_id = ${id} AND mode = 'coinrush')  AS best_coinrush,
        (SELECT COALESCE(MAX(score), 0) FROM scores WHERE player_id = ${id} AND mode = 'daily')     AS best_daily,
        (SELECT COUNT(*)::int FROM scores WHERE player_id = ${id})                                  AS games_played`;
    const s = statRows[0];
    const bestClassic = Number(s.best_classic);
    const bestCoinrush = Number(s.best_coinrush);
    const bestDaily = Number(s.best_daily);
    const gamesPlayed = Number(s.games_played);

    // World ranks: how many players have a strictly higher best in that mode.
    const rankRows = await db()`
      SELECT
        (SELECT COUNT(*)::int FROM scores sc
          WHERE sc.mode = 'classic' AND sc.score > ${bestClassic} AND ${bestClassic > 0}) AS rank_classic,
        (SELECT COUNT(*)::int FROM scores sc
          WHERE sc.mode = 'coinrush' AND sc.score > ${bestCoinrush} AND ${bestCoinrush > 0}) AS rank_coinrush,
        (SELECT COUNT(*)::int FROM scores sc
          WHERE sc.mode = 'daily' AND sc.score > ${bestDaily} AND ${bestDaily > 0}) AS rank_daily`;
    const rk = rankRows[0];

    const achRows = await db()`
      SELECT achievement_id FROM achievements WHERE player_id = ${id} ORDER BY unlocked_at`;

    return Response.json({
      player: {
        id: String(p.id),
        nickname: String(p.nickname),
        createdAt: p.created_at ? new Date(p.created_at as string).toISOString() : null,
      },
      bestClassic,
      bestCoinrush,
      bestDaily,
      gamesPlayed,
      achievements: achRows.map((a) => String(a.achievement_id)),
      rankClassic: bestClassic > 0 ? Number(rk.rank_classic) + 1 : null,
      rankCoinrush: bestCoinrush > 0 ? Number(rk.rank_coinrush) + 1 : null,
      rankDaily: bestDaily > 0 ? Number(rk.rank_daily) + 1 : null,
    });
  } catch (e) {
    return Response.json(
      { error: e instanceof Error ? e.message : "Stats lookup failed" },
      { status: 500 }
    );
  }
}

/** PATCH /api/players/{id} — rename. */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  let body: { nickname?: string };
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  const nickname = (body.nickname ?? "").trim();
  if (!isValidNickname(nickname)) {
    return Response.json(
      { error: "Nickname must be 3-16 letters, digits, underscores or spaces" },
      { status: 400 }
    );
  }

  try {
    const rows = await db()`
      UPDATE players SET nickname = ${nickname}
      WHERE id = ${id}
      RETURNING id, nickname`;
    if (rows.length === 0) {
      return Response.json({ error: "Player not found" }, { status: 404 });
    }
    return Response.json({ id: String(rows[0].id), nickname: String(rows[0].nickname) });
  } catch (e) {
    return Response.json(
      { error: e instanceof Error ? e.message : "Rename failed" },
      { status: 500 }
    );
  }
}
