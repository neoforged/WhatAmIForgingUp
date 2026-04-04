<script setup lang="ts">
import {ScalarType} from "~/utils/predicates";
import PredicateBuilder from "./PredicateBuilder.vue";
import PredicateListEditor from "./PredicateListEditor.vue";

const props = defineProps<{
  modelValue: any;
  meta: Record<string, PredicateType>;
  type: FieldType;

  hintObject?: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [any];
}>();
</script>

<template>
  <v-switch
      v-if="props.type === ScalarType.BOOLEAN"
      :model-value="modelValue ?? false"
      label="Value"
      @update:model-value="(v) => emit('update:modelValue', v)"
  />

  <v-text-field
      v-else-if="props.type === ScalarType.STRING"
      :model-value="modelValue ?? ''"
      label="Value"
      hide-details
      @update:model-value="(v) => emit('update:modelValue', v)"
  />
  <v-card v-else variant="elevated" class="pa-2">
    <PredicateBuilder
        v-if="(typeof props.type) == 'string'"
        :typeName="(props.type as string)!!"
        :modelValue="modelValue"
        :meta="meta"
        :hint-object="hintObject"
        @update:modelValue="(v) => emit('update:modelValue', v)"
    />

    <PredicateListEditor
        v-else
        :item-type="(props.type as any).list!!"
        :modelValue="modelValue"
        :meta="meta"
        :hint-object="hintObject"
        @update:modelValue="(v) => emit('update:modelValue', v)"
    />
  </v-card>
</template>
