<template>
  <v-container>
    <query-component
        v-model:version="version"
        :on-load="load"
        :on-reset="() => loading = true"
        :additionalProperties="{'Class': clazz}">
      <template v-slot:result-display>
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
                    v-model="groupBy"
                    label="Group by"
                    density="compact"
                    :items="['None', 'Mod']"
                    hide-details
                />
              </v-col>
            </v-row>
          </template>

          <v-data-table density="compact"
                        :items="items"
                        :loading="loading"
                        :search="search"
                        :group-by="groupByConfiguration"
                        :headers="headers"
                        items-per-page="25"
          >
            <template v-for="head in headers" v-slot:[`header.${head.key}`]="{ column }">
              <b>{{ column.title }}</b>
            </template>
            <template v-slot:item.mod.name="{ item, value }">
              <v-btn density="compact" color="primary" variant="text" border="false" @click="selectedMod = item.mod.id">{{ value }}</v-btn>
            </template>
            <template v-slot:item.cls="{ item, value }">
              <span @click="selectedFile = {mod: item.mod.id, file: getClassSourceFileName(item.cls)}">{{ value }}</span>
            </template>
          </v-data-table>
        </v-card>
      </template>

      <template v-slot:form>
        <v-text-field
            v-model="clazz"
            label="Class"
            placeholder="com.example.ExampleClass"
            density="compact"
            variant="underlined"
        />
        <div class="text-left">
          Implementations of the above class will be returned. To the greatest extent possible, indirect implementations (A extends B - which extends the class) will also be returned.
        </div>
      </template>
    </query-component>
    <mod-information-dialog :version="version" v-model:modId="selectedMod" />
    <file-source-dialog :version="version" v-model:selected-file="selectedFile" />
  </v-container>
</template>

<script setup lang="ts">
import QueryComponent from "~/components/query-component.vue";
import type {DataTableSortItem} from "vuetify";
import ModInformationDialog from "~/components/mod-information-dialog.vue";
import {IMPLEMENTATIONS} from "~~/graphql-requests/classes";
import {getClassSourceFileName} from "~/utils/utils";

definePageMeta({
  title: 'Implementations Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const clazz = queryClient.queryParam('class')

const selectedMod = ref<number>()
const selectedFile = ref<{
  mod: number,
  file: string
}>()

const items = ref([] as any[])
const loading = ref(true)
const search = ref(undefined)
const groupBy = ref(undefined)

const headers = [
  {title: 'Mod', key: 'mod.name'},
  {title: 'Class', key: 'cls'}
]
const groupByConfiguration = computed<DataTableSortItem[]>(() => {
  if (!groupBy.value || groupBy.value == 'None') {
    return []
  } else {
    return [{key: 'mod.name'}]
  }
})

const load = () => {
  fetchWithVersion(queryClient.apollo, IMPLEMENTATIONS, {
    class: clazz.value!!.replaceAll('.', '/')
  }, version.value)
      .then((result) => {
        const newValues = [] as any[]
        result?.gameVersion?.class?.inheritors?.forEach(inh => {
          inh.definitions.forEach(def => {
            newValues.push({
              mod: def.mod,
              cls: inh.name.replaceAll('/', '.')
            })
          })
        })
        items.value = newValues
        loading.value = false
      })
}

</script>
