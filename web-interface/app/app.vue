<template>
  <NuxtLayout>
    <v-app>
      <v-app-bar>
        <v-app-bar-nav-icon variant="text" @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
        <v-toolbar-title>WAIFU {{ useRoute().meta.title }}</v-toolbar-title>
        <template v-slot:append>
          <v-btn v-if="!username" :href="getOAuthURL()" @click="redirectToOAuth">Sign In</v-btn>
          <v-list-item v-else
                       lines="two"
                       :title="username"
                       :prepend-avatar="avatarUrl"
                       subtitle="Log out"
                       link @click="logout"
          />
        </template>
      </v-app-bar>
      <v-navigation-drawer
          v-model="drawer"
          temporary
      >
        <v-list density="compact" variant="text" rounded slim nav>
          <v-list-group value="Queries" nav>
            <template v-slot:activator="{ props }">
              <v-list-item
                  v-bind="props"
                  prepend-icon="mdi-database-search"
                  title="Queries"
              ></v-list-item>
            </template>
            <v-list-item title="All Queries" to="/queries/" router/>
            <div v-for="group in QUERIES">
              <v-divider class="ma-1 ms-10"/>
              <v-list-subheader class="text-high-emphasis text-uppercase font-weight-black" :title="group.group" />
              <v-list-item v-for="q in group.queries"
                           :title="q.name"
                           router :to="`/queries/${group.path}/${q.path}`"/>
            </div>
          </v-list-group>
        </v-list>
      </v-navigation-drawer>
      <v-main>
        <v-container class="pa-3">
          <NuxtPage/>
        </v-container>
      </v-main>

      <v-dialog v-model="hasAlerts" max-width="840" @close="alerts = []">
        <v-card>
          <v-card-text>
            <v-alert
                v-for="alert of alerts"
                :title="alert.title"
                color="#C51162"
                class="mb-1"
            >
              <template v-slot:text>
                {{ alert.description }}
              </template>
            </v-alert>
          </v-card-text>

          <v-card-actions>
            <v-btn @click="alerts = []" block>Close</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>
    </v-app>
  </NuxtLayout>
</template>

<script setup lang="ts">
import {getOAuthURL, redirectToOAuth} from '~/utils/api-utils'
import {deleteCookieByName} from '~/utils/utils'
import {useAlerts} from "~/utils/alerts";
import {QUERIES} from "~/query/queries";

const alerts = useAlerts()
const hasAlerts = computed(() => alerts.value.length > 0)

const route = useRoute()
const logout = () => {
  deleteCookieByName('discord-token')
  window.location.href = window.location.href
}

const drawer = ref(false)
const inAuth = route.path == '/oauth2/discord/completed'

const username = ref(undefined)
const avatarUrl = ref(undefined)

onMounted(() => {
  if (!inAuth && getCookieByName('discord-token')) {
    const infoCookie = getCookieByName('discord-identification')
    if (infoCookie) {
      const json = JSON.parse(infoCookie)
      username.value = json.name
      avatarUrl.value = json.avatar
    }
  }
})
</script>
