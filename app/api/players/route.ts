import { db } from "@/lib/db";
import { defaultNickname, isValidNickname } from "@/lib/game";

export const dynamic = "force-dynamic";

export async function POST(req: Request) {
  let body: { deviceId?: string; nickname?: string };
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  const deviceId = (body.deviceId ?? "").trim();
  if (deviceId.length < 6 || deviceId.length > 128) {
    return Response.json(
      { error: "deviceId is required (6-128 characters)" },
      { status: 400 }
    );
  }

  const nickname =
    body.nickname && isValidNickname(body.nickname)
      ? body.nickname.trim()
      : defaultNickname();

  try {
    const id = crypto.randomUUID();
    const rows = await db()`
      INSERT INTO players (id, device_id, nickname)
      VALUES (${id}, ${deviceId}, ${nickname})
      ON CONFLICT (device_id) DO UPDATE SET nickname = players.nickname
      RETURNING id, nickname, created_at`;

    const p = rows[0];
    return Response.json({
      player: {
        id: String(p.id),
        nickname: String(p.nickname),
        createdAt: p.created_at ? new Date(p.created_at as string).toISOString() : null,
      },
    });
  } catch (e) {
    return Response.json(
      { error: e instanceof Error ? e.message : "Registration failed" },
      { status: 500 }
    );
  }
}
