<template>
  <v-container>
    <h2>Classes</h2>
    <v-card
        title="Annotated Classes"
        to="annotated-classes" router>
      <template v-slot:text>
        Find the classes annotated with a given annotation.
      </template>
    </v-card>
    <br>
    <v-card
        title="Implementations"
        to="implementations" router>
      <template v-slot:text>
        Find the implementations of a given class.
      </template>
    </v-card>
    <br>
    <v-card
        title="Method References"
        to="method-references" router>
      <template v-slot:text>
        Find direct references to a given method.
      </template>
    </v-card>
    <br>
    <v-card
        title="Mixins targetting Classes in Package"
        to="mixins-package" router>
      <template v-slot:text>
        Find mixins that target classes in a certain package (and its subpackages).
      </template>
    </v-card>

    <h2>Data Files</h2>
    <v-card
        title="Recipes by type"
        to="recipes" router>
      <template v-slot:text>
        Find recipes of a given type.
      </template>
    </v-card>

    <h2>Tags</h2>
    <v-card
        title="Tags containing element"
        to="tags/containing" router>
      <template v-slot:text>
        Find all tags containing a given element, directly or indirectly (through other tags).
      </template>
    </v-card>
  </v-container>
</template>

<script>
definePageMeta({
  title: 'Queries'
})
</script>
