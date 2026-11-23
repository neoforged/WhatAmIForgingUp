<template>
  <div v-if="code" class="code-block" :class="{ 'code-block--compact': compact }">
    <pre><code ref="codeRef" :class="`language-${language}`">{{ code }}</code></pre>
  </div>
</template>

<script>
import {onMounted, ref, watch} from 'vue';
import hljs from 'highlight.js'

import 'highlight.js/styles/github-dark.css'

import java from 'highlight.js/lib/languages/java'
hljs.registerLanguage('java', java);
import json from 'highlight.js/lib/languages/json'
hljs.registerLanguage('json', json);

export default {
  name: 'CodeBlock',
  props: {
    code: {
      type: String,
      required: true
    },
    language: {
      type: String
    },
    compact: {
      type: Boolean,
      default: false
    }
  },
  setup(props) {
    const codeRef = ref(null);

    const highlight = () => {
      if (codeRef.value && props.language) {
        hljs.highlightElement(codeRef.value)
      }
    };

    onMounted(highlight);
    watch(() => props.code, highlight);

    return {codeRef};
  }
};
</script>

<style scoped>
.code-block {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.16);
  scrollbar-width: none;
  overflow-x: auto;
}

.code-block--compact {
  border: 1px solid rgba(139, 195, 74, 0.18);
  border-radius: 6px;
  background: rgba(139, 195, 74, 0.055);
  box-shadow: inset 3px 0 0 rgba(139, 195, 74, 0.34);
}

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
  line-height: 1.55;
  padding: 12px;
}

.code-block--compact code {
  padding: 6px 8px;
  font-size: 0.78rem;
  line-height: 1.4;
}
</style>
