import { requestJson } from "./client";
import type { AiSettings, AiDraft, AiDiagnostic } from "../types/ai";
const BASE_URL = (import.meta.env.VITE_AI_ADMIN_API_URL || "/api/admin/ai").replace(/\/$/, "");

export function getAiSettings(signal?: AbortSignal): Promise<AiSettings> {
  return requestJson(BASE_URL + "/configuration", { signal });
}
export function saveAiSettings(draft: AiDraft): Promise<AiSettings> {
  return requestJson(BASE_URL + "/configuration", {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(draft),
  });
}
export function listAiModels(draft: AiDraft): Promise<string[]> {
  return requestJson(BASE_URL + "/models", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(draft),
  }, true, draft.timeoutMillis + 15000);
}
export function testAiSettings(draft: AiDraft, analyze = false): Promise<AiDiagnostic> {
  return requestJson(BASE_URL + (analyze ? "/test-analysis" : "/test-connection"), {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(draft),
  }, true, draft.timeoutMillis + 15000);
}
