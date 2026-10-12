import type { Ticket } from "../types/ticket";
import { requestJson } from "./client";

const BASE_URL = (
  import.meta.env.VITE_TICKET_API_URL || "/api/tickets"
).replace(/\/$/, "");

export function getTickets(signal?: AbortSignal): Promise<Ticket[]> {
  return requestJson(BASE_URL, { signal });
}

export function getTicket(id: string, signal?: AbortSignal): Promise<Ticket> {
  return requestJson(`${BASE_URL}/${encodeURIComponent(id)}`, { signal });
}

export function createTicket(payload: {
  title: string;
  description: string;
}): Promise<Ticket> {
  return requestJson(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}
