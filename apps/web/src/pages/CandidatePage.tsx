import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card, Col, Row, Tag, Typography, message, Empty, Progress, Divider, Button, Tooltip } from 'antd'
import {
  ArrowLeftOutlined,
  UserOutlined,
  WarningOutlined,
  AuditOutlined,
  MessageOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SafetyCertificateOutlined,
  FlagOutlined,
  BulbOutlined,
  EyeOutlined,
  LockOutlined,
  DownloadOutlined,
} from '@ant-design/icons'
import { fetchCandidate, fetchInterviewPack, revealCandidate, type Candidate, type MatchReport } from '../api'
import { RadarChart } from '../components/RadarChart'

export function CandidatePage() {
  const { candidateId } = useParams()
  const id = Number(candidateId)
  const [candidate, setCandidate] = useState<Candidate | null>(null)
  const [report, setReport] = useState<MatchReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [revealed, setRevealed] = useState(false)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    Promise.all([fetchCandidate(id), fetchInterviewPack(id)])
      .then(([c, r]) => {
        setCandidate(c)
        setReport(r)
      })
      .catch(() => message.error('加载候选人失败（请先完成匹配）'))
      .finally(() => setLoading(false))
  }, [id])

  const handleReveal = async () => {
    if (!id) return
    try {
      const plain = await revealCandidate(id)
      setCandidate(plain)
      setRevealed(true)
      message.info('已展开明文，本次查看已记入操作日志')
    } catch {
      message.error('展开明文失败')
    }
  }

  /** PRD R4: 点击面试题关联风险 → 滚动到对应风险点 */
  const scrollToRisk = (riskType: string) => {
    const cat = riskCategoryMap[riskType] || '其他'
    // 找到该分类下第一个匹配的风险点
    const group = riskGroups[cat]
    if (!group || group.length === 0) return
    const idx = group.findIndex((r) => r.type === riskType)
    const targetId = `risk-${cat}-${idx >= 0 ? idx : 0}`
    const el = document.getElementById(targetId)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      el.style.background = `${riskCategoryColors[cat] || '#6b7280'}15`
      setTimeout(() => {
        el.style.background = ''
      }, 2000)
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 60, color: '#94a3b8' }}>
        加载中...
      </div>
    )
  }

  if (!candidate) return null

  const profile = candidate.profile
  const name = profile?.name || candidate.fileName
  const initials = name ? name.charAt(0).toUpperCase() : '?'
  const dimensions = report?.detail?.dimensions || []
  const gateChecks = report?.detail?.gateChecks || []
  const riskFlags = candidate.riskFlags || []
  const questions = report?.interviewPack?.questions || []
  const closingTips = report?.interviewPack?.closingTips || []

  // PRD R4: 风险点按 category 分组
  const riskCategoryMap: Record<string, string> = {
    '履历风险': '履历',
    '经验偏少': '履历',
    '技能夸大': '技能',
    '常规核验': '软性',
  }
  const riskGroups = riskFlags.reduce(
    (acc, r) => {
      const cat = riskCategoryMap[r.type] || '其他'
      if (!acc[cat]) acc[cat] = []
      acc[cat].push(r)
      return acc
    },
    {} as Record<string, typeof riskFlags>,
  )
  const riskGroupOrder = ['履历', '技能', '软性', '其他']
  const riskCategoryColors: Record<string, string> = {
    履历: '#f59e0b',
    技能: '#3b82f6',
    软性: '#8b5cf6',
    其他: '#6b7280',
  }

  const getScoreColor = (score?: number) => {
    if (score === undefined) return '#94a3b8'
    if (score >= 75) return '#10b981'
    if (score >= 50) return '#f59e0b'
    return '#ef4444'
  }

  return (
    <div className="fade-in">
      {/* Breadcrumb */}
      <Link
        to={`/jobs/${candidate.jobId}`}
        style={{ fontSize: 14, color: 'var(--color-text-secondary)', display: 'inline-block', marginBottom: 16 }}
      >
        <ArrowLeftOutlined /> 返回岗位
      </Link>

      {/* Candidate Header */}
      <div className="candidate-header">
        <div className="candidate-avatar">{initials}</div>
        <div className="candidate-info" style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <h2 style={{ margin: 0 }}>{name}</h2>
            {!revealed && (
              <Tooltip title="手机号/邮箱/身份证已默认打码，HRBP 可展开查看明文（操作用日志记录）">
                <Button
                  type="text"
                  size="small"
                  icon={<EyeOutlined />}
                  onClick={handleReveal}
                  style={{ color: 'var(--color-warning)' }}
                >
                  显示明文
                </Button>
              </Tooltip>
            )}
            {revealed && (
              <Tag icon={<LockOutlined />} color="warning">明文模式</Tag>
            )}
          </div>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginTop: 6 }}>
            {profile?.education && (
              <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                🎓 {profile.education}
              </span>
            )}
            {profile?.yearsOfExperience !== undefined && (
              <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                ⏱ {profile.yearsOfExperience} 年经验
              </span>
            )}
            {candidate.fileName && (
              <span style={{ fontSize: 13, color: 'var(--color-text-tertiary)' }}>
                📄 {candidate.fileName}
              </span>
            )}
          </div>
          {profile?.skills && profile.skills.length > 0 && (
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 10 }}>
              {profile.skills.map((s) => (
                <Tag key={s} style={{ borderRadius: 6 }}>{s}</Tag>
              ))}
            </div>
          )}
        </div>
        {report && (
          <div style={{ textAlign: 'center', padding: '0 16px' }}>
            <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginBottom: 4 }}>
              {report.passHardGate ? '通过门槛' : '未过门槛'}
            </div>
            <div
              style={{
                fontSize: 36,
                fontWeight: 800,
                color: getScoreColor(report.totalScore),
                fontVariantNumeric: 'tabular-nums',
                lineHeight: 1,
              }}
            >
              {report.totalScore?.toFixed(1) ?? '-'}
            </div>
            <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 4 }}>综合分</div>
          </div>
        )}
      </div>

      {/* Summary */}
      {profile?.summary && (
        <Card style={{ marginBottom: 20 }}>
          <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
            <UserOutlined style={{ marginRight: 6, color: 'var(--color-primary)' }} />
            候选人摘要
          </Typography.Text>
          <Typography.Paragraph style={{ margin: 0, color: 'var(--color-text-secondary)', lineHeight: 1.7 }}>
            {profile.summary}
          </Typography.Paragraph>
        </Card>
      )}

      {/* Gate Checks */}
      {gateChecks.length > 0 && (
        <Card style={{ marginBottom: 20 }}>
          <Typography.Text strong style={{ display: 'block', marginBottom: 12, fontSize: 15 }}>
            <SafetyCertificateOutlined style={{ marginRight: 6, color: 'var(--color-primary)' }} />
            门槛核验
          </Typography.Text>
          <Row gutter={[8, 8]}>
            {gateChecks.map((g) => (
              <Col span={12} key={g.key}>
                <div className={`gate-check-item ${g.passed ? 'passed' : 'failed'}`}>
                  {g.passed ? (
                    <CheckCircleOutlined style={{ color: 'var(--color-success)', fontSize: 18 }} />
                  ) : (
                    <CloseCircleOutlined style={{ color: 'var(--color-danger)', fontSize: 18 }} />
                  )}
                  <div style={{ flex: 1 }}>
                    <div className="gate-check-label">{g.label}</div>
                    <div className="gate-check-detail">
                      期望 {g.expected} · 实际 {g.actual}
                      {g.note && ` · ${g.note}`}
                    </div>
                  </div>
                </div>
              </Col>
            ))}
          </Row>
        </Card>
      )}

      {/* Risk + Match Breakdown */}
      <Row gutter={20} style={{ marginBottom: 20 }}>
        {/* Risk Flags - grouped by category */}
        <Col span={10}>
          <Card title={<><WarningOutlined style={{ color: 'var(--color-warning)', marginRight: 6 }} />风险点</>}>
            {riskFlags.length === 0 ? (
              <Empty description="无风险点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              riskGroupOrder.map((cat) => {
                const group = riskGroups[cat]
                if (!group || group.length === 0) return null
                return (
                  <div key={cat} style={{ marginBottom: 12 }}>
                    <div
                      style={{
                        fontSize: 12,
                        fontWeight: 600,
                        color: riskCategoryColors[cat] || '#6b7280',
                        marginBottom: 6,
                        paddingBottom: 4,
                        borderBottom: `2px solid ${riskCategoryColors[cat] || '#6b7280'}15`,
                      }}
                    >
                      {cat}风险
                    </div>
                    {group.map((r, i) => (
                      <div
                        key={i}
                        id={`risk-${cat}-${i}`}
                        style={{
                          display: 'flex',
                          gap: 10,
                          padding: '10px 0',
                          borderBottom:
                            i < group.length - 1 ? '1px solid var(--color-border-light)' : 'none',
                        }}
                      >
                        <Tag
                          color={r.severity === 'high' ? 'red' : r.severity === 'medium' ? 'orange' : 'default'}
                          style={{ height: 'fit-content', flexShrink: 0 }}
                        >
                          {r.severity}
                        </Tag>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 2 }}>{r.type}</div>
                          <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>{r.detail}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                )
              })
            )}
          </Card>
        </Col>

        {/* Dimension Radar */}
        <Col span={14}>
          <Card title={<><AuditOutlined style={{ color: 'var(--color-accent)', marginRight: 6 }} />能力维度雷达</>}>
            {dimensions.length > 0 ? (
              <div style={{ display: 'flex', justifyContent: 'center', padding: '8px 0' }}>
                <RadarChart dimensions={dimensions} size={260} />
              </div>
            ) : (
              <Empty description="暂无维度数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>
        </Col>
      </Row>

      {/* Dimension Details */}
      {dimensions.length > 0 && (
        <Card style={{ marginBottom: 20 }} title={<><AuditOutlined style={{ color: 'var(--color-accent)', marginRight: 6 }} />维度评分详情</>}>
          {dimensions.map((d) => (
            <div className="dimension-card" key={d.name}>
              <div className="dimension-header">
                <span className="dimension-name">
                  <FlagOutlined style={{ color: 'var(--color-accent)' }} />
                  {d.name}
                  <Tag style={{ marginLeft: 6, fontSize: 11 }}>权重 {Math.round(d.weight * 100)}%</Tag>
                </span>
                <span className="dimension-score">
                  {d.score.toFixed(0)} <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>→ 加权 {d.weightedScore.toFixed(1)}</span>
                </span>
              </div>
              <Progress
                percent={Math.round(d.score)}
                strokeColor={getScoreColor(d.score)}
                size="small"
                showInfo={false}
              />
              {d.evidence && (
                <div className="dimension-evidence">
                  <BulbOutlined style={{ marginRight: 4 }} />
                  {d.evidence}
                </div>
              )}
              {d.gap && (
                <div className="dimension-gap">
                  ⚠ 缺口：{d.gap}
                </div>
              )}
            </div>
          ))}
        </Card>
      )}

      {/* Interview Pack */}
      {report?.interviewPack && (
        <Card
          title={
            <>
              <MessageOutlined style={{ color: 'var(--color-primary)', marginRight: 6 }} />
              结构化面试题库
              {questions.length > 0 && <Tag color="blue" style={{ marginLeft: 8 }}>{questions.length} 题</Tag>}
            </>
          }
          extra={
            <Button
              icon={<DownloadOutlined />}
              size="small"
              onClick={() => {
                const md = buildInterviewPackMd(
                  name,
                  report.interviewPack?.opening || '',
                  questions,
                  closingTips,
                  dimensions,
                )
                downloadMd(`${name}-面试包.md`, md)
                message.success('面试包已导出')
              }}
            >
              导出 Markdown
            </Button>
          }
        >
          {/* Opening */}
          {report.interviewPack.opening && (
            <div
              style={{
                background: 'var(--color-primary-bg)',
                borderRadius: 'var(--radius-sm)',
                padding: '14px 18px',
                marginBottom: 16,
                borderLeft: '3px solid var(--color-primary)',
              }}
            >
              <Typography.Text strong style={{ display: 'block', marginBottom: 4, fontSize: 13, color: 'var(--color-primary)' }}>
                开场白
              </Typography.Text>
              <Typography.Paragraph style={{ margin: 0, color: 'var(--color-text-secondary)', lineHeight: 1.7 }}>
                {report.interviewPack.opening}
              </Typography.Paragraph>
            </div>
          )}

          {/* Questions */}
          {questions.map((q, idx) => (
            <div className="interview-question" key={idx}>
              <div className="interview-question-header">
                <div className="interview-question-num">{idx + 1}</div>
                <div style={{ flex: 1 }}>
                  <div className="interview-question-text">{q.question}</div>
                  <div className="interview-question-meta">
                    <Tag color="blue" style={{ marginRight: 6 }}>{q.category}</Tag>
                    意图：{q.intent || '-'}
                    {q.relatedRisk && (
                      <span>
                        {' · '}关联风险：
                        <a
                          onClick={(e) => {
                            e.preventDefault()
                            scrollToRisk(q.relatedRisk!)
                          }}
                          style={{ color: 'var(--color-warning)', cursor: 'pointer' }}
                        >
                          {q.relatedRisk}
                        </a>
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          ))}

          {/* Closing Tips */}
          {closingTips.length > 0 && (
            <>
              <Divider />
              <Typography.Text strong style={{ display: 'block', marginBottom: 12, fontSize: 15 }}>
                <CheckCircleOutlined style={{ color: 'var(--color-success)', marginRight: 6 }} />
                收尾提示
              </Typography.Text>
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {closingTips.map((t, i) => (
                  <li
                    key={i}
                    style={{
                      marginBottom: 8,
                      fontSize: 14,
                      color: 'var(--color-text-secondary)',
                      lineHeight: 1.6,
                    }}
                  >
                    {t}
                  </li>
                ))}
              </ul>
            </>
          )}
        </Card>
      )}
    </div>
  )
}

/** 构建面试包 Markdown 文本 */
function buildInterviewPackMd(
  name: string,
  opening: string,
  questions: { category: string; question: string; intent?: string; relatedRisk?: string }[],
  closingTips: string[],
  dimensions: { name: string; score: number; weightedScore: number; evidence?: string; gap?: string }[],
): string {
  const lines: string[] = []
  lines.push(`# ${name} — 面试包`, '')
  lines.push(`> 生成时间：${new Date().toLocaleString('zh-CN')}`, '')

  lines.push('## 开场白', '', opening, '')
  lines.push('## 维度评分摘要', '')
  lines.push('| 维度 | 分数 | 加权分 |', '|------|------|--------|')
  dimensions.forEach((d) => lines.push(`| ${d.name} | ${d.score.toFixed(0)} | ${d.weightedScore.toFixed(1)} |`))
  lines.push('')

  lines.push('## 追问问题库', '')
  questions.forEach((q, i) => {
    lines.push(`### ${i + 1}. [${q.category}] ${q.question}`, '')
    lines.push(`- **意图**：${q.intent || '-'}`)
    if (q.relatedRisk) lines.push(`- **关联风险**：${q.relatedRisk}`)
    lines.push('')
  })

  lines.push('## 收尾提示', '')
  closingTips.forEach((t) => lines.push(`- ${t}`))

  return lines.join('\n')
}

/** 触发浏览器下载 .md 文件 */
function downloadMd(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
