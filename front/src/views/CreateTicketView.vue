<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { createTicket } from "../api/ticketApi";
import AppIcon from "../components/AppIcon.vue";
import TicketFields from "../components/TicketFields.vue";
import AttachmentPicker from "../components/AttachmentPicker.vue";
import { ticketDraft, ticketPayload, validateFiles } from "../utils/ticketForm";
import { useAuth } from "../composables/useAuth";

const router = useRouter();
const { user } = useAuth();
const draft = ref(ticketDraft());
const files = ref<File[]>([]);
const creationMode = ref<"simple" | "complete">("simple");
const title = ref("");
const autoTitle = ref(false);
const description = ref("");
const submitting = ref(false);
const attempted = ref(false);
const error = ref("");
const titleError = computed(() =>
  attempted.value && !autoTitle.value && !title.value.trim()
    ? "Donnez un titre à votre demande."
    : "",
);
const descriptionError = computed(() =>
  attempted.value && !description.value.trim()
    ? "Décrivez votre demande pour permettre son analyse."
    : "",
);

async function submit() {
  if (submitting.value) return;
  attempted.value = true;
  error.value = "";
  if (titleError.value || descriptionError.value) {
    document
      .getElementById(titleError.value ? "ticket-title" : "ticket-description")
      ?.focus();
    return;
  }
  submitting.value = true;
  try {
    validateFiles(files.value);
    const ticket = await createTicket(ticketPayload({
      ...(creationMode.value === "complete" ? draft.value : ticketDraft()),
      title: title.value.trim(),
      description: description.value.trim(),
      autoTitle: autoTitle.value,
    }), files.value);
    await router.push({
      path: `/tickets/${ticket.id}`,
      query: { created: "1" },
    });
  } catch (cause) {
    error.value =
      cause instanceof Error
        ? cause.message
        : "Impossible de créer le ticket. Réessayez.";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <RouterLink to="/tickets" class="back-link"
    ><AppIcon name="arrow-left" :size="16" />Retour aux tickets</RouterLink
  >
  <div class="page-heading">
    <div>
      <p class="eyebrow">NOUVELLE DEMANDE</p>
      <h1>Un besoin ? Parlons-en<span class="heading-dot">.</span></h1>
      <p class="page-description">
        Décrivez votre demande. Nous nous occupons de la classer.
      </p>
    </div>
  </div>
  <div class="content-grid">
    <section class="panel form-panel">
      <div class="panel-heading">
        <div>
          <h2>Créer un ticket</h2>
          <p>Décrivez la demande. Vous pouvez laisser l’IA formuler son titre.</p>
        </div>
        <span class="soft-icon"><AppIcon name="file" /></span>
      </div>
      <form novalidate :aria-busy="submitting" @submit.prevent="submit">
        <fieldset class="creation-mode" :disabled="submitting">
          <legend class="sr-only">Mode de création du ticket</legend>
          <label>
            <input v-model="creationMode" type="radio" name="creation-mode" value="simple" />
            <span>Création simplifiée</span>
          </label>
          <label>
            <input v-model="creationMode" type="radio" name="creation-mode" value="complete" />
            <span>Création complète</span>
          </label>
        </fieldset>
        <div v-if="error" class="alert alert-error" role="alert">
          <AppIcon name="alert" />
          <div>
            <strong>Le ticket n’a pas pu être créé</strong>
            <p>{{ error }} Votre saisie a été conservée.</p>
          </div>
        </div>
        <label class="automatic-title-option">
          <input v-model="autoTitle" type="checkbox" :disabled="submitting" />
          Générer le titre automatiquement
        </label>
        <div class="field">
          <label for="ticket-title"
            >Titre de la demande <span v-if="!autoTitle" aria-hidden="true">*</span></label
          >
          <p id="title-hint" class="field-hint">
            {{ autoTitle ? 'Le titre sera proposé à partir de votre description.' : 'Un titre court qui résume le sujet.' }}
          </p>
          <input
            id="ticket-title"
            v-model="title"
            type="text"
            :required="!autoTitle"
            maxlength="255"
            placeholder="Ex. : Impossible de me connecter à mon compte"
            :disabled="submitting || autoTitle"
            :aria-invalid="Boolean(titleError)"
            :aria-describedby="
              titleError ? 'title-hint title-error' : 'title-hint'
            "
          />
          <p v-if="titleError" id="title-error" class="field-error">
            {{ titleError }}
          </p>
        </div>
        <div class="field">
          <label for="ticket-description"
            >Description <span aria-hidden="true">*</span></label
          >
          <p id="description-hint" class="field-hint">
            Précisez le contexte, le problème rencontré et le résultat attendu.
          </p>
          <textarea
            id="ticket-description"
            v-model="description"
            required
            maxlength="2000"
            rows="8"
            placeholder="Que se passe-t-il ? Depuis quand ? Ajoutez les détails qui nous aideront à comprendre votre demande…"
            :disabled="submitting"
            :aria-invalid="Boolean(descriptionError)"
            :aria-describedby="
              descriptionError
                ? 'description-hint description-error'
                : 'description-hint'
            "
          />
          <div class="field-bottom">
            <p
              v-if="descriptionError"
              id="description-error"
              class="field-error"
            >
              {{ descriptionError }}
            </p>
            <span class="character-count"
              >{{ description.length }} / 2 000</span
            >
          </div>
        </div>
        <template v-if="creationMode === 'complete'">
          <p class="reporter-note">Rapporteur : <strong>{{ user?.email }}</strong></p>
          <TicketFields v-model="draft" :disabled="submitting" />
        </template>
        <AttachmentPicker v-model="files" :disabled="submitting" />
        <div class="form-info">
          <AppIcon name="sparkles" />
          <p>
            La catégorie, la priorité, la sévérité et l’attribution seront déterminées
            à partir de votre description et des personnes éligibles. Vos choix manuels sont conservés.
          </p>
        </div>
        <div class="form-actions">
          <RouterLink
            v-if="!submitting"
            to="/tickets"
            class="button button-quiet"
            >Annuler</RouterLink
          ><button
            type="submit"
            class="button button-primary"
            :disabled="submitting"
          >
            <span v-if="submitting" class="loader loader-small"></span
            ><AppIcon v-else name="plus" />{{
              submitting ? "Création en cours…" : "Créer le ticket"
            }}
          </button>
        </div>
      </form>
    </section>
    <aside class="help-panel">
      <span class="note-icon"><AppIcon name="sparkles" :size="24" /></span>
      <p class="eyebrow">BIEN DÉMARRER</p>
      <h2>Un peu de contexte.<br />Un meilleur suivi.</h2>
      <p>
        Une demande précise nous aide à identifier rapidement ce qui compte.
      </p>
      <ol class="tips-list">
        <li>
          <span>01</span>
          <div>
            <h3>Décrivez le problème</h3>
            <p>Expliquez ce qui vous bloque avec vos propres mots.</p>
          </div>
        </li>
        <li>
          <span>02</span>
          <div>
            <h3>Ajoutez les détails utiles</h3>
            <p>
              Étapes, message d’erreur, impact : chaque précision est utile.
            </p>
          </div>
        </li>
        <li>
          <span>03</span>
          <div>
            <h3>Retrouvez votre suivi</h3>
            <p>Consultez le résumé et la priorité depuis la fiche du ticket.</p>
          </div>
        </li>
      </ol>
      <div class="help-footnote">
        <AppIcon name="info" :size="17" />
        <p>N’ajoutez pas de mot de passe ni d’information confidentielle.</p>
      </div>
    </aside>
  </div>
</template>
