<template>
  <div>
    <v-dialog v-model="showDialog" max-width="800">
      <v-card :title="fileContent ? `Source of ${file?.file}` : 'Attempting to locate source'" :loading="loading">
        <template v-slot:text>
          <v-select v-if="branches && branches.length > 0"
                    :items="branches"
                    v-model="selectedBranch"
                    label="Select branch"
                    density="compact"/>
          <p v-if="error">{{ error }}</p>
          <code-block v-if="fileContent" :code="fileContent" :language="fileExtension" class="mb-3"/>
          <v-chip v-if="fileLink"
                  :href="fileLink"
                  target="_blank"
                  text="View on GitHub"
                  append-icon="mdi-open-in-new"/>
        </template>
        <template v-slot:actions>
          <v-btn
              text="Close"
              @click="showDialog = false"
              variant="tonal"
          ></v-btn>
        </template>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import {useApolloClient} from "@vue/apollo-composable";
import {fetchWithVersion} from "~/utils/graphql-utils";
import {MOD_INFORMATION} from "~~/graphql-requests/mods";
import {downloadGitHubBranch, getGitHubBranches} from "~/utils/api-utils";
import JSZip from "jszip";
import CodeBlock from "~/components/code-block.vue";

const props = defineProps<{
  version?: string,
  selectedFile?: {
    mod: number,
    file: string
  }
}>()
const emit = defineEmits(["update:selectedFile"]);

const client = useApolloClient().client

const file = computed({
  get: () => props.selectedFile,
  set: (val) => emit("update:selectedFile", val)
});

const fileExtension = computed(() => {
  const split = props.selectedFile?.file?.split('.')
  return split ? split[split.length - 1] : undefined
})

const showDialog = ref(false)
const loading = ref(false)
const error = ref(null as string | null)

type GitHubRepo = {
  owner: string
  repo: string
}

const repo = ref<GitHubRepo>()
const branches = ref(null as string[] | null)
const selectedBranch = ref(null as string | null)

const fileContent = ref(null as string | null)
const fileLink = ref(null as string | null)

const extractGitHub = (url: string): GitHubRepo | null => {
  const match = url.match(/https:\/\/github\.com\/([\w-.]*)\/([\w-.]*)(\/.*)*/)
  if (match) {
    return {
      owner: match[1]!!,
      repo: match[2]!!
    }
  }
  return null
}

watch(file, newValue => {
  if (newValue == null) return

  showDialog.value = true
  loading.value = true
  fetchWithVersion(client, MOD_INFORMATION, {
    id: newValue!!.mod
  }, props.version!!)
      .then(result => {
        const mod = result?.gameVersion?._modInformation!!

        let repository: GitHubRepo | null = null;
        // First try the source links directly
        if (mod?.curseforge?.sourceUrl) {
          repository = extractGitHub(mod.curseforge.sourceUrl)
        }
        if (repository == null && mod.modrinth?.sourceUrl) {
          repository = extractGitHub(mod.modrinth.sourceUrl)
        }

        // Next try the issues links
        if (repository == null && mod.curseforge?.issuesUrl) {
          repository = extractGitHub(mod.curseforge.issuesUrl)
        }
        if (repository == null && mod.modrinth?.issuesUrl) {
          repository = extractGitHub(mod.modrinth.issuesUrl)
        }

        // And if all fails, try to extract from metadata
        if (repository == null && mod.metadata) {
          const metadata = mod.metadata as any

          // NeoForge
          if (metadata.issueTrackerURL) {
            repository = extractGitHub(metadata.issueTrackerURL)
          }

          // Fabric
          if (repository == null && metadata.contact?.sources) {
            repository = extractGitHub(metadata.contact.sources)
          }
          if (repository == null && metadata.contact?.issues) {
            repository = extractGitHub(metadata.contact.issues)
          }
        }

        if (repository == null) {
          error.value = `Project does not have a link to its source or the source is not hosted on GitHub.`
        } else {
          repo.value = repository
          getGitHubBranches(repository.owner, repository.repo)
              .then(repoBranches => {
                const [mcVersion, loader] = props.version!!.split('-')

                branches.value = repoBranches
                // The simplest scenario: repo has only one branch
                if (repoBranches.length == 1) {
                  selectedBranch.value = repoBranches[0]!!
                }
                // Repo has a branch named after the mc version
                else if (repoBranches.includes(mcVersion!!)) {
                  selectedBranch.value = mcVersion!!
                }

                // Repo has a branch named loader/mcVersion
                else if (repoBranches.includes(`${loader!!.toLowerCase()}/${mcVersion}`)) {
                  selectedBranch.value = `${loader!!.toLowerCase()}/${mcVersion}`
                }
                // Special case of the above for neo
                else if (loader?.toLowerCase() == 'neoforge' && repoBranches.includes(`neo/${mcVersion}`)) {
                  selectedBranch.value = `neo/${mcVersion}`
                }
              })
        }

        loading.value = false
      })
})

watch(selectedBranch, newBranch => {
  if (!newBranch || !repo.value || !file.value) return

  loading.value = true
  error.value = null
  fileContent.value = null
  fileLink.value = null

  const repository = repo.value!!

  downloadGitHubBranch(repository.owner, repository.repo, newBranch)
      .then(result => {
        const zip = new JSZip()
        zip.loadAsync(result)
            .then(zip => {
              const fileName = file.value!!.file
              console.log(zip.files)
              const zipFile = Object.values(zip.files)
                  .find(f => f.name.endsWith(fileName))

              if (!zipFile) {
                error.value = `Cannot find file named ${fileName} in branch ${newBranch}.`
                loading.value = false
              } else {
                zipFile.async('string').then(result => {
                  fileContent.value = result
                  fileLink.value = `https://github.com/${repository.owner}/${repository.repo}/blob/${newBranch}/${zipFile.name.substring(zipFile.name.indexOf('/') + 1)}`
                  loading.value = false
                })
              }
            })
      })
})

watch(showDialog, newValue => {
  if (!newValue) {
    loading.value = false
    error.value = null
    branches.value = null
    repo.value = undefined
    selectedBranch.value = null
    fileContent.value = null
    fileLink.value = null
  }
})
</script>
