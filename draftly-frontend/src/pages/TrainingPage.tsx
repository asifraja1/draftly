import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Loader2, Mail, CheckCircle2, AlertCircle, ChevronRight } from 'lucide-react'
import { processEmails, createRelation, getAllRelations, type PendingEmail } from '../api'

const COMMON_RELATIONS = [
  'Client', 'Boss', 'Colleague', 'Friend', 'Family',
  'Recruiter', 'Vendor', 'Partner', 'Newsletter', 'Unknown',
]

interface RelationForm {
  relationName: string
  context: string
}

export default function TrainingPage() {
  const qc = useQueryClient()
  const [pendingEmails, setPendingEmails] = useState<PendingEmail[]>([])
  const [forms, setForms] = useState<Record<string, RelationForm>>({})
  const [saved, setSaved] = useState<Set<string>>(new Set())
  const [stats, setStats] = useState<{ total: number; published: number } | null>(null)

  const { data: relations } = useQuery({ queryKey: ['relations'], queryFn: getAllRelations })

  const processMutation = useMutation({
    mutationFn: processEmails,
    onSuccess: (data) => {
      setStats({ total: data.totalFetched, published: data.publishedToKafka })
      setPendingEmails(data.pendingEmails)
      const initial: Record<string, RelationForm> = {}
      data.pendingEmails.forEach(e => {
        initial[e.senderEmail] = { relationName: '', context: '' }
      })
      setForms(initial)
    },
  })

  const saveMutation = useMutation({
    mutationFn: ({ email, form }: { email: PendingEmail; form: RelationForm }) =>
      createRelation(email.senderEmail, form.relationName, form.context),
    onSuccess: (_, { email }) => {
      setSaved(prev => new Set([...prev, email.senderEmail]))
      qc.invalidateQueries({ queryKey: ['relations'] })
    },
  })

  const grouped = pendingEmails.reduce<Record<string, PendingEmail[]>>((acc, e) => {
    if (!acc[e.senderEmail]) acc[e.senderEmail] = []
    acc[e.senderEmail].push(e)
    return acc
  }, {})

  const senders = Object.keys(grouped)

  return (
    <div className="max-w-3xl mx-auto p-6">
      <div className="flex items-center gap-3 mb-8">
        <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center">
          <BookOpen className="w-5 h-5 text-indigo-400" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-white">Train Draftly</h1>
          <p className="text-sm text-gray-400">Teach Draftly your relationship with each sender</p>
        </div>
      </div>

      {/* Fetch button */}
      <div className="bg-gray-900 border border-gray-800 rounded-2xl p-6 mb-6">
        <h2 className="text-white font-semibold mb-2">Step 1 — Fetch your last 50 emails</h2>
        <p className="text-sm text-gray-400 mb-4">
          Draftly will scan your Gmail inbox, identify unknown senders, and ask you to classify each relationship.
        </p>
        <button
          onClick={() => processMutation.mutate()}
          disabled={processMutation.isPending}
          className="flex items-center gap-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium px-5 py-2.5 rounded-xl transition-colors"
        >
          {processMutation.isPending ? (
            <><Loader2 className="w-4 h-4 animate-spin" /> Fetching emails…</>
          ) : (
            <><Mail className="w-4 h-4" /> Fetch &amp; Analyse Emails</>
          )}
        </button>

        {processMutation.isError && (
          <div className="mt-3 flex items-center gap-2 text-red-400 text-sm">
            <AlertCircle className="w-4 h-4" />
            Failed to fetch emails. Make sure you're logged in.
          </div>
        )}

        {stats && (
          <div className="mt-4 flex gap-4 text-sm">
            <span className="text-gray-400">Total fetched: <span className="text-white font-medium">{stats.total}</span></span>
            <span className="text-gray-400">Already trained: <span className="text-emerald-400 font-medium">{stats.published}</span></span>
            <span className="text-gray-400">Need relation: <span className="text-amber-400 font-medium">{senders.length}</span></span>
          </div>
        )}
      </div>

      {/* Pending senders */}
      {senders.length > 0 && (
        <div>
          <h2 className="text-white font-semibold mb-3">
            Step 2 — Set relations for {senders.length} sender{senders.length !== 1 ? 's' : ''}
          </h2>
          <div className="space-y-3">
            {senders.map(sender => {
              const emails = grouped[sender]
              const form = forms[sender] || { relationName: '', context: '' }
              const isSaved = saved.has(sender)

              return (
                <div
                  key={sender}
                  className={`bg-gray-900 border rounded-2xl p-5 transition-all ${
                    isSaved ? 'border-emerald-700/50 opacity-60' : 'border-gray-800'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <div>
                      <p className="text-white font-medium">{sender}</p>
                      <p className="text-xs text-gray-500 mt-0.5">{emails.length} email{emails.length !== 1 ? 's' : ''}</p>
                    </div>
                    {isSaved && <CheckCircle2 className="w-5 h-5 text-emerald-400 flex-shrink-0" />}
                  </div>

                  {/* Subject previews */}
                  <div className="space-y-1 mb-4">
                    {emails.slice(0, 2).map(e => (
                      <p key={e.messageId} className="text-xs text-gray-500 truncate flex items-center gap-1">
                        <ChevronRight className="w-3 h-3 flex-shrink-0" />
                        {e.subject || '(no subject)'}
                      </p>
                    ))}
                  </div>

                  {!isSaved && (
                    <>
                      {/* Quick relation chips */}
                      <div className="flex flex-wrap gap-2 mb-3">
                        {COMMON_RELATIONS.map(r => (
                          <button
                            key={r}
                            onClick={() => setForms(f => ({ ...f, [sender]: { ...f[sender], relationName: r } }))}
                            className={`px-3 py-1 text-xs rounded-full border transition-colors ${
                              form.relationName === r
                                ? 'bg-indigo-600 border-indigo-500 text-white'
                                : 'border-gray-700 text-gray-400 hover:border-gray-500'
                            }`}
                          >
                            {r}
                          </button>
                        ))}
                      </div>

                      {/* Custom relation input */}
                      <input
                        className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500 mb-2"
                        placeholder="Or type a custom relation…"
                        value={form.relationName}
                        onChange={e => setForms(f => ({ ...f, [sender]: { ...f[sender], relationName: e.target.value } }))}
                      />

                      <textarea
                        className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500 mb-3 resize-none"
                        placeholder="Context (optional) — e.g. 'My manager at ACME Corp, formal tone'"
                        rows={2}
                        value={form.context}
                        onChange={e => setForms(f => ({ ...f, [sender]: { ...f[sender], context: e.target.value } }))}
                      />

                      <button
                        disabled={!form.relationName || saveMutation.isPending}
                        onClick={() => saveMutation.mutate({ email: emails[0], form })}
                        className="flex items-center gap-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors"
                      >
                        {saveMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
                        Save &amp; Train
                      </button>
                    </>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Known relations */}
      {relations && relations.length > 0 && (
        <div className="mt-8">
          <h2 className="text-white font-semibold mb-3">Known Relations ({relations.length})</h2>
          <div className="bg-gray-900 border border-gray-800 rounded-2xl overflow-hidden">
            {relations.map((r, i) => (
              <div key={r.id} className={`flex items-center gap-3 px-5 py-3 ${i !== 0 ? 'border-t border-gray-800' : ''}`}>
                <div className="w-8 h-8 rounded-full bg-indigo-900/50 border border-indigo-700/30 flex items-center justify-center text-xs text-indigo-300 font-medium">
                  {r.emailAddress[0].toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-white truncate">{r.emailAddress}</p>
                  {r.context && <p className="text-xs text-gray-500 truncate">{r.context}</p>}
                </div>
                <span className="text-xs bg-indigo-900/40 text-indigo-300 px-2 py-0.5 rounded-full flex-shrink-0">
                  {r.relationName}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
