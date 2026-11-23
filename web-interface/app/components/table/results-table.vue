<template>
  <v-card title="Results" flat class="results-card">
    <template v-slot:text>
      <div class="results-toolbar" :class="{ 'has-additional-columns': additionalColumns.length > 0 }">
        <div class="search-control">
          <v-text-field
              v-model="search"
              label="Search"
              prepend-inner-icon="mdi-magnify"
              variant="outlined"
              density="compact"
              hide-details
              single-line
              clearable
          ></v-text-field>
        </div>

        <div class="filter-control">
          <v-select
              v-model="groups.groupBy.value"
              label="Group by"
              variant="outlined"
              density="compact"
              :items="groups.names"
              hide-details
              clearable
          />
        </div>

        <div v-if="additionalColumns.length > 0" class="filter-control additional-control">
          <v-select
              v-model="selectedAdditionalColumns"
              label="Additional columns"
              variant="outlined"
              :items="additionalColumns"
              multiple
              return-object
              density="compact"
              hide-details
              clearable
          />
        </div>
      </div>
    </template>

    <div class="results-table-scroll">
    <v-data-table density="compact"
                  class="results-table"
                  :items="computedItems"
                  :loading="loading"
                  :search="search"
                  :group-by="groups.items.value"
                  :headers="headers"
                  items-per-page="25"
                  @update:current-items="value => currentItems = (value as any[]).flatMap(expandCurrentItem)"
    >
      <template v-for="head in headers" v-slot:[`header.${head.key}`]="{ column }">
        <b>{{ column.title }}</b>
      </template>
      <template v-for="(column, idx) in columns" v-slot:[`item.col_${idx}`]="{ item, value }">
        <component :is="render(column, item, value)" />
      </template>
      <template v-slot:footer.prepend>
        <v-btn
            v-bind="props"
            :loading="loading || loadingSourcesDownload"
            density="compact"
            variant="text"
            class="mr-1"
            v-tooltip="'Download and decompile classes on this page'"
            size="30"
            rounded
            @click="downloadSources()"
            v-if="columns.find(c => c.sourceDownloader)"
        >
          <v-icon icon="mdi-source-repository-multiple" size="25" />
        </v-btn>
        <v-btn
            v-bind="props"
            :loading="loading"
            density="compact"
            variant="text"
            class="mr-1"
            v-tooltip="'Download data'"
            size="30"
            rounded
            @click="download()"
        >
          <v-icon icon="mdi-file-download-outline" size="25" />
        </v-btn>
      </template>
    </v-data-table>
    </div>
    <v-snackbar v-model="displaySourcesDownloadProgress" location="bottom right">
      <v-progress
          value-format="[value] out of [max] mods"
          color="primary"
          label="Decompiling classes..."
          :max="sourcesDownloadProgress?.totalMods ?? 0"
          :model-value="sourcesDownloadProgress?.processedMods ?? 0"
      ></v-progress>
    </v-snackbar>
  </v-card>
</template>
<script setup lang="ts">
import {type AdditionalColumn, render, type TableColumn} from "~/components/table/results-table-api";
import {browserDownload, grouper} from "~/utils/utils";
import JSZip from "jszip";
import {fetchWithVersion} from "~/utils/graphql-utils";
import {MOD_DOWNLOAD_URLS} from "~~/graphql-requests/mods";
import {downloadZip, getBaseUrl} from "~/utils/api-utils";
import {addAlert} from "~/utils/alerts";

const props = defineProps<{
  client: QueryClient;
  columns: TableColumn<any, any>[],
  loading: boolean,
  items: any[]
}>()

const additionalColumns = props.columns.flatMap(ac => ac.additionalColumns ?? [])

const selectedAdditionalColumns = ref<AdditionalColumn<any>[]>([])

const headers = computed(() => {
  const headers = selectedAdditionalColumns.value.map((col, idx) => {
    return {
      title: col.title,
      key: `add_col_${idx}`
    }
  })
  props.columns.forEach((col, idx) => headers.push({
    title: col.title,
    value: col.value,
    key: `col_${idx}`
  } as any))
  return headers
})

const additionalValues = reactive<any[][]>([])
watch(selectedAdditionalColumns, (nv) => {
  while (additionalValues.length > 0) additionalValues.pop()
  nv.forEach((ac, i) => ac.values(props.client, props.items).then(res => additionalValues[i] = res))
})

const computedItems = computed(() => props.items.map((it, itemIndex) => {
  const newObject = {...it}
  props.columns.forEach((col, idx) => {
    if (col.groupable) {
      newObject[`col_${idx}`] = col.value(it)
    }
  })
  selectedAdditionalColumns.value.forEach((_, idx) => newObject[`add_col_${idx}`] = additionalValues.at(idx)?.at(itemIndex) ?? 'Loading...')
  return newObject
}))

const currentItems = ref<any[]>([])
function expandCurrentItem(item: any): any[] {
  if (item.type == 'group') {
    return (item.items as any[]).flatMap(expandCurrentItem)
  } else {
    return [item.raw]
  }
}

const sourcesDownloadProgress = ref<{
  totalMods: number;
  processedMods: number;
}>()
const displaySourcesDownloadProgress = computed(() => loadingSourcesDownload.value && sourcesDownloadProgress.value !== undefined)

const download = () => {
  const blob = new Blob([JSON.stringify(props.items, null, 2)], { type: 'application/json' })
  browserDownload(blob, 'data.json')
}

const loadingSourcesDownload = ref(false)

const downloadSources = async () => {
  if (loadingSourcesDownload.value) return
  loadingSourcesDownload.value = true

  const selections: {
    mod: number;
    selection: FileSelection;
  }[] = []

  for (let column of props.columns) {
    if (!column.sourceDownloader) continue

    const uniqueValues = new Set()

    for (let item of currentItems.value) {
      const value = column.value(item)
      if (!uniqueValues.has(value)) {
        uniqueValues.add(value)
        const source = column.sourceDownloader(item, value)
        if (source) {
          selections.push(source)
        }
      }
    }
  }

  const modIds = [...new Set(selections.map(s => s.mod))]
  if (modIds.length > 50) {
    addAlert({
      title: 'Too many results',
      description: 'Cannot decompile in bulk more than 50 mods! Reduce the page size and try again.'
    })
    loadingSourcesDownload.value = false
    return
  }

  fetchWithVersion(props.client.apollo, MOD_DOWNLOAD_URLS, {
    ids: modIds
  }, props.client.version.value).then(async (data) => {
    const mods = data?.gameVersion?._modDownloadUrls
    if (!mods) return
    const sourcesZip = new JSZip()

    for (let i = 0; i < modIds.length; i++) {
      sourcesDownloadProgress.value = {
        processedMods: i,
        totalMods: mods.length
      }

      const modId = modIds[i]
      const mod = mods[i]
      if (!mod) continue

      const zip = await downloadZip(`${getBaseUrl()}/api/internal/cdn-proxy?url=${mod.downloadUrl}`)
          .catch(reason => {
            console.log(`Failed to download: ${reason}`)
            return null
          })
      if (!zip) continue
      for (let value of selections.filter(s => s.mod == modId)) {
        const result: any = await value.selection.binaryExtractor!!(zip)
        if (result.content) {
          sourcesZip.file(modId + (mod.modId ? '-' + mod.modId : '') + '_' + value.selection.name.replaceAll('/', '_'), result.content)
        }
      }
    }

    const blob = await sourcesZip.generateAsync({ type: 'blob' })
    browserDownload(blob, 'sources.zip')
    loadingSourcesDownload.value = false
    sourcesDownloadProgress.value = undefined
  }).catch(_ => loadingSourcesDownload.value = false)
}

const search = ref<string | undefined>(undefined)
const groups = grouper(props.columns.map((c, idx) => ({
  key: `col_${idx}`,
  title: c.title,
  groupable: c.groupable
})).filter(c => c.groupable))
</script>

<style scoped>
.results-card {
  min-width: 0;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  overflow: hidden;
  background-color: var(--waifu-card);
}

.results-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(150px, 220px);
  align-items: end;
  gap: 10px;
  margin: 10px;
}

.results-toolbar.has-additional-columns {
  grid-template-columns: minmax(220px, 1fr) minmax(150px, 220px) minmax(150px, 220px);
}

.search-control,
.filter-control {
  min-width: 0;
  height: 40px;
}

.results-toolbar :deep(.v-input),
.results-toolbar :deep(.v-input__control),
.results-toolbar :deep(.v-field),
.results-toolbar :deep(.v-field__input) {
  height: 40px;
  min-height: 40px;
  margin: 0;
}

.results-toolbar :deep(.v-field__input) {
  align-items: center;
  padding-top: 8px;
  padding-bottom: 8px;
  overflow: hidden;
}

.results-toolbar :deep(.v-select__selection) {
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.results-toolbar :deep(.v-field) {
  border-radius: 8px;
}

.results-toolbar :deep(.v-field__outline) {
  --v-field-border-opacity: 0.14;
}

.results-table-scroll {
  width: 100%;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
}

.results-table {
  min-width: 640px;
  background-color: var(--waifu-card)
}

.results-table :deep(th) {
  background: rgba(255, 255, 255, 0.025);
  white-space: nowrap;
}

.results-table :deep(td) {
  white-space: nowrap;
}

/* Mod link styling */
.results-table :deep(.mod-link) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 0;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.results-table :deep(.mod-name) {
  text-decoration: underline;
  text-decoration-color: rgba(255, 255, 255, 0.24);
  text-underline-offset: 3px;
  text-decoration-thickness: 1px;
}

.results-table :deep(.mod-icon) {
  flex: 0 0 auto;
  color: rgba(255, 255, 255, 0.48);
  opacity: 0.72;
  transition: opacity 120ms ease, color 120ms ease;
}

.results-table :deep(.mod-link:hover .mod-name),
.results-table :deep(.mod-link:focus-visible .mod-name) {
  text-decoration-color: var(--waifu-accent);
}

.results-table :deep(.mod-link:hover .mod-icon),
.results-table :deep(.mod-link:focus-visible .mod-icon) {
  color: var(--waifu-accent);
  opacity: 1;
}

.results-table :deep(.mod-link:focus-visible) {
  outline: 2px solid var(--waifu-accent);
  outline-offset: 3px;
}

@media (max-width: 600px) {
  .results-card :deep(.v-card-text) {
    padding: 12px;
  }

  .results-toolbar {
    grid-template-columns: 1fr;
    gap: 8px;
    margin: 0;
  }

  .results-toolbar.has-additional-columns {
    grid-template-columns: 1fr;
  }

  .search-control,
  .filter-control,
  .additional-control {
    width: 100%;
  }

  .results-table {
    min-width: 560px;
  }
}
</style>
