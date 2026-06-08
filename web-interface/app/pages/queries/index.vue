<template>
  <div>
    <h2>Classes</h2>
    <v-card
        title="Annotated Classes"
        to="/queries/classes/annotated-classes" router>
      <template v-slot:text>
        Find the classes annotated with a given annotation.
      </template>
    </v-card>
    <br>
    <v-card
        title="Implementations"
        to="/queries/classes/implementations" router>
      <template v-slot:text>
        Find the implementations of a given class.
      </template>
    </v-card>
    <br>
    <v-card
        title="Method References"
        to="/queries/classes/method-references" router>
      <template v-slot:text>
        Find direct references to a given method.
      </template>
    </v-card>
    <br>
    <v-card
        title="Mixins targetting Classes in Package"
        to="/queries/mixin/mixins-package" router>
      <template v-slot:text>
        Find mixins that target classes in a certain package (and its subpackages).
      </template>
    </v-card>

    <h2>Data Files</h2>
    <v-card
        title="Recipes by type"
        to="/queries/data-files/recipes" router>
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

    <h2>NeoForge</h2>
    <v-card
        title="Enum extensions"
        to="/queries/neoforge/enum-extensions" router>
      <template v-slot:text>
        Find all NeoForge enum extensions for a given enum.
      </template>
    </v-card>
  </div>
</template>

<script>
definePageMeta({
  title: 'Queries'
})
</script>
