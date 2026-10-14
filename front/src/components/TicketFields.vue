<script setup lang="ts">
import type { TicketDraft } from "../utils/ticketForm";
import { typeLabels, severityLabels, statusLabels, categoryLabels, priorityLabels } from "../utils/tickets";

const draft = defineModel<TicketDraft>({ required: true });
defineProps<{ disabled?: boolean; editing?: boolean }>();
const shortFields = [
  { key: "assignee", label: "Assigné à", placeholder: "Nom ou adresse e-mail" },
  { key: "component", label: "Composant", placeholder: "Ex. : Authentification" },
  { key: "affectedVersion", label: "Version affectée", placeholder: "Ex. : 1.2.0" },
  { key: "fixVersion", label: "Version de correction", placeholder: "Ex. : 1.2.1" },
] as const;
const longFields = [
  { key: "environment", label: "Environnement", max: 2000, placeholder: "OS, navigateur, serveur, configuration…" },
  { key: "stepsToReproduce", label: "Étapes de reproduction", max: 5000, placeholder: "1. Ouvrir…\n2. Cliquer…" },
  { key: "expectedResult", label: "Résultat attendu", max: 2000, placeholder: "Comportement attendu" },
  { key: "actualResult", label: "Résultat observé", max: 2000, placeholder: "Comportement et erreur constatés" },
  { key: "rootCause", label: "Analyse de cause", max: 10000, placeholder: "Cause identifiée et éléments de diagnostic" },
  { key: "technicalSolution", label: "Solution technique", max: 10000, placeholder: "Correction, contournement et validation" },
] as const;
</script>

<template>
  <div class="ticket-fields">
    <div class="field-grid">
      <div class="field">
        <label for="ticket-type">Type de ticket</label>
        <select id="ticket-type" v-model="draft.type" :disabled="disabled"><option v-for="(label, value) in typeLabels" :key="value" :value="value">{{ label }}</option></select>
      </div>
      <div class="field">
        <label for="ticket-severity">Sévérité</label>
        <select id="ticket-severity" v-model="draft.severity" :disabled="disabled"><option value="">Déterminée automatiquement</option><option v-for="(label, value) in severityLabels" :key="value" :value="value">{{ label }}</option></select>
      </div>
      <template v-if="editing">
        <div class="field"><label for="ticket-status">Statut</label><select id="ticket-status" v-model="draft.status" :disabled="disabled"><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></div>
        <div class="field"><label for="ticket-priority">Priorité</label><select id="ticket-priority" v-model="draft.priority" :disabled="disabled"><option :value="null" disabled>À déterminer</option><option v-for="(label, value) in priorityLabels" :key="value" :value="value">{{ label }}</option></select></div>
        <div class="field"><label for="ticket-category">Catégorie</label><select id="ticket-category" v-model="draft.category" :disabled="disabled"><option :value="null" disabled>À déterminer</option><option v-for="(label, value) in categoryLabels" :key="value" :value="value">{{ label }}</option></select></div>
      </template>
      <div v-for="field in shortFields" :key="field.key" class="field">
        <label :for="`ticket-${field.key}`">{{ field.label }}</label>
        <input :id="`ticket-${field.key}`" v-model="draft[field.key]" :placeholder="field.placeholder" maxlength="255" :disabled="disabled" />
      </div>
      <div class="field"><label for="ticket-due-date">Échéance</label><input id="ticket-due-date" v-model="draft.dueDate" type="date" :disabled="disabled" /></div>
    </div>
    <div class="field"><label for="ticket-labels">Labels</label><p class="field-hint">Séparés par des virgules, 20 labels maximum (50 caractères chacun).</p><input id="ticket-labels" v-model="draft.labelsText" placeholder="Ex. : régression, production, connexion" :disabled="disabled" /></div>
    <details class="technical-fields" :open="editing">
      <summary>Reproduction, analyse de cause et solution technique</summary>
      <div v-for="field in longFields" :key="field.key" class="field">
        <label :for="`ticket-${field.key}`">{{ field.label }}</label>
        <textarea :id="`ticket-${field.key}`" v-model="draft[field.key]" rows="4" :maxlength="field.max" :placeholder="field.placeholder" :disabled="disabled" />
      </div>
    </details>
  </div>
</template>
