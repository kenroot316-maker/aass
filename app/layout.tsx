import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Neon Rush API",
  description: "Backend for the Neon Rush arcade game (Vercel + Neon Postgres)",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body
        style={{
          background: "#0B0E1A",
          color: "#FFFFFF",
          fontFamily: "system-ui, -apple-system, sans-serif",
          margin: 0,
        }}
      >
        {children}
      </body>
    </html>
  );
}
