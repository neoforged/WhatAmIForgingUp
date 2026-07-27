import type {
  ClassDefinitionPredicate,
  RecipeFilePredicate,
  ReferencePredicate,
  TagEntryPredicate
} from "~~/graphql-requests/types/__generated__/graphql";
import {optional, parameters, type QueryType, queryType, renderAsTable} from "~/query/query-builder";
import {queryToPredicate} from "~/utils/query-utils";
import {classSearch, methodSearch, usualRegistries} from "~/utils/autocomplete";
import {METHOD_REFERENCES} from "~~/graphql-requests/methods";
import {codeColumn, fileColumn, modColumn} from "~/components/table/results-table-api";
import {fileNamed, getClassSourceFileName, getRecipeSourceFileName, sanitise} from "~/utils/utils";
import {CLASSES_ANNOTATED, IMPLEMENTATIONS} from "~~/graphql-requests/classes";
import {GET_ENUM_EXTENSIONS, RECIPES} from "~~/graphql-requests/data_files";
import {predicateQueryParameter, stringQueryParameter} from "~/query/query-parameters";
import {buildTagContainmentTree, build} from "~/components/table/tags-containing.vue"
import TagsContaining from "~/components/table/tags-containing.vue"

export const METHOD_REFERENCES_QUERY = queryType(
    () => h('span', 'Query direct references to the given method.'),
    parameters<{
      class: string;
      method: string;
      filter: ReferencePredicate | undefined;
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
      }),
      filter: optional(predicateQueryParameter({
        label: 'Filter',
        type: 'ReferencePredicate'
      }))
    })),
    async (client, params) => fetchWithVersion(client.apollo, METHOD_REFERENCES, {
      class: params.class.value!.replaceAll('.', '/'),
      methodFilter: {
        name: queryToPredicate(params.method.value)
      },
      filter: params.filter.value
    }, params.version.value)
        .then((result) =>
            result?.gameVersion?.class?.methods?.flatMap(mtd =>
                mtd.references.map(ref => ({
                  mod: ref.owner.mod,
                  cls: ref.owner.name,
                  mtd: mtd.name + mtd.descriptor
                }))
            ) ?? []),

    renderAsTable([
      modColumn({
        title: 'Mod',
        groupable: true,
        value: item => item.mod
      }),
      fileColumn({
        title: 'Class',
        groupable: true,
        value: item => item.cls,
        mod: item => item.mod,
        fileName: getClassSourceFileName
      }),
      {
        title: 'Referenced Method',
        groupable: true,
        value: item => item.mtd,
      }
    ])
)

export const CLASSES_ANNOTATED_QUERY = queryType(
    () => h('span', 'Query classes annotated with the given annotation.'),
    parameters<{
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
    async (client, params) => {
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
                type: {equals: params.annotation.value!.replaceAll('.', '/')}
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
    renderAsTable([
      modColumn({
        title: 'Mod',
        groupable: true,
        value: item => item.mod
      }),
      fileColumn({
        title: 'Class',
        value: item => item.cls,
        mod: item => item.mod,
        fileName: getClassSourceFileName
      }),
      {
        title: 'Annotation',
        value: item => item.annotation
      }
    ])
)

export const IMPLEMENTATIONS_QUERY = queryType(
    () => h('span', 'Query implementations of the given class. To the greatest extent possible, indirect implementations (A extends B - which extends the class) will also be returned.'),
    parameters<{
      class: string;
    }>(params => ({
      class: stringQueryParameter({
        label: 'Class',
        placeholder: 'com.example.ExampleClass',
        autocomplete: classSearch(params.version)
      })
    })),
    (client, params) =>
        fetchWithVersion(client.apollo, IMPLEMENTATIONS, {
          class: params.class.value!.replaceAll('.', '/')
        }, params.version.value)
            .then(result =>
                result?.gameVersion?.class?.inheritors?.flatMap(inh =>
                    inh.definitions.map(def => (
                        {
                          mod: def.mod,
                          cls: inh.name.replaceAll('/', '.')
                        }
                    ))
                ) ?? []
            ),
    renderAsTable([
      modColumn({
        title: 'Mod',
        groupable: true,
        value: item => item.mod
      }),
      fileColumn({
        title: 'Class',
        value: item => item.cls,
        mod: item => item.mod,
        fileName: getClassSourceFileName
      })
    ])
)

export const MIXINS_PACKAGE_QUERY = queryType(
    () => h('span', 'Query mixins targetting classes in the given package. Subpackages are included.'),
    parameters<{
      package: string;
      filter: ClassDefinitionPredicate | undefined;
    }>(_ => ({
      package: stringQueryParameter({
        label: 'Package',
        placeholder: 'com.example'
      }),
      filter: optional(predicateQueryParameter({
        label: 'Filter',
        type: 'ClassDefinitionPredicate'
      }))
    })),
    (client, params) =>
        client.fetchPaginated(CLASSES_ANNOTATED, {
          predicate: {
            allOf: sanitise([
              {
                anyAnnotation: {
                  allOf: [
                    {type: {equals: "org/spongepowered/asm/mixin/Mixin"}},
                    {
                      value: {
                        anyOf: [
                          {pathExists: `$.value ? (@ like_regex "${params.package.value!.replaceAll('.', '/')}/.*")`},
                          {pathExists: `$.targets ? (@ like_regex "${params.package.value!.replaceAll('.', '(\\.|/)')}(\\.|/).*")`}
                        ]
                      }
                    }
                  ]
                }
              },
              params.filter.value
            ])
          },
          annotationPredicate: {type: {equals: "org/spongepowered/asm/mixin/Mixin"}}
        }, data => data.gameVersion?.classDefinitions!)
            .then((values) =>
                values.map(item => {
                  const value = item.annotations[0]!!.value!! as any;
                  return {
                    mod: item.mod,
                    className: item.name.replaceAll('/', '.'),
                    targets: ((value.value ?? value.targets) as string[]).join(', ').replaceAll('/', '.')
                  }
                })!
            ),
    renderAsTable([
      modColumn({
        title: 'Mod',
        groupable: true,
        value: item => item.mod
      }),
      fileColumn({
        title: 'Mixin Class',
        value: item => item.className,
        mod: item => item.mod,
        fileName: getClassSourceFileName
      }),
      {
        title: 'Targets',
        groupable: true,
        value: item => item.targets
      }
    ])
)

export const RECIPES_QUERY = queryType(
    () => h('span', 'Query recipes of the given type.'),
    parameters<{
      recipeType: string,
      filter: RecipeFilePredicate | undefined
    }>(_ => ({
      recipeType: stringQueryParameter({
        label: 'Recipe type',
        placeholder: 'minecraft:crafting_shaped'
      }),
      filter: optional(predicateQueryParameter({
        label: 'Filter',
        type: 'RecipeFilePredicate'
      }))
    })),
    (client, params) => client.fetchPaginated(RECIPES, {
      predicate: {
        allOf: sanitise([
          {
            type: queryToPredicate(params.recipeType.value)
          },
          params.filter.value
        ])
      }
    }, data => data.gameVersion?.recipes!)
        .then((values) =>
            values!.map(entry => (
                {
                  mod: entry.mod,
                  name: entry.name,
                  recipe: JSON.stringify(entry.recipe, null, 2),
                  tp: entry.type
                }
            ))
        ),
    renderAsTable([
      modColumn({
        title: 'Mod',
        groupable: true,
        value: item => item.mod
      }),
      fileColumn({
        title: 'Recipe name',
        value: item => item.name,
        mod: item => item.mod,
        fileName: getRecipeSourceFileName
      }),
      {
        title: 'Recipe type',
        value: item => item.tp,
        groupable: true
      },
      codeColumn({
        title: 'Recipe content',
        language: 'json',
        value: item => item.recipe
      })
    ])
)

export const TAGS_CONTAINING_QUERY = queryType(
    () => h('span', 'Build a tree of all the tags containing the given element.'),
    parameters<{
      registry: string;
      object: string;
      filter: TagEntryPredicate | undefined;
    }>(_ => ({
      registry: stringQueryParameter({
        label: 'Registry',
        placeholder: 'minecraft:item',
        autocomplete: usualRegistries()
      }),
      object: stringQueryParameter({
        label: 'Element',
        placeholder: 'minecraft:oak_planks'
      }),
      filter: optional(predicateQueryParameter({
        label: 'Filter',
        type: 'TagEntryPredicate'
      }))
    })),
    (client, params) => buildTagContainmentTree(client, params.registry.value, params.filter.value, [params.object.value])
        .then(res => [
          {
            title: params.object.value,
            root: true,
            children: res[0]!!.get(params.object.value)!!.map(e => build(e, 0, res))
          }
        ]),
    (params, data) => defineComponent({
      setup() {
        return () => h(TagsContaining, {
          registry: params.registry.value,
          object: params.object.value,
          graph: data.value
        })
      }
    })
)

export const ENUM_EXTENSIONS_QUERY = queryType(
    () => h('span', 'Query NeoForge enum extensions for the given enum.'),
    parameters<{
      enum: string;
    }>(params => ({
      enum: stringQueryParameter({
        label: 'Enum',
        placeholder: 'com.example.ExampleEnum',
        autocomplete: classSearch(params.version)
      })
    })),
    async (client, params) => client.fetchPaginated(
        GET_ENUM_EXTENSIONS,
        {
          enum: params.enum.value!.replaceAll('.', '/')
        },
        data => data.gameVersion!.enumExtensions
    ).then(data => data.map(value => ({
      mod: value.mod,
      name: value.name,
      constructor: value.constructor,
      parameters: Array.isArray(value.parameters)
        ? `(${value.parameters.join(', ')})`
        : `${value.parameters.class?.replaceAll('/', '.')}.${value.parameters.field ?? value.parameters.method}`,
      parametersClass: value.parameters.class
    }))),
    renderAsTable([
      modColumn({
        title: 'Mod',
        groupable: true,
        value: item => item.mod
      }),
      fileColumn({
        title: 'Extension name',
        value: item => item.name,
        mod: item => item.mod,
        fileName: (_, item) => item.mod.enumExtensionsFile ? fileNamed(item.mod.enumExtensionsFile) : null
      }),
      {
        title: 'Constructor',
        groupable: true,
        value: item => item.constructor
      },
      fileColumn({
        title: 'Parameters',
        value: item => item.parameters,
        mod: item => item.mod,
        fileName: (_, item) => item.parametersClass ? getClassSourceFileName(item.parametersClass) : null
      })
    ])
)

export const QUERIES: {
  group: string
  path: string
  queries: {
    name: string
    path: string
    query: QueryType<any, any>
  }[]
}[] = [
  {
    group: 'Classes',
    path: 'classes',
    queries: [
      {
        name: 'Annotated Classes',
        path: 'annotated-classes',
        query: CLASSES_ANNOTATED_QUERY
      },
      {
        name: 'Implementations',
        path: 'implementations',
        query: IMPLEMENTATIONS_QUERY
      },
      {
        name: 'Method References',
        path: 'method-references',
        query: METHOD_REFERENCES_QUERY
      },
    ]
  },
  {
    group: 'Mixins',
    path: 'mixin',
    queries: [
      {
        name: 'Mixins targetting Classes in Package',
        path: 'mixins-package',
        query: MIXINS_PACKAGE_QUERY
      }
    ]
  },
  {
    group: 'Data Files',
    path: 'data-files',
    queries: [
      {
        name: 'Recipes',
        path: 'recipes',
        query: RECIPES_QUERY
      }
    ]
  },
  {
    group: 'Tags',
    path: 'tags',
    queries: [
      {
        name: 'Tags containing element',
        path: 'containing',
        query: TAGS_CONTAINING_QUERY
      }
    ]
  },
  {
    group: 'NeoForge',
    path: 'neoforge',
    queries: [
      {
        name: 'Enum Extensions',
        path: 'enum-extensions',
        query: ENUM_EXTENSIONS_QUERY
      }
    ]
  }
]
