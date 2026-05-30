import {getBaseUrl} from "~/utils/api-utils";

export interface AutoCompleteStrategy {
  (value?: string): Promise<string[]>
}

export function classSearch(version?: string): AutoCompleteStrategy {
  return async value => {
    if (!version) return []
    const splitVersion = version.split('-')
    return await fetch(`${getBaseUrl()}/api/internal/autocomplete/class?version=${splitVersion[0]}&loader=${splitVersion[1]}&query=${(value ?? '').replaceAll('.', '/')}`)
        .then(async res => await res.json() as string[])
        .then(res => res.map(e => e.replaceAll('/', '.')))
  }
}

export function methodSearch(version: string | undefined, cls: string): AutoCompleteStrategy {
  return async value => {
    if (!version || !cls) return []
    const splitVersion = version.split('-')
    return await fetch(`${getBaseUrl()}/api/internal/autocomplete/method?version=${splitVersion[0]}&loader=${splitVersion[1]}&class=${cls.replaceAll('.', '/')}&query=${value ?? ''}`)
        .then(async res => await res.json() as string[])
  }
}
