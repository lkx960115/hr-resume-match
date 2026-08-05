import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card, Col, Row, Tag, Typography, message, Empty, Progress, Divider, Spin } from 'antd'
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SafetyCertificateOutlined,
  FlagOutlined,
  BulbOutlined,
  UserOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { fetchCandidate, fetchInterviewPack, type Candidate, type MatchReport } from '../api'

/** PRD R6: 双人并排对比维度分、证据、风险点。 */
export function ComparePage() {
  const params = useParams()
  const idA = Number(params.idA)
  const idB = Number(params.idB)
  const [candA, setCandA] = useState<Candidate | null>(null)
  const [candB, setCandB] = useState<Candidate | null>(null)
  const [reportA, setReportA] = useState<MatchReport | null>(null)
  const [reportB, setReportB] = useState<MatchReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!idA || !idB) return
    setLoading(true)
    Promise.all([
      fetchCandidate(idA), fetchInterviewPack(idA),
      fetchCandidate(idB), fetchInterviewPack(idB),
    ])
      .then(([ca, ra, cb, rb]) => {
        setCandA(ca); setReportA(ra)
        setCandB(cb); setReportB(rb)
      })
      .catch(() => message.error('加载候选人失败'))
      .finally(() => setLoading(false))
  }, [idA, idB])

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
        <div style={{ marginTop: 16, color: 'var(--color-text-secondary)' }}>加载候选人数据...</div>
      </div>
    )
  }

  if (!candA || !candB) return null

  const nameA = candA.profile?.name || candA.fileName
  const nameB = candB.profile?.name || candB.fileName

  const allDimNames = Array.from(
    new Set([
      ...(reportA?.detail?.dimensions?.map((d) => d.name) || []),
      ...(reportB?.detail?.dimensions?.map((d) => d.name) || []),
    ])
  )

  const getScoreColor = (score?: number) => {
    if (score === undefined) return '#94a3b8'
    if (score >= 75) return '#10b981'
    if (score >= 50) return '#f59e0b'
    return '#ef4444'
  }

  const dimMapA = new Map((reportA?.detail?.dimensions || []).map((d) => [d.name, d]))
  const dimMapB = new Map((reportB?.detail?.dimensions || []).map((d) => [d.name, d]))

  return (
    <div className="fade-in">
      <Link
        to="/"
        style={{ fontSize: 14, color: 'var(--color-text-secondary)', display: 'inline-block', marginBottom: 20 }}
      >
        <ArrowLeftOutlined /> 返回首页
      </Link>

      <div style={{ textAlign: 'center', marginBottom: 24 }}>
        <h2 style={{ margin: 0 }}>
          <SwapOutlined style={{ marginRight: 8, color: 'var(--color-accent)' }} />
          候选人对比
        </h2>
      </div>

      {/* Score Header */}
      <Row gutter={20} style={{ marginBottom: 20 }}>
        <Col span={12}>
          <Card>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{
                width: 48, height: 48, borderRadius: '50%', background: 'var(--gradient-primary)',
                display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 20, fontWeight: 700,
              }}>
                {nameA.charAt(0)}
              </div>
              <div style={{ flex: 1 }}>
                <Typography.Title level={4} style={{ margin: 0 }}>{nameA}</Typography.Title>
                <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                  {candA.profile?.education} · {candA.profile?.yearsOfExperience} 年
                </div>
              </div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 32, fontWeight: 800, color: getScoreColor(reportA?.totalScore) }}>
                  {reportA?.totalScore?.toFixed(1) ?? '-'}
                </div>
                <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>综合分</div>
              </div>
            </div>
          </Card>
        </Col>
        <Col span={12}>
          <Card>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{
                width: 48, height: 48, borderRadius: '50%', background: 'var(--gradient-accent)',
                display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 20, fontWeight: 700,
              }}>
                {nameB.charAt(0)}
              </div>
              <div style={{ flex: 1 }}>
                <Typography.Title level={4} style={{ margin: 0 }}>{nameB}</Typography.Title>
                <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                  {candB.profile?.education} · {candB.profile?.yearsOfExperience} 年
                </div>
              </div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 32, fontWeight: 800, color: getScoreColor(reportB?.totalScore) }}>
                  {reportB?.totalScore?.toFixed(1) ?? '-'}
                </div>
                <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>综合分</div>
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      {/* Dimension Comparison */}
      {allDimNames.length > 0 && (
        <Card
          title={<><FlagOutlined style={{ color: 'var(--color-accent)', marginRight: 6 }} />维度评分对比</>}
          style={{ marginBottom: 20 }}
        >
          {allDimNames.map((dimName) => {
            const dA = dimMapA.get(dimName)
            const dB = dimMapB.get(dimName)
            const winner = (dA?.score ?? 0) > (dB?.score ?? 0) ? 'A' : (dB?.score ?? 0) > (dA?.score ?? 0) ? 'B' : null

            return (
              <div key={dimName} style={{ marginBottom: 20 }}>
                <div style={{ fontWeight: 600, fontSize: 15, marginBottom: 10 }}>
                  {dimName}
                  {winner && (
                    <Tag color={winner === 'A' ? 'blue' : 'purple'} style={{ marginLeft: 8 }}>
                      {winner === 'A' ? nameA : nameB} 领先
                    </Tag>
                  )}
                </div>
                <Row gutter={16}>
                  <Col span={12}>
                    <div
                      style={{
                        padding: 12, borderRadius: 'var(--radius-sm)',
                        background: winner === 'A' ? 'var(--color-primary-bg)' : 'var(--color-bg-secondary)',
                        border: winner === 'A' ? '1px solid var(--color-primary-border)' : '1px solid transparent',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                        <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>{nameA}</span>
                        <span style={{ fontWeight: 700, color: getScoreColor(dA?.score) }}>
                          {dA?.score.toFixed(0) ?? '-'} 分
                        </span>
                      </div>
                      <Progress percent={Math.round(dA?.score ?? 0)} strokeColor={getScoreColor(dA?.score)} size="small" showInfo={false} />
                      {dA?.evidence && (
                        <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 8, lineHeight: 1.5, paddingLeft: 8, borderLeft: '2px solid var(--color-border)' }}>
                          <BulbOutlined style={{ marginRight: 4, fontSize: 11 }} />{dA.evidence}
                        </div>
                      )}
                    </div>
                  </Col>
                  <Col span={12}>
                    <div
                      style={{
                        padding: 12, borderRadius: 'var(--radius-sm)',
                        background: winner === 'B' ? 'var(--color-accent-bg)' : 'var(--color-bg-secondary)',
                        border: winner === 'B' ? '1px solid var(--color-accent)' : '1px solid transparent',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                        <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>{nameB}</span>
                        <span style={{ fontWeight: 700, color: getScoreColor(dB?.score) }}>
                          {dB?.score.toFixed(0) ?? '-'} 分
                        </span>
                      </div>
                      <Progress percent={Math.round(dB?.score ?? 0)} strokeColor={getScoreColor(dB?.score)} size="small" showInfo={false} />
                      {dB?.evidence && (
                        <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 8, lineHeight: 1.5, paddingLeft: 8, borderLeft: '2px solid var(--color-border)' }}>
                          <BulbOutlined style={{ marginRight: 4, fontSize: 11 }} />{dB.evidence}
                        </div>
                      )}
                    </div>
                  </Col>
                </Row>
              </div>
            )
          })}
        </Card>
      )}

      {/* Gate Check Comparison */}
      <Card
        title={<><SafetyCertificateOutlined style={{ color: 'var(--color-primary)', marginRight: 6 }} />门槛核验对比</>}
        style={{ marginBottom: 20 }}
      >
        <Row gutter={16}>
          <Col span={12}>
            <div style={{
              padding: 12, borderRadius: 'var(--radius-sm)',
              background: reportA?.passHardGate ? '#f0fdf4' : '#fef2f2',
              textAlign: 'center', marginBottom: 12,
            }}>
              {reportA?.passHardGate ? (
                <Tag color="success" icon={<CheckCircleOutlined />} style={{ fontSize: 14, padding: '4px 12px' }}>通过门槛</Tag>
              ) : (
                <Tag color="warning" icon={<CloseCircleOutlined />} style={{ fontSize: 14, padding: '4px 12px' }}>未过门槛</Tag>
              )}
            </div>
            {(reportA?.detail?.gateChecks || []).map((g) => (
              <div key={g.key} style={{ display: 'flex', gap: 8, padding: '6px 0', fontSize: 13 }}>
                {g.passed ? (
                  <CheckCircleOutlined style={{ color: 'var(--color-success)', marginTop: 3 }} />
                ) : (
                  <CloseCircleOutlined style={{ color: 'var(--color-danger)', marginTop: 3 }} />
                )}
                <div>
                  <span style={{ fontWeight: 600 }}>{g.label}</span>
                  <span style={{ color: 'var(--color-text-secondary)' }}> · 期望 {g.expected} / 实际 {g.actual}</span>
                  {g.note && <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>{g.note}</div>}
                </div>
              </div>
            ))}
          </Col>
          <Col span={12}>
            <div style={{
              padding: 12, borderRadius: 'var(--radius-sm)',
              background: reportB?.passHardGate ? '#f0fdf4' : '#fef2f2',
              textAlign: 'center', marginBottom: 12,
            }}>
              {reportB?.passHardGate ? (
                <Tag color="success" icon={<CheckCircleOutlined />} style={{ fontSize: 14, padding: '4px 12px' }}>通过门槛</Tag>
              ) : (
                <Tag color="warning" icon={<CloseCircleOutlined />} style={{ fontSize: 14, padding: '4px 12px' }}>未过门槛</Tag>
              )}
            </div>
            {(reportB?.detail?.gateChecks || []).map((g) => (
              <div key={g.key} style={{ display: 'flex', gap: 8, padding: '6px 0', fontSize: 13 }}>
                {g.passed ? (
                  <CheckCircleOutlined style={{ color: 'var(--color-success)', marginTop: 3 }} />
                ) : (
                  <CloseCircleOutlined style={{ color: 'var(--color-danger)', marginTop: 3 }} />
                )}
                <div>
                  <span style={{ fontWeight: 600 }}>{g.label}</span>
                  <span style={{ color: 'var(--color-text-secondary)' }}> · 期望 {g.expected} / 实际 {g.actual}</span>
                  {g.note && <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>{g.note}</div>}
                </div>
              </div>
            ))}
          </Col>
        </Row>
      </Card>

      {/* Risk Comparison */}
      <Row gutter={20}>
        <Col span={12}>
          <Card title={<><FlagOutlined style={{ color: 'var(--color-warning)', marginRight: 6 }} />{nameA} 风险点</>}>
            {(candA.riskFlags || []).length === 0 ? (
              <Empty description="无风险点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              candA.riskFlags?.map((r, i) => (
                <div key={i} style={{ display: 'flex', gap: 8, padding: '8px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                  <Tag color={r.severity === 'high' ? 'red' : r.severity === 'medium' ? 'orange' : 'default'} style={{ flexShrink: 0 }}>
                    {r.severity}
                  </Tag>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{r.type}</div>
                    <div style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>{r.detail}</div>
                  </div>
                </div>
              ))
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card title={<><FlagOutlined style={{ color: 'var(--color-warning)', marginRight: 6 }} />{nameB} 风险点</>}>
            {(candB.riskFlags || []).length === 0 ? (
              <Empty description="无风险点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              candB.riskFlags?.map((r, i) => (
                <div key={i} style={{ display: 'flex', gap: 8, padding: '8px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                  <Tag color={r.severity === 'high' ? 'red' : r.severity === 'medium' ? 'orange' : 'default'} style={{ flexShrink: 0 }}>
                    {r.severity}
                  </Tag>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{r.type}</div>
                    <div style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>{r.detail}</div>
                  </div>
                </div>
              ))
            )}
          </Card>
        </Col>
      </Row>
    </div>
  )
}
