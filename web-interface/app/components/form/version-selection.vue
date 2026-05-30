<template>
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
      <v-list-item v-bind="itemProps" :subtitle="item.active ? undefined : 'No longer updated, data is stale.'"
                   :append-avatar="loaderToLogo(item.value)"/>
    </template>
  </v-select>
</template>

<script setup lang="ts">
const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits(["update:modelValue"]);

const version = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val)
});

const availableVersions = ref([] as { value: string, title: string; active: boolean; }[])

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

populateVersions()

</script>
