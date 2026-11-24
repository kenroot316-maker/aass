import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    await db()`SELECT 1`;
    return Response.json({
      ok: true,
      status: "ok",
      database: "connected",
      version: "0.2.0",
    });
  } catch (e) {
    return Response.json({
      ok: true,
      status: "ok",
      database: "not connected",
      version: "0.2.0",
      hint: "Set DATABASE_URL in Vercel to the Neon connection string.",
      detail: e instanceof Error ? e.message : String(e),
    });
  }
}
