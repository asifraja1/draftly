import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      '/api': { target: 'http://localhost:8001', changeOrigin: true },
      '/auth': { target: 'http://localhost:8001', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8001', changeOrigin: true },
      '/login': { target: 'http://localhost:8001', changeOrigin: true },
      '/logout': { target: 'http://localhost:8001', changeOrigin: true },
    },
  },
})
