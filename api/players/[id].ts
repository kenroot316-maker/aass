import { db, ensureSchema } from "../../lib/db";
import { isValidNickname } from "../../lib/game";

/** GET /api/players/{id} — full profile: bests, world ranks, achievements. */
export default async function handler(req: any, res: any) {
  const id = String(req.query.id ?? "");
  if (!id) {
    res.status(400).json({ error: "id is required" });
    return;
  }

  if (req.method === "GET") {
    try {
      await ensureSchema();
      const playerRows = await db()`
        SELECT id, nickname, avatar, created_at FROM players WHERE id = ${id}`;
      if (playerRows.length === 0) {
        res.status(404).json({ error: "Player not found" });
        return;
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

      res.status(200).json({
        player: {
          id: String(p.id),
          nickname: String(p.nickname),
          createdAt: p.created_at ? new Date(p.created_at as string).toISOString() : null,
          avatar: p.avatar ? String(p.avatar) : null,
        },
        bestClassic,
        bestCoinrush,
        bestDaily,
        gamesPlayed,
        achievements: achRows.map((a: any) => String(a.achievement_id)),
        rankClassic: bestClassic > 0 ? Number(rk.rank_classic) + 1 : null,
        rankCoinrush: bestCoinrush > 0 ? Number(rk.rank_coinrush) + 1 : null,
        rankDaily: bestDaily > 0 ? Number(rk.rank_daily) + 1 : null,
      });
    } catch (e) {
      res.status(500).json({ error: e instanceof Error ? e.message : "Stats lookup failed" });
    }
    return;
  }

  if (req.method === "PATCH") {
    let body: any = {};
    try {
      body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : (req.body ?? {});
    } catch {
      body = {};
    }
    // nickname: optional now — validated exactly as before when provided
    const hasNickname = body.nickname !== undefined && body.nickname !== null;
    const nickname = String(body.nickname ?? "").trim();
    if (hasNickname && !isValidNickname(nickname)) {
      res.status(400).json({ error: "Nickname must be 3-16 letters, digits, underscores or spaces" });
      return;
    }

    // avatar: undefined/null = leave unchanged, "" = clear, "data:image/..." = set
    let avatarParam: string | null | undefined;
    if (body.avatar !== undefined && body.avatar !== null) {
      if (typeof body.avatar !== "string") {
        res.status(400).json({ error: "avatar must be a string: a data:image/ URI or an empty string to clear" });
        return;
      }
      if (body.avatar === "") {
        avatarParam = null;
      } else if (body.avatar.startsWith("data:image/") && body.avatar.length <= 400000) {
        avatarParam = body.avatar;
      } else {
        res.status(400).json({ error: "avatar must be a data:image/ URI of at most 400000 characters, or an empty string to clear" });
        return;
      }
    }

    if (!hasNickname && avatarParam === undefined) {
      res.status(400).json({ error: "Nothing to update: provide nickname and/or avatar" });
      return;
    }

    await ensureSchema();
    try {
      let rows: any[];
      if (hasNickname && avatarParam !== undefined) {
        rows = await db()`
          UPDATE players SET nickname = ${nickname}, avatar = ${avatarParam}
          WHERE id = ${id}
          RETURNING id, nickname, avatar`;
      } else if (hasNickname) {
        rows = await db()`
          UPDATE players SET nickname = ${nickname}
          WHERE id = ${id}
          RETURNING id, nickname, avatar`;
      } else {
        rows = await db()`
          UPDATE players SET avatar = ${avatarParam}
          WHERE id = ${id}
          RETURNING id, nickname, avatar`;
      }
      if (rows.length === 0) {
        res.status(404).json({ error: "Player not found" });
        return;
      }
      res.status(200).json({
        id: String(rows[0].id),
        nickname: String(rows[0].nickname),
        avatar: rows[0].avatar ? String(rows[0].avatar) : null,
      });
    } catch (e) {
      res.status(500).json({ error: e instanceof Error ? e.message : "Update failed" });
    }
    return;
  }

  res.status(405).json({ error: "Use GET or PATCH" });
}
