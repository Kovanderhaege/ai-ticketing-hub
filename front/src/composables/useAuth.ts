import { readonly, shallowRef } from "vue";
import { ApiError, onUnauthorized, requestJson } from "../api/client";
import { AUTH_URL } from "../api/config";

interface User {
  id: string;
  email: string;
}

const user = shallowRef<User | null>(null);
const sessionError = shallowRef("");
let generation = 0;
let pending: Promise<void> | undefined;

onUnauthorized(() => {
  generation++;
  user.value = null;
});

async function restoreSession() {
  if (pending) return pending;
  const startedAt = generation;
  pending = (async () => {
    try {
      const current = await requestJson<User>(`${AUTH_URL}/me`, {}, false);
      if (generation === startedAt) {
        user.value = current;
        sessionError.value = "";
      }
    } catch (cause) {
      if (generation === startedAt) {
        user.value = null;
        sessionError.value = cause instanceof ApiError && cause.status === 401
          ? "" : cause instanceof Error ? cause.message : "Impossible de vérifier votre session.";
      }
    }
  })().finally(() => { pending = undefined; });
  return pending;
}

async function login(email: string, password: string) {
  sessionError.value = "";
  try {
    await requestJson<void>(`${AUTH_URL}/login`, {
      method: "POST",
      body: new URLSearchParams({ email: email.trim(), password }),
    }, false);
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 401)
      throw new Error("Adresse e-mail ou mot de passe incorrect.");
    throw cause;
  }
  generation++;
  user.value = await requestJson<User>(`${AUTH_URL}/me`, {}, false);
}

async function register(email: string, password: string) {
  await requestJson<User>(`${AUTH_URL}/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: email.trim(), password }),
  }, false);
}

async function logout() {
  await requestJson<void>(`${AUTH_URL}/logout`, { method: "POST" });
  generation++;
  user.value = null;
  sessionError.value = "";
}

export function useAuth() {
  return { user: readonly(user), sessionError: readonly(sessionError), restoreSession, login, register, logout };
}
