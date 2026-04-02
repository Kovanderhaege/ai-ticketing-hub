
<script setup lang="ts">
  import { ref } from 'vue';

  const name = ref('Hugo')
</script>

<template>
  <div>
    <h1>Welcome, {{ name }} !</h1>
  </div>


  <div>
    <nav>
      <router-link to="/tickets">Tickets</router-link> |
      <router-link to="/tickets/new">Créer un ticket</router-link> |
      <router-link to="/notifications">Notifications</router-link>
    </nav>

    <hr />

    <router-view />
  </div>
</template>
