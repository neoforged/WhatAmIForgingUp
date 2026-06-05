<template>
  <v-dialog v-model="showDialog" max-width="500">
    <v-card :title="title ?? 'Mod Information'" :loading="loading">
      <template v-slot:subtitle v-if="!loading">
        <i>by <b>{{ authors }}</b></i> | {{ license }}<br/>
        Version {{ modVersion }} for {{ version }}
      </template>
      <template v-slot:text v-if="!loading">
        <p>{{ platformProject?.description }}</p>
        <p v-if="modIds?.length ?? 0 > 0">Mod IDs: <code v-for="mid of modIds" class="text-cyan">{{ mid }}</code></p>
        <p v-if="mavenCoordinates">Maven Coordinates: <code>{{ mavenCoordinates }}</code></p>
        <p v-if="curseforge">CurseForge Project: <a :href="curseforge?.projectUrl" target="_blank">{{ curseforge?.title }}</a></p>
        <p v-if="modrinth">Modrinth Project: <a :href="modrinth?.projectUrl" target="_blank">{{ modrinth?.title }}</a></p>
        <p v-if="declaredDependencies?.length ?? 0 > 0">
          Declared dependencies:
          <span v-for="dep of declaredDependencies">
            <br />
            - <code class="text-blue">{{ dep.modId }}</code> ({{ dep.versionRange }} <b v-if="dep.type">{{ dep.type }}</b>)
          </span>
        </p>
        <v-chip-group column>
          <v-chip :disabled="!platformProject?.sourceUrl"
                  :href="platformProject?.sourceUrl ?? undefined"
                  target="_blank"
                  text="Source"
                  append-icon="mdi-open-in-new"/>
          <v-chip :disabled="!platformProject?.issuesUrl && !metadata?.issueTrackerURL"
                  :href="platformProject?.issuesUrl ?? metadata?.issueTrackerURL ?? undefined"
                  target="_blank"
                  text="Issues"
                  append-icon="mdi-open-in-new"/>
        </v-chip-group>
      </template>

      <template v-slot:append>
        <v-avatar v-if="platformProject?.iconUrl" rounded="0" :size="sm ? '48' : '64'">
          <v-img :src="platformProject?.iconUrl" />
        </v-avatar>
      </template>

      <template v-slot:actions>
        <v-btn
            text="Close"
            @click="close"
            variant="tonal"
        ></v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import {fetchWithVersion} from "~/utils/graphql-utils";
import {MOD_INFORMATION} from "~~/graphql-requests/mods";
import {useApolloClient} from "@vue/apollo-composable";
import type {PlatformInformationFragment} from "~~/graphql-requests/types/__generated__/graphql";
import {useDisplay} from "vuetify/framework";

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

const sm = useDisplay().smAndDown

const loading = ref(false)

const title = ref(null as string | null)
const modIds = ref(null as string[] | null)
const metadata = ref(null as any | null)
const authors = ref(null as string | null)
const license = ref(null as string | null)
const modVersion = ref(null as string | null)
const platformProject = ref(null as PlatformInformationFragment | null)
const curseforge = ref(null as PlatformInformationFragment | null)
const modrinth = ref(null as PlatformInformationFragment | null)
const mavenCoordinates = ref(null as string | null)

const declaredDependencies = ref(null as {modId: string, versionRange: string, type?: string}[] | null)

const showDialog = ref(false)

const merge = (a?: PlatformInformationFragment | null, b?: PlatformInformationFragment | null): PlatformInformationFragment | null => {
  if (!a && !b) return null
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
  if (newValue == null) return

  showDialog.value = true
  loading.value = true
  fetchWithVersion(client, MOD_INFORMATION, {
    id: newValue!!
  }, props.version!!)
      .then(result => {
        loading.value = false
        const mod = result?.gameVersion?._modInformation!!

        title.value = mod.name
        modIds.value = mod.modIds
        metadata.value = mod.metadata
        authors.value = mod.authors
        license.value = mod.license
        modVersion.value = mod.version
        mavenCoordinates.value = mod.mavenCoordinates
        modrinth.value = mod.modrinth
        curseforge.value = mod.curseforge

        if (props.version!!.toLowerCase().endsWith('-fabric')) {
          declaredDependencies.value = Object.entries(metadata.value?.depends ?? {})
              .map(([key, value]) => {
                return {
                  modId: key,
                  versionRange: value as string,
                  type: 'required' // TODO - the other dep types
                }
              })
        } else {
          declaredDependencies.value = Object.values(metadata.value?.dependencies ?? {})
              .flatMap((entry: any) => entry)
              .map((dependency: any) => {
                return {
                  ...dependency,
                  type: dependency.type ?? 'required'
                }
              })
        }

        platformProject.value = merge(mod.curseforge, mod.modrinth)
      })
})

watch(showDialog, newValue => {
  if (newValue == false) {
    modId.value = undefined;
    [title, modIds, metadata, authors, license, modVersion, platformProject, mavenCoordinates, curseforge, modrinth, declaredDependencies].forEach(v => v.value = null)
  }
})

const close = () => {
  showDialog.value = false
}
</script>
