import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  metadataBase: new URL('https://learn2debug.vercel.app'),
  title: 'Learn2Debug | Java Debugging Coach',
  description:
    'Analyze Java code, surface beginner-friendly debugging feedback, and get concrete fixes with documentation links.',
  applicationName: 'Learn2Debug',
  keywords: ['Java debugger', 'code analysis', 'Learn2Debug', 'bug finder', 'programming tutor'],
  icons: {
    icon: [
      { url: '/icon.svg', type: 'image/svg+xml' },
      { url: '/favicon.ico', sizes: 'any' },
    ],
    apple: '/apple-icon.svg',
    shortcut: '/favicon.ico',
  },
  manifest: '/manifest.webmanifest',
  openGraph: {
    title: 'Learn2Debug | Java Debugging Coach',
    description:
      'Analyze Java code, surface beginner-friendly debugging feedback, and get concrete fixes with documentation links.',
    siteName: 'Learn2Debug',
    type: 'website',
  },
  twitter: {
    card: 'summary',
    title: 'Learn2Debug | Java Debugging Coach',
    description:
      'Analyze Java code, surface beginner-friendly debugging feedback, and get concrete fixes with documentation links.',
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
