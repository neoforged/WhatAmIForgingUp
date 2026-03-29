<template>
  <v-container>
    <v-card
      title="Mixins targetting Classes in Package"
      to="mixins-package" router>
      <template v-slot:text>
        Find mixins that target classes in a certain package (and its subpackages).
      </template>
    </v-card>
  </v-container>
</template>

<script>
definePageMeta({
  title: 'Queries'
})
</script>
