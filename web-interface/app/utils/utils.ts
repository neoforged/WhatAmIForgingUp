export function getCookieByName(key: string): string | undefined {
  return document.cookie
      .split("; ")
      .find((row) => row.startsWith(key + "="))
      ?.split("=")[1]
}

export function deleteCookieByName(name: string) {
  document.cookie = name + "=; Max-Age=0; Path=/"
}

export function getClassSourceFileName(className: string): string {
  return className.split('$')[0]!!.replaceAll('.', '/') + '.java'
}
