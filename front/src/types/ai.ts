export type AiProvider = "OPENAI" | "ANTHROPIC" | "OLLAMA" | "OPENAI_COMPATIBLE";
export interface AiSettings {
  enabled: boolean;
  provider: AiProvider;
  model: string;
  baseUrl: string;
  apiKeyConfigured: boolean;
  outputFormat: "SCHEMA" | "JSON" | "PROMPT";
  timeoutMillis: number;
  connectTimeoutMillis: number;
  maxTokens: number;
  maxAttempts: number;
  version: number;
  updatedAt: string | null;
  updatedBy: string | null;
  serviceLinkConfigured: boolean;
}
export type AiDraft = Pick<AiSettings, "enabled" | "provider" | "model" | "baseUrl" | "outputFormat" |
  "timeoutMillis" | "connectTimeoutMillis" | "maxTokens" | "maxAttempts" | "version"> & {
  apiKey: string;
  clearApiKey: boolean;
};
export interface AiDiagnostic { success: boolean; message: string; analysis: Record<string, string | null> | null; }
