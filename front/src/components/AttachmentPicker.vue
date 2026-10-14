<script setup lang="ts">
import { ref } from "vue";
import { formatFileSize, validateFiles } from "../utils/ticketForm";
const files = defineModel<File[]>({ required: true });
defineProps<{ disabled?: boolean }>();
const error = ref("");
function select(event: Event) {
  const input = event.target as HTMLInputElement;
  error.value = "";
  try {
    const selected = [...files.value, ...Array.from(input.files ?? [])];
    validateFiles(selected);
    files.value = selected;
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "Fichiers invalides."; }
  input.value = "";
}
</script>
<template>
  <div class="field attachment-picker">
    <label for="ticket-files">Joindre des fichiers</label>
    <p id="files-hint" class="field-hint">Captures d’écran, logs, documents… 10 Mo par fichier, 20 fichiers et 50 Mo par envoi.</p>
    <input id="ticket-files" type="file" multiple :disabled="disabled" aria-describedby="files-hint" @change="select" />
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    <ul v-if="files.length" class="attachment-list">
      <li v-for="(file, index) in files" :key="index"><span>{{ file.name }} <small>{{ formatFileSize(file.size) }}</small></span><button type="button" class="button button-small" :disabled="disabled" :aria-label="`Retirer ${file.name}`" @click="files.splice(index, 1)">Retirer</button></li>
    </ul>
  </div>
</template>
