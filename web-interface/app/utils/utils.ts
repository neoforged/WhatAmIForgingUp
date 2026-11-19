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

function formatValue(value: any, keySort: (a: string, b: string) => number): string {
  if (Array.isArray(value)) {
    return '{' + (value as any[]).map(v => formatValue(v, keySort)).join(', ') + '}'
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

export function formatAnnotation(annotation: any, tp: string, keySort: (a: string, b: string) => number = () => 0): string {
  const splitType = tp.split('/')
  const base = '@' + splitType[splitType.length - 1]

  const arg = Object.entries(annotation)
      .sort(([k1, v1], [k2, v2]) => keySort(k1, k2))
      .filter(it => it[0] != '_$tp')
      .map(([key, value]) => key + '=' + formatValue(value, keySort)).join(', ')

  return arg.length == 0 ? base : `${base}(${arg})`
}

function createPriorityComparator<T>(priorityList: T[]): (a: T, b: T) => number {
  const priorityMap = new Map<T, number>();
  priorityList.forEach((item, index) => priorityMap.set(item, index));

  return (a: T, b: T): number => {
    const indexA = priorityMap.has(a) ? priorityMap.get(a)! : Infinity;
    const indexB = priorityMap.has(b) ? priorityMap.get(b)! : Infinity;

    if (indexA < indexB) return -1;
    if (indexA > indexB) return 1;
    return 0;
  };
}

export const MIXIN_INJECTION_ANNOTATION_VALUE_COMPARATOR = createPriorityComparator<string>(['method', 'target'])

export function isMixinInjection(annotation: string): boolean {
  return annotation == 'org/spongepowered/asm/mixin/Overwrite'
    || annotation.startsWith('org/spongepowered/asm/mixin/injection/')
    || annotation.startsWith('com/llamalad7/mixinextras/injector/')
}
