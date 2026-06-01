import {getBaseUrl} from "~/utils/api-utils";

export interface AutoCompleteStrategy {
  (value?: string): Promise<string[]>
}

type MaybeRef<T> = T | Ref<T>
function unwrap<T>(input?: MaybeRef<T>): T | undefined {
  return input ? (isRef(input) ? input.value : input) : undefined
}

export function classSearch(version: MaybeRef<string | undefined>): AutoCompleteStrategy {
  return async value => {
    const ver = unwrap(version)
    if (!ver) return []
    const splitVersion = ver.split('-')
    return await fetch(`${getBaseUrl()}/api/internal/autocomplete/class?version=${splitVersion[0]}&loader=${splitVersion[1]}&query=${(value ?? '').replaceAll('.', '/')}`)
        .then(async res => await res.json() as string[])
        .then(res => res.map(e => e.replaceAll('/', '.')))
  }
}

export function methodSearch(versionRef: MaybeRef<string | undefined>, clsRef: MaybeRef<string | undefined>): AutoCompleteStrategy {
  return async value => {
    const version = unwrap(versionRef), cls = unwrap(clsRef)
    if (!version || !cls) return []
    const splitVersion = version.split('-')
    return await fetch(`${getBaseUrl()}/api/internal/autocomplete/method?version=${splitVersion[0]}&loader=${splitVersion[1]}&class=${cls.replaceAll('.', '/')}&query=${value ?? ''}`)
        .then(async res => await res.json() as string[])
  }
}

const registries = ['minecraft:item', 'minecraft:block', 'minecraft:fluid']
export function usualRegistries(): AutoCompleteStrategy {
  return async value => registries
}
