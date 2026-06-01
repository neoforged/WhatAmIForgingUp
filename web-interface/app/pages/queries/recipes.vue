<template>
  <div>
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
                    v-model="group.groupBy.value"
                    label="Group by"
                    density="compact"
                    :items="group.names"
                    hide-details
                />
              </v-col>
            </v-row>
          </template>

          <v-data-table density="compact"
                        :items="items"
                        :loading="loading"
                        :search="search"
                        :group-by="group.items.value"
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
              <code-block class="pa-1" language="json" :code="item.recipe" />
            </template>
          </v-data-table>
        </v-card>
      </template>

      <template v-slot:form>
        <div class="text-left">
          Recipes of the given type will be returned.
        </div>
      </template>
    </query-component>
    <mod-information-dialog :version="version" v-model:modId="selectedMod" />
    <file-source-dialog :version="version" v-model:selected-file="selectedFile" />
  </div>
</template>

<script setup lang="ts">
import QueryComponent from "~/components/query-component.vue";
import ModInformationDialog from "~/components/mod-information-dialog.vue";
import {type FileSelection, getRecipeSourceFileName} from "~/utils/utils";
import {RECIPES} from "~~/graphql-requests/data_files";
import type {RecipeFilePredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {predicateQueryParameter, queryToPredicate, stringQueryParameter} from "~/utils/query-utils";

definePageMeta({
  title: 'Recipes Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const recipeType = queryClient.defineParameter('recipetype', stringQueryParameter({
  label: 'Recipe type',
  placeholder: 'minecraft:crafting_shaped'
}))
const filter = queryClient.defineOptionalParameter<RecipeFilePredicate>('filter', predicateQueryParameter({
  label: 'Filter',
  type: 'RecipeFilePredicate'
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
  {title: 'Recipe name', key: 'name'},
  {title: 'Recipe type', key: 'tp'},
  {title: 'Recipe content', key: 'recipe'},
]
const group = grouper([
  {title: 'Mod', key: 'mod.name'},
  {title: 'Recipe type', key: 'tp'}
])

const load = () => {
  loading.value = true

  const predicates: RecipeFilePredicate[] = [
    {
      type: queryToPredicate(recipeType.value)
    }
  ]
  if (filter.value) {
    predicates.push(filter.value)
  }

  queryClient.fetchPaginated(RECIPES, {
    predicate: {
      allOf: predicates
    }
  }, data => data.gameVersion?.recipes!!)
      .then((values) => {
        items.value = values!!.map(entry => {
          return {
            mod: entry.mod,
            name: entry.name,
            recipe: JSON.stringify(entry.recipe, null, 2),
            tp: entry.type
          }
        })
        loading.value = false
      })
}

</script>
