import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './AppLayout'
import { HomePage } from './pages/HomePage'
import { JobPage } from './pages/JobPage'
import { CandidatePage } from './pages/CandidatePage'
import { ComparePage } from './pages/ComparePage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/jobs/:jobId" element={<JobPage />} />
          <Route path="/candidates/:candidateId" element={<CandidatePage />} />
          <Route path="/compare/:idA/:idB" element={<ComparePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
