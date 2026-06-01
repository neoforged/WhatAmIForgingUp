import vuetify, { transformAssetUrls } from 'vite-plugin-vuetify'
export default defineNuxtConfig({
  //...
  build: {
    transpile: ['vuetify'],
  },
  app: {
    head: {
      link: [
        { rel: 'manifest', href: '/appmanifest.json' }
      ]
    }
  },
  vite: {
    plugins: [
      // @ts-expect-error
      vuetify({ autoImport: true }),
    ],
    vue: {
      template: {
        transformAssetUrls,
      },
    },
    optimizeDeps: {
      include: [
        '@vue/devtools-core',
        '@vue/devtools-kit',
        '@vue/apollo-composable',
        '@apollo/client/core',
        '@apollo/client/link/context',
        'jszip', // CJS
        'graphql-tag',
        'highlight.js',
        'highlight.js/lib/languages/java',
        'highlight.js/lib/languages/json',
        '@run-slicer/vf',
      ]
    }
  },
  devtools: { enabled: true },
  compatibilityDate: '2026-03-29',
  ssr: false
})
