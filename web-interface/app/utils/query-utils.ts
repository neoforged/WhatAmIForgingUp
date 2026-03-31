import {useApolloClient} from "@vue/apollo-composable";
import type {OperationVariables, TypedDocumentNode} from "@apollo/client";
import type {InputMaybe} from "~~/graphql-requests/types/__generated__/graphql";
import {Loader} from "~~/graphql-requests/types/__generated__/graphql";
import type {RelayConnection} from './graphql-utils'

export class QueryClient {
  apollo = useApolloClient().client;
  private route = useRoute();
  private router = useRouter()

  version = this.queryParam('version')

  queryParam(key: string): Ref<string, string> {
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
