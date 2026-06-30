import path from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/api/v1/auth':         { target: 'http://localhost:8081', changeOrigin: true },
      '/api/v1/users':        { target: 'http://localhost:8081', changeOrigin: true },
      '/api/v1/categories':   { target: 'http://localhost:8082', changeOrigin: true },
      '/api/v1/expenses':     { target: 'http://localhost:8083', changeOrigin: true },
      '/api/v1/savings-goals':{ target: 'http://localhost:8084', changeOrigin: true },
      '/api/v1/budgets':      { target: 'http://localhost:8085', changeOrigin: true },
    },
  },
})
