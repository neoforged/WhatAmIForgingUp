import {useApolloClient} from "@vue/apollo-composable";
import type {OperationVariables, TypedDocumentNode} from "@apollo/client";
import type {InputMaybe, StringPredicate} from "~~/graphql-requests/types/__generated__/graphql";
import {Loader} from "~~/graphql-requests/types/__generated__/graphql";
import type {RelayConnection} from './graphql-utils'
import type {Ref} from "vue";
import {type QueryParameter, type QueryParameterType, versionQueryParameter} from '~/query/query-parameters'

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
