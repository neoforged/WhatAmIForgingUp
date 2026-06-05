import type {Loader} from "~~/graphql-requests/types/__generated__/graphql";
import JSZip from "jszip";

export async function getAllVersions(): Promise<{
  gameVersion: string,
  loader: Loader,
  activelyIndexed: boolean
}[]> {
  return fetchReportError(`${getBaseUrl()}/api/internal/indexed-versions`, 'available versions')
      .then(async result => await result.json())
      .catch(() => [])
}

export function getOAuthURL(): string {
  return `${getBaseUrl()}/oauth2/discord`
}

export function redirectToOAuth() {
  document.cookie = `redirect-url=${encodeURIComponent(window.location.href)}; Path=/`
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

export async function downloadZip(url: string): Promise<JSZip> {
  const res = await fetch(url)
  const zip = new JSZip()
  return await zip.loadAsync(res.arrayBuffer())
}

export async function fetchReportError(url: string, context: string): Promise<Response> {
  return await fetch(url)
      .then(async result => {
        if (result.ok) {
          return result
        } else {
          addAlert({
            title: `Failed to fetch ${context}`,
            description: `
Status code: ${result.status}
Response: ${await result.text()}`
          })
          return Promise.reject()
        }
      })
      .catch(error => {
        addAlert({
          title: `Failed to fetch ${context}`,
          description: error
        })
        return Promise.reject()
      })
}
