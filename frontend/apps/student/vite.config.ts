import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'node:path'
// Compiler versions follow the verified unibest base family; omit unused app targets/plugins.
const uniPlugin = (uni as unknown as { default?: typeof uni }).default || uni
export default defineConfig({
  plugins: [uniPlugin()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  server: { host: '0.0.0.0', port: 5173, strictPort: true },
  build: { sourcemap: true },
})
