import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { defineConfig } from 'vitest/config';

/**
 * Справочники для экспедиции без сети: отдаются из кэша и обновляются в фоне (doc/architecture.md §5).
 * Только регулярное выражение: функция-шаблон попадает в sw.js текстом, без переменных этого файла (qa № 138).
 */
const OFFLINE_CATALOG = /\/api\/v1\/catalog\/(bosses|dictionaries)$/;

export default defineConfig({
  plugins: [
    react(),
    // Сайт ставится на телефон как приложение; статика и справочники доступны без сети (задача 7.1)
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'script-defer',
      includeAssets: ['icons/*.png'],
      manifest: {
        name: 'Primal — помощник',
        short_name: 'Primal',
        description: 'Помощник для настольной игры Primal: бой с монстром, экспедиция и кампания.',
        lang: 'ru',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#1a1b1e',
        theme_color: '#1a1b1e',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Новый service worker сразу берёт открытые вкладки: экспедиция без сети работает после первого открытия
        clientsClaim: true,
        skipWaiting: true,
        globPatterns: ['**/*.{js,css,html,png,svg,woff2}'],
        navigateFallback: '/index.html',
        // Адреса API — не страницы приложения: без сети они падают, а не отдают index.html
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [
          {
            urlPattern: OFFLINE_CATALOG,
            handler: 'StaleWhileRevalidate',
            options: { cacheName: 'primal-catalog' },
          },
        ],
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      // Бэкенд из IDE (doc/setup.md, раздел 4). SSE проходит без буферизации.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
  },
});
