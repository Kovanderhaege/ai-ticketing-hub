import type { Ticket } from "../types/ticket"

const BASE_URL = "http://localhost:8083/tickets"

export async function getTickets(): Promise<Ticket[]> {
  const response = await fetch(BASE_URL)
  if (!response.ok) {
    throw new Error("Failed to fetch tickets")
  }
  return response.json()
}

export async function getTicket(id: string): Promise<Ticket> {
  const response = await fetch(`${BASE_URL}/${id}`)
  if (!response.ok) {
    throw new Error("Failed to fetch tickets")
  }
  return response.json()
}


export async function createTicket(payload: {
  title: string
  description: string
}): Promise<Ticket> {
  const response = await fetch(BASE_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok) {
    throw new Error("Failed to create ticket")
  }

  return response.json()
}