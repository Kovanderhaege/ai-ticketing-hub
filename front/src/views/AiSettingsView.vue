<script setup lang="ts">
import { computed, nextTick, onMounted, onScopeDispose, reactive, ref, watch } from "vue";
import AppIcon from "../components/AppIcon.vue";
import { getAiSettings, saveAiSettings, listAiModels, testAiSettings } from "../api/aiApi";
import type { AiDraft, AiSettings, AiDiagnostic } from "../types/ai";
import { formatDate, categoryLabels, priorityLabels, severityLabels } from "../utils/tickets";

const saved = ref<AiSettings | null>(null);
const draft = reactive<AiDraft>({
  enabled: true, provider: "OLLAMA", model: "", baseUrl: "", apiKey: "", clearApiKey: false,
  outputFormat: "SCHEMA", timeoutMillis: 30000, connectTimeoutMillis: 5000, maxTokens: 4096, maxAttempts: 2, version: 0,
});
const loading = ref(true);
const busy = ref("");
const error = ref("");
const notice = ref("");
const models = ref<string[]>([]);
const diagnostic = ref<AiDiagnostic | null>(null);
const signature = ref("");
const loadController = new AbortController();
onScopeDispose(() => loadController.abort());
const dirty = computed(() => JSON.stringify(draft) !== signature.value);
const cloud = computed(() => ["OPENAI", "ANTHROPIC"].includes(draft.provider));
const defaults: Record<string, string> = {
  OPENAI: "https://api.openai.com/v1", ANTHROPIC: "https://api.anthropic.com/v1", OLLAMA: "http://localhost:11434",
};
const endpoint = (provider: string, url: string) => (url.trim() || defaults[provider] || "").replace(/\/+$/, "");
const sameEndpoint = computed(() => saved.value?.provider === draft.provider
  && endpoint(saved.value.provider, saved.value.baseUrl) === endpoint(draft.provider, draft.baseUrl));
const reusableKey = computed(() => sameEndpoint.value && saved.value?.apiKeyConfigured && !draft.clearApiKey);
const timeoutSeconds = computed({ get: () => draft.timeoutMillis / 1000, set: (value: number) => { draft.timeoutMillis = Math.round(value * 1000); } });
const connectSeconds = computed({ get: () => draft.connectTimeoutMillis / 1000, set: (value: number) => { draft.connectTimeoutMillis = Math.round(value * 1000); } });
watch(() => [draft.provider, draft.baseUrl], () => {
  draft.apiKey = ""; draft.clearApiKey = false; models.value = [];
  if (draft.provider === "ANTHROPIC" && draft.outputFormat === "JSON") draft.outputFormat = "SCHEMA";
});
watch(() => JSON.stringify(draft), () => { diagnostic.value = null; notice.value = ""; });

function assign(settings: AiSettings) {
  saved.value = settings;
  Object.assign(draft, {
    enabled: settings.enabled, provider: settings.provider, model: settings.model, baseUrl: settings.baseUrl,
    apiKey: "", clearApiKey: false, outputFormat: settings.outputFormat, timeoutMillis: settings.timeoutMillis,
    connectTimeoutMillis: settings.connectTimeoutMillis, maxTokens: settings.maxTokens,
    maxAttempts: settings.maxAttempts, version: settings.version,
  });
  signature.value = JSON.stringify(draft);
}
async function load() {
  if (busy.value) return;
  loading.value = true; error.value = "";
  try { assign(await getAiSettings(loadController.signal)); }
  catch (cause) { if (!loadController.signal.aborted) error.value = cause instanceof Error ? cause.message : "Impossible de charger la configuration."; }
  finally { loading.value = false; }
}
onMounted(load);
function validate(requireModel: boolean) {
  if (requireModel && !draft.model.trim()) throw new Error("Indiquez le modèle à utiliser.");
  if (draft.provider === "OPENAI_COMPATIBLE" && !draft.baseUrl.trim()) throw new Error("Indiquez l’adresse de votre serveur LLM.");
  if (draft.baseUrl.trim()) {
    let url: URL;
    try { url = new URL(draft.baseUrl.trim()); } catch { throw new Error("Indiquez une adresse de serveur HTTP ou HTTPS valide."); }
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.search || url.hash)
      throw new Error("Indiquez une adresse de serveur HTTP ou HTTPS sans identifiants ni paramètres.");
  }
  if (requireModel && cloud.value && !draft.apiKey.trim() && !reusableKey.value)
    throw new Error("Renseignez la clé API de ce fournisseur.");
  for (const [value, min, max] of [
    [draft.timeoutMillis, 100, 120000], [draft.connectTimeoutMillis, 100, 30000],
    [draft.maxTokens, 1, 32768], [draft.maxAttempts, 1, 3],
  ]) if (!Number.isFinite(value) || !Number.isInteger(value) || value! < min! || value! > max!)
    throw new Error("Vérifiez les limites des paramètres avancés.");
}
async function act(action: "save" | "models" | "connection" | "analysis") {
  if (busy.value || !saved.value?.serviceLinkConfigured) return;
  error.value = ""; notice.value = ""; diagnostic.value = null;
  try {
    validate(action === "save" ? draft.enabled : action !== "models");
    busy.value = action;
    const payload = { ...draft, model: draft.model.trim(), baseUrl: draft.baseUrl.trim() };
    if (action === "save") {
      assign(await saveAiSettings(payload));
      await nextTick();
      notice.value = "Configuration enregistrée. Les prochaines analyses utiliseront ces paramètres.";
    } else if (action === "models") {
      models.value = await listAiModels(payload);
      notice.value = models.value.length ? "Modèles disponibles chargés." : "Aucun modèle installé n’a été trouvé sur ce serveur.";
    } else diagnostic.value = await testAiSettings(payload, action === "analysis");
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "L’opération n’a pas abouti."; }
  finally { busy.value = ""; }
}
const analysisLabels: Record<string,string> = {
  subject: "Sujet", summary: "Résumé", category: "Catégorie", priority: "Priorité", severity: "Sévérité", assignee: "Attribué à",
};
function valueLabel(field: string, value: string | null) {
  if (!value) return "Non attribué";
  return (field === "category" ? categoryLabels[value] : field === "priority" ? priorityLabels[value]
    : field === "severity" ? severityLabels[value] : value) || value;
}
</script>

<template>
  <div class="page-heading">
    <div><p class="eyebrow">ADMINISTRATION</p><h1>Configuration de l’IA<span class="heading-dot">.</span></h1>
      <p class="page-description">Choisissez le modèle qui analyse les tickets de votre entreprise.</p></div>
  </div>
  <div v-if="loading && !saved" class="panel loading-state" role="status"><span class="loader"></span>Chargement de la configuration…</div>
  <div v-if="error" class="alert alert-error" role="alert">
    <AppIcon name="alert" /><div><strong>L’opération n’a pas abouti</strong><p>{{ error }}<template v-if="saved"> Votre saisie a été conservée.</template></p></div>
    <button type="button" class="button button-small" :disabled="Boolean(busy) || loading" @click="load">{{ saved ? 'Recharger la configuration' : 'Réessayer' }}</button>
  </div>
  <p v-if="notice" class="alert alert-success" role="status">{{ notice }}</p>
  <div v-if="saved" class="content-grid ai-settings-layout">
    <section class="panel form-panel">
      <div class="panel-heading"><div><h2>Modèle d’analyse</h2><p>Ces paramètres s’appliquent à toute cette installation.</p></div><span class="soft-icon"><AppIcon name="sparkles" /></span></div>
      <form novalidate :aria-busy="Boolean(busy)" @submit.prevent="act('save')">
        <div v-if="!saved.serviceLinkConfigured" class="alert alert-error" role="alert">La liaison entre les services doit être configurée par l’administrateur du serveur avant de pouvoir enregistrer ou tester les paramètres.</div>
        <fieldset class="ai-settings-fields" :disabled="Boolean(busy) || loading || !saved.serviceLinkConfigured">
          <legend class="sr-only">Configuration du modèle</legend>
          <label class="ai-enabled"><input v-model="draft.enabled" type="checkbox" />Activer l’analyse automatique des tickets</label>
          <p class="field-hint">{{ draft.enabled ? 'Les champs non renseignés seront proposés par le modèle.' : 'Les tickets restent utilisables et peuvent être classés manuellement.' }}</p>
          <div class="field"><label for="ai-provider">Fournisseur</label><select id="ai-provider" v-model="draft.provider">
            <option value="OLLAMA">Ollama — modèle local</option><option value="OPENAI_COMPATIBLE">Serveur personnel — API compatible OpenAI</option>
            <option value="OPENAI">OpenAI</option><option value="ANTHROPIC">Claude (Anthropic)</option>
          </select></div>
          <div class="ai-location-note"><AppIcon name="info" :size="18" /><p>{{ cloud
            ? 'Le contenu nécessaire à l’analyse sera envoyé au fournisseur externe sélectionné.'
            : 'Pour conserver les tickets sur votre réseau, choisissez un modèle réellement exécuté sur un serveur interne.' }}</p></div>
          <div class="field"><label for="ai-base-url">Adresse du serveur</label>
            <p id="ai-server-hint" class="field-hint">Adresse accessible depuis le serveur de l’application. « localhost » désigne ce serveur.</p>
            <input id="ai-base-url" v-model="draft.baseUrl" type="url" maxlength="2048" :placeholder="defaults[draft.provider] || 'http://serveur-ia:8000/v1'" aria-describedby="ai-server-hint" />
            <p v-if="draft.provider !== 'OPENAI_COMPATIBLE'" class="field-hint">Laisser vide pour utiliser l’adresse par défaut.</p>
          </div>
          <div class="field"><label for="ai-model">Modèle</label><div class="ai-model-row">
            <input id="ai-model" v-model="draft.model" list="ai-models" maxlength="255" placeholder="Identifiant exact du modèle" autocomplete="off" />
            <button v-if="draft.provider === 'OLLAMA'" type="button" class="button" @click="act('models')">{{ busy === 'models' ? 'Chargement…' : 'Charger les modèles' }}</button>
          </div><datalist id="ai-models"><option v-for="model in models" :key="model" :value="model" /></datalist>
          <p class="field-hint">Le modèle doit être installé ou accessible avec les droits du compte fournisseur.</p></div>
          <div class="field"><label for="ai-key">Clé API {{ cloud ? '' : '(facultative)' }}</label>
            <p v-if="saved.apiKeyConfigured && !sameEndpoint" class="field-hint">La clé enregistrée ne sera pas réutilisée pour ce nouveau fournisseur ou cette nouvelle adresse.</p>
            <p v-else-if="reusableKey" class="field-hint">Une clé est enregistrée. Laissez ce champ vide pour la conserver.</p>
            <input id="ai-key" v-model="draft.apiKey" type="password" autocomplete="new-password" maxlength="4096" placeholder="Saisir une nouvelle clé" :disabled="draft.clearApiKey" />
            <label v-if="saved.apiKeyConfigured && sameEndpoint" class="ai-key-clear"><input v-model="draft.clearApiKey" type="checkbox" />Supprimer la clé enregistrée</label>
          </div>
          <details class="technical-fields ai-advanced"><summary>Paramètres avancés</summary><div class="field-grid">
            <div class="field"><label for="ai-format">Format de réponse</label><select id="ai-format" v-model="draft.outputFormat"><option value="SCHEMA">JSON avec schéma (recommandé)</option><option v-if="draft.provider !== 'ANTHROPIC'" value="JSON">JSON simple</option><option value="PROMPT">JSON demandé dans les instructions</option></select></div>
            <div class="field"><label for="ai-timeout">Délai de réponse (secondes)</label><input id="ai-timeout" v-model.number="timeoutSeconds" type="number" min="0.1" max="120" step="0.1" /></div>
            <div class="field"><label for="ai-connect">Délai de connexion (secondes)</label><input id="ai-connect" v-model.number="connectSeconds" type="number" min="0.1" max="30" step="0.1" /></div>
            <div class="field"><label for="ai-tokens">Limite de sortie (tokens)</label><input id="ai-tokens" v-model.number="draft.maxTokens" type="number" min="1" max="32768" /></div>
            <div class="field"><label for="ai-attempts">Nombre maximal de tentatives</label><input id="ai-attempts" v-model.number="draft.maxAttempts" type="number" min="1" max="3" /></div>
          </div></details>
          <div class="ai-test-actions"><button type="button" class="button" @click="act('connection')">{{ busy === 'connection' ? 'Test en cours…' : 'Tester la connexion' }}</button>
            <button type="button" class="button" @click="act('analysis')">{{ busy === 'analysis' ? 'Analyse en cours…' : 'Analyser un ticket fictif' }}</button></div>
          <p class="field-hint">Les tests utilisent la saisie actuelle, sans l’enregistrer. Ils envoient un exemple fictif au modèle et peuvent être facturés par le fournisseur.</p>
          <div class="form-actions"><span v-if="dirty" class="field-hint">Modifications non enregistrées</span><button type="submit" class="button button-primary" :disabled="!dirty">{{ busy === 'save' ? 'Enregistrement…' : 'Enregistrer la configuration' }}</button></div>
        </fieldset>
      </form>
    </section>
    <aside class="help-panel ai-settings-help">
      <span class="note-icon"><AppIcon name="sparkles" :size="24" /></span><p class="eyebrow">INSTALLATION ENTREPRISE</p><h2>Votre modèle.<br />Votre organisation.</h2>
      <p>La connexion au modèle est établie par le serveur de l’application.</p>
      <p>Une modification s’applique aux prochaines analyses. Les analyses déjà commencées conservent leur configuration.</p>
      <p>Les règles métier et les personnes éligibles restent définies par le contexte de votre installation.</p>
      <div v-if="saved.updatedAt" class="help-footnote"><AppIcon name="clock" :size="17" /><p>Dernière modification le {{ formatDate(saved.updatedAt, true) }} par {{ saved.updatedBy }}.</p></div>
    </aside>
    <section v-if="diagnostic" class="panel ai-diagnostic" :class="diagnostic.success ? 'ai-diagnostic-success' : 'ai-diagnostic-error'">
      <div :role="diagnostic.success ? 'status' : 'alert'"><h2>{{ diagnostic.success ? 'Test réussi' : 'Test non concluant' }}</h2><p>{{ diagnostic.message }}</p></div>
      <dl v-if="diagnostic.analysis"><div v-for="(value, field) in diagnostic.analysis" :key="field"><dt>{{ analysisLabels[field] || field }}</dt><dd>{{ valueLabel(field, value) }}</dd></div></dl>
    </section>
  </div>
</template>
