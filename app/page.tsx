import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

interface Entry {
  rank: number;
  playerId: string;
  nickname: string;
  score: number;
}

async function topClassic(): Promise<Entry[] | null> {
  try {
    const rows = await db()`
      SELECT s.player_id, p.nickname, s.score,
             ROW_NUMBER() OVER (ORDER BY s.score DESC)::int AS rank
      FROM scores s JOIN players p ON p.id = s.player_id
      WHERE s.mode = 'classic'
      ORDER BY s.score DESC LIMIT 5`;
    return rows.map((r) => ({
      rank: Number(r.rank),
      playerId: String(r.player_id),
      nickname: String(r.nickname),
      score: Number(r.score),
    }));
  } catch {
    return null;
  }
}

export default async function StatusPage() {
  let dbUp = false;
  let dbError = "";
  try {
    await db()`SELECT 1`;
    dbUp = true;
  } catch (e) {
    dbError = e instanceof Error ? e.message : String(e);
  }
  const entries = dbUp ? await topClassic() : null;

  return (
    <main style={{ maxWidth: 640, margin: "0 auto", padding: "48px 24px" }}>
      <h1 style={{ color: "#22E0FF", letterSpacing: 2 }}>NEON RUSH API</h1>
      <p style={{ color: "#B9C2D8" }}>
        Backend service for the Neon Rush arcade game. The Android app talks to
        the endpoints under <code>/api</code>.
      </p>

      <section style={{ marginTop: 24 }}>
        <h2 style={{ color: "#FF2D95", fontSize: 18 }}>Status</h2>
        <p>
          Database:{" "}
          <strong style={{ color: dbUp ? "#B6FF3C" : "#FF2D95" }}>
            {dbUp ? "connected" : "not configured"}
          </strong>
        </p>
        {!dbUp && (
          <p style={{ color: "#8892A6", fontSize: 14 }}>
            Set the <code>DATABASE_URL</code> environment variable in Vercel
            (Neon connection string) and redeploy. {dbError}
          </p>
        )}
        <p style={{ color: "#8892A6", fontSize: 14 }}>
          Endpoints: <code>POST /api/players</code>, <code>GET /api/players/&#123;id&#125;/stats</code>,
          <code> PATCH /api/players/&#123;id&#125;</code>, <code>POST /api/scores</code>,
          <code> GET /api/leaderboard?mode=classic|coinrush|daily</code>, <code>GET /api/health</code>
        </p>
      </section>

      {entries && entries.length > 0 && (
        <section style={{ marginTop: 24 }}>
          <h2 style={{ color: "#B6FF3C", fontSize: 18 }}>Top Classic Runners</h2>
          <ol style={{ paddingLeft: 20 }}>
            {entries.map((e) => (
              <li key={e.playerId}>
                #{e.rank} {e.nickname} — {e.score}
              </li>
            ))}
          </ol>
        </section>
      )}
    </main>
  );
}
