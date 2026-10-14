<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getTicket, updateTicket, reanalyzeTicket, uploadAttachments, deleteAttachment, attachmentUrl, downloadAttachment } from "../api/ticketApi";
import TicketFields from "../components/TicketFields.vue";
import AttachmentPicker from "../components/AttachmentPicker.vue";
import { ticketDraft, ticketPayload, validateFiles, formatFileSize } from "../utils/ticketForm";
import AppIcon from "../components/AppIcon.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useAsyncData } from "../composables/useAsyncData";
import type { Ticket, TicketAttachment } from "../types/ticket";
import { categoryLabel, formatDate, typeLabels, severityLabels } from "../utils/tickets";

const route = useRoute();
const router = useRouter();
const editing = ref(false);
const saving = ref(false);
const saveError = ref("");
const notice = ref("");
const analysisBusy = ref(false);
const analysisError = ref("");
const analysisFailures: Record<string, string> = {
  CONFIGURATION_ERROR: "La connexion au modèle doit être configurée ou corrigée.",
  AUTHENTICATION_ERROR: "Les identifiants du fournisseur doivent être corrigés.",
  PROVIDER_UNAVAILABLE: "Le modèle est temporairement inaccessible.",
  PROVIDER_ERROR: "Le fournisseur n’a pas pu traiter la demande.",
  INVALID_RESPONSE: "La réponse du modèle n’a pas pu être validée.",
  REFUSED: "Le modèle n’a pas pu analyser cette demande.",
};
const draft = ref(ticketDraft());
const files = ref<File[]>([]);
const attachmentBusy = ref(false);
const attachmentError = ref("");
const downloading = ref<string | null>(null);
const technicalSections = [
  { key: "environment", label: "Environnement" },
  { key: "stepsToReproduce", label: "Étapes de reproduction" },
  { key: "expectedResult", label: "Résultat attendu" },
  { key: "actualResult", label: "Résultat observé" },
  { key: "rootCause", label: "Analyse de cause" },
  { key: "technicalSolution", label: "Solution technique" },
] as const;
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
    editing.value = false;
    files.value = [];
    notice.value = saveError.value = attachmentError.value = "";
    analysisError.value = "";
    void load();
  },
  { immediate: true },
);
function dismissSuccess() {
  const query = { ...route.query };
  delete query.created;
  void router.replace({ query });
}
function edit() {
  if (!ticket.value) return;
  draft.value = ticketDraft(ticket.value);
  saveError.value = notice.value = "";
  editing.value = true;
}
async function reanalyze() {
  if (!ticket.value || analysisBusy.value) return;
  const id = ticket.value.id;
  analysisBusy.value = true;
  analysisError.value = "";
  try {
    const updated = await reanalyzeTicket(id);
    if (String(route.params.id) !== id) return;
    ticket.value = updated;
    notice.value = "L’analyse a été relancée. Vos choix manuels sont conservés.";
  } catch (cause) {
    if (String(route.params.id) === id) analysisError.value = cause instanceof Error ? cause.message : "Impossible de relancer l’analyse.";
  } finally { analysisBusy.value = false; }
}
async function save() {
  if (!ticket.value || saving.value) return;
  const id = ticket.value.id;
  saving.value = true;
  saveError.value = "";
  try {
    const updated = await updateTicket(id, ticketPayload(draft.value));
    if (String(route.params.id) !== id) return;
    ticket.value = updated;
    editing.value = false;
    notice.value = "Le ticket a été enregistré.";
  } catch (cause) {
    if (String(route.params.id) === id) saveError.value = cause instanceof Error ? cause.message : "Impossible d’enregistrer le ticket.";
  } finally { saving.value = false; }
}
async function changeAttachments(attachmentId?: string) {
  if (!ticket.value || attachmentBusy.value) return;
  const id = ticket.value.id;
  attachmentBusy.value = true;
  attachmentError.value = "";
  try {
    if (attachmentId) {
      await deleteAttachment(id, attachmentId);
      if (String(route.params.id) !== id || !ticket.value) return;
      ticket.value = { ...ticket.value, attachments: (ticket.value.attachments || []).filter(file => file.id !== attachmentId) };
    } else {
      validateFiles(files.value);
      const attachments = await uploadAttachments(id, files.value);
      if (String(route.params.id) !== id || !ticket.value) return;
      ticket.value = { ...ticket.value, attachments };
      files.value = [];
    }
    notice.value = attachmentId ? "La pièce jointe a été supprimée." : "Les pièces jointes ont été ajoutées.";
    await load();
  } catch (cause) {
    if (String(route.params.id) === id) attachmentError.value = cause instanceof Error ? cause.message : "Impossible de modifier les pièces jointes.";
  } finally { attachmentBusy.value = false; }
}
async function downloadFile(file: TicketAttachment) {
  if (!ticket.value || downloading.value) return;
  const id = ticket.value.id;
  downloading.value = file.id;
  attachmentError.value = "";
  try {
    const blob = await downloadAttachment(id, file.id);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = file.filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) {
    if (String(route.params.id) === id) attachmentError.value = cause instanceof Error ? cause.message : "Impossible de télécharger le fichier.";
  } finally { downloading.value = null; }
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
    <button class="button button-small" :disabled="loading || editing || saving || attachmentBusy" @click="load">
      Réessayer
    </button>
  </div>
  <template v-if="ticket">
    <div v-if="notice" class="alert alert-success" role="status">{{ notice }}</div>
    <div class="page-heading detail-heading">
      <div>
        <p class="eyebrow">TICKET #{{ ticket.id.slice(0, 8).toUpperCase() }}</p>
        <h1>{{ ticket.title }}</h1>
        <p class="page-description">
          Créé le {{ formatDate(ticket.createdAt, true) }}
        </p>
      </div>
      <div class="detail-actions">
      <button v-if="!editing" class="button button-primary" :disabled="loading || attachmentBusy || saving" @click="edit">Modifier le ticket</button>
      <button class="button" :disabled="loading || editing || attachmentBusy || saving" @click="load">
        <AppIcon name="refresh" :class="{ spinning: loading }" />Actualiser
      </button>
      </div>
    </div>
    <section v-if="editing" class="panel form-panel ticket-edit-panel">
      <div class="panel-heading"><h2>Modifier le ticket</h2></div>
      <form :aria-busy="saving" @submit.prevent="save">
        <div v-if="saveError" class="alert alert-error" role="alert">{{ saveError }} Votre saisie a été conservée.</div>
        <div class="field"><label for="edit-title">Titre de la demande *</label><input id="edit-title" v-model="draft.title" required maxlength="255" :disabled="saving" /></div>
        <div class="field"><label for="edit-description">Description *</label><textarea id="edit-description" v-model="draft.description" required maxlength="2000" rows="6" :disabled="saving" /></div>
        <p class="reporter-note">Rapporteur : <strong>{{ ticket.reporter || 'Non renseigné' }}</strong></p>
        <TicketFields v-model="draft" editing :disabled="saving" />
        <div class="form-actions"><button type="button" class="button" :disabled="saving" @click="editing = false">Annuler</button><button type="submit" class="button button-primary" :disabled="saving">{{ saving ? 'Enregistrement…' : 'Enregistrer' }}</button></div>
      </form>
    </section>
    <template v-else>
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
          <div v-if="ticket.analysisStatus === 'FAILED'" class="alert alert-error" role="alert">
            <div><strong>L’analyse a échoué</strong><p>{{ analysisFailures[ticket.analysisFailureCode || ''] || 'L’analyse n’a pas abouti. Vous pouvez la relancer.' }}</p></div>
          </div>
          <p v-else-if="ticket.analysisStatus === 'PENDING'" class="analysis-pending" aria-live="polite">Analyse en cours. Actualisez dans quelques instants.</p>
          <p v-if="analysisError" class="alert alert-error" role="alert">{{ analysisError }}</p>
          <p v-if="ticket.summary" class="ticket-description">
            {{ ticket.summary }}
          </p>
          <div v-else-if="!ticket.analysisStatus">
            <p class="analysis-pending">
              L’analyse n’est pas encore disponible.
            </p>
            <p>
              Le résumé, la catégorie et la priorité apparaîtront ici après le
              traitement du ticket. Actualisez dans quelques instants.
            </p>
          </div>
        </section>
        <div class="analysis-actions">
          <button class="button" :disabled="analysisBusy || loading || ticket.analysisStatus === 'PENDING'" @click="reanalyze">{{ analysisBusy ? 'Relance…' : 'Relancer l’analyse' }}</button>
          <p v-if="ticket.analysisProvider" class="field-hint">Modèle : {{ ticket.analysisModel }} · {{ ticket.analysisProvider }}</p>
        </div>
        <section class="panel description-panel">
          <div class="panel-heading"><h2>Diagnostic et résolution</h2></div>
          <div class="technical-details">
            <div v-for="section in technicalSections" :key="section.key"><h3>{{ section.label }}</h3><p class="ticket-description">{{ ticket[section.key] || 'Non renseigné' }}</p></div>
          </div>
        </section>
        <section class="panel description-panel">
          <div class="panel-heading"><h2>Pièces jointes ({{ ticket.attachments?.length || 0 }})</h2></div>
          <div class="attachments-content">
            <p v-if="attachmentError" class="alert alert-error" role="alert">{{ attachmentError }}</p>
            <ul v-if="ticket.attachments?.length" class="attachment-list">
              <li v-for="file in ticket.attachments" :key="file.id">
                <div><a :href="attachmentUrl(ticket.id, file.id)" :download="file.filename" :aria-disabled="Boolean(downloading)" @click.prevent="downloadFile(file)">{{ file.filename }}</a><small>{{ formatFileSize(file.size) }} · {{ formatDate(file.createdAt, true) }} · {{ file.uploadedBy }}</small><small v-if="downloading === file.id" role="status">Téléchargement…</small></div>
                <button type="button" class="button button-small" :disabled="attachmentBusy" :aria-label="`Supprimer ${file.filename}`" @click="changeAttachments(file.id)">Supprimer</button>
              </li>
            </ul>
            <p v-else class="field-hint">Aucune pièce jointe pour ce ticket.</p>
            <AttachmentPicker v-model="files" :disabled="attachmentBusy" />
            <button class="button button-primary" :disabled="attachmentBusy || !files.length" @click="changeAttachments()">{{ attachmentBusy ? 'Traitement…' : 'Ajouter les fichiers' }}</button>
          </div>
        </section>
      </div>
      <aside class="panel properties-panel">
        <div class="panel-heading"><h2>Informations du ticket</h2></div>
        <dl>
          <div><dt>Rapporteur</dt><dd>{{ ticket.reporter || 'Non renseigné' }}</dd></div>
          <div><dt>Assigné à</dt><dd>{{ ticket.assignee || 'Non assigné' }}</dd></div>
          <div><dt>Type</dt><dd>{{ typeLabels[ticket.type || ''] || 'Non renseigné' }}</dd></div>
          <div><dt>Sévérité</dt><dd>{{ severityLabels[ticket.severity || ''] || 'Non renseignée' }}</dd></div>
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
            <dt>Composant</dt><dd>{{ ticket.component || 'Non renseigné' }}</dd>
          </div>
          <div><dt>Version affectée</dt><dd>{{ ticket.affectedVersion || 'Non renseignée' }}</dd></div>
          <div><dt>Version de correction</dt><dd>{{ ticket.fixVersion || 'Non renseignée' }}</dd></div>
          <div><dt>Échéance</dt><dd>{{ ticket.dueDate ? formatDate(`${ticket.dueDate}T00:00:00`) : 'Non renseignée' }}</dd></div>
          <div class="property-labels"><dt>Labels</dt><dd class="label-list"><span v-for="label in ticket.labels" :key="label" class="badge">{{ label }}</span><span v-if="!ticket.labels?.length">Aucun label</span></dd></div>
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
</template>
