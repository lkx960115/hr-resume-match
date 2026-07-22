import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card, Col, List, Progress, Row, Space, Tag, Typography, message, Descriptions } from 'antd'
import { fetchCandidate, fetchInterviewPack, type Candidate, type MatchReport } from '../api'

export function CandidatePage() {
  const { candidateId } = useParams()
  const id = Number(candidateId)
  const [candidate, setCandidate] = useState<Candidate | null>(null)
  const [report, setReport] = useState<MatchReport | null>(null)

  useEffect(() => {
    if (!id) return
    Promise.all([fetchCandidate(id), fetchInterviewPack(id)])
      .then(([c, r]) => {
        setCandidate(c)
        setReport(r)
      })
      .catch(() => message.error('加载候选人失败（请先完成匹配）'))
  }, [id])

  if (!candidate) return null
  const profile = candidate.profile

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Text>
        <Link to={`/jobs/${candidate.jobId}`}>← 返回岗位</Link>
      </Typography.Text>

      <Card title={profile?.name || candidate.fileName}>
        <Descriptions column={2} size="small">
          <Descriptions.Item label="学历">{profile?.education || '-'}</Descriptions.Item>
          <Descriptions.Item label="年限">{profile?.yearsOfExperience ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="技能" span={2}>
            <Space wrap>
              {profile?.skills?.map((s) => (
                <Tag key={s}>{s}</Tag>
              ))}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="摘要" span={2}>
            {profile?.summary || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="风险点">
            <List
              dataSource={candidate.riskFlags || []}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Space>
                        <Tag color={item.severity === 'high' ? 'red' : item.severity === 'medium' ? 'orange' : 'default'}>
                          {item.severity}
                        </Tag>
                        {item.type}
                      </Space>
                    }
                    description={item.detail}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            title="匹配拆解"
            extra={
              report ? (
                <Space>
                  {report.passHardGate ? <Tag color="success">过门槛</Tag> : <Tag color="warning">未过门槛</Tag>}
                  <span className="score">{report.totalScore?.toFixed(1)}</span>
                </Space>
              ) : null
            }
          >
            {(report?.detail?.gateChecks || []).map((g) => (
              <div key={g.key} style={{ marginBottom: 8 }}>
                <Space>
                  {g.passed ? <Tag color="success">通过</Tag> : <Tag color="error">未过</Tag>}
                  <Typography.Text>
                    {g.label}：期望 {g.expected} / 实际 {g.actual}
                  </Typography.Text>
                </Space>
              </div>
            ))}
            {(report?.detail?.dimensions || []).map((d) => (
              <div key={d.name} style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography.Text strong>
                    {d.name}（权重 {Math.round(d.weight * 100)}%）
                  </Typography.Text>
                  <Typography.Text>
                    {d.score.toFixed(0)} → 加权 {d.weightedScore.toFixed(1)}
                  </Typography.Text>
                </div>
                <Progress percent={Math.round(d.score)} strokeColor="#1f4b7a" size="small" />
                <Typography.Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 12 }}>
                  证据：{d.evidence}
                  <br />
                  缺口：{d.gap}
                </Typography.Paragraph>
              </div>
            ))}
          </Card>
        </Col>
      </Row>

      <Card title="结构化面试题库">
        <Typography.Paragraph>{report?.interviewPack?.opening}</Typography.Paragraph>
        <List
          dataSource={report?.interviewPack?.questions || []}
          renderItem={(q, idx) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <Space>
                    <Tag>{idx + 1}</Tag>
                    <Tag color="blue">{q.category}</Tag>
                    {q.question}
                  </Space>
                }
                description={`意图：${q.intent || '-'}${q.relatedRisk ? ` · 关联风险：${q.relatedRisk}` : ''}`}
              />
            </List.Item>
          )}
        />
        <Typography.Title level={5}>收尾提示</Typography.Title>
        <ul>
          {(report?.interviewPack?.closingTips || []).map((t) => (
            <li key={t}>{t}</li>
          ))}
        </ul>
      </Card>
    </Space>
  )
}
