import type { Ticket, TicketPayload } from "../types/ticket";

export type TicketDraft = Omit<TicketPayload, "labels"> & { labelsText: string };

export function ticketDraft(ticket?: Ticket): TicketDraft {
  return {
    title: ticket?.title ?? "", description: ticket?.description ?? "",
    assignee: ticket?.assignee ?? "", type: ticket?.type ?? "SUPPORT", severity: ticket?.severity ?? "",
    component: ticket?.component ?? "", affectedVersion: ticket?.affectedVersion ?? "", fixVersion: ticket?.fixVersion ?? "",
    dueDate: ticket?.dueDate ?? "", environment: ticket?.environment ?? "", stepsToReproduce: ticket?.stepsToReproduce ?? "",
    expectedResult: ticket?.expectedResult ?? "", actualResult: ticket?.actualResult ?? "", rootCause: ticket?.rootCause ?? "",
    technicalSolution: ticket?.technicalSolution ?? "", labelsText: (ticket?.labels ?? []).join(", "),
    ...(ticket ? { status: ticket.status, category: ticket.category, priority: ticket.priority } : {}),
  };
}

export function ticketPayload(draft: TicketDraft): TicketPayload {
  const { labelsText, ...fields } = draft;
  const labels = [...new Set(labelsText.split(",").map(label => label.trim()).filter(Boolean))];
  if (labels.length > 20 || labels.some(label => label.length > 50))
    throw new Error("Utilisez au maximum 20 labels de 50 caractères chacun.");
  if ((!fields.autoTitle && !fields.title.trim()) || !fields.description.trim()) throw new Error("Renseignez la description et un titre, ou activez sa génération automatique.");
  return { ...fields, title: fields.title.trim(), description: fields.description.trim(), severity: fields.severity || null, assignee: fields.assignee?.trim() || null, dueDate: fields.dueDate || null, labels };
}

export function validateFiles(files: File[]) {
  if (files.length > 20 || files.reduce((sum, file) => sum + file.size, 0) > 50 * 1024 * 1024)
    throw new Error("Maximum 20 fichiers et 50 Mo par envoi.");
  if (files.some(file => file.size > 10 * 1024 * 1024)) throw new Error("Chaque fichier doit faire au maximum 10 Mo.");
  if (files.some(file => !file.size)) throw new Error("Les fichiers vides ne sont pas acceptés.");
}

export function formatFileSize(size: number): string {
  return size < 1024 * 1024 ? `${Math.max(1, Math.round(size / 1024))} Ko` : `${(size / (1024 * 1024)).toFixed(1)} Mo`;
}
