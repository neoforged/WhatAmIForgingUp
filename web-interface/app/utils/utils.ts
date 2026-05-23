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

export function getRecipeSourceFileName(recipeName: string): string {
  // TODO - we should try to use the namespace too, for more accurate matching. That will however require being able to find a file based on several names (recipe/recipes)
  return recipeName.split(':').pop()!! + '.json'
}
