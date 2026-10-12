import { test, expect, type Page } from "@playwright/test";

async function mockApi(page: Page, initiallyAuthenticated = false) {
  const state = { authenticated: initiallyAuthenticated, ticketRequests: 0, registrations: 0, duplicate: false };
  await page.route(/\/api\/(auth|tickets|notifications)(\/|$)/, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const json = (body: unknown, status = 200) => route.fulfill({ status, json: body });
    if (path === "/api/auth/csrf") return json({ headerName: "X-CSRF-TOKEN", token: "test-csrf" });
    if (method === "POST") {
      expect(request.headers()["x-csrf-token"]).toBe("test-csrf");
    }
    if (path === "/api/auth/me")
      return json(state.authenticated ? { id: "user-1", email: "alice@example.com" } : {}, state.authenticated ? 200 : 401);
    if (path === "/api/auth/login") {
      const params = new URLSearchParams(request.postData() || "");
      if (params.get("password") !== "long-password-123") return json({}, 401);
      state.authenticated = true;
      return route.fulfill({ status: 204 });
    }
    if (path === "/api/auth/logout") {
      state.authenticated = false;
      return route.fulfill({ status: 204 });
    }
    if (path === "/api/auth/register") {
      state.registrations++;
      return json({ id: "user-1", email: "alice@example.com" }, state.duplicate ? 409 : 201);
    }
    state.ticketRequests++;
    if (!state.authenticated) return json({}, 401);
    if (path === "/api/tickets" && method === "POST") {
      return json({ id: "ticket-1", ...request.postDataJSON(), status: "NEW", createdAt: new Date().toISOString() });
    }
    if (path === "/api/tickets/ticket-1") {
      return json({ id: "ticket-1", title: "Mon ticket privé", description: "Un problème à résoudre", status: "NEW", createdAt: new Date().toISOString() });
    }
    return json([]);
  });
  return state;
}

async function signIn(page: Page, password = "long-password-123") {
  await page.getByLabel("Adresse e-mail").fill("alice@example.com");
  await page.getByLabel("Mot de passe", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Se connecter", exact: true }).click();
}

for (const path of ["/tickets", "/tickets/new", "/tickets/ticket-1", "/notifications"]) {
  test(`anonymous navigation to ${path} never loads private data`, async ({ page }) => {
    const state = await mockApi(page);
    await page.goto(path);
    await expect(page).toHaveURL(/\/login\?redirect=/);
    await expect(page.getByRole("heading", { name: "Bienvenue dans votre espace." })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Navigation principale" })).toHaveCount(0);
    expect(state.ticketRequests).toBe(0);
  });
}

test("login resumes creation, survives reload and logout blocks browser back", async ({ page }) => {
  const state = await mockApi(page);
  await page.goto("/tickets/new");
  await signIn(page);
  await expect(page).toHaveURL(/\/tickets\/new$/);
  await expect(page.getByText("alice@example.com", { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "Créer un ticket", exact: true })).toBeVisible();
  await page.getByLabel("Titre de la demande").fill("Mon ticket privé");
  await page.getByLabel("Description", { exact: false }).fill("Un problème à résoudre");
  await page.getByRole("button", { name: "Créer le ticket", exact: true }).click();
  await expect(page).toHaveURL(/\/tickets\/ticket-1/);
  await page.getByRole("button", { name: "Se déconnecter", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(state.authenticated).toBe(false);
  await page.goBack();
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByText("Mon ticket privé", { exact: true })).toHaveCount(0);
});

test("invalid credentials remain on login with a clear error", async ({ page }) => {
  await mockApi(page);
  await page.goto("/login");
  await signIn(page, "wrong-password");
  await expect(page.getByRole("alert")).toHaveText("Adresse e-mail ou mot de passe incorrect.");
  await expect(page).toHaveURL(/\/login$/);
});

test("registration validates confirmation and returns to login", async ({ page }) => {
  const state = await mockApi(page);
  await page.goto("/register");
  await page.getByLabel("Adresse e-mail").fill("alice@example.com");
  await page.getByLabel("Mot de passe", { exact: true }).fill("long-password-123");
  await page.getByLabel("Confirmer le mot de passe").fill("different-password");
  await page.getByRole("button", { name: "Créer mon compte" }).click();
  await expect(page.getByRole("alert")).toContainText("ne correspondent pas");
  expect(state.registrations).toBe(0);
  await page.getByLabel("Confirmer le mot de passe").fill("long-password-123");
  await page.getByRole("button", { name: "Créer mon compte" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("status")).toContainText("Votre compte a été créé");
  await signIn(page);
  await expect(page).toHaveURL(/\/tickets$/);
});

test("duplicate accounts show a useful error", async ({ page }) => {
  const state = await mockApi(page);
  state.duplicate = true;
  await page.goto("/register");
  await page.getByLabel("Adresse e-mail").fill("alice@example.com");
  await page.getByLabel("Mot de passe", { exact: true }).fill("long-password-123");
  await page.getByLabel("Confirmer le mot de passe").fill("long-password-123");
  await page.getByRole("button", { name: "Créer mon compte" }).click();
  await expect(page.getByRole("alert")).toContainText("déjà utilisée");
});

test("expired session hides private content and requests login", async ({ page }) => {
  const state = await mockApi(page, true);
  await page.goto("/tickets");
  await expect(page.getByRole("navigation", { name: "Navigation principale" })).toBeVisible();
  state.authenticated = false;
  await page.getByRole("button", { name: /actualiser/i }).click();
  await expect(page).toHaveURL(/\/login\?.*reason=expired/);
  await expect(page.getByRole("navigation", { name: "Navigation principale" })).toHaveCount(0);
});

test("login never follows an external redirect", async ({ page }) => {
  await mockApi(page);
  await page.goto("/login?redirect=https://example.com");
  await signIn(page);
  await expect(page).toHaveURL("http://localhost:4174/tickets");
});

test("mobile login fits the viewport", async ({ page }) => {
  await mockApi(page);
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/login");
  await expect(page.getByRole("button", { name: "Se connecter", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
