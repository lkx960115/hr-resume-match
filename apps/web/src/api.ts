import axios from 'axios'

export const api = axios.create({
  baseURL: '/api',
  timeout: 120000,
})

export type HardRequirement = {
  key: string
  label: string
  operator: string
  value: string
  onFail?: string
}

export type DimensionWeight = {
  name: string
  description?: string
  weight: number
}

export type Job = {
  id: number
  title: string
  jdText?: string
  hardRequirements: HardRequirement[]
  dimensions: DimensionWeight[]
  createdAt?: string
}

export type CandidateProfile = {
  name?: string
  education?: string
  yearsOfExperience?: number
  skills?: string[]
  experiences?: { company?: string; title?: string; start?: string; end?: string; description?: string }[]
  highlights?: string[]
  summary?: string
}

export type RiskFlag = {
  type: string
  severity: string
  detail: string
}

export type Candidate = {
  id: number
  jobId: number
  fileName: string
  parseStatus: string
  parseError?: string
  profile?: CandidateProfile
  riskFlags?: RiskFlag[]
}

export type DimensionScore = {
  name: string
  score: number
  weight: number
  weightedScore: number
  evidence?: string
  gap?: string
  confidence?: number
}

export type GateCheck = {
  key: string
  label: string
  passed: boolean
  expected?: string
  actual?: string
  note?: string
}

export type InterviewQuestion = {
  category: string
  question: string
  intent?: string
  relatedRisk?: string
}

export type MatchReport = {
  id: number
  jobId: number
  candidateId: number
  candidateName?: string
  fileName?: string
  passHardGate: boolean
  totalScore?: number
  summary?: string
  status: string
  detail?: {
    gateChecks?: GateCheck[]
    dimensions?: DimensionScore[]
  }
  interviewPack?: {
    opening?: string
    questions?: InterviewQuestion[]
    closingTips?: string[]
  }
}

export async function fetchJobs() {
  const { data } = await api.get<Job[]>('/jobs')
  return data
}

export async function fetchJob(id: number) {
  const { data } = await api.get<Job>(`/jobs/${id}`)
  return data
}

export async function createJob(payload: Omit<Job, 'id' | 'createdAt'>) {
  const { data } = await api.post<Job>('/jobs', payload)
  return data
}

export async function fetchCandidates(jobId: number) {
  const { data } = await api.get<Candidate[]>(`/jobs/${jobId}/resumes`)
  return data
}

export async function uploadResumes(jobId: number, files: File[]) {
  const form = new FormData()
  files.forEach((f) => form.append('files', f))
  const { data } = await api.post<Candidate[]>(`/jobs/${jobId}/resumes`, form)
  return data
}

export async function triggerMatch(jobId: number) {
  const { data } = await api.post<MatchReport[]>(`/jobs/${jobId}/match`)
  return data
}

export async function fetchMatches(jobId: number) {
  const { data } = await api.get<MatchReport[]>(`/jobs/${jobId}/matches`)
  return data
}

export async function fetchCandidate(id: number) {
  const { data } = await api.get<Candidate>(`/candidates/${id}`)
  return data
}

export async function fetchInterviewPack(candidateId: number) {
  const { data } = await api.get<MatchReport>(`/candidates/${candidateId}/interview-pack`)
  return data
}

export async function seedDemo() {
  const { data } = await api.post<{ jobId: number; candidateCount: number; message: string }>(
    '/demo/seed',
    null,
    { timeout: 600000 },
  )
  return data
}

export async function fetchLlmStatus() {
  const { data } = await api.get<{ mode: string; provider: string; hasApiKey: boolean; model: string }>('/llm/status')
  return data
}
