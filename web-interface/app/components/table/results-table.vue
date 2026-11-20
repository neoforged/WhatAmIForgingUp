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

        <v-col sm="4" cols="12">
          <v-select
              v-model="groups.groupBy.value"
              label="Group by"
              density="compact"
              :items="groups.names"
              hide-details
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
                  v-model:itemsPerPage="itemsPerPage"
                  v-model:page="page"
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
  </v-card>
</template>
<script setup lang="ts">
import {render, type TableColumn} from "~/components/table/results-table-api";
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

const headers = props.columns.map((col, idx) => {
  return {
    title: col.title,
    value: col.value,
    key: `col_${idx}`
  }
})

const itemsPerPage = ref(25)
const page = ref(1)

const computedItems = computed(() => props.items.map(it => {
  const newObject = {...it}
  props.columns.forEach((col, idx) => {
    if (col.groupable) {
      newObject[`col_${idx}`] = col.value(it)
    }
  })
  return newObject
}))

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

    for (let i = (page.value - 1) * itemsPerPage.value; i < Math.min(props.items.length, page.value * itemsPerPage.value); i++) {
      const item = props.items[i]
      const source = column.sourceDownloader(item, column.value(item))
      if (source) {
        selections.push(source)
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
      const modId = modIds[i]
      const mod = mods[i]
      if (!mod) continue

      const zip = await downloadZip(`${getBaseUrl()}/api/internal/cdn-proxy?url=${mod.downloadUrl}`)
      for (let value of selections.filter(s => s.mod == modId)) {
        const result: any = await value.selection.binaryExtractor!!(zip)
        if (result.content) {
          sourcesZip.file(modId + (mod.modId ? '-' + mod.modId : '') + '_' + value.selection.name.replaceAll('/', '_'), result.content)
        }
      }
    }

    const blob = await sourcesZip.generateAsync({ type: 'blob' })
    browserDownload(blob, 'data.zip')
    loadingSourcesDownload.value = false
  }).catch(_ => loadingSourcesDownload.value = false)
}

const search = ref<string | undefined>(undefined)
const groups = grouper(props.columns.map((c, idx) => ({
  key: `col_${idx}`,
  title: c.title,
  groupable: c.groupable
})).filter(c => c.groupable))
</script>
