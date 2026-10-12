<script setup lang="ts">
import { watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getTicket } from "../api/ticketApi";
import AppIcon from "../components/AppIcon.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useAsyncData } from "../composables/useAsyncData";
import type { Ticket } from "../types/ticket";
import { categoryLabel, formatDate } from "../utils/tickets";

const route = useRoute();
const router = useRouter();
const {
  data: ticket,
  loading,
  error,
  load,
} = useAsyncData<Ticket | null>(
  (signal) => getTicket(String(route.params.id), signal),
  null,
);
watch(
  () => route.params.id,
  () => {
    ticket.value = null;
    void load();
  },
  { immediate: true },
);
function dismissSuccess() {
  const query = { ...route.query };
  delete query.created;
  void router.replace({ query });
}
</script>

<template>
  <RouterLink to="/tickets" class="back-link"
    ><AppIcon name="arrow-left" :size="16" />Retour aux tickets</RouterLink
  >
  <div
    v-if="route.query.created === '1'"
    class="alert alert-success"
    role="status"
  >
    <AppIcon name="check-circle" />
    <div>
      <strong>Votre ticket a bien été créé</strong>
      <p>
        Vous pouvez suivre son analyse et retrouver toutes ses informations ici.
      </p>
    </div>
    <button
      class="icon-button"
      aria-label="Fermer la confirmation"
      @click="dismissSuccess"
    >
      <AppIcon name="close" :size="17" />
    </button>
  </div>
  <div v-if="loading && !ticket" class="panel loading-state" role="status">
    <span class="loader"></span>
    <p>Chargement du ticket…</p>
  </div>
  <div v-if="error" class="alert alert-error" role="alert">
    <AppIcon name="alert" />
    <div>
      <strong>Impossible de charger le ticket</strong>
      <p>
        {{ error
        }}<template v-if="ticket">
          Les dernières informations chargées restent affichées.</template
        >
      </p>
    </div>
    <button class="button button-small" :disabled="loading" @click="load">
      Réessayer
    </button>
  </div>
  <template v-if="ticket">
    <div class="page-heading detail-heading">
      <div>
        <p class="eyebrow">TICKET #{{ ticket.id.slice(0, 8).toUpperCase() }}</p>
        <h1>{{ ticket.title }}</h1>
        <p class="page-description">
          Créé le {{ formatDate(ticket.createdAt, true) }}
        </p>
      </div>
      <button class="button" :disabled="loading" @click="load">
        <AppIcon name="refresh" :class="{ spinning: loading }" />Actualiser
      </button>
    </div>
    <div class="content-grid">
      <div class="detail-content">
        <section class="panel description-panel">
          <div class="panel-heading">
            <h2><AppIcon name="file" />Description de la demande</h2>
          </div>
          <p class="ticket-description">{{ ticket.description }}</p>
        </section>
        <section class="analysis-panel">
          <div class="analysis-heading">
            <span class="note-icon"><AppIcon name="sparkles" /></span>
            <div>
              <p class="eyebrow">ANALYSE AUTOMATIQUE</p>
              <h2>L’essentiel de votre demande</h2>
            </div>
          </div>
          <p v-if="ticket.summary" class="ticket-description">
            {{ ticket.summary }}
          </p>
          <div v-else>
            <p class="analysis-pending">
              L’analyse n’est pas encore disponible.
            </p>
            <p>
              Le résumé, la catégorie et la priorité apparaîtront ici après le
              traitement du ticket. Actualisez dans quelques instants.
            </p>
          </div>
        </section>
      </div>
      <aside class="panel properties-panel">
        <div class="panel-heading"><h2>Informations du ticket</h2></div>
        <dl>
          <div>
            <dt>Statut</dt>
            <dd><StatusBadge :value="ticket.status" /></dd>
          </div>
          <div>
            <dt>Priorité</dt>
            <dd><StatusBadge :value="ticket.priority" kind="priority" /></dd>
          </div>
          <div>
            <dt>Catégorie</dt>
            <dd>{{ categoryLabel(ticket.category) }}</dd>
          </div>
          <div class="property-divider">
            <dt>Créé le</dt>
            <dd>{{ formatDate(ticket.createdAt, true) }}</dd>
          </div>
          <div>
            <dt>Dernière mise à jour</dt>
            <dd>{{ formatDate(ticket.updatedAt, true) }}</dd>
          </div>
          <div class="property-id">
            <dt>Identifiant</dt>
            <dd>{{ ticket.id }}</dd>
          </div>
        </dl>
        <div class="property-note">
          <AppIcon name="info" :size="16" />
          <p>
            Ces informations évoluent au fil du traitement de votre demande.
          </p>
        </div>
      </aside>
    </div>
  </template>
</template>
