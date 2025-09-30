import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // Everything under /cmd -> http://localhost:8080 (command-service)
      '/cmd': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/cmd/, ''),
      },
      // Everything under /qry -> http://localhost:8081 (query-service)
      '/qry': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/qry/, ''),
      },
    },
  },
})