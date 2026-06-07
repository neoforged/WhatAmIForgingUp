<template>
  <div>
    <query-component
        :on-load="load"
        :parameters="queryClient.parameters">
      <template v-slot:result-display>
        <v-card title="Results" :loading="loading" flat>
          <template v-if="!loading" v-slot:text>
            <h3>Element <code>{{ object }}</code> (in registry {{ registry }}) was found in {{ allElements?.length ?? 1 - 1 }} tags, shown below:</h3>
            <v-treeview
                :items="graph"
                hide-actions
                density="compact"
                item-value="title"
                indent-lines="simple"
                items-registration="props"
                :opened="allElements?.map(e => e.title)"
            >
              <template v-slot:prepend="{ item }">
                <v-icon v-if="!item.root" icon="mdi-arrow-up-left" />
              </template>
              <template v-slot:title="{ item }">
                {{ item.title }}
                <v-btn v-for="mod of item.mods" density="compact" color="primary" variant="text" border="false" @click="selectedMod = mod.id">{{ mod.name }}</v-btn>
              </template>
            </v-treeview>

          </template>
        </v-card>
      </template>

      <template v-slot:form>
        <div class="text-left">
          A tree of all the tags containing the given element will be returned.
        </div>
      </template>
    </query-component>
    <mod-information-dialog :version="version" v-model:modId="selectedMod" />
  </div>
</template>

<script setup lang="ts">
import QueryComponent from "~/components/query-component.vue";
import ModInformationDialog from "~/components/mod-information-dialog.vue";
import type {TagEntryPredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {predicateQueryParameter, stringQueryParameter} from "~/query/query-parameters";
import {TAG_ENTRIES} from "~~/graphql-requests/data_files";
import {usualRegistries} from "~/utils/autocomplete";

definePageMeta({
  title: 'Tags containing element Query'
})

const queryClient = useQueryClient()

const version = queryClient.version
const registry = queryClient.defineParameter('registry', stringQueryParameter({
  label: 'Registry',
  placeholder: 'minecraft:item',
  autocomplete: usualRegistries()
}))
const object = queryClient.defineParameter('element', stringQueryParameter({
  label: 'Element',
  placeholder: 'minecraft:oak_planks'
}))

const filter = queryClient.defineOptionalParameter<TagEntryPredicate>('filter', predicateQueryParameter({
  label: 'Filter',
  type: 'TagEntryPredicate'
}))

const selectedMod = ref<number>()
const loading = ref(true)

interface Mod {
  id: number
  name: string
}

interface TagEntry {
  mods: Mod[]
  tag: string
}

async function getTagsContaining(entries: string[]): Promise<TagEntry[][]> {
  const predicates: TagEntryPredicate[] = [
    {
      entry: {
        isIn: entries
      }
    }
  ]

  if (filter.value) {
    predicates.push(filter.value)
  }

  const queryResult = await queryClient.fetchPaginated(TAG_ENTRIES, {
    registry: registry.value,
    predicate: {
      allOf: predicates
    }
  }, data => data.gameVersion?.tagEntries!!)

  const indices: Record<string, number> = {}
  for (let i = 0; i < entries.length; i++) {
    indices[entries[i]!!] = i
  }

  const result: TagEntry[][] = []
  for (let i = 0; i < entries.length; i++) result.push([])

  queryResult.forEach(e => {
    result[indices[e.entry]!!]!!.push({
      mods: [e.mod],
      tag: e.tag
    })
  })

  return result
}

async function buildTagContainmentTree(roots: string[]): Promise<Map<string, TagEntry[]>[]> {
  const levels: Map<string, TagEntry[]>[] = []
  const visited = new Set<string>(roots)

  let current = roots

  while (current.length > 0) {
    const result = await getTagsContaining(current)

    const levelMap = new Map<string, TagEntry[]>()
    const next: string[] = []

    for (let i = 0; i < current.length; i++) {
      const entry = current[i]!!

      const parents: TagEntry[] = []
      const knownParents: Map<string, TagEntry> = new Map()
      result[i]!!.forEach((value) => {
        if (knownParents.has(value.tag)) {
          knownParents.get(value.tag)?.mods.push(...value.mods)
        } else {
          parents.push(value)
          knownParents.set(value.tag, value)
        }
      })

      levelMap.set(entry, parents)

      for (const p of parents) {
        const tag = p.tag
        if (!visited.has(tag)) {
          visited.add(tag)
          next.push('#' + tag)
        }
      }
    }

    levels.push(levelMap)
    current = next
  }

  return levels
}

interface GraphElement {
  title: string
  root?: boolean
  mods?: Mod[]
  children: GraphElement[]
}

const graph = ref<GraphElement[]>()
const allElements = computed(() => {
  const expand = (el: GraphElement): GraphElement[] => [el].concat(el.children.flatMap(expand))
  return graph.value?.flatMap(expand)
})

function build(entry: TagEntry, level: number, levels: Map<string, TagEntry[]>[]): GraphElement {
  const kids: GraphElement[] = []
  if (level < levels.length - 1) {
    // This will be undefined if the tag was already found on an upper level. This way, we avoid loops
    levels[level + 1]!!.get('#' + entry.tag)?.forEach((v, k) => kids.push(build(v, level + 1, levels)))
  }
  return {
    title: '#' + entry.tag,
    mods: entry.mods,
    children: kids
  }
}


const load = () => {
  loading.value = true

  buildTagContainmentTree([object.value])
      .then(res => {
        graph.value = [
          {
            title: object.value,
            root: true,
            children: res[0]!!.get(object.value)!!.map(e => build(e, 0, res))
          }
        ]
        loading.value = false
      })
}

</script>
