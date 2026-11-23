import {VIcon} from "vuetify/components";
import type {FileSelection} from "~/utils/utils";
import {useFileSelection, useModSelection} from "~/utils/globals";
import CodeBlock from "~/components/code-block.vue";
import {fetchWithVersion} from "~/utils/graphql-utils";
import {MOD_INFORMATION} from "~~/graphql-requests/mods";

export interface AdditionalColumn<T> {
  title: string;
  values: (client: QueryClient, items: T[]) => Promise<any[]>;
}

export interface TableColumn<T, Z> {
  title: string
  value: (item: T) => Z;

  groupable?: boolean
  additionalColumns?: AdditionalColumn<T>[]

  renderer?: (item: T, value: Z) => Component

  sourceDownloader?: (item: T, value: Z) => {
    mod: number;
    selection: FileSelection;
  } | null
}

export function render(col: TableColumn<any, any>, item: any, value: any): Component {
  if (col.renderer) {
    return col.renderer(item, value)
  }
  return h('span', value)
}

interface Mod {
  id: number
  name: string
}

export function modColumn<T>(
    config: Omit<TableColumn<T, Mod>, 'renderer'>,
    selectedMod: Ref<number | undefined> = useModSelection()
): TableColumn<T, string> {
  return {
    ...config,
    value: item => config.value(item).name,
    renderer: (item, value) => h('button', {
      type: 'button',
      class: 'mod-link',
      title: `View information for ${value}`,
      'aria-label': `View information for ${value}`,
      onClick: () => selectedMod.value = config.value(item).id
    }, [
      h('span', {class: 'mod-name'}, value),
      h(VIcon, {
        icon: 'mdi-information-outline',
        size: 14,
        class: 'mod-icon',
        'aria-hidden': 'true'
      })
    ]),
    additionalColumns: [
      {
        title: 'Mod downloads',
        values: async (client, items) => {
          return (await fetchWithVersion(client.apollo, MOD_INFORMATION, {
            ids: items.map(it => config.value(it).id),
            includeDownloadUrl: false
          }, client.version.value))?.gameVersion?._modInformation.map(mi => (mi.curseforge?.downloads ?? 0) + (mi.modrinth?.downloads ?? 0)) ?? [];
        }
      }
    ],
    sourceDownloader: undefined,
  }
}

export function fileColumn<T>(
    config: Omit<TableColumn<T, string>, 'renderer'> & {
      mod: (item: T) => Mod,
      fileName: (value: string, item: T) => FileSelection | null
    },
    selectedFile: Ref<{
      mod: number,
      file: FileSelection
    } | undefined> = useFileSelection()
): TableColumn<T, string> {
  return {
    ...config,
    sourceDownloader: (item, value) => {
      const selection = config.fileName(value, item)
      if (selection) {
        return {
          mod: config.mod(item).id,
          selection: selection
        }
      }
      return null
    },
    renderer: (item, value) => h('span', {
      onClick: () => {
        const selection = config.fileName(value, item)
        if (selection) {
          selectedFile.value = {
            mod: config.mod(item).id,
            file: selection
          }
        }
      }
    }, value)
  }
}

export function codeColumn<T>(
    config: Omit<TableColumn<T, string>, 'renderer'> & { language?: string }
): TableColumn<T, string> {
  return {
    ...config,
    renderer: (item, value) => h(CodeBlock, {
      language: config.language,
      code: value,
      compact: true
    })
  }
}
