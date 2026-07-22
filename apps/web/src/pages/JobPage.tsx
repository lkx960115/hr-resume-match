import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  Card,
  Col,
  Progress,
  Row,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
  Descriptions,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { UploadFile } from 'antd/es/upload/interface'
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

  const columns: ColumnsType<MatchReport> = [
    {
      title: '排名',
      width: 70,
      render: (_, __, index) => index + 1,
    },
    {
      title: '候选人',
      dataIndex: 'candidateName',
      render: (v, row) => v || row.fileName,
    },
    {
      title: '门槛',
      dataIndex: 'passHardGate',
      width: 100,
      render: (v: boolean) => (v ? <Tag color="success">通过</Tag> : <Tag color="warning">未过</Tag>),
    },
    {
      title: '综合分',
      dataIndex: 'totalScore',
      width: 100,
      render: (v?: number) => <span className="score">{v?.toFixed(1) ?? '-'}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      ellipsis: true,
    },
    {
      title: '操作',
      width: 100,
      render: (_, row) => (
        <Button type="link" onClick={() => navigate(`/candidates/${row.candidateId}`)}>
          详情
        </Button>
      ),
    },
  ]

  if (!job) return null

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Text>
        <Link to="/">← 返回</Link>
      </Typography.Text>

      <Card title={job.title}>
        <Typography.Paragraph type="secondary">{job.jdText}</Typography.Paragraph>
        <Descriptions size="small" column={1} title="硬性门槛">
          {job.hardRequirements?.map((h) => (
            <Descriptions.Item key={h.key} label={h.label}>
              {h.operator} {h.value}
            </Descriptions.Item>
          ))}
        </Descriptions>
        <Typography.Text strong>能力权重</Typography.Text>
        <Row gutter={[12, 12]} style={{ marginTop: 8 }}>
          {job.dimensions?.map((d) => (
            <Col span={12} key={d.name}>
              <div className="panel">
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>{d.name}</span>
                  <span>{Math.round(d.weight * 100)}%</span>
                </div>
                <Progress percent={Math.round(d.weight * 100)} showInfo={false} strokeColor="#1f4b7a" />
              </div>
            </Col>
          ))}
        </Row>
      </Card>

      <Card
        title="简历"
        extra={
          <Space>
            <Upload
              multiple
              accept=".pdf,.docx,.txt,.md"
              showUploadList={false}
              fileList={[] as UploadFile[]}
              beforeUpload={(file, fileList) => {
                // 只在本批最后一份时触发，避免 onChange 累积重复上传
                if (file === fileList[fileList.length - 1]) {
                  void onUploadFiles(fileList)
                }
                return false
              }}
            >
              <Button loading={uploading} disabled={matching}>
                上传简历
              </Button>
            </Upload>
            <Button type="primary" loading={matching} disabled={uploading} onClick={onMatch}>
              重新匹配
            </Button>
          </Space>
        }
      >
        <Table
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={candidates}
          columns={[
            { title: '文件', dataIndex: 'fileName' },
            {
              title: '姓名',
              render: (_, r) => r.profile?.name || '-',
            },
            {
              title: '解析',
              dataIndex: 'parseStatus',
              render: (v: string) => <Tag>{v}</Tag>,
            },
            {
              title: '年限',
              render: (_, r) => r.profile?.yearsOfExperience ?? '-',
            },
          ]}
        />
      </Card>

      <Card title="可解释匹配结果榜">
        <Table rowKey="id" size="middle" columns={columns} dataSource={matches} pagination={false} />
      </Card>
    </Space>
  )
}
