import { neon } from "@neondatabase/serverless";

/**
 * Lazily-created Neon SQL client. We build it on first use (not at import)
 * so a missing DATABASE_URL never breaks the Vercel build itself —
 * routes return a clear 500 instead.
 *
 * The driver's default generic resolves to a union that makes every call
 * site need narrowing, so we present the simpler shape we actually use:
 * a template-tag function that resolves to an array of row objects.
 */
type Row = Record<string, any>;
export type SqlFn = (
  strings: TemplateStringsArray,
  ...values: any[]
) => Promise<Row[]>;

let client: SqlFn | null = null;

export function db(): SqlFn {
  if (client === null) {
    const url = process.env.DATABASE_URL;
    if (!url) {
      throw new Error("DATABASE_URL is not configured");
    }
    client = neon(url) as unknown as SqlFn;
  }
  return client;
}
