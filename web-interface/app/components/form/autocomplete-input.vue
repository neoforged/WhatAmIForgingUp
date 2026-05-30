<template>
  <v-combobox
      v-model="value"
      :label="label"
      :placeholder="placeholder"
      :items="autoComplete"
      :loading="loading"
      density="compact"
      variant="underlined"
      menu-icon=""
      @update:focused="focused => focused ? querySelections(value) : undefined"
  />
</template>

<script setup lang="ts">
import type {AutoCompleteStrategy} from "~/utils/autocomplete";

const props = defineProps<{
  modelValue?: string,
  label: string,
  placeholder: string,
  strategy: AutoCompleteStrategy
}>()

const emit = defineEmits(["update:modelValue"]);

const value = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val)
});

const autoComplete = ref<string[]>([])
const loading = ref(false)

watch(value, querySelections)

function querySelections(v?: string) {
  loading.value = true
  setTimeout(() => {
    if (v !== value.value) {
      return // avoid race condition
    }
    props.strategy(v).then(res => {
      autoComplete.value = res
      loading.value = false
    })
  }, 500)
}
</script>
