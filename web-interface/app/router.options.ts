// noinspection JSUnusedGlobalSymbols

import type {RouterConfig} from '@nuxt/schema'
import {QUERIES} from "~/query/queries";
import Query from "~/components/query.vue";

export default {
  // https://router.vuejs.org/api/interfaces/routeroptions#routes
  routes: _routes => _routes.concat(QUERIES.flatMap(group => group.queries.flatMap(q => ({
    name: q.name,
    path: `/queries/${group.path}/${q.path}`,
    component: defineComponent({
      setup() {
        useSeoMeta({
          title: `${q.name} Query`
        })
        return () => h(Query, {
          type: q.query
        })
      }
    })
  })))),
} satisfies RouterConfig
