import axios from 'axios'

const api = axios.create({
  baseURL: '',
  withCredentials: true,
})

export interface PendingEmail {
  messageId: string
  threadId: string
  senderEmail: string
  subject: string
}

export interface ProcessResponse {
  totalFetched: number
  publishedToKafka: number
  pendingRelations: number
  pendingEmails: PendingEmail[]
}

export interface Relation {
  id: number
  emailAddress: string
  relationName: string
  context: string
  createdAt: string
}

export interface Draft {
  id: number
  threadId: string
  senderEmail: string
  originalEmailBody: string
  draftContent: string
  status: 'PENDING' | 'MODIFIED' | 'SENT' | 'NEEDS_INPUT' | 'ANSWERED'
  question?: string
  decisionOptions?: string   // JSON array string
  createdAt: string
}

export interface Summary {
  id: number
  threadId: string
  senderEmail: string
  summaryText: string
  createdAt: string
}

export const processEmails = () =>
  api.post<ProcessResponse>('/api/emails/process').then(r => r.data)

export const createRelation = (emailAddress: string, relationName: string, context: string) =>
  api.post<Relation>('/api/relations', { emailAddress, relationName, context }).then(r => r.data)

export const getAllRelations = () =>
  api.get<Relation[]>('/api/relations').then(r => r.data)

export const getDrafts = () =>
  api.get<Draft[]>('/api/drafts').then(r => r.data)

export const modifyDraft = (id: number, modifiedContent: string) =>
  api.put<Draft>(`/api/drafts/${id}`, { modifiedContent }).then(r => r.data)

export const sendDraft = (id: number) =>
  api.post<Draft>(`/api/drafts/${id}/send`).then(r => r.data)

export const submitDecision = (id: number, decision: string) =>
  api.post<Draft>(`/api/drafts/${id}/decision`, { decision }).then(r => r.data)

export const getSummaries = () =>
  api.get<Summary[]>('/api/summaries').then(r => r.data)
