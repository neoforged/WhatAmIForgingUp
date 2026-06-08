<template>
  <v-card title="Results" :loading="graph === undefined" flat>
    <template v-if="graph" v-slot:text>
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

<script lang="ts">
import type {TagEntryPredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {TAG_ENTRIES} from "~~/graphql-requests/data_files";

export default defineComponent({
  props: {
    object: {
      type: String,
      required: true
    },
    registry: {
      type: String,
      required: true
    },
    graph: {
      type: Array as PropType<GraphElement[]>
    }
  },

  setup(props) {
    const expand = (el: GraphElement): GraphElement[] =>
        [el].concat(el.children.flatMap(expand))

    const allElements = computed(() =>
        props.graph.flatMap(expand)
    )

    const selectedMod = useModSelection()

    return {
      allElements,
      selectedMod
    }
  }
})

interface Mod {
  id: number
  name: string
}

interface TagEntry {
  mods: Mod[]
  tag: string
}

async function getTagsContaining(client: QueryClient, registry: string, extraPredicate: TagEntryPredicate | undefined, entries: string[]): Promise<TagEntry[][]> {
  const predicates: TagEntryPredicate[] = [
    {
      entry: {
        isIn: entries
      }
    }
  ]

  if (extraPredicate) {
    predicates.push(extraPredicate)
  }

  const queryResult = await client.fetchPaginated(TAG_ENTRIES, {
    registry: registry,
    predicate: {
      allOf: predicates
    }
  }, data => data.gameVersion?.tagEntries!)

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

export async function buildTagContainmentTree(client: QueryClient, registry: string, extraPredicate: TagEntryPredicate | undefined, roots: string[]): Promise<Map<string, TagEntry[]>[]> {
  const levels: Map<string, TagEntry[]>[] = []
  const visited = new Set<string>(roots)

  let current = roots

  while (current.length > 0) {
    const result = await getTagsContaining(client, registry, extraPredicate, current)

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

export function build(entry: TagEntry, level: number, levels: Map<string, TagEntry[]>[]): GraphElement {
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
</script>
