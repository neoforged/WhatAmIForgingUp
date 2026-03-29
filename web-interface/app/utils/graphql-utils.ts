import type {OperationVariables, TypedDocumentNode} from "@apollo/client"
import type {InputMaybe} from "~~/graphql-requests/types/__generated__/graphql";
import type {ApolloClient} from "@apollo/client/core";

export type RelayConnection<Node> = {
  edges: Array<{ node: Node }>
  pageInfo: {
    endCursor: string | null
    hasNextPage: boolean
  }
}

export async function loadAll<
    TData,
    TVariables extends OperationVariables & {cursor?: InputMaybe<string> | undefined},
    TNode
>(
    client: ApolloClient,
    query: TypedDocumentNode<TData, TVariables>,
    variables: TVariables,
    extract: (data: TData) => RelayConnection<TNode>
): Promise<TNode[]> {
  const allNodes: TNode[] = []

  let afterCursor: string | null = null
  let hasNext = true
  while (hasNext) {
    const {data, error} = await client.query({
      query: query,
      variables: {
        ...variables,
        cursor: afterCursor
      },
      errorPolicy: 'all'
    }).catch(reason => ({data: undefined as TData, error: {message: reason}}))

    if (error) {
      alert(`Request failed: ${error.message}`)
      return []
    }

    const relay = extract(data)
    afterCursor = relay.pageInfo.endCursor
    hasNext = relay.pageInfo.hasNextPage

    relay.edges.forEach(edge => allNodes.push(edge.node))
  }

  return allNodes
}
