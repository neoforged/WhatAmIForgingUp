<template>
  <v-container>
    <query-component
        v-model:version="version"
        v-model:predicate="filter"
        predicate-type="ClassDefinitionPredicate"
        :on-load="load"
        :on-reset="() => loading = true"
        :additionalProperties="{'Annotation': annotation}">
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
            v-model="annotation"
            label="Annotation"
            placeholder="com.example.ExampleAnnotation"
            density="compact"
            variant="underlined"
        />
        <div class="text-left">
          Classes annotated with the annotation above will be returned.
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
import {CLASSES_ANNOTATED} from "~~/graphql-requests/classes";
import type {ClassDefinitionPredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {type FileSelection, getClassSourceFileName} from "~/utils/utils";

definePageMeta({
  title: 'Classes with Annotation Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const annotation = queryClient.queryParam('annotation')
const filter = queryClient.jsonQueryParam<Record<string, any>>('filter')

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
  {title: 'Class', key: 'cls'},
  {title: 'Annotation', key: 'annotation'}
]
const groupByConfiguration = computed<DataTableSortItem[]>(() => {
  if (!groupBy.value || groupBy.value == 'None') {
    return []
  } else {
    return [{key: 'mod.name'}]
  }
})

const formatValue = (value: any): string => {
  if (Array.isArray(value)) {
    return '{' + (value as any[]).map(formatValue).join(', ') + '}'
  } else if (value._$tp) {
    return formatAnnotation(value, value._$tp)
  } else if (value.enum) {
    const splitType = value.enum.split('/')
    return splitType[splitType.length - 1] + '.' + value.value
  } else if (typeof value == 'string') {
    return `"${value}"`
  }
  return value.toString()
}

const formatAnnotation = (annotation: any, tp: string): string => {
  const splitType = tp.split('/')
  const base = '@' + splitType[splitType.length - 1]

  const arg = Object.entries(annotation).map(([key, value]) => key + '=' + formatValue(value)).join(', ')

  return arg.length == 0 ? base : `${base}(${arg})`
}

const load = () => {
  const classDefPredicates: ClassDefinitionPredicate[] = [
    {
      anyAnnotation: {
        type: {equals: annotation.value!!.replaceAll('.', '/')}
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
    annotationPredicate: {
      type: {equals: annotation.value!!.replaceAll('.', '/')}
    }
  }, data => data.gameVersion?.classDefinitions!!)
      .then((values) => {
        items.value = values.map(item => {
          const value = item.annotations[0]!!.value!! as any;

          return {
            mod: item.mod,
            cls: item.name.replaceAll('/', '.'),
            annotation: formatAnnotation(value, annotation.value)
          }
        })!!
        loading.value = false
      })
}

</script>
