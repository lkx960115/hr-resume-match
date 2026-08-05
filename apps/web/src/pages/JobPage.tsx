import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  Card,
  Checkbox,
  Col,
  Progress,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Upload,
  message,
  Empty,
  Tooltip,
  Segmented,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { UploadFile } from 'antd/es/upload/interface'
import {
  ArrowLeftOutlined,
  UploadOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  AuditOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  TrophyOutlined,
  FileTextOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import {
  fetchCandidates,
  fetchJob,
  fetchMatches,
  triggerMatch,
  uploadResumes,
  type Candidate,
  type Job,
  type MatchReport,
} from '../api'

export function JobPage() {
  const { jobId } = useParams()
  const id = Number(jobId)
  const navigate = useNavigate()
  const [job, setJob] = useState<Job | null>(null)
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [matches, setMatches] = useState<MatchReport[]>([])
  const [uploading, setUploading] = useState(false)
  const [matching, setMatching] = useState(false)
  const [gateFilter, setGateFilter] = useState<string>('all') // 'all' | 'passed' | 'failed'
  const [compareIds, setCompareIds] = useState<Set<number>>(new Set())
  const uploadingLock = useRef(false)

  const reload = useCallback(async () => {
    if (!id) return
    const [j, c, m] = await Promise.all([fetchJob(id), fetchCandidates(id), fetchMatches(id)])
    setJob(j)
    setCandidates(c)
    setMatches(m)
  }, [id])

  useEffect(() => {
    reload().catch(() => message.error('加载岗位失败'))
  }, [reload])

  const onUploadFiles = async (files: File[]) => {
    if (!files.length || uploadingLock.current) return
    uploadingLock.current = true
    setUploading(true)
    try {
      await uploadResumes(id, files)
      await reload()
      message.success(`已上传并解析 ${files.length} 份简历`)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } }; message?: string }
      message.error(err.response?.data?.error || err.message || '上传失败')
    } finally {
      uploadingLock.current = false
      setUploading(false)
    }
  }

  const onMatch = async () => {
    setMatching(true)
    try {
      await triggerMatch(id)
      await reload()
      message.success('匹配完成')
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } } }
      message.error(err.response?.data?.error || '匹配失败')
    } finally {
      setMatching(false)
    }
  }

  // 匹配中的进度模拟（后端目前同步处理，后续改为异步后可接入真实进度）
  const matchingProgress = matching ? {
    percent: 90,
    status: 'active' as const,
    tip: '正在分析候选人画像并逐维度打分...',
  } : null

  const getScoreColor = (score?: number) => {
    if (score === undefined) return 'normal'
    if (score >= 75) return 'high'
    if (score >= 50) return 'mid'
    return 'low'
  }

  // PRD R3/Q1: 按门槛筛选
  const filteredMatches = matches.filter((m) => {
    if (gateFilter === 'all') return true
    if (gateFilter === 'passed') return m.passHardGate
    return !m.passHardGate
  })

  const passedCount = matches.filter((m) => m.passHardGate).length
  const failedCount = matches.filter((m) => !m.passHardGate).length

  const columns: ColumnsType<MatchReport> = [
    {
      title: '排名',
      width: 70,
      render: (_, __, index) => {
        const cls = index === 0 ? 'gold' : index === 1 ? 'silver' : index === 2 ? 'bronze' : 'normal'
        return <span className={`rank-badge ${cls}`}>{index + 1}</span>
      },
    },
    {
      title: '候选人',
      dataIndex: 'candidateName',
      render: (v, row) => (
        <Space>
          <span style={{ fontWeight: 600 }}>{v || row.fileName}</span>
        </Space>
      ),
    },
    {
      title: '门槛',
      dataIndex: 'passHardGate',
      width: 100,
      render: (v: boolean) =>
        v ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>通过</Tag>
        ) : (
          <Tag color="warning" icon={<CloseCircleOutlined />}>未过</Tag>
        ),
    },
    {
      title: '综合分',
      dataIndex: 'totalScore',
      width: 180,
      render: (v?: number) => {
        const level = getScoreColor(v)
        return (
          <div className="score-bar-container">
            <div className="score-bar">
              <div
                className={`score-bar-fill ${level}`}
                style={{ width: `${v ?? 0}%` }}
              />
            </div>
            <span className="score-text" style={{ color: v === undefined ? '#94a3b8' : undefined }}>
              {v?.toFixed(1) ?? '-'}
            </span>
          </div>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const color = v === 'completed' ? 'success' : v === 'failed' ? 'error' : 'processing'
        return <Tag color={color}>{v}</Tag>
      },
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      ellipsis: true,
      render: (v?: string) => v || <span style={{ color: '#94a3b8' }}>-</span>,
    },
    {
      title: '对比',
      width: 70,
      render: (_, row) => (
        <Checkbox
          checked={compareIds.has(row.candidateId)}
          disabled={!compareIds.has(row.candidateId) && compareIds.size >= 2}
          onChange={(e) => {
            setCompareIds((prev) => {
              const next = new Set(prev)
              if (e.target.checked) next.add(row.candidateId)
              else next.delete(row.candidateId)
              return next
            })
          }}
        />
      ),
    },
    {
      title: '操作',
      width: 90,
      render: (_, row) => (
        <Button type="link" onClick={() => navigate(`/candidates/${row.candidateId}`)}>
          详情 →
        </Button>
      ),
    },
  ]

  if (!job) {
    return (
      <div style={{ textAlign: 'center', padding: 60 }}>
        <Spin />
      </div>
    )
  }

  return (
    <div className="fade-in">
      {/* Breadcrumb */}
      <Link to="/" style={{ fontSize: 14, color: 'var(--color-text-secondary)', display: 'inline-block', marginBottom: 16 }}>
        <ArrowLeftOutlined /> 返回岗位列表
      </Link>

      {/* Job Header */}
      <Card style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div style={{ flex: 1 }}>
            <Typography.Title level={3} style={{ margin: '0 0 8px' }}>
              <TrophyOutlined style={{ color: 'var(--color-primary)', marginRight: 8 }} />
              {job.title}
            </Typography.Title>
            {job.jdText && (
              <Typography.Paragraph type="secondary" style={{ margin: 0, maxWidth: 800 }}>
                {job.jdText}
              </Typography.Paragraph>
            )}
          </div>
        </div>

        {/* Hard Requirements */}
        <div style={{ marginTop: 20 }}>
          <Typography.Text strong style={{ fontSize: 15, display: 'block', marginBottom: 12 }}>
            <SafetyCertificateOutlined style={{ color: 'var(--color-primary)', marginRight: 6 }} />
            硬性门槛
          </Typography.Text>
          <Row gutter={[8, 8]}>
            {job.hardRequirements?.map((h) => (
              <Col key={h.key}>
                <Tooltip title={h.onFail || `${h.operator} ${h.value}`}>
                  <Tag style={{ padding: '4px 12px', fontSize: 13, borderRadius: 6 }}>
                    {h.label}: {h.operator} {h.value}
                  </Tag>
                </Tooltip>
              </Col>
            ))}
          </Row>
        </div>

        {/* Dimension Weights */}
        <div style={{ marginTop: 20 }}>
          <Typography.Text strong style={{ fontSize: 15, display: 'block', marginBottom: 12 }}>
            <AuditOutlined style={{ color: 'var(--color-accent)', marginRight: 6 }} />
            能力维度权重
          </Typography.Text>
          <Row gutter={[12, 12]}>
            {job.dimensions?.map((d) => (
              <Col span={12} key={d.name}>
                <div className="panel" style={{ padding: '12px 16px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <span style={{ fontWeight: 500, fontSize: 14 }}>{d.name}</span>
                    <span style={{ fontWeight: 700, color: 'var(--color-primary)', fontSize: 14 }}>
                      {Math.round(d.weight * 100)}%
                    </span>
                  </div>
                  <Progress
                    percent={Math.round(d.weight * 100)}
                    showInfo={false}
                    strokeColor={{ from: '#2b5aed', to: '#6366f1' }}
                    size="small"
                  />
                </div>
              </Col>
            ))}
          </Row>
        </div>
      </Card>

      {/* Resume Upload */}
      <Card
        style={{ marginBottom: 20 }}
        title={
          <Space>
            <FileTextOutlined style={{ color: 'var(--color-primary)' }} />
            <span>简历管理</span>
            <Tag>{candidates.length} 份</Tag>
          </Space>
        }
        extra={
          <Space>
            <div style={{ display: 'inline-block' }}>
              <Upload
                multiple
                accept=".pdf,.docx,.doc,.txt,.md"
                showUploadList={false}
                fileList={[] as UploadFile[]}
                beforeUpload={(file, fileList) => {
                  const ext = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
                  const allowed = ['.pdf', '.docx', '.doc', '.txt', '.md']
                  if (!allowed.includes(ext)) {
                    message.error(`不支持的文件格式：${file.name}，请上传 PDF / DOCX / DOC / TXT / MD`)
                    return Upload.LIST_IGNORE
                  }
                  if (file === fileList[fileList.length - 1]) {
                    void onUploadFiles(fileList)
                  }
                  return false
                }}
              >
                <Button icon={<UploadOutlined />} loading={uploading} disabled={matching}>
                  上传简历
                </Button>
              </Upload>
              <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 4, lineHeight: 1.4 }}>
                支持 PDF / DOCX / DOC / TXT / MD，单个 ≤ 20MB
              </div>
            </div>
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              loading={matching}
              disabled={uploading || candidates.length === 0}
              onClick={onMatch}
            >
              重新匹配
            </Button>
          </Space>
        }
      >
        {candidates.length === 0 ? (
          <Empty
            description={
              <span style={{ color: 'var(--color-text-secondary)' }}>
                暂无简历，点击「上传简历」按钮批量上传 PDF / DOCX / DOC / TXT / MD 文件
              </span>
            }
          />
        ) : (
          <Table
            size="small"
            rowKey="id"
            pagination={false}
            dataSource={candidates}
            columns={[
              {
                title: '文件名',
                dataIndex: 'fileName',
                render: (v: string) => (
                  <span style={{ fontWeight: 500 }}>{v}</span>
                ),
              },
              {
                title: '姓名',
                width: 120,
                render: (_, r) => r.profile?.name || <span style={{ color: '#94a3b8' }}>-</span>,
              },
              {
                title: '学历',
                width: 100,
                render: (_, r) => r.profile?.education || <span style={{ color: '#94a3b8' }}>-</span>,
              },
              {
                title: '年限',
                width: 80,
                render: (_, r) => r.profile?.yearsOfExperience ?? <span style={{ color: '#94a3b8' }}>-</span>,
              },
              {
                title: '解析状态',
                dataIndex: 'parseStatus',
                width: 100,
                render: (v: string) => {
                  const done = v === 'READY' || v === 'parsed'
                  const failed = v === 'FAILED' || v === 'failed'
                  const color = done ? 'success' : failed ? 'error' : 'processing'
                  const label = done ? '已解析' : failed ? '失败' : v || '处理中'
                  return <Tag color={color}>{label}</Tag>
                },
              },
            ]}
          />
        )}
      </Card>

      {/* Match Results */}
      <Card
        title={
          <Space>
            <TrophyOutlined style={{ color: 'var(--color-warning)' }} />
            <span>可解释匹配结果榜</span>
            {matches.length > 0 && <Tag color="blue">{matches.length} 人</Tag>}
          </Space>
        }
        extra={
          matches.length > 0 && (
            <Segmented
              size="small"
              value={gateFilter}
              onChange={(v) => setGateFilter(v as string)}
              options={[
                { label: `全部 (${matches.length})`, value: 'all' },
                { label: `已过门槛 (${passedCount})`, value: 'passed' },
                { label: `未过门槛 (${failedCount})`, value: 'failed' },
              ]}
            />
          )
        }
      >
        {matchingProgress && (
          <div style={{ padding: '24px 0', textAlign: 'center' }}>
            <Progress
              percent={matchingProgress.percent}
              status={matchingProgress.status}
              strokeColor="var(--color-primary)"
              style={{ maxWidth: 400, margin: '0 auto' }}
            />
            <div style={{ marginTop: 12, color: 'var(--color-text-secondary)', fontSize: 14 }}>
              {matchingProgress.tip}
            </div>
          </div>
        )}
        {!matching && matches.length === 0 ? (
          <Empty
            description={
              <span style={{ color: 'var(--color-text-secondary)' }}>
                暂无匹配结果，请上传简历后点击「重新匹配」
              </span>
            }
          />
        ) : (
          <>
            <Table
              rowKey="id"
              size="middle"
              columns={columns}
              dataSource={filteredMatches}
              pagination={false}
            />
            {compareIds.size >= 1 && (
              <div style={{ marginTop: 16, textAlign: 'center' }}>
                <Button
                  type="primary"
                  icon={<SwapOutlined />}
                  disabled={compareIds.size < 2}
                  onClick={() => {
                    const ids = Array.from(compareIds)
                    navigate(`/compare/${ids[0]}/${ids[1]}`)
                  }}
                >
                  对比选中 ({compareIds.size}/2)
                </Button>
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  )
}
