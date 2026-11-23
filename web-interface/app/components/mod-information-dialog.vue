<template>
  <v-dialog v-model="showDialog" width="min(520px, calc(100vw - 24px))">
    <v-card :loading="loading" class="mod-dialog-card">
      <v-card-item class="mod-dialog-header">
        <template v-slot:prepend>
          <v-avatar v-if="platformProject?.iconUrl" class="mod-icon" :size="sm ? 44 : 56" rounded="8">
            <v-img :src="platformProject.iconUrl" :alt="title ?? 'Mod icon'" />
          </v-avatar>
          <v-avatar v-else class="mod-icon mod-icon-placeholder" :size="sm ? 44 : 56" rounded="8">
            <v-icon icon="mdi-nintendo-game-boy" :size="sm ? 22 : 26" />
          </v-avatar>
        </template>

        <v-card-title class="mod-title">{{ title ?? 'Mod Information' }}</v-card-title>
        <v-card-subtitle v-if="!loading" class="mod-subtitle">
          <span v-if="authors">by <strong>{{ authors }}</strong></span>
          <span v-if="authors && license" class="secondary-text separator">·</span>
          <span v-if="license">{{ license }}</span>
        </v-card-subtitle>
      </v-card-item>

      <v-divider />

      <v-card-text v-if="!loading" class="mod-dialog-content">
        <p v-if="platformProject?.description || metadata?.description" class="mod-description">
          {{ platformProject?.description ?? metadata.description }}
        </p>

        <div v-if="version" class="content-section">
          <span class="section-label">Version:</span>
          <code v-if="modVersion" class="inline-value">{{ modVersion }}</code>
          <span v-if="modVersion" class="secondary-text">for {{ version }}</span>
        </div>

        <div v-if="mavenCoordinates" class="content-section">
          <span class="section-label">Maven coordinates:</span>
          <code class="inline-value">{{ mavenCoordinates }}</code>
        </div>

        <div v-if="modIds?.length" class="content-section">
          <span class="section-label">Mod IDs:</span>
          <code v-for="mid of modIds" :key="mid" class="text-cyan inline-value">{{ mid }}</code>
        </div>

        <div v-if="declaredDependencies?.length" class="content-section">
          <div class="section-label">Declared dependencies</div>
          <div class="dependency-list">
            <div v-for="dep of declaredDependencies" :key="`${dep.modId}-${dep.versionRange}-${dep.type}`" class="dependency-row">
              <code class="text-blue">{{ dep.modId }}</code>
              <span class="dependency-range secondary-text">{{ dep.versionRange }}</span>
              <span v-if="dep.type" class="dependency-type secondary-text">{{ dep.type }}</span>
            </div>
          </div>
        </div>

        <div class="content-section">
          <div class="project-links">
            <v-btn
                v-if="curseforge"
                :href="curseforge.projectUrl"
                target="_blank"
                size="small"
                variant="outlined"
                color="light-green"
                append-icon="mdi-open-in-new"
            >CurseForge</v-btn>
            <v-btn
                v-if="modrinth"
                :href="modrinth.projectUrl"
                target="_blank"
                size="small"
                variant="outlined"
                color="light-green"
                append-icon="mdi-open-in-new"
            >Modrinth</v-btn>
            <v-btn
                v-if="platformProject?.sourceUrl"
                :href="platformProject.sourceUrl"
                target="_blank"
                size="small"
                variant="text"
                prepend-icon="mdi-code-tags"
                append-icon="mdi-open-in-new"
            >Source</v-btn>
            <v-btn
                v-if="platformProject?.issuesUrl || metadata?.issueTrackerURL"
                :href="platformProject?.issuesUrl ?? metadata.issueTrackerURL"
                target="_blank"
                size="small"
                variant="text"
                prepend-icon="mdi-bug-outline"
                append-icon="mdi-open-in-new"
            >Issues</v-btn>
          </div>
        </div>
      </v-card-text>

      <v-divider />
      <v-card-actions class="mod-dialog-actions">
        <v-btn text="Close" @click="close" variant="tonal" />
      </v-card-actions>
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
    ids: [newValue],
    includeDownloadUrl: false
  }, props.version!!)
      .then(result => {
        loading.value = false
        const mod = result?.gameVersion?._modInformation[0]!

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

<style scoped>
.mod-dialog-card {
  max-height: calc(100vh - 32px);
  border-radius: 12px;
  overflow: hidden;
}

.mod-dialog-header {
  min-height: 78px;
  padding: 14px 16px;
}

.mod-icon {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.03);
}

.mod-icon-placeholder {
  color: var(--waifu-accent);
}

.mod-title {
  line-height: 1.25;
  white-space: normal;
  overflow-wrap: anywhere;
  padding-inline: 2px 8px;
  font-size: 1.18rem;
  font-weight: 700;
}

.mod-subtitle {
  min-height: 20px;
  padding-inline: 2px 8px;
  color: rgba(255, 255, 255, 0.62);
  white-space: normal;
}

.separator {
  margin: 0 6px;
}

.mod-dialog-content {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 16px 16px 18px;
}

.mod-description {
  margin: 0 0 14px;
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.55;
  white-space: pre-line;
}

.secondary-text {
  color: rgba(255, 255, 255, 0.46);
}

.secondary-text:not(.separator) {
  margin-inline: 6px;
}

.inline-value {
  color: rgb(51 158 230);
  margin-left: 6px;
  overflow-wrap: anywhere;
}

.content-section {
  margin-top: 18px;
}

.section-label {
  color: rgba(255, 255, 255, 0.58);
  font-size: .95rem;
  font-weight: 550;
}

.project-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dependency-list {
  margin-top: 5px;
}

.dependency-row {
  display: grid;
  grid-template-columns: minmax(100px, 1fr) minmax(120px, 1.2fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 4px 0;
}

.dependency-range {
  font-size: 0.86rem;
  overflow-wrap: anywhere;
}

.dependency-type {
  justify-self: end;
  font-size: 0.78rem;
}

.dependency-row .secondary-text {
  margin-inline: 0;
}

.mod-dialog-actions {
  justify-content: flex-end;
  padding: 10px 16px;
}

@media (max-width: 600px) {
  .mod-dialog-card {
    max-height: calc(100vh - 24px);
    border-radius: 12px;
  }

  .mod-dialog-content {
    max-height: calc(100vh - 176px);
    padding: 14px 12px 16px;
  }
}
</style>
