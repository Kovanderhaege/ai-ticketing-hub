import type { Ticket, TicketPayload, TicketAttachment } from "../types/ticket";
import { requestJson, requestBlob } from "./client";

const BASE_URL = (
  import.meta.env.VITE_TICKET_API_URL || "/api/tickets"
).replace(/\/$/, "");

export function getTickets(signal?: AbortSignal): Promise<Ticket[]> {
  return requestJson(BASE_URL, { signal });
}

export function getTicket(id: string, signal?: AbortSignal): Promise<Ticket> {
  return requestJson(`${BASE_URL}/${encodeURIComponent(id)}`, { signal });
}

export function createTicket(payload: TicketPayload, files: File[] = []): Promise<Ticket> {
  if (files.length) {
    const body = new FormData();
    body.append("ticket", new Blob([JSON.stringify(payload)], { type: "application/json" }));
    files.forEach(file => body.append("files", file));
    return requestJson(BASE_URL, { method: "POST", body });
  }
  return requestJson(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateTicket(id: string, payload: TicketPayload): Promise<Ticket> {
  return requestJson(`${BASE_URL}/${encodeURIComponent(id)}`, {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
  });
}

export function reanalyzeTicket(id: string): Promise<Ticket> {
  return requestJson(`${BASE_URL}/${encodeURIComponent(id)}/analysis`, { method: "POST" });
}

export function uploadAttachments(id: string, files: File[]): Promise<TicketAttachment[]> {
  const body = new FormData();
  files.forEach(file => body.append("files", file));
  return requestJson(`${BASE_URL}/${encodeURIComponent(id)}/attachments`, { method: "POST", body });
}

export function attachmentUrl(ticketId: string, attachmentId: string): string {
  return `${BASE_URL}/${encodeURIComponent(ticketId)}/attachments/${encodeURIComponent(attachmentId)}`;
}

export function deleteAttachment(ticketId: string, attachmentId: string): Promise<void> {
  return requestJson(attachmentUrl(ticketId, attachmentId), { method: "DELETE" });
}

export function downloadAttachment(ticketId: string, attachmentId: string): Promise<Blob> {
  return requestBlob(attachmentUrl(ticketId, attachmentId));
}
