<template>
  <div v-if="!dialog">
    <v-card
        title="Parameters"
        density="compact"
    >
      <template v-slot:append>
        <v-btn
            append-icon="mdi-pencil"
            color="primary"
            density="compact"
            variant="text"
            rounded
            @click="modify"
        >Modify
        </v-btn>
      </template>
      <v-chip-group class="pa-2">
        <v-chip v-for="p in parameters">{{ p.type.label }}:
          <span>&nbsp;</span>
          <component v-if="p.humanReadable" :is="p.humanReadable" />
          <span v-else>{{ p.value.value }}</span>
        </v-chip>
      </v-chip-group>
    </v-card>
    <br/>
    <slot name="result-display"></slot>
  </div>
  <v-dialog eager v-model="dialog" persistent width="auto" class="text-center">
    <v-card
        max-width="800"
        prepend-icon="mdi-database-search"
        title="Configure query"
    >
      <v-form @submit.prevent class="pa-4">
        <component v-for="p in parameters" :is="p.formComponent" />
        <slot name="form"></slot>
        <v-btn class="mt-2" type="submit" color="primary" :disabled="!allParametersSet()" @click="submit" block>Submit</v-btn>
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
