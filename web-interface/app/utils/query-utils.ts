import {useApolloClient} from "@vue/apollo-composable";
import type {OperationVariables, TypedDocumentNode} from "@apollo/client";
import type {InputMaybe, StringPredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {Loader} from "~~/graphql-requests/types/__generated__/graphql";
import type {RelayConnection} from './graphql-utils'
import AutocompleteInput from "~/components/form/autocomplete-input.vue";
import VersionSelection from "~/components/form/version-selection.vue";
import PredicateInput from "~/components/form/predicate-input.vue";
import type {Component, Ref} from "vue";

export class QueryClient {
  apollo = useApolloClient().client;
  private route = useRoute();
  private router = useRouter()

  parameters: QueryParameter<any>[] = []
  version = this.defineParameter('version', versionQueryParameter())

  defineParameter<T>(key: string, type: QueryParameterType<T>): Ref<T> {
    const queryParam = this.queryParam(key)
    const paramRef = computed({
      get: () => type.deserialise(queryParam.value ?? ''),
      set: (nv) => queryParam.value = type.serialise(nv) ?? '',
    })
    this.parameters.push({
      key, type,
      value: paramRef,
      formComponent: type.formComponent(paramRef as any),
      humanReadable: type.humanReadable ? type.humanReadable(paramRef as any) : undefined,
      optional: false
    })
    return paramRef
  }

  defineOptionalParameter<T>(key: string, type: QueryParameterType<T>): Ref<T | undefined> {
    const queryParam = this.queryParam(key)
    const paramRef = computed({
      get: () => queryParam.value === undefined || queryParam.value === null ? undefined : type.deserialise(queryParam.value),
      set: (nv) => queryParam.value = nv === undefined || nv === null ? undefined : type.serialise(nv),
    })
    this.parameters.push({
      key, type,
      value: paramRef,
      formComponent: type.formComponent(paramRef as any),
      humanReadable: type.humanReadable ? type.humanReadable(paramRef as any) : undefined,
      optional: true
    })
    return paramRef
  }

  queryParam(key: string): Ref<string | undefined> {
    const reference = ref(this.route.query[key] as string)
    watch(reference, newValue => {
      const newQuery = {...this.route.query}
      newQuery[key] = newValue
      this.router.replace({
        query: newQuery
      })
    })
    return reference
  }

  async fetchPaginated<
      TData,
      TVariables extends OperationVariables & {
        cursor?: InputMaybe<string> | undefined,
        version: string,
        loader: Loader
      },
      TNode
  >(
      query: TypedDocumentNode<TData, TVariables>,
      variables: Omit<TVariables, 'version' | 'loader'>,
      extract: (data: TData) => RelayConnection<TNode>
  ): Promise<TNode[]> {
    const splitVersion = this.version.value!!.split('-')
    return await loadAll(this.apollo, query, {
      ...variables,
      version: splitVersion[0]!!,
      loader: splitVersion[1]!! as Loader,
    } as any, extract)
  }
}

export function useQueryClient(): QueryClient {
  return new QueryClient()
}

export interface QueryParameterType<T> {
  label: string
  formComponent(value: Ref<T>): Component
  humanReadable?(value: Ref<T | undefined>): Component

  deserialise(input: string): T
  serialise(input: T): string
}

export interface QueryParameter<T> {
  key: string
  value: Ref<T | undefined>
  type: QueryParameterType<T>
  optional: boolean

  formComponent: Component
  humanReadable?: Component
}

export function versionQueryParameter(): QueryParameterType<string> {
  return {
    label: 'Version',
    formComponent: value => {
      return defineComponent({
        setup() {
          return () => h(VersionSelection, {
            modelValue: value.value,
            'onUpdate:modelValue': v => value.value = v
          })
        }
      })
    },
    serialise: (input) => input,
    deserialise: (input) => input
  }
}

export function stringQueryParameter(options: {
  label: string,
  placeholder: string,
  autocomplete?: AutoCompleteStrategy
}): QueryParameterType<string> {
  return {
    label: options.label,
    formComponent: value => {
      return defineComponent({
        setup() {
          return () => h(AutocompleteInput, {
            modelValue: value.value == '' ? undefined : value.value,
            'onUpdate:modelValue': v => value.value = v,
            label: options.label,
            placeholder: options.placeholder,
            strategy: options.autocomplete
          })
        }
      })
    },
    serialise: (input) => input,
    deserialise: (input) => input
  }
}

export function predicateQueryParameter<T>(options: {
  label: string,
  type: string
}): QueryParameterType<T> {
  return {
    label: options.label,
    formComponent: value => {
      return defineComponent({
        setup() {
          return () => h(PredicateInput, {
            modelValue: value.value as Record<string, any>,
            'onUpdate:modelValue': v => value.value = v as T,
            type: options.type
          })
        }
      })
    },
    humanReadable: value => {
      return defineComponent({
        setup() {
          return () => h('span', value.value ? formatFilter(value.value, options.type) : 'None')
        }
      })
    },
    serialise: (input) => JSON.stringify(input),
    deserialise: (input) => JSON.parse(input) as T
  }
}

export function queryToPredicate(query: string): StringPredicate {
  if (query.startsWith('/') && query.endsWith('/')) {
    return {
      matches: `^${query.substring(1, query.length - 1)}$`
    }
  }
  return {
    equals: query
  }
}
