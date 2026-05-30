<template>
  <v-container>
    <query-component
        v-model:version="version"
        :on-load="load"
        :on-reset="() => loading = true"
        :additionalProperties="{'Recipe type': recipeType}">
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
                        items-per-page="10"
          >
            <template v-for="head in headers" v-slot:[`header.${head.key}`]="{ column }">
              <b>{{ column.title }}</b>
            </template>
            <template v-slot:item.mod.name="{ item, value }">
              <v-btn density="compact" color="primary" variant="text" border="false" @click="selectedMod = item.mod.id">{{ value }}</v-btn>
            </template>
            <template v-slot:item.name="{ item, value }">
              <span @click="selectedFile = {mod: item.mod.id, file: getRecipeSourceFileName(item.name)}">{{ value }}</span>
            </template>
            <template v-slot:item.recipe="{ item, value }">
              <code-block class="pa-1" language="json" :code="JSON.stringify(item.recipe, null, 2)" />
            </template>
          </v-data-table>
        </v-card>
      </template>

      <template v-slot:form>
        <v-text-field
            v-model="recipeType"
            label="Recipe type"
            placeholder="minecraft:crafting_shaped"
            density="compact"
            variant="underlined"
        />
        <div class="text-left">
          Recipes of the given type will be returned.
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
import {type FileSelection, getRecipeSourceFileName} from "~/utils/utils";
import {RECIPES} from "~~/graphql-requests/recipes";

definePageMeta({
  title: 'Recipes Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const recipeType = queryClient.queryParam('recipetype')

const selectedMod = ref<number>()
const selectedFile = ref<{
  mod: number,
  file: FileSelection
}>()

const items = ref([] as any[])
const loading = ref(true)
const search = ref(undefined)
const groupBy = ref(undefined)

const headers = [
  {title: 'Mod', key: 'mod.name'},
  {title: 'Recipe name', key: 'name'},
  {title: 'Recipe content', key: 'recipe'}
]
const groupByConfiguration = computed<DataTableSortItem[]>(() => {
  if (!groupBy.value || groupBy.value == 'None') {
    return []
  } else {
    return [{key: 'mod.name'}]
  }
})

const load = () => {
  queryClient.fetchPaginated(RECIPES, {
    predicate: {
      type: {
        equals: recipeType.value!!
      }
    }
  }, data => data.gameVersion?.recipes!!)
      .then((values) => {
        items.value = values!!
        loading.value = false
      })
}

</script>
