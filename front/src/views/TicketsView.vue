<template>
  <div>
    <h2>Tickets</h2>

    <button @click="loadTickets">Refresh</button>

    <ul v-if="tickets.length">
      <li v-for="t in tickets" :key="t.id">
        <strong>{{ t.title }}</strong>
        | status: {{ t.status }}
        | priority: {{ t.priority }}
        | category: {{ t.category }}
        <br />
        {{ t.summary }}
      </li>
    </ul>

    <p v-else>Aucun ticket</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue"
import { getTickets } from "../api/ticketApi"
import type { Ticket } from "../types/ticket"

const tickets = ref<Ticket[]>([])

async function loadTickets() {
  try {
    tickets.value = await getTickets()
  } catch (e) {
    console.error(e)
  }
}

onMounted(loadTickets)
</script>