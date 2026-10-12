import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  if (mode === 'production') {
    const apiUrl = env.VITE_API_BASE_URL || ''
    const disallowedPatterns = [
      '10.10.13.114',
      '192.168.74.55',
      '192.168.',
      'localhost',
      '127.0.0.1',
    ]
    for (const pattern of disallowedPatterns) {
      if (apiUrl.includes(pattern)) {
        throw new Error(
          `[Production Build Guard] VITE_API_BASE_URL contains disallowed staging/development address: "${pattern}" (Found: "${apiUrl}"). Production builds must use an HTTPS production endpoint.`
        )
      }
    }
    if (!apiUrl.startsWith('https://')) {
      throw new Error(
        `[Production Build Guard] Production VITE_API_BASE_URL must use https:// protocol. Found: "${apiUrl}".`
      )
    }
  }

  return {
    plugins: [react()],
    server: {
      host: true,
      port: 5173,
    },
    define: {
      global: 'window',
    },
  }
})
