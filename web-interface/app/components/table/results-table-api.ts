import {VBtn} from "vuetify/components";
import type {FileSelection} from "~/utils/utils";
import {useFileSelection, useModSelection} from "~/utils/globals";
import CodeBlock from "~/components/code-block.vue";

export interface TableColumn<T, Z> {
  title: string
  value: (item: T) => Z;

  groupable?: boolean

  renderer?: (item: T, value: Z) => Component
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
    renderer: (item, value) => h(VBtn, {
      density: 'compact',
      color: 'primary',
      variant: 'text',
      border: false,
      onClick: () => selectedMod.value = config.value(item).id
    }, () => value)
  }
}

export function fileColumn<T>(
    config: Omit<TableColumn<T, string>, 'renderer'> & {
      mod: (item: T) => Mod,
      fileName: (value: string, item: T) => FileSelection | undefined
    },
    selectedFile: Ref<{
      mod: number,
      file: FileSelection
    } | undefined> = useFileSelection()
): TableColumn<T, string> {
  return {
    ...config,
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
    config: Omit<TableColumn<T, string>, 'renderer'> & { language: string }
): TableColumn<T, string> {
  return {
    ...config,
    renderer: (item, value) => h(CodeBlock, {
      language: config.language,
      code: value
    })
  }
}
