import type { Notification } from "../types/notification"

const BASE_URL = "http://localhost:8082/notification"

export async function getNotifications(): Promise<Notification[]> {
  const response = await fetch(BASE_URL)
  if (!response.ok) {
    throw new Error("Failed to fetch notifications")
  }
  return response.json()
}