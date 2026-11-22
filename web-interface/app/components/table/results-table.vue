<template>
  <v-card title="Results" flat>
    <template v-slot:text>
      <v-row>
        <v-col sm="8" cols="12">
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
        </v-col>

        <v-col :sm="additionalColumns.length > 0 ? 2 : 4" cols="12">
          <v-select
              v-model="groups.groupBy.value"
              label="Group by"
              density="compact"
              :items="groups.names"
              hide-details
              clearable
          />
        </v-col>

        <v-col sm="2" cols="12">
          <v-select
              v-if="additionalColumns.length > 0"
              v-model="selectedAdditionalColumns"
              label="Additional columns"
              :items="additionalColumns"
              multiple
              return-object
              density="compact"
              clearable
          />
        </v-col>
      </v-row>
    </template>

    <v-data-table density="compact"
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
import {useApolloClient} from "@vue/apollo-composable";
import {fetchWithVersion} from "~/utils/graphql-utils";
import {MOD_DOWNLOAD_URLS} from "~~/graphql-requests/mods";
import {downloadZip, getBaseUrl} from "~/utils/api-utils";
import {addAlert} from "~/utils/alerts";

const client = useApolloClient().client

const props = defineProps<{
  version: string;
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
  nv.forEach((ac, i) => ac.values(client, props.items, props.version).then(res => additionalValues[i] = res))
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

  fetchWithVersion(client, MOD_DOWNLOAD_URLS, {
    ids: modIds
  }, props.version).then(async (data) => {
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
