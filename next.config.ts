import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The status page queries the database at request time.
  // Everything under /api is a route handler already.
  typescript: { ignoreBuildErrors: false },
};

export default nextConfig;
