<script setup lang="ts">
import PredicateFieldEditor from "./PredicateFieldEditor.vue";

const props = defineProps<{
  itemType: FieldType
  modelValue: any[];
  meta: Record<string, PredicateType>;

  hintObject?: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [any[]];
}>();

function addItem() {
  emit("update:modelValue", [...(props.modelValue ?? []), {}]);
}
function removeItem(i: number) {
  const next = [...(props.modelValue ?? [])];
  next.splice(i, 1);
  emit("update:modelValue", next);
}
function updateItem(i: number, v: any) {
  const next = [...(props.modelValue ?? [])];
  next[i] = v;
  emit("update:modelValue", next);
}
</script>

<template>
  <div>
    <div v-if="(modelValue?.length ?? 0) === 0" class="text-medium-emphasis">
      No items.
    </div>

    <div v-for="(item, idx) in (modelValue ?? [])" :key="idx" class="mb-2">
      <v-card variant="outlined" class="pa-2">
        <v-btn size="small" variant="outlined" color="error" @click="removeItem(idx)">Remove item</v-btn>

        <PredicateFieldEditor
            :type="itemType"
            :modelValue="item"
            :meta="meta"
            :hint-object="hintObject"
            @update:modelValue="(v: any) => updateItem(idx, v)"
        />
      </v-card>
    </div>

    <v-btn size="small" variant="outlined" @click="addItem">Add item</v-btn>
  </div>
</template>
