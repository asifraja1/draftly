import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FileText, Send, Edit2, Check, X, Loader2, RefreshCw, Inbox, HelpCircle } from 'lucide-react'
import { getDrafts, modifyDraft, sendDraft, submitDecision, type Draft } from '../api'

function DecisionCard({ draft }: { draft: Draft }) {
  const qc = useQueryClient()
  let options: string[] = []
  try { options = JSON.parse(draft.decisionOptions || '[]') } catch { options = [] }
  if (options.length === 0) options = ['Yes', 'No']

  const decide = useMutation({
    mutationFn: (choice: string) => submitDecision(draft.id, choice),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['drafts'] }),
  })

  return (
    <div className="bg-gray-900 border border-amber-700/40 rounded-2xl p-5">
      <div className="flex items-center gap-2 mb-3">
        <HelpCircle className="w-4 h-4 text-amber-400" />
        <span className="text-amber-300 text-xs font-medium uppercase tracking-wide">Needs your decision</span>
      </div>
      <p className="text-white font-medium mb-1">{draft.senderEmail}</p>
      {draft.originalEmailBody && (
        <div className="bg-gray-800/60 rounded-lg p-3 text-xs text-gray-400 mb-3 max-h-24 overflow-y-auto whitespace-pre-wrap">
          {draft.originalEmailBody}
        </div>
      )}
      <p className="text-sm text-gray-200 mb-4">{draft.question || 'How would you like to respond?'}</p>
      <div className="flex flex-wrap gap-2">
        {options.map(opt => (
          <button
            key={opt}
            disabled={decide.isPending}
            onClick={() => decide.mutate(opt)}
            className="flex items-center gap-1.5 bg-amber-700 hover:bg-amber-600 disabled:opacity-50 text-white text-sm px-4 py-2 rounded-lg transition-colors"
          >
            {decide.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : null}
            {opt}
          </button>
        ))}
      </div>
      <p className="text-xs text-gray-500 mt-3">Once you choose, Draftly will write the reply using your decision.</p>
    </div>
  )
}

function DraftCard({ draft, onSent }: { draft: Draft; onSent: () => void }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [content, setContent] = useState(draft.draftContent)

  const modMutation = useMutation({
    mutationFn: () => modifyDraft(draft.id, content),
    onSuccess: () => {
      setEditing(false)
      qc.invalidateQueries({ queryKey: ['drafts'] })
    },
  })

  const sendMutation = useMutation({
    mutationFn: () => sendDraft(draft.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['drafts'] })
      onSent()
    },
  })

  if (draft.status === 'SENT') {
    return (
      <div className="bg-gray-900/50 border border-gray-800 rounded-2xl p-5 opacity-50">
        <div className="flex items-center gap-2 text-emerald-400 text-sm font-medium">
          <Check className="w-4 h-4" /> Sent to {draft.senderEmail}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
      {/* Header */}
      <div className="flex items-start justify-between mb-4">
        <div>
          <p className="text-white font-medium">{draft.senderEmail}</p>
          <p className="text-xs text-gray-500 mt-0.5">Thread: {draft.threadId}</p>
        </div>
        <span className={`text-xs px-2 py-0.5 rounded-full ${
          draft.status === 'MODIFIED' ? 'bg-amber-900/40 text-amber-300' : 'bg-indigo-900/40 text-indigo-300'
        }`}>
          {draft.status}
        </span>
      </div>

      {/* Original email */}
      {draft.originalEmailBody && (
        <div className="mb-4">
          <p className="text-xs text-gray-500 mb-1 uppercase tracking-wide">Original email</p>
          <div className="bg-gray-800/60 rounded-lg p-3 text-xs text-gray-400 max-h-24 overflow-y-auto whitespace-pre-wrap">
            {draft.originalEmailBody}
          </div>
        </div>
      )}

      {/* Draft content */}
      <div className="mb-4">
        <p className="text-xs text-gray-500 mb-1 uppercase tracking-wide">AI Draft</p>
        {editing ? (
          <textarea
            className="w-full bg-gray-800 border border-indigo-500 rounded-lg p-3 text-sm text-white focus:outline-none resize-none"
            rows={6}
            value={content}
            onChange={e => setContent(e.target.value)}
          />
        ) : (
          <div className="bg-gray-800/60 rounded-lg p-3 text-sm text-gray-300 whitespace-pre-wrap">
            {draft.draftContent}
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex gap-2">
        {editing ? (
          <>
            <button
              onClick={() => modMutation.mutate()}
              disabled={modMutation.isPending}
              className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white text-sm px-4 py-2 rounded-lg transition-colors"
            >
              {modMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Check className="w-3.5 h-3.5" />}
              Save
            </button>
            <button
              onClick={() => { setEditing(false); setContent(draft.draftContent) }}
              className="flex items-center gap-1.5 border border-gray-700 text-gray-400 hover:text-white text-sm px-4 py-2 rounded-lg transition-colors"
            >
              <X className="w-3.5 h-3.5" /> Cancel
            </button>
          </>
        ) : (
          <>
            <button
              onClick={() => setEditing(true)}
              className="flex items-center gap-1.5 border border-gray-700 hover:border-gray-500 text-gray-300 text-sm px-4 py-2 rounded-lg transition-colors"
            >
              <Edit2 className="w-3.5 h-3.5" /> Edit
            </button>
            <button
              onClick={() => sendMutation.mutate()}
              disabled={sendMutation.isPending}
              className="flex items-center gap-1.5 bg-emerald-700 hover:bg-emerald-600 disabled:opacity-50 text-white text-sm px-4 py-2 rounded-lg transition-colors"
            >
              {sendMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
              Send
            </button>
          </>
        )}
      </div>
    </div>
  )
}

export default function DraftsPage() {
  const qc = useQueryClient()
  const { data: drafts, isLoading, refetch } = useQuery({
    queryKey: ['drafts'],
    queryFn: getDrafts,
    refetchInterval: 10000,
  })

  const needsInput = drafts?.filter(d => d.status === 'NEEDS_INPUT') ?? []
  const pending = drafts?.filter(d => d.status === 'PENDING' || d.status === 'MODIFIED') ?? []
  const sent = drafts?.filter(d => d.status === 'SENT') ?? []
  // 'ANSWERED' rows are hidden (superseded by the generated draft that follows)

  return (
    <div className="max-w-3xl mx-auto p-6">
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-600/20 border border-emerald-500/30 flex items-center justify-center">
            <FileText className="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-white">AI Drafts</h1>
            <p className="text-sm text-gray-400">Review, edit, and send AI-generated replies</p>
          </div>
        </div>
        <button
          onClick={() => refetch()}
          className="flex items-center gap-1.5 border border-gray-700 hover:border-gray-500 text-gray-400 text-sm px-3 py-2 rounded-lg transition-colors"
        >
          <RefreshCw className="w-4 h-4" /> Refresh
        </button>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-16 text-gray-500">
          <Loader2 className="w-6 h-6 animate-spin mr-2" /> Loading drafts…
        </div>
      )}

      {!isLoading && pending.length === 0 && needsInput.length === 0 && (
        <div className="text-center py-16">
          <Inbox className="w-12 h-12 text-gray-700 mx-auto mb-3" />
          <p className="text-gray-500">No pending drafts</p>
          <p className="text-xs text-gray-600 mt-1">Drafts appear here when new emails arrive and Draftly generates replies.</p>
        </div>
      )}

      {/* Decisions the agent needs from you, first */}
      {needsInput.length > 0 && (
        <div className="space-y-4 mb-4">
          {needsInput.map(d => (
            <DecisionCard key={d.id} draft={d} />
          ))}
        </div>
      )}

      <div className="space-y-4">
        {pending.map(d => (
          <DraftCard key={d.id} draft={d} onSent={() => qc.invalidateQueries({ queryKey: ['drafts'] })} />
        ))}
      </div>

      {sent.length > 0 && (
        <div className="mt-8">
          <p className="text-sm text-gray-500 mb-3">Sent ({sent.length})</p>
          <div className="space-y-2">
            {sent.map(d => (
              <DraftCard key={d.id} draft={d} onSent={() => {}} />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
