import {VBtn} from "vuetify/components";
import type {FileSelection} from "~/utils/utils";
import {useFileSelection, useModSelection} from "~/utils/globals";

export interface TableColumn {
  title: string
  key: string

  groupable?: boolean

  renderer?: (item: any, value: any) => Component
}

export function render(col: TableColumn, item: any, value: any): Component {
  if (col.renderer) {
    return col.renderer(item, value)
  }
  return h('span', value)
}

export function modColumn(config: Omit<Omit<TableColumn, 'key'>, 'renderer'>, key: string = 'mod', selectedMod: Ref<number | undefined> = useModSelection()): TableColumn {
  return {
    ...config,
    key: key + '.name',
    renderer: (item, value) => h(VBtn, {
      density: 'compact',
      color: 'primary',
      variant: 'text',
      border: false,
      onClick: () => selectedMod.value = get(item, key).id
    }, () => value)
  }
}

export function fileColumn(fileFunction: (value: string) => FileSelection, config: Omit<TableColumn, 'renderer'>, modKey: string = 'mod', selectedFile: Ref<{
  mod: number,
  file: FileSelection
} | undefined> = useFileSelection()): TableColumn {
  return {
    ...config,
    renderer: (item, value) => h('span', {
      onClick: () => selectedFile.value = {
        mod: get(item, modKey).id,
        file: fileFunction(value)
      }
    }, value)
  }
}

function get(object: any, key: string): any {
  for (let path of key.split('.')) {
    object = object[path]
  }
  return object
}
