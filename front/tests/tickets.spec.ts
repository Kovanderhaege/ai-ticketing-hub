import { test, expect, type Page } from "@playwright/test";

async function mockTickets(page: Page) {
  const state = {
    ticket: {
      id: "ticket-1", title: "Erreur de connexion", description: "Une erreur 500 empêche la connexion",
      reporter: "alice@example.com", assignee: "bob@example.com", type: "BUG", severity: "CRITICAL",
      component: "Auth", affectedVersion: "1.2", fixVersion: "1.3", dueDate: "2026-11-01",
      environment: "Firefox", stepsToReproduce: "1. Se connecter", expectedResult: "Session ouverte", actualResult: "Erreur 500",
      rootCause: "Jeton expiré", technicalSolution: "Renouveler le jeton", labels: ["production"],
      status: "NEW", category: null, priority: null, summary: null,
      analysisStatus: "PENDING" as "PENDING" | "SUCCEEDED" | "FAILED",
      analysisFailureCode: null as string | null, analysisProvider: null as string | null, analysisModel: null as string | null,
      createdAt: "2026-10-13T08:00:00Z", updatedAt: "2026-10-13T08:00:00Z",
      attachments: [] as { id: string; filename: string; size: number; contentType: string; uploadedBy: string; createdAt: string }[],
    },
    creations: 0, uploads: 0, analyses: 0, failSave: false,
  };
  let nextFile = 1;
  function addFiles(body: string) {
    for (const match of body.matchAll(/name="files"; filename="([^"]+)"/g))
      state.ticket.attachments.push({ id: `file-${nextFile++}`, filename: match[1]!, size: 25, contentType: "text/plain", uploadedBy: "alice@example.com", createdAt: "2026-10-13T08:00:00Z" });
  }
  await page.route(/\/api\/(auth|tickets|notifications)(\/|$)/, async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const json = (body: unknown, status = 200) => route.fulfill({ status, json: body });
    if (path === "/api/auth/csrf") return json({ headerName: "X-CSRF-TOKEN", token: "test-csrf" });
    if (path === "/api/auth/me") return json({ id: "user-1", email: "alice@example.com" });
    if (!["GET", "HEAD"].includes(method)) expect(request.headers()["x-csrf-token"]).toBe("test-csrf");
    if (path === "/api/tickets" && method === "POST") {
      state.creations++;
      const body = request.postData() || "";
      if (request.headers()["content-type"]?.startsWith("multipart/")) {
        const payload = body.match(/Content-Type: application\/json\r\n\r\n([^\r]+)/i)?.[1];
        expect(payload).toBeTruthy();
        Object.assign(state.ticket, JSON.parse(payload!));
        addFiles(body);
      } else {
        const payload = request.postDataJSON();
        Object.assign(state.ticket, payload);
        if (payload.autoTitle && !payload.title) state.ticket.title = "Analyse en cours…";
      }
      return json(state.ticket);
    }
    if (path === "/api/tickets/ticket-1" && method === "PUT") {
      if (state.failSave) return json({ message: "Impossible d’enregistrer pour le moment." }, 400);
      Object.assign(state.ticket, request.postDataJSON());
      return json(state.ticket);
    }
    if (path === "/api/tickets/ticket-1/analysis" && method === "POST") {
      state.analyses++;
      state.ticket.analysisStatus = "PENDING";
      state.ticket.analysisFailureCode = null;
      return json(state.ticket);
    }
    if (path === "/api/tickets/ticket-1/attachments" && method === "POST") {
      state.uploads++; addFiles(request.postData() || ""); return json(state.ticket.attachments, 201);
    }
    if (path.startsWith("/api/tickets/ticket-1/attachments/")) {
      if (method === "DELETE") {
        state.ticket.attachments = state.ticket.attachments.filter(file => file.id !== path.split("/").pop());
        return route.fulfill({ status: 204 });
      }
      return route.fulfill({ body: "log contents", headers: { "Content-Type": "application/octet-stream", "Content-Disposition": "attachment; filename=error.log" } });
    }
    if (path === "/api/tickets/ticket-1") return json(state.ticket);
    if (path === "/api/tickets") return json([state.ticket]);
    return json([]);
  });
  return state;
}

test("create with classic fields and files, then reload the saved ticket", async ({ page }) => {
  const state = await mockTickets(page);
  await page.goto("/tickets/new");
  await page.getByRole("radio", { name: "Création complète", exact: true }).check();
  await page.getByLabel("Titre de la demande").fill("Connexion cassée");
  await page.getByLabel("Description", { exact: false }).fill("Erreur sur la page de connexion");
  await page.getByLabel("Type de ticket").selectOption("BUG");
  await page.getByLabel("Sévérité").selectOption("BLOCKER");
  await page.getByLabel("Assigné à").fill("dev@example.com");
  await page.getByLabel("Labels", { exact: true }).fill("production, auth, production");
  await page.getByText("Reproduction, analyse de cause et solution technique", { exact: true }).click();
  await page.getByLabel("Analyse de cause", { exact: true }).fill("Cache de session invalide");
  await page.getByLabel("Solution technique", { exact: true }).fill("Purger le cache");
  await page.getByLabel("Joindre des fichiers").setInputFiles([{ name: "error.log", mimeType: "text/plain", buffer: Buffer.from("error trace") }, { name: "screen.png", mimeType: "image/png", buffer: Buffer.from([1, 2, 3]) }]);
  await page.getByRole("radio", { name: "Création simplifiée", exact: true }).check();
  await expect(page.getByLabel("Assigné à")).toHaveCount(0);
  await expect(page.getByLabel("Titre de la demande")).toHaveValue("Connexion cassée");
  await expect(page.getByRole("button", { name: "Retirer error.log", exact: true })).toBeVisible();
  await page.getByRole("radio", { name: "Création complète", exact: true }).check();
  await expect(page.getByLabel("Assigné à")).toHaveValue("dev@example.com");
  await expect(page.getByLabel("Labels", { exact: true })).toHaveValue("production, auth, production");
  await page.getByRole("button", { name: "Créer le ticket", exact: true }).click();
  await expect(page).toHaveURL(/\/tickets\/ticket-1\?created=1/);
  expect(state.creations).toBe(1);
  expect(state.ticket.labels).toEqual(["production", "auth"]);
  expect(state.ticket.reporter).toBe("alice@example.com");
  await expect(page.getByRole("link", { name: "error.log", exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "Connexion cassée", exact: true })).toBeVisible();
  await expect(page.getByText("Cache de session invalide", { exact: true })).toBeVisible();
  await expect(page.getByText("Purger le cache", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "screen.png", exact: true })).toBeVisible();
});

test("edit status, diagnosis and labels with a recoverable save error", async ({ page }) => {
  const state = await mockTickets(page);
  await page.goto("/tickets/ticket-1");
  await page.getByRole("button", { name: "Modifier le ticket", exact: true }).click();
  await page.getByLabel("Statut", { exact: true }).selectOption("RESOLVED");
  await page.getByLabel("Priorité", { exact: true }).selectOption("HIGH");
  await page.getByLabel("Solution technique", { exact: true }).fill("Correctif validé");
  await page.getByLabel("Labels", { exact: true }).fill("auth, corrigé");
  state.failSave = true;
  await page.getByRole("button", { name: "Enregistrer", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Impossible d’enregistrer pour le moment.");
  await expect(page.getByLabel("Solution technique", { exact: true })).toHaveValue("Correctif validé");
  state.failSave = false;
  await page.getByRole("button", { name: "Enregistrer", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Le ticket a été enregistré.");
  await page.reload();
  await expect(page.getByText("Correctif validé", { exact: true })).toBeVisible();
  await expect(page.getByText("Résolu", { exact: true })).toBeVisible();
  expect(state.ticket.labels).toEqual(["auth", "corrigé"]);
});

test("add, download and delete attachments", async ({ page }) => {
  const state = await mockTickets(page);
  await page.goto("/tickets/ticket-1");
  await page.getByLabel("Joindre des fichiers").setInputFiles({ name: "error.log", mimeType: "text/plain", buffer: Buffer.from("log contents") });
  await page.getByRole("button", { name: "Ajouter les fichiers", exact: true }).click();
  await expect(page.getByRole("link", { name: "error.log", exact: true })).toBeVisible();
  const download = page.waitForEvent("download");
  await page.getByRole("link", { name: "error.log", exact: true }).click();
  const downloaded = await download;
  expect(downloaded.suggestedFilename()).toBe("error.log");
  expect(await downloaded.failure()).toBeNull();
  await page.getByRole("button", { name: "Supprimer error.log", exact: true }).click();
  await expect(page.getByRole("link", { name: "error.log", exact: true })).toHaveCount(0);
  expect(state.uploads).toBe(1);
  expect(state.ticket.attachments).toHaveLength(0);
});

test("reject oversized files before sending and search by label", async ({ page }) => {
  const state = await mockTickets(page);
  await page.goto("/tickets/ticket-1");
  await page.getByLabel("Joindre des fichiers").setInputFiles({ name: "big.log", mimeType: "text/plain", buffer: Buffer.alloc(10 * 1024 * 1024 + 1) });
  await expect(page.getByRole("alert")).toContainText("10 Mo");
  await expect(page.getByRole("button", { name: "Ajouter les fichiers", exact: true })).toBeDisabled();
  expect(state.uploads).toBe(0);
  await page.goto("/tickets");
  await page.getByRole("searchbox").fill("production");
  await expect(page.getByRole("link", { name: "Erreur de connexion", exact: true })).toBeVisible();
  await page.getByRole("searchbox").fill("inexistant");
  await expect(page.getByText("Aucun ticket ne correspond", { exact: true })).toBeVisible();
});

test("ticket creation and details fit a mobile viewport", async ({ page }) => {
  await mockTickets(page);
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/tickets/new");
  await expect(page.getByRole("radio", { name: "Création simplifiée", exact: true })).toBeChecked();
  await expect(page.getByLabel("Labels", { exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: "test-results/ticket-create-simple-mobile.png", fullPage: true });
  await page.getByRole("radio", { name: "Création complète", exact: true }).check();
  await expect(page.getByLabel("Labels", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: "test-results/ticket-create-mobile.png", fullPage: true });
  await page.goto("/tickets/ticket-1");
  await expect(page.getByRole("heading", { name: "Diagnostic et résolution", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: "test-results/ticket-detail-mobile.png", fullPage: true });
});

test("create from description with an automatically generated subject and severity", async ({ page }) => {
  const state = await mockTickets(page);
  await page.goto("/tickets/new");
  await page.getByLabel("Générer le titre automatiquement").check();
  await expect(page.getByLabel("Titre de la demande")).toBeDisabled();
  await page.getByLabel("Description", { exact: false }).fill("La connexion échoue pour tous les utilisateurs");
  await page.getByRole("button", { name: "Créer le ticket", exact: true }).click();
  await expect(page).toHaveURL(/\/tickets\/ticket-1\?created=1/);
  expect(state.ticket.severity).toBeNull();
  expect(state.ticket.assignee).toBeNull();
  await expect(page.getByRole("heading", { name: "Analyse en cours…", exact: true })).toBeVisible();
  await expect(page.getByText("Analyse en cours. Actualisez dans quelques instants.")).toBeVisible();
});

test("show a provider failure and retry analysis using session and CSRF", async ({ page }) => {
  const state = await mockTickets(page);
  state.ticket.analysisStatus = "FAILED";
  state.ticket.analysisFailureCode = "PROVIDER_UNAVAILABLE";
  await page.goto("/tickets/ticket-1");
  await expect(page.getByRole("alert")).toContainText("Le modèle est temporairement inaccessible.");
  await page.getByRole("button", { name: "Relancer l’analyse", exact: true }).click();
  await expect(page.getByText("Analyse en cours. Actualisez dans quelques instants.")).toBeVisible();
  expect(state.analyses).toBe(1);
  await expect(page.getByRole("button", { name: "Relancer l’analyse", exact: true })).toBeDisabled();
  await expect(page.getByText("Vos choix manuels sont conservés.", { exact: false })).toBeVisible();
});
