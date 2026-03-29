<template>
  <h1>Logging in...</h1>
</template>

<script setup lang="ts">
import {deleteCookieByName, getCookieByName} from "~/utils/utils";
import {getUserInformation} from "~/utils/discord-utils";

const route = useRoute()

onMounted(() => {
  const queryToken = route.query.token
  if (queryToken) {
    document.cookie = `discord-token=${queryToken}; Path=/`
  }

  const discordToken = getCookieByName('discord-token')!!

  getUserInformation(discordToken).then(info => {
    document.cookie = `discord-identification=${JSON.stringify(info)}; Path=/`
    const redirectUrl = getCookieByName('redirect-url') ?? window.location.origin

    deleteCookieByName('redirect-url')
    window.location.href = redirectUrl
  })
})

</script>
