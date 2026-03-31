<template>
  <v-dialog v-model="showDialog">
    <v-card :title="title ?? 'Mod Information'" :loading="loading">
      <v-card-text>
        {{ platformProject?.description }}
      </v-card-text>

      <template v-slot:append>
        <v-avatar v-if="platformProject?.iconUrl" rounded="0" size="64">
          <v-img :src="platformProject?.iconUrl" />
        </v-avatar>
      </template>

      <v-card-actions>
        <v-spacer></v-spacer>

        <v-btn
            text="Close"
            @click="close"
        ></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import {fetchWithVersion} from "~/utils/graphql-utils";
import {MOD_INFORMATION} from "~~/graphql-requests/mods";
import {useApolloClient} from "@vue/apollo-composable";
import type {PlatformInformationFragment} from "~~/graphql-requests/types/__generated__/graphql";

const props = defineProps<{
  version?: string,
  modId?: number
}>()
const emit = defineEmits(["update:modId"]);

const modId = computed({
  get: () => props.modId,
  set: (val) => emit("update:modId", val)
});

const client = useApolloClient().client

const loading = ref(false)

const title = ref(undefined as string | undefined)
const platformProject = ref(undefined as PlatformInformationFragment | undefined)

const showDialog = ref(false)

const merge = (a?: PlatformInformationFragment | null, b?: PlatformInformationFragment | null): PlatformInformationFragment | undefined => {
  if (!a && !b) return undefined
  if (a && !b) return a
  if (b && !a) return b

  const newObject = {...a} as any
  Object.keys(b as object).forEach((key) => {
    if (!newObject[key]) {
      newObject[key] = (b as any)[key]
    }
  })

  return newObject as PlatformInformationFragment
}

watch(modId, newValue => {
  if (newValue == undefined) return
  title.value = undefined
  platformProject.value = undefined

  showDialog.value = true
  loading.value = true
  fetchWithVersion(client, MOD_INFORMATION, {
    id: newValue!!
  }, props.version!!)
      .then(result => {
        loading.value = false
        const mod = result?.gameVersion?._modInformation!!

        title.value = mod.name

        platformProject.value = merge(mod.curseforge, mod.modrinth)
      })
})

const close = () => {
  showDialog.value = false
}
</script>
