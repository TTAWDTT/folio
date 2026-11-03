import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss(), {
    name: 'folio-dev-boot',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/boot.js', (_request, response) => {
        response.setHeader('Content-Type', 'text/javascript');
        response.end();
      });
    },
  }],
  build: { target: 'chrome120', sourcemap: false, emptyOutDir: true },
});
