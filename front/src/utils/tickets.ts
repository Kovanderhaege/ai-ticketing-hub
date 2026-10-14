export const statusLabels: Record<string, string> = {
  NEW: "À analyser",
  ANALYZED: "Analysé",
  IN_PROGRESS: "En cours",
  RESOLVED: "Résolu",
  REOPENED: "Réouvert",
  CLOSED: "Clôturé",
};

export const typeLabels: Record<string, string> = {
  BUG: "Anomalie", TASK: "Tâche", IMPROVEMENT: "Amélioration", SUPPORT: "Support",
};
export const severityLabels: Record<string, string> = {
  MINOR: "Mineure", MAJOR: "Majeure", CRITICAL: "Critique", BLOCKER: "Bloquante",
};

export const priorityLabels: Record<string, string> = {
  HIGH: "Haute",
  MEDIUM: "Moyenne",
  LOW: "Basse",
};

export const categoryLabels: Record<string, string> = {
  TECHNICAL: "Technique",
  BILLING: "Facturation",
  ACCOUNT: "Compte",
  OTHER: "Autre",
};

export function categoryLabel(value: string | null): string {
  return value ? categoryLabels[value] || value : "À déterminer";
}

export function formatDate(value: string, withTime = false): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Date indisponible";
  return new Intl.DateTimeFormat("fr-FR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    ...(withTime ? ({ hour: "2-digit", minute: "2-digit" } as const) : {}),
  }).format(date);
}

export function normalizeSearch(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}
