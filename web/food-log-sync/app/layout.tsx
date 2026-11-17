import type { Metadata } from "next";


export const metadata: Metadata = {
  title: "Food Log",
  description: "Your foods, recipes and daily weights, synced with Android.",
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="antialiased">{children}</body>
    </html>
  );
}
