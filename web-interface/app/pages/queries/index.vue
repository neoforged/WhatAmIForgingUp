<template>
  <div>
    <div v-for="group in QUERIES">
      <h2>{{ group.group }}</h2>
      <div v-for="query in group.queries">
        <v-card
            :title="query.name"
            :to="`/queries/${group.path}/${query.path}`" router>
          <template v-slot:text>
            <component :is="query.query.description" />
          </template>
        </v-card>
        <br />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {QUERIES} from "~/query/queries";

definePageMeta({
  title: 'Queries'
})
</script>
