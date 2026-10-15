import { AUTH_URL } from "./config";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const unauthorizedListeners = new Set<() => void>();

export function onUnauthorized(listener: () => void) {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

export function requestJson<T>(url: string, options: RequestInit = {}, notifyUnauthorized = true, timeoutMs?: number): Promise<T> {
  return request<T>(url, options, notifyUnauthorized, "json", timeoutMs);
}

export function requestBlob(url: string, options: RequestInit = {}): Promise<Blob> {
  return request<Blob>(url, options, true, "blob");
}

async function request<T>(
  url: string,
  options: RequestInit = {},
  notifyUnauthorized = true,
  responseType: "json" | "blob" = "json",
  timeoutMs?: number,
): Promise<T> {
  const timeout = AbortSignal.timeout(timeoutMs ?? (options.body instanceof FormData || responseType === "blob" ? 120000 : 12000));
  const signal = options.signal
    ? AbortSignal.any([options.signal, timeout])
    : timeout;
  try {
    const headers = new Headers(options.headers);
    if (!["GET", "HEAD", "OPTIONS"].includes((options.method || "GET").toUpperCase())) {
      // Obtain the current token: login/logout rotate it, including in other tabs.
      const csrf = await requestJson<{ headerName: string; token: string }>(
        `${AUTH_URL}/csrf`, { signal }, false,
      );
      headers.set(csrf.headerName, csrf.token);
    }
    const response = await fetch(url, {
      ...options, headers, signal, credentials: "include", cache: "no-store",
    });
    if (!response.ok) {
      if (response.status === 401) {
        if (notifyUnauthorized) unauthorizedListeners.forEach((listener) => listener());
        throw new ApiError(401, "Votre session a expiré. Connectez-vous pour continuer.");
      }
      const details = await response.json().catch(() => null);
      if (response.status === 403)
        throw new ApiError(403, "La vérification de sécurité a échoué. Réessayez.");
      if (response.status === 409)
        throw new ApiError(409, typeof details?.message === "string" ? details.message : "Cette adresse e-mail est déjà utilisée. Connectez-vous à votre compte.");
      if (response.status === 404)
        throw new Error(
          "Cette ressource est introuvable. Elle a peut-être été supprimée.",
        );
      if (response.status >= 500)
        throw new Error(
          typeof details?.message === "string" ? details.message : "Le service est momentanément indisponible. Réessayez dans quelques instants.",
        );
      if (typeof details?.message === "string") throw new ApiError(response.status, details.message);
      if (response.status === 413) throw new ApiError(413, "Maximum 10 Mo par fichier et 50 Mo par envoi.");
      throw new Error(
        "La demande n’a pas pu aboutir. Vérifiez les informations et réessayez.",
      );
    }
    if (response.status === 204) return undefined as T;
    return (await (responseType === "blob" ? response.blob() : response.json())) as T;
  } catch (cause) {
    if (options.signal?.aborted) throw cause;
    if (timeout.aborted)
      throw new Error(
        "Le service met trop de temps à répondre. Réessayez dans quelques instants.",
      );
    if (cause instanceof TypeError)
      throw new Error(
        "Connexion au service impossible. Vérifiez votre connexion et réessayez.",
      );
    throw cause;
  }
}
