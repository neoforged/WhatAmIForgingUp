import type {TableColumn} from "~/components/table/results-table-api";
import type {QueryParameterType} from '~/query/query-parameters'
import ResultsTable from "~/components/table/results-table.vue";

export interface QueryParameterSet {
  [key: string]: QueryParameterType<any>
}

type ExtractAsSet<T extends Record<string, any>> = {
  [K in keyof T]: QueryParameterType<T[K]>
}

type ExtractQueryParameters<T extends QueryParameterSet> = {
  [K in keyof T]: T[K] extends QueryParameterType<infer U> ? Ref<U> : never
}

type ExtractQueryParametersWithVersion<R extends QueryParameterSet> = ExtractQueryParameters<R> & {
  version: Ref<string>
}

type QueryRenderer<P extends QueryParameterSet, D> =
    (params: ExtractQueryParametersWithVersion<P>, data: Ref<D | undefined>) => Component
type QueryDataFetcher<P extends QueryParameterSet, D> =
    (client: QueryClient, params: ExtractQueryParametersWithVersion<P>) => Promise<D>

export interface QueryType<P extends QueryParameterSet, D> {
  parameters: (params: ExtractQueryParametersWithVersion<P>) => P
  description: Component

  queryData: QueryDataFetcher<P, D>
  renderer: QueryRenderer<P, D>
}

export function optional<T>(type: QueryParameterType<T>): QueryParameterType<T | undefined> {
  return {
    ...type,
    optional: true
  } as any
}

export function parameters<Z extends Record<string, any>>(p: (params: ExtractQueryParametersWithVersion<ExtractAsSet<Z>>) => ExtractAsSet<Z>): (params: ExtractQueryParametersWithVersion<ExtractAsSet<Z>>) => ExtractAsSet<Z> {
  return p
}

export function queryType<P extends QueryParameterSet, D>(
    description: Component,
    parameters: (params: ExtractQueryParametersWithVersion<P>) => P,
    queryData: QueryDataFetcher<P, D>,
    renderer: QueryRenderer<P, D>
): QueryType<P, D> {
  return {
    description, parameters, queryData, renderer
  }
}

export function renderAsTable<T extends QueryParameterSet, D>(columns: TableColumn<D, any>[]): QueryRenderer<T, D[]> {
  return (_, data) => {
    return defineComponent({
      setup() {
        return () => h(ResultsTable, {
          columns: columns,
          items: data.value ?? [],
          loading: data.value === undefined
        })
      }
    })
  }
}
