<template>
  <v-sheet
      :elevation="2"
      style="overflow-x: auto;"
      :color="isDark ? 'grey-darken-4' : 'grey-lighten-4'"
  >
    <pre><code ref="codeRef" :class="`language-${language} pa-3`">{{ code }}</code></pre>
  </v-sheet>
</template>

<script>
import { onMounted, ref, watch, computed } from 'vue';
import { useTheme } from 'vuetify';
import hljs from 'highlight.js'

import 'highlight.js/styles/github-dark.css'

import java from 'highlight.js/lib/languages/java'
hljs.registerLanguage('java', java);

export default {
  name: 'CodeBlock',
  props: {
    code: {
      type: String,
      required: true
    },
    language: {
      type: String,
      default: 'java'
    }
  },
  setup(props) {
    const codeRef = ref(null);
    const theme = useTheme();

    const isDark = computed(() => theme.global.current.value.dark);

    const highlight = () => {
      if (codeRef.value) {
        hljs.highlightElement(codeRef.value)
      }
    };

    onMounted(highlight);
    watch(() => props.code, highlight);

    return { codeRef, isDark };
  }
};
</script>

<style scoped>
pre {
  margin: 0;
  padding: 0;
  overflow-x: auto;
  white-space: pre;
}

code {
  display: block;
  min-width: 100%;
  white-space: pre;
  font-family: 'Fira Code', monospace;
  font-size: 0.9rem;
}
</style>
