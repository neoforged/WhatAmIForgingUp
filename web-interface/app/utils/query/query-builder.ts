import type {TableColumn} from "~/components/table/results-table-api";
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

export interface QueryType<R extends QueryParameterSet, D> {
  parameters: (params: ExtractQueryParametersWithVersion<R>) => R
  description: Component

  queryData(client: QueryClient, params: ExtractQueryParametersWithVersion<R>): Promise<D>
  renderer(params: ExtractQueryParametersWithVersion<R>, data: Ref<D | undefined>): Component
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

export function queryType<T extends QueryParameterSet, D>(t: QueryType<T, D>): QueryType<T, D> {
  return t
}

export function renderAsTable<T extends QueryParameterSet>(columns: TableColumn[]):
    (params: ExtractQueryParametersWithVersion<T>, data: Ref) => Component {
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
