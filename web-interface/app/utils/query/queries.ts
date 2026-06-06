import type {ClassDefinitionPredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {parameters, queryType, renderAsTable, optional} from "~/utils/query/query-builder";
import {predicateQueryParameter, queryToPredicate, stringQueryParameter} from "~/utils/query-utils";
import {classSearch, methodSearch} from "~/utils/autocomplete";
import {METHOD_REFERENCES} from "~~/graphql-requests/methods";
import {fileColumn, modColumn} from "~/components/table/results-table-api";
import {getClassSourceFileName, sanitise} from "~/utils/utils";
import {CLASSES_ANNOTATED} from "~~/graphql-requests/classes";

export const METHOD_REFERENCES_QUERY = queryType({
  description: () => h('span', 'Query direct references to the given method.'),
  parameters: parameters<{
    class: string;
    method: string;
  }>(params => ({
    class: stringQueryParameter({
      label: 'Class',
      placeholder: 'com.example.ExampleClass',
      autocomplete: classSearch(params.version)
    }),
    method: stringQueryParameter({
      label: 'Method',
      placeholder: 'exampleMethod',
      autocomplete: methodSearch(params.version, params.class)
    })
  })),

  queryData: (client, params) => fetchWithVersion(client.apollo, METHOD_REFERENCES, {
    class: params.class.value!!.replaceAll('.', '/'),
    methodFilter: {
      name: queryToPredicate(params.method.value)
    }
  }, params.version.value)
      .then((result) =>
          result?.gameVersion?.class?.methods?.flatMap(mtd =>
              mtd.references.map(ref => ({
                mod: ref.owner.mod,
                cls: ref.owner.name,
                mtd: mtd.name + mtd.descriptor
              }))
          )),

  renderer: renderAsTable([
    modColumn({
      title: 'Mod',
      groupable: true
    }),
    fileColumn(getClassSourceFileName, {
      title: 'Class',
      key: 'cls',
      groupable: true
    }),
    {
      title: 'Referenced Method',
      key: 'mtd',
      groupable: true
    }
  ])
})

export const CLASSES_ANNOTATED_QUERY = queryType({
  description: () => h('span', 'Query classes annotated with the given annotation.'),
  parameters: parameters<{
    annotation: string;
    filter: ClassDefinitionPredicate | undefined;
  }>(params => ({
    annotation: stringQueryParameter({
      label: 'Annotation',
      placeholder: 'com.example.ExampleAnnotation',
      autocomplete: classSearch(params.version)
    }),
    filter: optional(predicateQueryParameter({
      label: 'Filter',
      type: 'ClassDefinitionPredicate'
    }))
  })),

  queryData: async (client, params) => {
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

    const values = await client.fetchPaginated(CLASSES_ANNOTATED, {
      predicate: {
        allOf: sanitise([
          {
            anyAnnotation: {
              type: {equals: params.annotation.value!!.replaceAll('.', '/')}
            }
          },
          params.filter.value
        ])
      },
      annotationPredicate: {
        type: {equals: params.annotation.value!.replaceAll('.', '/')}
      }
    }, data => (data.gameVersion?.classDefinitions)!);
    return values.map(item => ({
      mod: item.mod,
      cls: item.name.replaceAll('/', '.'),
      annotation: formatAnnotation(item.annotations[0]!.value! as any, params.annotation.value)
    }));
  },

  renderer: renderAsTable([
    modColumn({
      title: 'Mod',
      groupable: true
    }),
    fileColumn(getClassSourceFileName, {
      title: 'Class',
      key: 'cls'
    }),
    {
      title: 'Annotation',
      key: 'annotation'
    }
  ])
})
