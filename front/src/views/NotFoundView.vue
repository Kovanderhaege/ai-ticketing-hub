<script setup lang="ts">
import AppIcon from "../components/AppIcon.vue";
</script>

<template>
  <section class="panel empty-state">
    <span class="empty-icon"><AppIcon name="search" :size="30" /></span>
    <p class="eyebrow">ERREUR 404</p>
    <h1>Page introuvable</h1>
    <p>Cette page n’existe pas ou son adresse a changé.</p>
    <RouterLink to="/tickets" class="button button-primary"
      ><AppIcon name="arrow-left" :size="16" />Retour aux tickets</RouterLink
    >
  </section>
</template>
