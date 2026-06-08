import JSZip from "jszip";
import {decompile} from "@run-slicer/vf";
import type {DataTableSortItem} from "vuetify/framework";

export function getCookieByName(key: string): string | undefined {
  return document.cookie
      .split("; ")
      .find((row) => row.startsWith(key + "="))
      ?.split("=")[1]
}

export function deleteCookieByName(name: string) {
  document.cookie = name + "=; Max-Age=0; Path=/"
}

export interface FileSelection {
  name: string,

  binaryExtractor?: (zip: JSZip) => Promise<{ content: string } | { error: string }>
}

export function getClassSourceFileName(className: string): FileSelection {
  const rootClassName = className.split('$')[0]!!.replaceAll('.', '/')
  return {
    name: rootClassName + '.java',
    binaryExtractor: async zip => {
      const fileName = className.replaceAll('.', '/') + '.class'
      const zipFile = zip.file(fileName)

      if (!zipFile) {
        return {error: `Cannot find file named ${fileName} in mod file.`}
      } else {
        const decompResult = await decompile(fileName, {
          source: async (name) => name == fileName ? await zipFile.async('uint8array') : null
        })
        const content = decompResult[className.replaceAll('.', '/')] ?? decompResult[rootClassName]
        return content ? {content: content} : {error: `Decompilation failed or class named ${fileName} does not exist in mod file.`}
      }
    }
  }
}

export function fileNamed(name: string): FileSelection {
  return {
    name,
    binaryExtractor: async zip => {
      const zipFile = Object.values(zip.files)
          .find(f => f.name.endsWith(name))

      if (!zipFile) {
        return {error: `Cannot find file named ${name} in mod file.`}
      } else {
        return {content: await zipFile.async('string')}
      }
    }
  }
}

export function getRecipeSourceFileName(recipeName: string): FileSelection {
  // TODO - we should try to use the namespace too, for more accurate matching. That will however require being able to find a file based on several names (recipe/recipes)
  return fileNamed(recipeName.split(':').pop()!! + '.json')
}

export function grouper(groups: {
  title: string,
  key: string
}[]): {
  groupBy: Ref<string | undefined>,
  names: string[],
  items: Ref<DataTableSortItem[]>
} {
  const groupBy = ref<string | undefined>(undefined)
  const groupByConfiguration = computed<DataTableSortItem[]>(() => {
    if (!groupBy.value || groupBy.value == 'None') {
      return []
    } else {
      return [{key: groups.filter(g => g.title == groupBy.value)[0]!.key}]
    }
  })
  return {
    groupBy: groupBy,
    items: groupByConfiguration,
    names: groups.map(g => g.title)
  }
}

export function sanitise<T>(array: (T | undefined)[]): T[] {
  return array.filter(t => t !== undefined)
}
