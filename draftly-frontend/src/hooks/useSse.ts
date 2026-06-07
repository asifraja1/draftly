import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'

export function useSse() {
  const qc = useQueryClient()

  useEffect(() => {
    const es = new EventSource('/api/events', { withCredentials: true })

    es.addEventListener('draft', () => {
      qc.invalidateQueries({ queryKey: ['drafts'] })
    })

    es.addEventListener('needs_input', () => {
      qc.invalidateQueries({ queryKey: ['drafts'] })
    })

    es.addEventListener('summary', () => {
      qc.invalidateQueries({ queryKey: ['summaries'] })
    })

    return () => es.close()
  }, [qc])
}
