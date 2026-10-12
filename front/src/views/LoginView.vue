<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "../components/AppIcon.vue";
import { useAuth } from "../composables/useAuth";

const route = useRoute();
const router = useRouter();
const { login, register, sessionError } = useAuth();
const registering = computed(() => route.path === "/register");
const email = ref("");
const password = ref("");
const confirmation = ref("");
const submitting = ref(false);
const error = ref("");
const registered = ref(false);

watch(registering, () => {
  error.value = "";
  password.value = "";
  confirmation.value = "";
});

async function submit() {
  if (submitting.value) return;
  error.value = "";
  if (registering.value && password.value !== confirmation.value) {
    error.value = "Les mots de passe ne correspondent pas.";
    return;
  }
  if (registering.value && new TextEncoder().encode(password.value).length > 72) {
    error.value = "Le mot de passe dépasse 72 octets. Utilisez moins de caractères.";
    return;
  }
  submitting.value = true;
  try {
    if (registering.value) {
      await register(email.value, password.value);
      registered.value = true;
      await router.replace({ path: "/login", query: route.query });
    } else {
      await login(email.value, password.value);
      // Only resume internal, protected routes; never trust an arbitrary redirect URL.
      const destination = route.query.redirect;
      await router.replace(typeof destination === "string" && /^\/(tickets(?:[/?]|$)|notifications(?:[/?]|$))/.test(destination)
        ? destination : "/tickets");
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "La demande n’a pas pu aboutir. Réessayez.";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section class="auth-card panel" aria-labelledby="auth-title">
    <div class="auth-heading">
      <span class="brand-mark"><AppIcon name="layers" /></span>
      <p class="eyebrow">KOVA · TICKETING HUB</p>
      <h1 id="auth-title">{{ registering ? "Créer votre compte" : "Bienvenue dans votre espace" }}<span class="heading-dot">.</span></h1>
      <p class="page-description">{{ registering ? "Un compte pour créer et suivre vos demandes." : "Connectez-vous pour consulter et créer des tickets." }}</p>
    </div>
    <form :aria-busy="submitting" @submit.prevent="submit">
      <p v-if="registered && !registering" class="auth-notice" role="status">Votre compte a été créé. Vous pouvez maintenant vous connecter.</p>
      <p v-else-if="route.query.reason === 'expired' && !registering" class="auth-notice" role="status">Votre session a expiré. Reconnectez-vous pour continuer.</p>
      <div v-if="error || sessionError" class="alert alert-error" role="alert">{{ error || sessionError }}</div>
      <div class="field">
        <label for="auth-email">Adresse e-mail</label>
        <input id="auth-email" v-model="email" type="email" autocomplete="username" required maxlength="254" :disabled="submitting" placeholder="vous@exemple.fr" />
      </div>
      <div class="field">
        <label for="auth-password">Mot de passe</label>
        <input id="auth-password" v-model="password" type="password" :autocomplete="registering ? 'new-password' : 'current-password'" required :minlength="registering ? 12 : undefined" :maxlength="registering ? 72 : undefined" :disabled="submitting" :aria-describedby="registering ? 'password-hint' : undefined" />
        <p v-if="registering" id="password-hint" class="field-hint">Au moins 12 caractères, au plus 72 octets.</p>
      </div>
      <div v-if="registering" class="field">
        <label for="auth-confirmation">Confirmer le mot de passe</label>
        <input id="auth-confirmation" v-model="confirmation" type="password" autocomplete="new-password" required minlength="12" maxlength="72" :disabled="submitting" />
      </div>
      <button type="submit" class="button button-primary auth-submit" :disabled="submitting">
        <span v-if="submitting" class="loader loader-small"></span>
        {{ submitting ? "Veuillez patienter…" : registering ? "Créer mon compte" : "Se connecter" }}
      </button>
    </form>
    <p class="auth-switch">
      {{ registering ? "Déjà un compte ?" : "Pas encore de compte ?" }}
      <RouterLink v-if="!submitting" :to="{ path: registering ? '/login' : '/register', query: route.query }">{{ registering ? "Se connecter" : "Créer un compte" }}</RouterLink>
    </p>
  </section>
</template>
