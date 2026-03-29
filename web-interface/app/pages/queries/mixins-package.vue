<template>
  <v-container>
    <query-component
        v-model:version="version"
        :on-load="load"
        :on-reset="() => loading = true"
        :additionalProperties="{'Package': pkg}">
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
        <div class="text-left">
          Mixins targetting classes in the package above will be returned. Subpackages are included.
        </div>
      </template>
    </query-component>
  </v-container>
</template>

<script setup lang="ts">
import {MIXINS_ANNOTATION_PREDICATE} from "~~/graphql-requests/mixins";
import QueryComponent from "~/components/query-component.vue";
import type {DataTableSortItem} from "vuetify";

definePageMeta({
  title: 'Mixins targetting Classes in Package Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const pkg = queryClient.queryParam('pkg')

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
  queryClient.fetchPaginated(MIXINS_ANNOTATION_PREDICATE, {
    predicate: {
      value: {
        anyOf: [
          {pathExists: `$.value ? (@ like_regex "${pkg.value!!.replaceAll('.', '/')}/.*")`},
          {pathExists: `$.targets ? (@ like_regex "${pkg.value!!.replaceAll('.', '(\\.|/)')}(\\.|/).*")`}
        ]
      }
    }
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
