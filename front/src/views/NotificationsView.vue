<template>
  <div>Notifications</div>

    <button @click="loadNotifications">Refresh</button>

    <ul v-if="notifications.length">
      <li v-for="n in notifications" :key="n.id">
          <Strong>{{n.message}}</Strong>
          | priority: {{ n.priority }}
          | ticket:  <router-link :to="`/ticket/${n.ticketId}`">{{ n.ticketId }}</router-link>
          | date: {{ n.createdAt }}
      </li>
    </ul>
  
</template>

<script setup lang="ts">
  import {ref, onMounted} from "vue"
  import { getNotifications } from "../api/notificationApi";
  import type { Notification } from "../types/notification";

  const notifications = ref<Notification[]>([])

  async function loadNotifications() {
  try {
    notifications.value = await getNotifications()
  } catch (e) {
    console.error(e)
  }
}

onMounted(loadNotifications)
</script>