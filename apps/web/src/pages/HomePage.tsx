import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Spin, message, Tag, Empty, Modal, Progress } from 'antd'
import {
  ThunderboltOutlined,
  FileSearchOutlined,
  AuditOutlined,
  MessageOutlined,
  ReloadOutlined,
  ArrowRightOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { fetchJobs, seedDemo, fetchCandidates, fetchMatches, type Job } from '../api'

export function HomePage() {
  const [jobs, setJobs] = useState<Job[]>([])
  const [loading, setLoading] = useState(true)
  const [seeding, setSeeding] = useState(false)
  const [progressVisible, setProgressVisible] = useState(false)
  const [progress, setProgress] = useState(0)
  const [progressText, setProgressText] = useState('正在提交任务...')
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null)
  const navigate = useNavigate()

  const load = async () => {
    setLoading(true)
    try {
      setJobs(await fetchJobs())
    } catch {
      message.error('无法连接后端，请先启动 apps/api')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    return () => {
      if (pollTimer.current) clearInterval(pollTimer.current)
    }
  }, [])

  const clearPoll = () => {
    if (pollTimer.current) {
      clearInterval(pollTimer.current)
      pollTimer.current = null
    }
  }

  const onSeed = async () => {
    setSeeding(true)
    try {
      const res = await seedDemo()
      const jobIds = res.jobIds?.length ? res.jobIds : [res.jobId]
      message.success('样例任务已提交，正在后台处理')
      setProgressVisible(true)
      setProgress(0)
      setProgressText('正在准备解析...')

      let elapsed = 0
      const maxWait = 600 // 10 分钟

      const check = async () => {
        try {
          elapsed += 2
          let totalCandidates = 0
          let parsedCandidates = 0
          let totalMatches = 0
          let completedMatches = 0

          await Promise.all(
            jobIds.map(async (jobId) => {
              const [candidates, matches] = await Promise.all([
                fetchCandidates(jobId),
                fetchMatches(jobId),
              ])
              totalCandidates += candidates.length || 0
              parsedCandidates += candidates.filter(
                (c) => c.parseStatus === 'READY' || c.parseStatus === 'FAILED' || c.parseStatus === 'parsed'
              ).length
              totalMatches += matches.length || 0
              completedMatches += matches.filter(
                (m) => m.status === 'completed' || m.status === 'failed' || m.status === 'DONE'
              ).length
            })
          )

          const total = totalCandidates || res.candidateCount
          const parsePct = total > 0 ? Math.min(50, Math.round((parsedCandidates / total) * 50)) : 0
          const matchPct = totalMatches > 0 ? Math.min(50, Math.round((completedMatches / totalMatches) * 50)) : 0
          setProgress(parsePct + matchPct)
          setProgressText(`已解析 ${parsedCandidates}/${total} 份简历，已生成 ${completedMatches}/${totalMatches} 份匹配报告`)

          const done = totalCandidates > 0 && parsedCandidates === total && totalMatches > 0 && completedMatches === totalMatches
          if (done) {
            clearPoll()
            setProgressVisible(false)
            setSeeding(false)
            await load()
            message.success(`样例数据准备完毕，已创建 ${jobIds.length} 个岗位`)
            return
          }

          if (elapsed >= maxWait) {
            clearPoll()
            setProgressVisible(false)
            setSeeding(false)
            await load()
            message.warning('处理时间较长，已刷新岗位列表，请手动点击查看结果')
          }
        } catch {
          // 轮询失败继续，避免网络抖动中断
        }
      }

      await check()
      pollTimer.current = setInterval(check, 2000)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } }; message?: string }
      message.error(err.response?.data?.error || err.message || '加载样例失败')
      setSeeding(false)
      setProgressVisible(false)
    }
  }

  const features = [
    { icon: <SafetyCertificateOutlined />, color: 'blue', title: '硬性门槛', desc: '学历、年限、必会技能自动核验' },
    { icon: <AuditOutlined />, color: 'green', title: '可解释匹配', desc: '维度分数 + 证据摘录 + 缺口说明' },
    { icon: <FileSearchOutlined />, color: 'purple', title: '简历解析', desc: 'PDF / DOCX / TXT 自动结构化' },
    { icon: <MessageOutlined />, color: 'orange', title: '面试题库', desc: '基于风险点生成结构化追问' },
  ]

  return (
    <div className="fade-in">
      {/* Hero Section */}
      <div className="hero">
        <div className="hero-badge">
          <ThunderboltOutlined /> AI 驱动 · 可解释 · 一键演示
        </div>
        <h1 className="hero-title">简历匹配与结构化面试助手</h1>
        <p className="hero-subtitle">
          上传脱敏简历，设定岗位硬性门槛与能力权重，系统自动解析、打分、排序，
          并生成可解释的匹配报告与结构化面试追问题库。
        </p>
        <div className="hero-actions">
          <Button
            type="primary"
            size="large"
            loading={seeding}
            onClick={onSeed}
            icon={<ThunderboltOutlined />}
          >
            加载样例数据并开始演示
          </Button>
          <Button
            size="large"
            ghost
            style={{ color: 'white', borderColor: 'rgba(255,255,255,0.4)' }}
            onClick={load}
            icon={<ReloadOutlined />}
          >
            刷新岗位列表
          </Button>
        </div>
      </div>

      {/* Feature Highlights */}
      <div className="feature-grid">
        {features.map((f) => (
          <div key={f.title} className="feature-card">
            <div className={`feature-icon ${f.color}`}>{f.icon}</div>
            <h3 className="feature-title">{f.title}</h3>
            <p className="feature-desc">{f.desc}</p>
          </div>
        ))}
      </div>

      {/* Job List */}
      <div className="section-header">
        <h2 className="section-title">
          <span className="section-title-bar" />
          岗位列表
        </h2>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 60 }}>
          <Spin size="large" />
        </div>
      ) : jobs.length === 0 ? (
        <div className="panel empty-state">
          <div className="empty-state-icon">
            <TeamOutlined />
          </div>
          <div className="empty-state-title">暂无岗位</div>
          <div className="empty-state-desc">
            点击上方「加载样例数据并开始演示」按钮，
            系统将自动创建 Java 后端、前端、产品经理、算法工程师等多个岗位及对应样例简历，并完成解析与匹配。
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: 16 }}>
          {jobs.map((job) => (
            <div
              key={job.id}
              className="job-card"
              onClick={() => navigate(`/jobs/${job.id}`)}
            >
              <div className="job-card-header">
                <h3 className="job-card-title">{job.title}</h3>
                <Tag color="blue">{job.dimensions?.length || 0} 维度</Tag>
              </div>
              <div className="job-card-meta">
                <span className="job-card-meta-item">
                  <SafetyCertificateOutlined /> 门槛 {job.hardRequirements?.length || 0}
                </span>
                <span className="job-card-meta-item">
                  <AuditOutlined /> 权重 {job.dimensions?.length || 0}
                </span>
                {job.createdAt && (
                  <span className="job-card-meta-item">
                    {new Date(job.createdAt).toLocaleDateString('zh-CN')}
                  </span>
                )}
              </div>
              <div className="job-card-stats">
                <div className="job-card-stat">
                  <span className="job-card-stat-value">{job.hardRequirements?.length || 0}</span>
                  <span className="job-card-stat-label">硬性门槛</span>
                </div>
                <div className="job-card-stat">
                  <span className="job-card-stat-value">{job.dimensions?.length || 0}</span>
                  <span className="job-card-stat-label">能力维度</span>
                </div>
                <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center' }}>
                  <Button type="link" icon={<ArrowRightOutlined />}>
                    查看匹配
                  </Button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={progressVisible}
        closable={false}
        footer={null}
        centered
        title="正在准备样例演示"
      >
        <div style={{ textAlign: 'center', padding: '12px 0 24px' }}>
          <Progress percent={progress} status="active" strokeColor={{ from: '#2b5aed', to: '#7c3aed' }} />
          <p style={{ marginTop: 16, color: 'var(--color-text-secondary)' }}>{progressText}</p>
          <p style={{ fontSize: 12, color: '#94a3b8' }}>解析与匹配在后台运行，请耐心等待，无需重复点击</p>
        </div>
      </Modal>
    </div>
  )
}
