import VersionSelection from "~/components/form/version-selection.vue";
import AutocompleteInput from "~/components/form/autocomplete-input.vue";
import PredicateInput from "~/components/form/predicate-input.vue";

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
