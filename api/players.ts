import { db, ensureSchema } from "../lib/db";
import { defaultNickname, isValidNickname } from "../lib/game";

function parseBody(raw: any): any {
  if (raw == null) return {};
  if (typeof raw === "string") {
    try { return JSON.parse(raw); } catch { return {}; }
  }
  return raw;
}

/** POST /api/players — device-based auto account (upsert). */
export default async function handler(req: any, res: any) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Use POST" });
    return;
  }
  const body = parseBody(req.body);
  const deviceId = String(body.deviceId ?? "").trim();
  if (deviceId.length < 6 || deviceId.length > 128) {
    res.status(400).json({ error: "deviceId is required (6-128 characters)" });
    return;
  }

  const nickname =
    body.nickname && isValidNickname(String(body.nickname))
      ? String(body.nickname).trim()
      : defaultNickname();

  try {
    await ensureSchema();
    const id = crypto.randomUUID();
    const rows = await db()`
      INSERT INTO players (id, device_id, nickname)
      VALUES (${id}, ${deviceId}, ${nickname})
      ON CONFLICT (device_id) DO UPDATE SET nickname = players.nickname
      RETURNING id, nickname, created_at`;

    const p = rows[0];
    res.status(200).json({
      player: {
        id: String(p.id),
        nickname: String(p.nickname),
        createdAt: p.created_at ? new Date(p.created_at as string).toISOString() : null,
      },
    });
  } catch (e) {
    res.status(500).json({ error: e instanceof Error ? e.message : "Registration failed" });
  }
}
