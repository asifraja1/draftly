import { useQuery } from '@tanstack/react-query'
import { ScrollText, Loader2, RefreshCw } from 'lucide-react'
import { getSummaries } from '../api'

export default function SummariesPage() {
  const { data: summaries, isLoading, refetch } = useQuery({
    queryKey: ['summaries'],
    queryFn: getSummaries,
    refetchInterval: 15000,
  })

  return (
    <div className="max-w-3xl mx-auto p-6">
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-amber-600/20 border border-amber-500/30 flex items-center justify-center">
            <ScrollText className="w-5 h-5 text-amber-400" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-white">Summaries</h1>
            <p className="text-sm text-gray-400">Newsletters, notifications, and low-priority emails</p>
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
          <Loader2 className="w-6 h-6 animate-spin mr-2" /> Loading summaries…
        </div>
      )}

      {!isLoading && (!summaries || summaries.length === 0) && (
        <div className="text-center py-16">
          <ScrollText className="w-12 h-12 text-gray-700 mx-auto mb-3" />
          <p className="text-gray-500">No summaries yet</p>
          <p className="text-xs text-gray-600 mt-1">Newsletters and notifications will be summarised here.</p>
        </div>
      )}

      <div className="space-y-3">
        {summaries?.map(s => (
          <div key={s.id} className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
            <div className="flex items-center justify-between mb-3">
              <p className="text-white font-medium text-sm">{s.senderEmail}</p>
              <span className="text-xs text-gray-600">
                {new Date(s.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
              </span>
            </div>
            <p className="text-sm text-gray-300 leading-relaxed">{s.summaryText}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
