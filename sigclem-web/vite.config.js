import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // En desarrollo, las peticiones /api se envían al backend de Spring Boot (SIGCLEM_API_URL permite usar otro,
    // p. ej. la instancia de pruebas de carga).
    proxy: { '/api': process.env.SIGCLEM_API_URL || 'http://localhost:8080' },
  },
  test: { environment: 'node' },
});
