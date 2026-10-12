import type { Notification } from "../types/notification";
import { requestJson } from "./client";

const BASE_URL = (
  import.meta.env.VITE_NOTIFICATION_API_URL || "/api/notifications"
).replace(/\/$/, "");

export function getNotifications(
  signal?: AbortSignal,
): Promise<Notification[]> {
  return requestJson(BASE_URL, { signal });
}
