import { Outlet, Link } from 'react-router-dom'
import { Space, Tag } from 'antd'
import { useEffect, useState } from 'react'
import { fetchLlmStatus } from './api'

export function AppLayout() {
  const [llm, setLlm] = useState<{ provider: string; hasApiKey: boolean; model: string } | null>(null)

  useEffect(() => {
    fetchLlmStatus()
      .then(setLlm)
      .catch(() => setLlm(null))
  }, [])

  return (
    <div className="app-shell">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, marginBottom: 22 }}>
        <div>
          <Link to="/" style={{ textDecoration: 'none' }}>
            <h1 className="brand">简历匹配与结构化面试助手</h1>
          </Link>
          <p className="brand-sub">hr-resume-match · 硬性门槛 + 能力证据 + 权重排序 · 可演示 Demo</p>
        </div>
        <Space>
          {llm ? (
            <Tag color={llm.provider === 'openai' ? 'blue' : 'default'}>
              LLM: {llm.provider} / {llm.model}
            </Tag>
          ) : (
            <Tag>后端未连接</Tag>
          )}
        </Space>
      </div>
      <Outlet />
    </div>
  )
}
