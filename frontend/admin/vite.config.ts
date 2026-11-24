import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 개발 중에도 /api를 백엔드로 넘겨 배포와 같은 "같은 출처"로 둔다(ADR-008·009). CORS를 열지 않는다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    strictPort: true,
    proxy: { '^/api/': 'http://localhost:8080' },
  },
  preview: { port: 5174, strictPort: true },
});
