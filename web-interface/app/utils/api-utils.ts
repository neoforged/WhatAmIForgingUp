import type {Loader} from "~~/graphql-requests/types/__generated__/graphql";

export async function getAllVersions(): Promise<{
  gameVersion: string,
  loader: Loader,
  activelyIndexed: boolean
}[]> {
  const result = await fetch(`${getBaseUrl()}/api/internal/indexed-versions`)
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

export async function getGitHubBranches(owner: string, repo: string): Promise<string[]> {
  const res = await fetch(getBaseUrl() + `/api/internal/github-refs/${owner}/${repo}`)
  return (await res.json() as string[]).map(s => s.replace('refs/heads/', ''))
}

export async function downloadGitHubBranch(owner: string, repo: string, branch: string): Promise<ArrayBuffer> {
  const res = await fetch(getBaseUrl() + `/api/internal/download-github/${owner}/${repo}/refs/heads/${branch}`)
  return await res.arrayBuffer()
}
