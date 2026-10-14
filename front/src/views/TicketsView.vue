<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { getTickets } from "../api/ticketApi";
import AppIcon from "../components/AppIcon.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useAsyncData } from "../composables/useAsyncData";
import type { Ticket } from "../types/ticket";
import {
  categoryLabel,
  formatDate,
  normalizeSearch,
  statusLabels,
} from "../utils/tickets";

const {
  data: tickets,
  loading,
  error,
  hasLoaded,
  load,
} = useAsyncData<Ticket[]>(getTickets, []);
const search = ref("");
const status = ref("");
const priority = ref("");
const sort = ref("recent");
const page = ref(1);
const pageSize = 8;
const tabs = [
  { value: "", label: "Tous les tickets" },
  ...Object.entries(statusLabels).map(([value, label]) => ({ value, label })),
];
const stats = computed(() => [
  {
    label: "Total des tickets",
    value: tickets.value.length,
    icon: "inbox",
    tone: "green",
    caption: "Toutes vos demandes",
  },
  {
    label: "À analyser",
    value: tickets.value.filter((t) => t.status === "NEW").length,
    icon: "clock",
    tone: "blue",
    caption: "En attente de classification",
  },
  {
    label: "Priorité haute",
    value: tickets.value.filter(
      (t) => t.priority === "HIGH" && t.status !== "CLOSED",
    ).length,
    icon: "flag",
    tone: "orange",
    caption: "À suivre en priorité",
  },
  {
    label: "Tickets clôturés",
    value: tickets.value.filter((t) => t.status === "CLOSED").length,
    icon: "check-circle",
    tone: "gray",
    caption: "Demandes terminées",
  },
]);
const filteredTickets = computed(() => {
  const query = normalizeSearch(search.value.trim());
  const weights: Record<string, number> = { HIGH: 3, MEDIUM: 2, LOW: 1 };
  return tickets.value
    .filter(
      (ticket) =>
        (!status.value || ticket.status === status.value) &&
        (!priority.value || ticket.priority === priority.value) &&
        (!query ||
          normalizeSearch(
            [
              ticket.title,
              ticket.description,
              ticket.summary,
              ticket.id,
              ticket.reporter,
              ticket.assignee,
              ticket.component,
              ...(ticket.labels || []),
              categoryLabel(ticket.category),
            ].join(" "),
          ).includes(query)),
    )
    .sort((a, b) => {
      if (sort.value === "priority") {
        const difference =
          (weights[b.priority || ""] || 0) - (weights[a.priority || ""] || 0);
        if (difference) return difference;
      }
      return (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0);
    });
});
const pageCount = computed(() =>
  Math.max(1, Math.ceil(filteredTickets.value.length / pageSize)),
);
const visibleTickets = computed(() =>
  filteredTickets.value.slice(
    (page.value - 1) * pageSize,
    page.value * pageSize,
  ),
);
const hasFilters = computed(() =>
  Boolean(search.value || status.value || priority.value),
);
watch([search, status, priority, sort], () => {
  page.value = 1;
});
watch(pageCount, (count) => {
  page.value = Math.min(page.value, count);
});
function resetFilters() {
  search.value = "";
  status.value = "";
  priority.value = "";
}
onMounted(load);
</script>

<template>
  <div class="page-heading">
    <div>
      <p class="eyebrow">VUE D’ENSEMBLE</p>
      <h1>Tous les tickets<span class="heading-dot">.</span></h1>
      <p class="page-description">
        Gardez le fil de vos demandes et concentrez-vous sur l’essentiel.
      </p>
    </div>
    <RouterLink to="/tickets/new" class="button button-primary"
      ><AppIcon name="plus" />Nouveau ticket</RouterLink
    >
  </div>

  <section
    class="stats-grid"
    aria-label="Indicateurs des tickets"
    :aria-busy="loading"
  >
    <article v-for="stat in stats" :key="stat.label" class="stat-card">
      <div class="stat-top">
        <span>{{ stat.label }}</span
        ><span class="stat-icon" :class="stat.tone"
          ><AppIcon :name="stat.icon"
        /></span>
      </div>
      <strong class="stat-value">{{ hasLoaded ? stat.value : "—" }}</strong
      ><span class="stat-caption">{{ stat.caption }}</span>
    </article>
  </section>

  <div v-if="error" class="alert alert-error" role="alert">
    <AppIcon name="alert" />
    <div>
      <strong>Impossible d’actualiser les tickets</strong>
      <p>
        {{ error
        }}<template v-if="hasLoaded">
          Les dernières données chargées restent affichées.</template
        >
      </p>
    </div>
    <button class="button button-small" :disabled="loading" @click="load">
      Réessayer
    </button>
  </div>

  <section
    class="panel tickets-panel"
    aria-label="Liste des tickets"
    :aria-busy="loading"
  >
    <div class="panel-heading">
      <div>
        <h2>
          Suivi des demandes
          <span class="count-pill">{{ hasLoaded ? tickets.length : "—" }}</span>
        </h2>
        <p>Une vue claire de chaque ticket, du début à la fin.</p>
      </div>
      <button class="button button-small" :disabled="loading" @click="load">
        <AppIcon name="refresh" :class="{ spinning: loading }" />Actualiser
      </button>
    </div>
    <div class="filter-tabs" role="group" aria-label="Filtrer par statut">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="{ selected: status === tab.value }"
        :aria-pressed="status === tab.value"
        @click="status = tab.value"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="table-toolbar">
      <label class="search-field"
        ><AppIcon name="search" /><input
          v-model="search"
          type="search"
          placeholder="Rechercher un ticket…"
          aria-label="Rechercher un ticket"
      /></label>
      <div class="table-selects">
        <label class="select-wrap"
          ><AppIcon name="filter" /><select
            v-model="priority"
            aria-label="Filtrer par priorité"
          >
            <option value="">Toutes les priorités</option>
            <option value="HIGH">Priorité haute</option>
            <option value="MEDIUM">Priorité moyenne</option>
            <option value="LOW">Priorité basse</option>
          </select></label
        ><select v-model="sort" aria-label="Trier les tickets">
          <option value="recent">Plus récents</option>
          <option value="priority">Priorité décroissante</option>
        </select>
      </div>
    </div>

    <div v-if="loading && !hasLoaded" class="loading-state" role="status">
      <span class="loader"></span>
      <p>Chargement de vos tickets…</p>
    </div>
    <template v-else-if="visibleTickets.length">
      <div class="table-scroll">
        <table class="ticket-table">
          <caption class="sr-only">
            Tickets correspondant aux filtres
          </caption>
          <thead>
            <tr>
              <th scope="col">TICKET</th>
              <th scope="col">STATUT</th>
              <th scope="col">PRIORITÉ</th>
              <th scope="col">CATÉGORIE</th>
              <th scope="col">CRÉÉ LE</th>
              <th scope="col"><span class="sr-only">Consulter</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ticket in visibleTickets" :key="ticket.id">
              <td class="ticket-main-cell">
                <RouterLink
                  :to="`/tickets/${ticket.id}`"
                  class="ticket-title"
                  >{{ ticket.title }}</RouterLink
                >
                <p class="ticket-preview">
                  {{ ticket.summary || ticket.description }}
                </p>
                <p v-if="ticket.reporter || ticket.assignee" class="ticket-people">{{ ticket.reporter }}<template v-if="ticket.assignee"> · Assigné à {{ ticket.assignee }}</template></p>
                <div v-if="ticket.labels?.length" class="label-list"><span v-for="label in ticket.labels" :key="label" class="badge">{{ label }}</span></div>
                <span class="ticket-id"
                  >#{{ ticket.id.slice(0, 8).toUpperCase() }}</span
                >
              </td>
              <td data-label="Statut">
                <StatusBadge :value="ticket.status" />
              </td>
              <td data-label="Priorité">
                <StatusBadge :value="ticket.priority" kind="priority" />
              </td>
              <td data-label="Catégorie">
                <span class="category-text">{{
                  categoryLabel(ticket.category)
                }}</span>
              </td>
              <td data-label="Créé le" class="date-cell">
                {{ formatDate(ticket.createdAt) }}
              </td>
              <td class="row-action">
                <RouterLink
                  :to="`/tickets/${ticket.id}`"
                  class="icon-button"
                  :aria-label="`Consulter le ticket : ${ticket.title}`"
                  ><AppIcon name="arrow-right" :size="17"
                /></RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <div v-else-if="hasLoaded" class="empty-state">
      <span class="empty-icon"
        ><AppIcon :name="hasFilters ? 'search' : 'inbox'" :size="30"
      /></span>
      <h3>
        {{
          hasFilters
            ? "Aucun ticket ne correspond"
            : "Tout commence par un ticket"
        }}
      </h3>
      <p>
        {{
          hasFilters
            ? "Essayez un autre mot-clé ou ajustez vos filtres."
            : "Créez votre première demande. Son analyse et sa classification seront automatiques."
        }}
      </p>
      <button v-if="hasFilters" class="button" @click="resetFilters">
        Réinitialiser les filtres</button
      ><RouterLink v-else to="/tickets/new" class="button button-primary"
        ><AppIcon name="plus" />Créer mon premier ticket</RouterLink
      >
    </div>
    <div v-else class="empty-state">
      <span class="empty-icon"><AppIcon name="inbox" :size="30" /></span>
      <h3>Vos tickets s’afficheront ici</h3>
      <p>
        La liste sera disponible dès que la connexion au service sera rétablie.
      </p>
    </div>
    <div class="table-footer">
      <span aria-live="polite"
        >{{
          hasLoaded
            ? `${filteredTickets.length} ticket${filteredTickets.length > 1 ? "s" : ""}`
            : "En attente des données"
        }}<template v-if="hasFilters">
          correspondant aux filtres</template
        ></span
      >
      <div v-if="pageCount > 1" class="pagination">
        <button
          class="icon-button"
          :disabled="page === 1"
          aria-label="Page précédente"
          @click="page--"
        >
          <AppIcon name="chevron-left" :size="16" /></button
        ><span>Page {{ page }} sur {{ pageCount }}</span
        ><button
          class="icon-button"
          :disabled="page === pageCount"
          aria-label="Page suivante"
          @click="page++"
        >
          <AppIcon name="chevron-right" :size="16" />
        </button>
      </div>
      <span v-else class="table-footer-note"
        ><AppIcon name="sparkles" :size="14" />La clarté, à chaque étape.</span
      >
    </div>
  </section>
  <div class="inline-note">
    <AppIcon name="info" :size="16" />
    <p>
      La catégorie, la priorité et le résumé sont renseignés après l’analyse
      automatique. Actualisez pour voir les dernières mises à jour.
    </p>
  </div>
</template>
