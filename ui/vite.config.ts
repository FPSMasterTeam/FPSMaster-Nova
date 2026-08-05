import path from 'path';
import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Identity endpoint served ONLY by our own web UI. The Nova client (dev builds) probes
// http://localhost:3000/__nova_identity before trusting port 3000 as its Vite dev server, so a
// foreign dev server (NestJS API, another Vite app, ...) that happens to occupy 3000 is rejected
// instead of loading its 404 page into the webview. KEEP path + marker in sync with
// BasicBrowser.NOVA_IDENTITY_PATH / NOVA_IDENTITY_MARKER and LocalServer.kt.
const NOVA_IDENTITY_PATH = '/__nova_identity';
const NOVA_IDENTITY_MARKER = 'fpsmaster-nova-webui';

function novaIdentityPlugin(): Plugin {
  return {
    name: 'nova-identity-endpoint',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        // Ignore query string; match the identity path exactly.
        const pathname = (req.url ?? '').split('?')[0];
        if (pathname !== NOVA_IDENTITY_PATH) {
          next();
          return;
        }
        res.statusCode = 200;
        res.setHeader('Content-Type', 'application/json; charset=UTF-8');
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.end(JSON.stringify({ app: NOVA_IDENTITY_MARKER, service: 'vite-dev' }));
      });
    },
  };
}

export default defineConfig({
  server: {
    port: 3000,
    host: '0.0.0.0',
    // In production the page is served by the mod's bundled HTTP server, so the UI calls /api/* with
    // relative URLs and automatically follows whatever port that server bound to (it auto-falls-back
    // off 7781 when busy). In dev the page is served by Vite on 3000, so proxy /api to the mod's
    // HTTP server (default 7781) to keep the exact same relative URLs working here too.
    proxy: {
      '/api': 'http://localhost:7781',
    },
  },
  plugins: [novaIdentityPlugin(), react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '.'),
    },
  },
});
