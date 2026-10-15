import { test, expect, type Page } from "@playwright/test";
import type { AiSettings, AiDraft } from "../src/types/ai";

async function mockApi(page: Page, role = "ADMIN", overrides: Partial<AiSettings> = {}) {
  const state = {
    settings: {
      enabled: true, provider: "OLLAMA", model: "", baseUrl: "", apiKeyConfigured: false,
      outputFormat: "SCHEMA", timeoutMillis: 30000, connectTimeoutMillis: 5000,
      maxTokens: 4096, maxAttempts: 2, version: 0, updatedAt: null, updatedBy: null,
      serviceLinkConfigured: true, ...overrides,
    } as AiSettings,
    reads: 0, saves: [] as AiDraft[], tests: [] as { path: string; body: AiDraft }[],
    conflict: false,
  };
  await page.route(/\/api\/(auth|admin|tickets|notifications)(\/|$)/, async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    if (path === "/api/auth/me")
      return route.fulfill({ json: { id: "admin-id", email: "admin@example.com", role } });
    if (path === "/api/auth/csrf")
      return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "csrf-token" } });
    if (path === "/api/admin/ai/configuration") {
      if (method === "GET") { state.reads++; return route.fulfill({ json: state.settings }); }
      expect(request.headers()["x-csrf-token"]).toBe("csrf-token");
      const payload = request.postDataJSON() as AiDraft;
      state.saves.push(payload);
      if (state.conflict) return route.fulfill({ status: 409, json: { message: "La configuration a été modifiée. Rechargez-la avant d’enregistrer." } });
      const { apiKey, clearApiKey, ...fields } = payload;
      state.settings = { ...state.settings, ...fields,
        apiKeyConfigured: !clearApiKey && (Boolean(apiKey) || (state.settings.provider === fields.provider && state.settings.apiKeyConfigured)),
        version: state.settings.version + 1, updatedAt: "2026-10-15T10:00:00Z", updatedBy: "admin@example.com",
      };
      return route.fulfill({ json: state.settings });
    }
    if (path.startsWith("/api/admin/ai/")) {
      expect(request.headers()["x-csrf-token"]).toBe("csrf-token");
      state.tests.push({ path, body: request.postDataJSON() });
      if (path.endsWith("/models")) return route.fulfill({ json: ["internal-model:latest", "tickets-model"] });
      return route.fulfill({ json: { success: true, message: "Réponse du modèle validée.",
        analysis: path.endsWith("/test-analysis") ? {
          subject: "Connexion indisponible", summary: "Tous les utilisateurs sont bloqués.",
          priority: "HIGH", severity: "BLOCKER", category: "TECHNICAL", assignee: null,
        } : null } });
    }
    return route.fulfill({ json: [] });
  });
  return state;
}

test("a regular user cannot open the settings page or request its data", async ({ page }) => {
  const state = await mockApi(page, "USER");
  await page.goto("/admin/ai");
  await expect(page).toHaveURL(/\/tickets$/);
  await expect(page.getByRole("link", { name: "Administration", exact: true })).toHaveCount(0);
  expect(state.reads).toBe(0);
});

test("an administrator can save a cloud provider and its key stays out of browser storage", async ({ page }) => {
  const state = await mockApi(page);
  await page.goto("/admin/ai");
  await expect(page.getByRole("heading", { name: /Configuration de l’IA/ })).toBeVisible();
  await page.getByLabel("Fournisseur", { exact: true }).selectOption("OPENAI");
  await page.getByLabel("Modèle", { exact: true }).fill("company-approved-model");
  const key = page.locator("#ai-key");
  await expect(key).toHaveAttribute("type", "password");
  await key.fill("fake-api-key-for-test");
  await page.getByRole("button", { name: "Enregistrer la configuration", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Configuration enregistrée");
  expect(state.saves).toHaveLength(1);
  expect(state.saves[0]).toMatchObject({ provider: "OPENAI", model: "company-approved-model", apiKey: "fake-api-key-for-test", version: 0 });
  await expect(key).toHaveValue("");
  await expect(page.getByText("Une clé est enregistrée.", { exact: false })).toBeVisible();
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain("fake-api-key");
  await page.reload();
  await expect(page.getByLabel("Modèle", { exact: true })).toHaveValue("company-approved-model");
  await expect(key).toHaveValue("");
});

test("Ollama discovery and a synthetic analysis use the draft without saving", async ({ page }) => {
  const state = await mockApi(page);
  await page.goto("/admin/ai");
  await page.getByRole("button", { name: "Charger les modèles", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Modèles disponibles");
  expect(state.tests[0]?.body.model).toBe("");
  await expect(page.locator("#ai-models option")).toHaveCount(2);
  await page.getByLabel("Modèle", { exact: true }).fill("tickets-model");
  await page.getByRole("button", { name: "Analyser un ticket fictif", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Test réussi", exact: true })).toBeVisible();
  await expect(page.locator(".ai-diagnostic")).toContainText("Connexion indisponible");
  await expect(page.locator(".ai-diagnostic")).toContainText("Non attribué");
  expect(state.saves).toHaveLength(0);
  expect(state.tests[1]?.body.model).toBe("tickets-model");
});

test("changing the server requires an explicit key instead of reusing the old one", async ({ page }) => {
  const state = await mockApi(page, "ADMIN", { provider: "OPENAI", model: "approved-model", apiKeyConfigured: true });
  await page.goto("/admin/ai");
  await page.getByLabel("Adresse du serveur", { exact: true }).fill("https://new-provider.example/v1");
  await page.getByRole("button", { name: "Tester la connexion", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Renseignez la clé API");
  expect(state.tests).toHaveLength(0);
  expect(state.saves).toHaveLength(0);
});

test("a concurrent settings change preserves the administrator draft and credential", async ({ page }) => {
  const state = await mockApi(page, "ADMIN", { provider: "OPENAI", model: "old-model", apiKeyConfigured: true, version: 4 });
  state.conflict = true;
  await page.goto("/admin/ai");
  await page.getByLabel("Modèle", { exact: true }).fill("new-model");
  await page.locator("#ai-key").fill("fake-replacement-secret");
  await page.getByRole("button", { name: "Enregistrer la configuration", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("La configuration a été modifiée");
  await expect(page.getByLabel("Modèle", { exact: true })).toHaveValue("new-model");
  await expect(page.locator("#ai-key")).toHaveValue("fake-replacement-secret");
  expect(state.settings.model).toBe("old-model");
});

test("automatic analysis can be disabled before a model is configured", async ({ page }) => {
  const state = await mockApi(page);
  await page.goto("/admin/ai");
  await page.getByLabel("Activer l’analyse automatique des tickets", { exact: true }).uncheck();
  await page.getByRole("button", { name: "Enregistrer la configuration", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Configuration enregistrée");
  expect(state.saves[0]).toMatchObject({ enabled: false, model: "" });
});

test("an unconfigured internal service link explains why settings cannot be saved", async ({ page }) => {
  await mockApi(page, "ADMIN", { serviceLinkConfigured: false });
  await page.goto("/admin/ai");
  await expect(page.getByRole("alert")).toContainText("La liaison entre les services");
  await expect(page.getByRole("button", { name: "Tester la connexion", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Enregistrer la configuration", exact: true })).toBeDisabled();
});

test("the settings page remains usable on a narrow screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockApi(page);
  await page.goto("/admin/ai");
  await expect(page.getByLabel("Modèle", { exact: true })).toBeVisible();
  await page.getByText("Paramètres avancés", { exact: true }).click();
  await expect(page.getByLabel("Délai de réponse (secondes)", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: "../../mobile-ai-settings.png", fullPage: true });
});
