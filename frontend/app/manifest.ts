import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Learn2Debug',
    short_name: 'Learn2Debug',
    description:
      'Analyze Java code, surface beginner-friendly debugging feedback, and get concrete fixes with documentation links.',
    start_url: '/',
    display: 'standalone',
    background_color: '#120d0b',
    theme_color: '#f6c453',
    icons: [
      {
        src: '/icon.svg',
        sizes: 'any',
        type: 'image/svg+xml',
      },
      {
        src: '/apple-icon.svg',
        sizes: '180x180',
        type: 'image/svg+xml',
      },
    ],
  };
}
