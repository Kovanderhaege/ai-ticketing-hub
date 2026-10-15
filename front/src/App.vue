<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "./components/AppIcon.vue";
import { useAuth } from "./composables/useAuth";

const route = useRoute();
const router = useRouter();
const { user, logout } = useAuth();
const signingOut = ref(false);
const logoutError = ref("");

async function signOut() {
  if (signingOut.value) return;
  signingOut.value = true;
  logoutError.value = "";
  try {
    await logout();
    await router.replace("/login");
  } catch (cause) {
    logoutError.value = cause instanceof Error ? cause.message : "Impossible de vous déconnecter. Réessayez.";
  } finally {
    signingOut.value = false;
  }
}
const section = computed(() =>
  route.path.startsWith("/admin/") ? "Administration" : route.path === "/notifications" ? "Notifications" : "Tickets",
);
const today = new Intl.DateTimeFormat("fr-FR", {
  day: "numeric",
  month: "long",
  year: "numeric",
}).format(new Date());
</script>

<template>
  <a class="skip-link" href="#main-content">Aller au contenu</a>
  <main v-if="!user" id="main-content" class="auth-shell" tabindex="-1">
    <RouterView v-if="route.meta.public" />
    <p v-else role="status">Vérification de votre session…</p>
  </main>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <RouterLink
        to="/tickets"
        class="brand"
        aria-label="Kova — accueil des tickets"
      >
        <span class="brand-mark"><AppIcon name="layers" /></span>
        <span>Kova<span class="brand-caption">TICKETING HUB</span></span>
      </RouterLink>
      <div class="workspace-label">
        <span class="workspace-avatar">K</span
        ><span>Espace support<small>Votre centre de suivi</small></span>
      </div>
      <p class="nav-label">ESPACE DE TRAVAIL</p>
      <nav aria-label="Navigation principale" class="main-nav" :class="{ 'has-administration': user.role === 'ADMIN' }">
        <RouterLink
          to="/tickets"
          :class="{
            active:
              route.path === '/tickets' ||
              (route.path.startsWith('/tickets/') &&
                route.path !== '/tickets/new'),
          }"
          ><AppIcon name="inbox" /><span>Tous les tickets</span></RouterLink
        >
        <RouterLink
          to="/tickets/new"
          :class="{ active: route.path === '/tickets/new' }"
          ><AppIcon name="plus" /><span>Créer un ticket</span></RouterLink
        >
        <RouterLink
          to="/notifications"
          :class="{ active: route.path === '/notifications' }"
          ><AppIcon name="bell" /><span>Notifications</span></RouterLink
        >
        <RouterLink v-if="user.role === 'ADMIN'" to="/admin/ai" :class="{ active: route.path === '/admin/ai' }"><AppIcon name="sparkles" /><span>Administration</span></RouterLink>
      </nav>
      <div class="sidebar-note">
        <span class="note-icon"><AppIcon name="sparkles" /></span>
        <h2>Moins de tri.<br />Plus de clarté.</h2>
        <p>
          Vos tickets sont analysés et classés automatiquement pour vous aider à
          prioriser.
        </p>
        <RouterLink to="/tickets/new"
          >Créer une demande <AppIcon name="arrow-right"
        /></RouterLink>
      </div>
      <div class="sidebar-footer">
        <span class="footer-dot"></span> AI Ticketing Hub <span>v1.0</span>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          <AppIcon name="grid" /><span>Espace support</span
          ><span class="breadcrumb-divider">/</span
          ><strong>{{ section }}</strong>
        </div>
        <div class="topbar-actions">
          <span class="user-email" :title="user.email">{{ user.email }}</span>
          <button type="button" class="button button-quiet" :disabled="signingOut" @click="signOut">{{ signingOut ? "Déconnexion…" : "Se déconnecter" }}</button>
          <span class="today">{{ today }}</span
          ><RouterLink
            class="icon-button"
            to="/notifications"
            aria-label="Voir les notifications"
            ><AppIcon name="bell"
          /></RouterLink>
        </div>
      </header>
      <main id="main-content" tabindex="-1">
        <p v-if="logoutError" class="alert alert-error" role="alert">{{ logoutError }}</p>
        <RouterView />
      </main>
      <footer class="workspace-footer">
        <span>Kova · AI Ticketing Hub</span
        ><span>Chaque demande mérite un suivi.</span>
      </footer>
    </div>
  </div>
</template>
