<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { getNotifications } from "../api/notificationApi";
import AppIcon from "../components/AppIcon.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useAsyncData } from "../composables/useAsyncData";
import type { Notification } from "../types/notification";
import { formatDate, normalizeSearch } from "../utils/tickets";

const {
  data: notifications,
  loading,
  error,
  hasLoaded,
  load,
} = useAsyncData<Notification[]>(getNotifications, []);
const search = ref("");
const priority = ref("");
const filteredNotifications = computed(() => {
  const query = normalizeSearch(search.value.trim());
  return notifications.value
    .filter(
      (n) =>
        (!priority.value || n.priority === priority.value) &&
        (!query ||
          normalizeSearch(`${messageLabel(n.message)} ${n.ticketId}`).includes(
            query,
          )),
    )
    .sort(
      (a, b) => (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0),
    );
});
function messageLabel(message: string) {
  return message === "High priority ticket detected"
    ? "Un ticket à traiter en priorité"
    : message;
}
onMounted(load);
</script>

<template>
  <div class="page-heading">
    <div>
      <p class="eyebrow">GARDEZ LE FIL</p>
      <h1>Notifications<span class="heading-dot">.</span></h1>
      <p class="page-description">
        Les demandes qui méritent votre attention, réunies ici.
      </p>
    </div>
    <button class="button" :disabled="loading" @click="load">
      <AppIcon name="refresh" :class="{ spinning: loading }" />Actualiser
    </button>
  </div>
  <div v-if="error" class="alert alert-error" role="alert">
    <AppIcon name="alert" />
    <div>
      <strong>Impossible d’actualiser les notifications</strong>
      <p>
        {{ error
        }}<template v-if="hasLoaded">
          Les dernières notifications chargées restent affichées.</template
        >
      </p>
    </div>
    <button class="button button-small" :disabled="loading" @click="load">
      Réessayer
    </button>
  </div>
  <section
    class="panel"
    aria-label="Liste des notifications"
    :aria-busy="loading"
  >
    <div class="panel-heading">
      <div>
        <h2>
          Votre fil d’activité
          <span class="count-pill">{{
            hasLoaded ? notifications.length : "—"
          }}</span>
        </h2>
        <p>Les alertes générées après l’analyse de vos tickets.</p>
      </div>
      <span class="soft-icon"><AppIcon name="bell" /></span>
    </div>
    <div class="table-toolbar">
      <label class="search-field"
        ><AppIcon name="search" /><input
          v-model="search"
          type="search"
          aria-label="Rechercher une notification"
          placeholder="Rechercher une notification…" /></label
      ><label class="select-wrap"
        ><AppIcon name="filter" /><select
          v-model="priority"
          aria-label="Filtrer les notifications par priorité"
        >
          <option value="">Toutes les priorités</option>
          <option value="HIGH">Priorité haute</option>
          <option value="MEDIUM">Priorité moyenne</option>
          <option value="LOW">Priorité basse</option>
        </select></label
      >
    </div>
    <div v-if="loading && !hasLoaded" class="loading-state" role="status">
      <span class="loader"></span>
      <p>Chargement des notifications…</p>
    </div>
    <ul v-else-if="filteredNotifications.length" class="notification-list">
      <li v-for="notification in filteredNotifications" :key="notification.id">
        <span class="notification-icon"><AppIcon name="flag" /></span>
        <div class="notification-content">
          <div class="notification-heading">
            <RouterLink :to="`/tickets/${notification.ticketId}`">{{
              messageLabel(notification.message)
            }}</RouterLink
            ><StatusBadge :value="notification.priority" kind="priority" />
          </div>
          <p>Ticket #{{ notification.ticketId.slice(0, 8).toUpperCase() }}</p>
          <time :datetime="notification.createdAt">{{
            formatDate(notification.createdAt, true)
          }}</time>
        </div>
        <RouterLink
          :to="`/tickets/${notification.ticketId}`"
          class="icon-button"
          :aria-label="`Ouvrir le ticket ${notification.ticketId}`"
          ><AppIcon name="arrow-right" :size="18"
        /></RouterLink>
      </li>
    </ul>
    <div v-else-if="hasLoaded" class="empty-state">
      <span class="empty-icon"
        ><AppIcon :name="search || priority ? 'search' : 'bell'" :size="30"
      /></span>
      <h3>
        {{
          search || priority
            ? "Aucune notification ne correspond"
            : "Vous êtes à jour"
        }}
      </h3>
      <p>
        {{
          search || priority
            ? "Essayez une autre recherche ou retirez les filtres."
            : "Les alertes concernant vos tickets apparaîtront ici dès qu’elles seront disponibles."
        }}
      </p>
      <button
        v-if="search || priority"
        class="button"
        @click="
          search = '';
          priority = '';
        "
      >
        Réinitialiser les filtres</button
      ><RouterLink v-else to="/tickets" class="button"
        >Voir les tickets<AppIcon name="arrow-right" :size="16"
      /></RouterLink>
    </div>
    <div v-else class="empty-state">
      <span class="empty-icon"><AppIcon name="bell" :size="30" /></span>
      <h3>Votre fil d’activité s’affichera ici</h3>
      <p>Réessayez lorsque le service sera disponible.</p>
    </div>
    <div class="table-footer">
      <span aria-live="polite">{{
        hasLoaded
          ? `${filteredNotifications.length} notification${filteredNotifications.length > 1 ? "s" : ""}`
          : "En attente des données"
      }}</span
      ><span class="table-footer-note">Les plus récentes en premier</span>
    </div>
  </section>
</template>
