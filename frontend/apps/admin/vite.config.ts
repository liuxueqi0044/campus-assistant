import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5180,
    proxy: {
      '/admin-api': {
        target: process.env.CAMPUS_ADMIN_BACKEND || 'http://127.0.0.1:48080',
        changeOrigin: true
      }
    }
  }
})
