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
                  :items="items"
                  :loading="loading"
                  :search="search"
                  :group-by="groups.items.value"
                  :headers="headers"
                  items-per-page="25"
    >
      <template v-for="head in headers" v-slot:[`header.${head.key}`]="{ column }">
        <b>{{ column.title }}</b>
      </template>
      <template v-for="(column, idx) in columns" v-slot:[`item.${idx}`]="{ item, value }">
        <component :is="render(column, item, value)" />
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
    key: idx.toString()
  }
})

const search = ref<string | undefined>(undefined)
const groups = grouper(props.columns.filter(c => c.groupable).map((c, idx) => ({
  key: idx.toString(),
  title: c.title
})))
</script>
