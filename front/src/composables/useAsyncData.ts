import { onScopeDispose, ref, shallowRef } from "vue";

export function useAsyncData<T>(
  fetcher: (signal: AbortSignal) => Promise<T>,
  initial: T,
) {
  const data = shallowRef<T>(initial);
  const loading = ref(false);
  const error = ref("");
  const hasLoaded = ref(false);
  let controller: AbortController | undefined;

  async function load() {
    controller?.abort();
    const request = new AbortController();
    controller = request;
    loading.value = true;
    error.value = "";
    try {
      const result = await fetcher(request.signal);
      if (!request.signal.aborted) {
        data.value = result;
        hasLoaded.value = true;
      }
    } catch (cause) {
      if (!request.signal.aborted)
        error.value =
          cause instanceof Error
            ? cause.message
            : "Une erreur est survenue. Réessayez.";
    } finally {
      if (!request.signal.aborted) loading.value = false;
    }
  }

  onScopeDispose(() => controller?.abort());
  return { data, loading, error, hasLoaded, load };
}
