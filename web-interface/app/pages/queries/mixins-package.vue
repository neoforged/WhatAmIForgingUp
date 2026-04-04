<template>
  <v-container>
    <query-component
        v-model:version="version"
        :on-load="load"
        :on-reset="() => loading = true"
        :additionalProperties="{'Package': pkg, 'Filter': filter ? formatFilter(filter, 'ClassDefinitionPredicate') : 'None'}">
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
                    :items="['None', 'Mod', 'Mixin Target']"
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
              <v-btn density="compact" color="primary" variant="text" border="false" @click="selectedMod = item.mod.id">
                {{ value }}
              </v-btn>
            </template>
            <template v-slot:item.className="{ item, value }">
              <span @click="selectedClass = {mod: item.mod.id, class: item.className}">{{ value }}</span>
            </template>
          </v-data-table>
        </v-card>
      </template>

      <template v-slot:form>
        <v-text-field
            v-model="pkg"
            label="Package"
            placeholder="com.example"
            density="compact"
            variant="underlined"
        />
        <div class="text-left mb-2">
          Mixins targetting classes in the package above will be returned. Subpackages are included.
        </div>

        <v-expansion-panels>
          <v-expansion-panel title="Optional filter">
            <v-expansion-panel-text>
              <predicate-builder type-name="ClassDefinitionPredicate" v-model="filter" :meta="predicates" clearable/>
            </v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>
      </template>
    </query-component>
    <mod-information-dialog :version="version" v-model:modId="selectedMod" />
    <class-source-dialog :version="version" v-model:selectedClass="selectedClass" />
  </v-container>
</template>

<script setup lang="ts">
import QueryComponent from "~/components/query-component.vue";
import type {DataTableSortItem} from "vuetify";
import ModInformationDialog from "~/components/mod-information-dialog.vue";
import PredicateBuilder from "~/components/predicates/PredicateBuilder.vue";
import {CLASSES_ANNOTATED} from "~~/graphql-requests/classes";
import type {ClassDefinitionPredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {formatFilter} from "~/utils/predicates";

definePageMeta({
  title: 'Mixins targetting Classes in Package Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const pkg = queryClient.queryParam('pkg')

const filter = queryClient.jsonQueryParam<Record<string, any>>('filter')

const selectedMod = ref<number>()
const selectedClass = ref<{
  mod: number,
  class: string
}>()

const items = ref([] as any[])
const loading = ref(true)
const search = ref(undefined)
const groupBy = ref(undefined)

const headers = [
  {title: 'Mod', key: 'mod.name'},
  {title: 'Mixin Class', key: 'className'},
  {title: 'Mixin Target', key: 'targets'}
]
const groupByConfiguration = computed<DataTableSortItem[]>(() => {
  if (!groupBy.value || groupBy.value == 'None') {
    return []
  } else if (groupBy.value == 'Mod') {
    return [{key: 'mod.name'}]
  } else {
    return [{key: 'targets'}]
  }
})

const load = () => {
  const classDefPredicates: ClassDefinitionPredicate[] = [
    {
      anyAnnotation: {
        allOf: [
          {type: {equals: "org/spongepowered/asm/mixin/Mixin"}},
          {
            value: {
              anyOf: [
                {pathExists: `$.value ? (@ like_regex "${pkg.value!!.replaceAll('.', '/')}/.*")`},
                {pathExists: `$.targets ? (@ like_regex "${pkg.value!!.replaceAll('.', '(\\.|/)')}(\\.|/).*")`}
              ]
            }
          }
        ]
      }
    }
  ]

  if (filter.value && Object.keys(filter.value).length > 0) {
    classDefPredicates.push(filter.value)
  }

  queryClient.fetchPaginated(CLASSES_ANNOTATED, {
    predicate: {
      allOf: classDefPredicates
    },
    annotationPredicate: {type: {equals: "org/spongepowered/asm/mixin/Mixin"}}
  }, data => data.gameVersion?.classDefinitions!!)
      .then((values) => {
        items.value = values.map(item => {
          const value = item.annotations[0]!!.value!! as any;

          return {
            mod: item.mod,
            className: item.name.replaceAll('/', '.'),
            targets: ((value.value ?? value.targets) as string[]).join(', ').replaceAll('/', '.')
          }
        })!!
        loading.value = false
      })
}

</script>
