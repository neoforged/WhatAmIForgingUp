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
                  items-per-page="25"
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
            :loading="loading"
            density="compact"
            variant="text"
            class="mr-1"
            v-tooltip="'Download data'"
            size="30"
            rounded
            @click="download()"
        >
          <v-icon icon="mdi-download-circle" size="30" />
        </v-btn>
      </template>
    </v-data-table>
  </v-card>
</template>
<script setup lang="ts">
import {render, type TableColumn} from "~/components/table/results-table-api";
import {grouper} from "~/utils/utils";

const props = defineProps<{
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
  const url = URL.createObjectURL(blob)

  const a = document.createElement("a");
  a.href = url;
  a.download = "data.json";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);

  URL.revokeObjectURL(url);
}

const search = ref<string | undefined>(undefined)
const groups = grouper(props.columns.map((c, idx) => ({
  key: `col_${idx}`,
  title: c.title,
  groupable: c.groupable
})).filter(c => c.groupable))
</script>
