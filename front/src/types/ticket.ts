export interface Ticket {
  id: string
  title: string
  description: string
  status: string
  category: string | null
  priority: string | null
  summary: string | null
  createdAt: string
  updatedAt: string
}