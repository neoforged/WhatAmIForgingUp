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
        >Modify</v-btn>
      </template>
      <v-chip-group class="pa-2">
        <v-chip>Version: {{ version }}</v-chip>
        <v-chip v-for="key in Object.keys(props.additionalProperties)">{{ key }}: {{ additionalProperties[key] }}</v-chip>
      </v-chip-group>
    </v-card>
    <br />
    <slot name="result-display"></slot>
  </div>
  <v-dialog v-model="dialog" width="auto" class="text-center" v-else>
    <v-card
        max-width="800"
        prepend-icon="mdi-database-search"
        title="Configure query"
    >
      <v-form @submit.prevent class="pa-4">
        <v-select
            v-model="version"
            label="Version"
            density="compact"
            variant="underlined"
            :loading="availableVersions.length == 0"
            :disabled="availableVersions.length == 0"
            :items="availableVersions"
        >
          <template v-slot:item="{ props: itemProps, item }">
            <v-list-item v-bind="itemProps" :subtitle="item.active ? undefined : 'No longer updated, data is stale.'" :append-avatar="loaderToLogo(item.value)" />
          </template>
        </v-select>
        <slot name="form"></slot>
        <v-btn class="mt-2" type="submit" color="primary" :disabled="!version" @click="submit" block>Submit</v-btn>
      </v-form>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
const props = defineProps<{
  version?: string,
  additionalProperties: Record<string, any>,
  validationRules?: any,
  onLoad: () => void,
  onReset: () => void
}>()

const emit = defineEmits(["update:version"]);

const version = computed({
  get: () => props.version,
  set: (val) => emit("update:version", val)
});

let allSet: boolean = version.value != undefined && version.value != ''
Object.keys(props.additionalProperties).forEach(key => allSet = allSet && props.additionalProperties[key] as boolean)

const dialog = ref(!allSet)

const availableVersions = ref([] as {value: string, title: string}[])

const populateVersions = () => {
  getAllVersions().then(ver => availableVersions.value = ver.map(v => ({
    value: v.gameVersion + '-' + v.loader,
    title: v.gameVersion + ' ' + v.loader,
    active: v.activelyIndexed
  })))
}

const loaderToLogo = (version: string) => {
  const loader = version.split('-')[1]!!.toLowerCase()
  if (loader == 'neoforge') {
    return 'https://github.com/neoforged.png'
  } else if (loader == 'fabric') {
    return 'https://github.com/fabricmc.png'
  }
  return 'https://github.com/minecraftforge.png'
}

if (dialog.value) {
  populateVersions()
}

const submit = async () => {
  dialog.value = false
  props.onLoad!!()
}

const modify = () => {
  dialog.value = true
  if (availableVersions.value.length == 0) populateVersions()
  props.onReset!!()
}

if (!dialog.value) {
  props.onLoad!!()
}
</script>
