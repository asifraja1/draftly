import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import LoginPage from './pages/LoginPage'
import TrainingPage from './pages/TrainingPage'
import DraftsPage from './pages/DraftsPage'
import SummariesPage from './pages/SummariesPage'
import Layout from './components/Layout'
import './index.css'

const qc = new QueryClient({ defaultOptions: { queries: { retry: 1 } } })

export default function App() {
  return (
    <QueryClientProvider client={qc}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<Layout />}>
            <Route path="/train" element={<TrainingPage />} />
            <Route path="/drafts" element={<DraftsPage />} />
            <Route path="/summaries" element={<SummariesPage />} />
            <Route path="/" element={<Navigate to="/train" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
