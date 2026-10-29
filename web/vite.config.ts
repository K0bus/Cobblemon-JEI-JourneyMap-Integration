import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8420',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: resolve(__dirname, '../common/src/main/resources/assets/cobblemon_jei/web'),
    emptyOutDir: true
  }
})
