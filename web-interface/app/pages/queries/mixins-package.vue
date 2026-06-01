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
              <span @click="selectedFile = {mod: item.mod.id, file: getClassSourceFileName(item.className)}">{{ value }}</span>
            </template>
          </v-data-table>
        </v-card>
      </template>

      <template v-slot:form>
        <div class="text-left mb-2">
          Mixins targetting classes in the package above will be returned. Subpackages are included.
        </div>
      </template>
    </query-component>
    <mod-information-dialog :version="version" v-model:modId="selectedMod" />
    <file-source-dialog :version="version" v-model:selected-file="selectedFile" />
  </div>
</template>

<script setup lang="ts">
import QueryComponent from "~/components/query-component.vue";
import type {DataTableSortItem} from "vuetify";
import ModInformationDialog from "~/components/mod-information-dialog.vue";
import {CLASSES_ANNOTATED} from "~~/graphql-requests/classes";
import type {ClassDefinitionPredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {type FileSelection} from "~/utils/utils";
import {predicateQueryParameter, stringQueryParameter} from "~/utils/query-utils";

definePageMeta({
  title: 'Mixins targetting Classes in Package Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const pkg = queryClient.defineParameter('package', stringQueryParameter({
  label: 'Package',
  placeholder: 'com.example'
}))
const filter = queryClient.defineOptionalParameter<ClassDefinitionPredicate>('filter', predicateQueryParameter({
  label: 'Filter',
  type: 'ClassDefinitionPredicate'
}))

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
  loading.value = true

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

  if (filter.value) {
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
