import type {Loader} from "~~/graphql-requests/types/__generated__/graphql";

export async function getAllVersions(): Promise<{
  gameVersion: string,
  loader: Loader
}[]> {
  const result = await fetch(`${getBaseUrl()}/api/indexed-versions`)
  return await result.json()
}

export function getOAuthURL(): string {
  return `${getBaseUrl()}/oauth2/discord`
}

export function redirectToOAuth() {
  document.cookie = `redirect-url=${window.location.href}; Path=/`
  window.location.href = getOAuthURL()
}

export function getBaseUrl() {
  if (window.location.host == 'localhost:3000') {
    return 'http://localhost:6532'
  }
  return window.location.origin
}
