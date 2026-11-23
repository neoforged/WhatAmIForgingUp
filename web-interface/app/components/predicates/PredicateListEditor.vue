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
    <div v-if="(modelValue?.length ?? 0) === 0" class="empty-list text-medium-emphasis">
      No predicates added.
    </div>

    <div v-for="(item, idx) in (modelValue ?? [])" :key="idx" class="mb-2">
      <v-card variant="outlined" class="list-item-card pa-2">
        <div class="list-item-actions">
          <v-btn size="small" variant="text" color="error" prepend-icon="mdi-delete-outline" @click="removeItem(idx)">Remove</v-btn>
        </div>

        <PredicateFieldEditor
            :type="itemType"
            :modelValue="item"
            :meta="meta"
            :hint-object="hintObject"
            @update:modelValue="(v: any) => updateItem(idx, v)"
        />
      </v-card>
    </div>

    <v-btn size="small" class="ma-2" variant="tonal" color="light-green" prepend-icon="mdi-plus" @click="addItem">Add predicate</v-btn>
  </div>
</template>

<style scoped>
.list-item-card {
  border-radius: 10px;
}

.list-item-actions {
  display: flex;
  justify-content: flex-end;
  min-height: 32px;
}

.empty-list {
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px dashed rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  font-size: .9rem;
}
</style>
