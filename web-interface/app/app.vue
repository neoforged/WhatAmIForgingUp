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
        <v-list nav>
          <v-list-item prepend-icon="mdi-database-search" title="Queries" to="/queries/" router/>
        </v-list>
      </v-navigation-drawer>
      <v-main>
        <v-container>
          <NuxtPage/>
        </v-container>
      </v-main>
    </v-app>
  </NuxtLayout>
</template>

<script setup lang="ts">
import {getOAuthURL, redirectToOAuth} from '~/utils/api-utils'
import {deleteCookieByName} from '~/utils/utils'

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
