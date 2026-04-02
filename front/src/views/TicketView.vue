<template>
  <div>
    <h2>Ticket details</h2>

    <p v-if="loading">Loading...</p>
    <p v-else-if="error">{{ error }}</p>

    <div v-else-if="ticket" class="ticket-card">
      <h3>{{ ticket.title }}</h3>

      <p>{{ ticket.description }}</p>

      <div class="meta">
        <span>Status: {{ ticket.status }}</span>
        <span v-if="ticket.priority">Priority: {{ ticket.priority }}</span>
        <span v-if="ticket.category">Category: {{ ticket.category }}</span>
      </div>

      <p v-if="ticket.summary" class="summary">
        <strong>Summary:</strong> {{ ticket.summary }}
      </p>

      <div class="dates">
        <small>Created: {{ formatDate(ticket.createdAt) }}</small>
        <small>Updated: {{ formatDate(ticket.updatedAt) }}</small>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue"
import { useRoute } from "vue-router"
import { getTicket } from "../api/ticketApi"
import type { Ticket } from "../types/ticket"

const route = useRoute()

const ticket = ref<Ticket | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function loadTicket() {
  try {
    loading.value = true
    error.value = null

    const id = route.params.id as string
    ticket.value = await getTicket(id)
  } catch (e) {
    console.error(e)
    error.value = "Failed to fetch ticket"
  } finally {
    loading.value = false
  }
}

function formatDate(date: string): string {
  return new Date(date).toLocaleString()
}

onMounted(loadTicket)
</script>