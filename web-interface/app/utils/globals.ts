import type {FileSelection} from "~/utils/utils";

const selectedMod = ref<number>()
const selectedFile = ref<{
  mod: number,
  file: FileSelection
}>()

export function useModSelection(): Ref<number | undefined> {
  return selectedMod
}

export function useFileSelection(): Ref<{
  mod: number,
  file: FileSelection
} | undefined> {
  return selectedFile
}
