export async function requestJson<T>(
  url: string,
  options: RequestInit = {},
): Promise<T> {
  const timeout = AbortSignal.timeout(12000);
  const signal = options.signal
    ? AbortSignal.any([options.signal, timeout])
    : timeout;
  try {
    const response = await fetch(url, { ...options, signal });
    if (!response.ok) {
      if (response.status === 404)
        throw new Error(
          "Cette ressource est introuvable. Elle a peut-être été supprimée.",
        );
      if (response.status >= 500)
        throw new Error(
          "Le service est momentanément indisponible. Réessayez dans quelques instants.",
        );
      throw new Error(
        "La demande n’a pas pu aboutir. Vérifiez les informations et réessayez.",
      );
    }
    return (await response.json()) as T;
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
