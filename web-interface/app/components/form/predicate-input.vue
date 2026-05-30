<template>
  <v-expansion-panels>
    <v-expansion-panel title="Optional filter">
      <v-expansion-panel-text>
        <predicate-builder
            :meta="predicates"
            :type-name="type"
            v-model="value"
            clearable/>
      </v-expansion-panel-text>
    </v-expansion-panel>
  </v-expansion-panels>
</template>

<script setup lang="ts">
import {predicates} from "~/utils/predicates";
import PredicateBuilder from "~/components/predicates/PredicateBuilder.vue";

const props = defineProps<{
  modelValue?: Record<string, string>,
  type: string
}>()

const emit = defineEmits(["update:modelValue"]);

const value = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", !val || Object.keys(val).length == 0 ? undefined : val)
});


</script>
