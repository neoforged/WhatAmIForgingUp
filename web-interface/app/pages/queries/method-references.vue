<template>
  <v-container>
    <query-component
        :on-load="load"
        :parameters="queryClient.parameters">
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
                    clearable
                    v-model="groups.groupBy.value"
                    label="Group by"
                    density="compact"
                    :items="groups.names"
                    hide-details
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
        <div class="text-left">
          <b>Direct</b> references to the method with the given name, from the given class will be returned.
        </div>
      </template>
    </query-component>
    <mod-information-dialog :version="version" v-model:modId="selectedMod" />
    <file-source-dialog :version="version" v-model:selected-file="selectedFile" />
  </v-container>
</template>

<script setup lang="ts">
import QueryComponent from "~/components/query-component.vue";
import ModInformationDialog from "~/components/mod-information-dialog.vue";
import {type FileSelection, getClassSourceFileName, grouper} from "~/utils/utils";
import {METHOD_REFERENCES} from "~~/graphql-requests/methods";
import {classSearch, methodSearch} from "~/utils/autocomplete";
import AutocompleteInput from "~/components/form/autocomplete-input.vue";
import {queryToPredicate, stringQueryParameter} from "~/utils/query-utils";

definePageMeta({
  title: 'Method References Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const clazz = queryClient.defineParameter('class', stringQueryParameter({
  label: 'Class',
  placeholder: 'com.example.ExampleClass',
  autocomplete: classSearch(version)
}))
const method = queryClient.defineParameter('method', stringQueryParameter({
  label: 'Method',
  placeholder: 'exampleMethod',
  autocomplete: methodSearch(version, clazz)
}))

const selectedMod = ref<number>()
const selectedFile = ref<{
  mod: number,
  file: FileSelection
}>()

const items = ref([] as any[])
const loading = ref(true)
const search = ref(undefined)

const headers = [
  {title: 'Mod', key: 'mod.name'},
  {title: 'Class', key: 'cls'},
  {title: 'Referenced Method', key: 'mtd'}
]

const groups = grouper(headers)

const load = () => {
  loading.value = true
  fetchWithVersion(queryClient.apollo, METHOD_REFERENCES, {
    class: clazz.value!!.replaceAll('.', '/'),
    methodFilter: {
      name: queryToPredicate(method.value)
    }
  }, version.value)
      .then((result) => {
        const newValues = [] as any[]
        result?.gameVersion?.class?.methods?.forEach(mtd => {
          mtd.references.forEach(ref => {
            newValues.push({
              mod: ref.owner.mod,
              cls: ref.owner.name,
              mtd: mtd.name + mtd.descriptor
            })
          })
        })
        items.value = newValues
        loading.value = false
      })
}

</script>
