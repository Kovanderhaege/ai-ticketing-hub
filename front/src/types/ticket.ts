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
  reporter: string | null
  assignee: string | null
  type: string | null
  severity: string | null
  component: string | null
  affectedVersion: string | null
  fixVersion: string | null
  dueDate: string | null
  environment: string | null
  stepsToReproduce: string | null
  expectedResult: string | null
  actualResult: string | null
  rootCause: string | null
  technicalSolution: string | null
  labels: string[]
  attachments: TicketAttachment[]
  analysisStatus?: "PENDING" | "SUCCEEDED" | "FAILED" | null
  analysisFailureCode?: string | null
  analysisProvider?: string | null
  analysisModel?: string | null
}

export interface TicketAttachment {
  id: string
  filename: string
  contentType: string
  size: number
  createdAt: string
  uploadedBy: string
}

export type TicketPayload = Pick<Ticket, "title" | "description" | "assignee" | "type" | "severity" | "component" | "affectedVersion" | "fixVersion" | "dueDate" | "environment" | "stepsToReproduce" | "expectedResult" | "actualResult" | "rootCause" | "technicalSolution" | "labels"> & {
  status?: string
  category?: string | null
  priority?: string | null
  autoTitle?: boolean
}
