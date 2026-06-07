<template>
  <div>
    <query-component
        :on-load="load"
        :parameters="queryClient.parameters">
      <template v-slot:result-display>
        <component :is="component"/>
      </template>

      <template v-slot:form>
        <component :is="type.description" />
      </template>
    </query-component>
  </div>
</template>

<script setup lang="ts">
import {type QueryType} from '~/query/query-builder'
import QueryComponent from "~/components/query-component.vue";

const props = defineProps<{
  type: QueryType<any, any>
}>()

const queryClient = useQueryClient()

const parameters: Record<string, Ref> = {}
parameters['version'] = queryClient.version

const defineParameters = new Proxy({}, {
  get(target, prop) {
    const property = String(prop)
    return computed({
      get: () => parameters[property]?.value,
      set: (value: any) => parameters[property]!.value = value
    })
  }
})

const data = ref()

Object.entries(props.type.parameters(defineParameters as any)).forEach(entry => {
  const key = entry[0]
  const parameter: QueryParameterType<any> = entry[1] as QueryParameterType<any>
  if ((parameter as any).optional === true) {
    parameters[key] = queryClient.defineOptionalParameter(key, parameter)
  } else {
    parameters[key] = queryClient.defineParameter(key, parameter)
  }
})

const component = props.type.renderer(parameters as any, data)

function load() {
  data.value = undefined
  props.type.queryData(queryClient, parameters as any)
      .then((result: any) => data.value = result)
}
</script>
