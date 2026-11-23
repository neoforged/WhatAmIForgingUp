<template>
  <div v-if="!dialog">
    <v-card class="parameters-card">
      <v-card-item class="parameters-header">
        <v-card-title class="parameters-title">Parameters</v-card-title>
        <template v-slot:append>
        <v-btn
            prepend-icon="mdi-pencil-outline"
            density="comfortable"
            variant="outlined"
            color="secondary"
            @click="modify"
        >Modify
        </v-btn>
        </template>
      </v-card-item>
      <v-divider />
      <v-card-text class="parameters-content">
        <div v-for="p in parameters" :key="p.key" class="parameter-row">
          <span class="parameter-label">{{ p.type.label }}</span>
          <span class="parameter-value">
            <component v-if="p.humanReadable" :is="p.humanReadable" />
            <span v-else>{{ p.value.value }}</span>
          </span>
        </div>
      </v-card-text>
    </v-card>
    <br/>
    <slot name="result-display"></slot>
  </div>
  <v-dialog eager v-model="dialog" persistent width="min(760px, calc(100vw - 32px))">
    <v-card
        class="query-dialog-card"
    >
      <v-card-item class="query-dialog-header">
        <template v-slot:prepend>
          <v-avatar size="40" rounded="10" color="light-green" variant="tonal">
            <v-icon icon="mdi-database-search" size="21" />
          </v-avatar>
        </template>
        <v-card-title>Configure query</v-card-title>
        <v-card-subtitle>{{ routeTitle }}</v-card-subtitle>
      </v-card-item>
      <v-divider />
      <v-form @submit.prevent class="query-dialog-form">
        <div class="query-dialog-fields">
          <component v-for="p in parameters" :is="p.formComponent" :key="p.key" />
          <slot name="form"></slot>
        </div>
        <v-divider />
        <v-card-actions class="query-dialog-actions">
          <v-btn
              type="submit"
              color="primary"
              :disabled="!allParametersSet()"
              @click="submit"
              prepend-icon="mdi-play"
              class="run-query-button"
          >Run query</v-btn>
        </v-card-actions>
      </v-form>
    </v-card>
  </v-dialog>
  <mod-information-dialog :version="version" v-model:modId="selectedMod" />
  <file-source-dialog :version="version" v-model:selected-file="selectedFile" />
</template>

<script setup lang="ts">
import type {QueryParameter} from "~/query/query-parameters";
import ModInformationDialog from "~/components/mod-information-dialog.vue";
import {useFileSelection, useModSelection} from "~/utils/globals";

const route = useRoute()
const routeTitle = computed(() => route.meta.title ?? 'Query parameters')

const props = defineProps<{
  parameters: QueryParameter<any>[]
  onLoad: () => void
}>()

// The version is always the first parameter
const version = (props.parameters[0] as QueryParameter<string>).value

const selectedMod = useModSelection()
const selectedFile = useFileSelection()

const dialog = ref(false)

const submit = async () => {
  dialog.value = false
  props.onLoad!!()
}

const allParametersSet = () => {
  for (const param of props.parameters) {
    if (!param.optional && !(param.value.value as boolean)) return false
  }
  return true
}

const modify = () => {
  dialog.value = true
}

if (allParametersSet()) {
  props.onLoad!!()
  dialog.value = false
} else {
  dialog.value = true
}
</script>

<style scoped>
.parameters-card {
  border-radius: 8px;
  background: var(--waifu-card);
}

.parameters-header {
  min-height: 48px;
  padding: 6px 12px 6px 16px;
}

.parameters-title {
  font-size: 0.95rem;
  font-weight: 700;
}

.parameters-content {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px 24px;
  padding: 10px 16px 12px;
}

.parameter-row {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(72px, auto) minmax(0, 1fr);
  align-items: baseline;
  gap: 8px;
}

.parameter-label {
  color: rgba(255, 255, 255, 0.52);
  font-size: 0.82rem;
}

.parameter-value {
  min-width: 0;
  font-size: 0.88rem;
  overflow-wrap: anywhere;
}

.query-dialog-card {
  max-height: calc(100vh - 32px);
  border-radius: 14px;
  overflow: hidden;
}

.query-dialog-header {
  min-height: 74px;
  padding: 12px 16px;
}

.query-dialog-header :deep(.v-card-title) {
  font-size: 1.12rem;
  font-weight: 700;
}

.query-dialog-header :deep(.v-card-subtitle) {
  color: rgba(255, 255, 255, 0.52);
}

.query-dialog-form {
  max-height: calc(100vh - 140px);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.query-dialog-fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
}

.query-dialog-actions {
  justify-content: flex-end;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.015);
}

.run-query-button {
  min-width: 132px;
}

:deep(.v-dialog) {
  align-items: center;
}

@media (max-width: 600px) {
  .parameters-header {
    padding-inline: 10px 6px;
  }

  .parameters-content {
    grid-template-columns: 1fr;
    padding-inline: 12px;
  }

  .query-dialog-card {
    max-height: calc(100vh - 24px);
    border-radius: 12px;
  }

  .query-dialog-form {
    max-height: calc(100vh - 132px);
  }

  .query-dialog-fields {
    padding: 12px;
  }

  .query-dialog-actions {
    padding: 10px 12px;
  }

  .run-query-button {
    width: 100%;
  }
}
</style>
