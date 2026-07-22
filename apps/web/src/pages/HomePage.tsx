import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Card, List, Space, Typography, message, Spin } from 'antd'
import { fetchJobs, seedDemo, type Job } from '../api'

export function HomePage() {
  const [jobs, setJobs] = useState<Job[]>([])
  const [loading, setLoading] = useState(true)
  const [seeding, setSeeding] = useState(false)
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
  }, [])

  const onSeed = async () => {
    setSeeding(true)
    try {
      const res = await seedDemo()
      message.success(res.message)
      await load()
      navigate(`/jobs/${res.jobId}`)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } }; message?: string }
      message.error(err.response?.data?.error || err.message || '加载样例失败')
    } finally {
      setSeeding(false)
    }
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div className="panel">
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          快速演示
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          一键加载「Java 后端工程师」岗位 + 4 份脱敏样例简历，并自动完成解析与可解释匹配。
          未配置 OpenAI Key 时自动走 Mock，保证能演示。
        </Typography.Paragraph>
        <Button type="primary" loading={seeding} onClick={onSeed}>
          加载样例数据并开始演示
        </Button>
      </div>

      <Card title="岗位列表" extra={<Button onClick={load}>刷新</Button>}>
        {loading ? (
          <Spin />
        ) : (
          <List
            locale={{ emptyText: '暂无岗位，请先加载样例数据' }}
            dataSource={jobs}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button type="link" key="open" onClick={() => navigate(`/jobs/${item.id}`)}>
                    进入
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={item.title}
                  description={`维度 ${item.dimensions?.length || 0} · 门槛 ${item.hardRequirements?.length || 0}`}
                />
              </List.Item>
            )}
          />
        )}
      </Card>
    </Space>
  )
}
