<script setup lang="ts">
import { computed } from "vue";
import PredicateFieldEditor from "./PredicateFieldEditor.vue";

const props = defineProps<{
  typeName: string;
  modelValue: Record<string, any> | undefined;
  meta: Record<string, PredicateType>;
  clearable?: boolean;

  hintObject?: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [Record<string, any>];
}>();

const typeMeta = computed(() => props.meta[props.typeName]);
const fields = computed(() => typeMeta.value?.fields ?? ({} as Record<string, PredicateField>));

const activeFieldName = computed<string | null>(() => {
  const keys = Object.keys(props.modelValue ?? {});
  return keys.length ? keys[0]!! : null;
});

const activeField = computed<PredicateField | undefined>(() => {
  return activeFieldName.value ? typeMeta.value?.fields[activeFieldName.value!!] : undefined
});

const fieldItems = computed(() => {
  const hintObject = props.hintObject
  return Object.entries(fields.value).map(([key, value]) => ({
    title: `${value.name}`,
    value: key,
    props: {
      subtitle: value.hint?.replaceAll('{object}', hintObject ?? 'The value')
    }
  }))
});

function chooseField(fieldName: string | null) {
  if (!fieldName) {
    emit("update:modelValue", {});
    return;
  }
  const f = fields.value[fieldName]
  if (!f) return;
  emit("update:modelValue", { [fieldName]: getDefaultValue(f.type) });
}
</script>

<template>
  <v-card variant="outlined" class="predicate-builder-card">
    <v-select
        label="Predicate kind"
        :items="fieldItems"
        :model-value="activeFieldName"
        @update:model-value="chooseField"
        :clearable="clearable"
        variant="outlined"
        density="comfortable"
        hide-details
    />

    <div v-if="activeFieldName" class="predicate-field">
      <div v-if="activeField?.hint" class="predicate-hint">
        {{ activeField.hint.replaceAll('{object}', hintObject ?? 'The value') }}
      </div>
      <PredicateFieldEditor
          :type="activeField!!.type"
          :meta="meta"
          v-model="modelValue[activeFieldName]"
          :hint-object="activeField!!.object"
          @update:modelValue="(v) => emit('update:modelValue', { [activeFieldName]: v })"
      />
    </div>
  </v-card>
</template>

<style scoped>
.predicate-builder-card {
  border-radius: 10px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.018);
}

.predicate-field {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}

.predicate-hint {
  margin-bottom: 10px;
  color: rgba(255, 255, 255, 0.58);
  font-size: 0.82rem;
  line-height: 1.45;
}
</style>
