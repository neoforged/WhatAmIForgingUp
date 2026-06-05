import {ApolloError, type OperationVariables, type ServerError, type TypedDocumentNode} from "@apollo/client/core"
import {type InputMaybe, Loader} from "~~/graphql-requests/types/__generated__/graphql";
import type {ApolloClient} from "@apollo/client/core";
import {addAlert} from "~/utils/alerts";

export type RelayConnection<Node> = {
  edges: Array<{ node: Node }>
  pageInfo: {
    endCursor: string | null
    hasNextPage: boolean
  }
}

export async function fetchWithVersion<
    TData,
    TVariables extends OperationVariables & {
      version: string,
      loader: Loader
    }
>(
    client: ApolloClient,
    query: TypedDocumentNode<TData, TVariables>,
    variables: Omit<TVariables, 'version' | 'loader'>,
    version: string
): Promise<TData | undefined> {
  const splitVersion = version.split('-')

  const {data, error} = await client.query({
    query: query,
    variables: {
      ...variables,
      version: splitVersion[0]!!,
      loader: splitVersion[1]!! as Loader,
    } as any,
    errorPolicy: 'all'
  }).catch(reason => ({data: undefined as TData, error: {message: reason}}))

  if (error) {
    await reportError(error)
    return undefined
  }

  return data
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
      await reportError(error)
      return []
    }

    const relay = extract(data)
    afterCursor = relay.pageInfo.endCursor
    hasNext = relay.pageInfo.hasNextPage

    relay.edges.forEach(edge => allNodes.push(edge.node))
  }

  return allNodes
}

async function reportError(error: { message: any } | ApolloError) {
  const err = error instanceof ApolloError ? error : error.message as ApolloError

  const netErr: ServerError | undefined = (err.networkError as any)?.response ? err.networkError as any : undefined
  if (netErr?.statusCode === 429) {
    const isAuthenticated: boolean = JSON.parse(getCookieByName('discord-identification') ?? '{}').name
    const tryAgainIn = `${netErr.response.headers.get('x-ratelimit-reset')} seconds`
    addAlert({
      title: 'GraphQL fetch error',
      description: `
Rate limit reached!
${!isAuthenticated ? `Consider authenticating or try again in ${tryAgainIn}.` : `Try again in ${tryAgainIn}.`}
If this error persists, it is likely that your query is too large. Consider narrowing its scope (for instance, to just a modpack).
`
    })
    return
  }

  if (netErr) {
    addAlert({
      title: 'GraphQL fetch error',
      description: await netErr.response.text()
    })
    return
  }

  addAlert({
    title: 'GraphQL fetch error',
    description: error.message
  })
}
