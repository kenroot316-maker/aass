import { db } from "../lib/db";

export default async function handler(_req: any, res: any) {
  const VERSION = "0.2.0";
  try {
    await db()`SELECT 1`;
    res.status(200).json({
      ok: true,
      status: "ok",
      database: "connected",
      version: VERSION,
    });
  } catch (e) {
    res.status(200).json({
      ok: true,
      status: "ok",
      database: "not connected",
      version: VERSION,
      hint: "Set DATABASE_URL in Vercel to the Neon connection string.",
      detail: e instanceof Error ? e.message : String(e),
    });
  }
}
