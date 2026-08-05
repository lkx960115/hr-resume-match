import { Outlet, Link } from 'react-router-dom'
import { Badge, Tag, Tooltip, Dropdown, message } from 'antd'
import type { MenuProps } from 'antd'
import { RobotOutlined, GithubOutlined, DownOutlined, ApiOutlined, ThunderboltOutlined, ToolOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { fetchLlmStatus, setLlmMode } from './api'

export function AppLayout() {
  const [llm, setLlm] = useState<{ mode: string; provider: string; hasApiKey: boolean; model: string } | null>(null)

  const refreshStatus = () => {
    fetchLlmStatus()
      .then(setLlm)
      .catch(() => setLlm(null))
  }

  useEffect(() => {
    refreshStatus()
  }, [])

  const connected = llm !== null

  const modeItems: MenuProps['items'] = [
    {
      key: 'auto',
      icon: <ApiOutlined />,
      label: 'Auto（自动检测）',
      onClick: async () => {
        try {
          const s = await setLlmMode('auto')
          setLlm(s)
          message.success('已切换为 Auto 模式')
        } catch { message.error('切换失败') }
      },
    },
    {
      key: 'openai',
      icon: <ThunderboltOutlined />,
      label: 'OpenAI（真实 LLM）',
      disabled: !llm?.hasApiKey,
      onClick: async () => {
        try {
          const s = await setLlmMode('openai')
          setLlm(s)
          message.success('已切换为 OpenAI 模式')
        } catch { message.error('切换失败') }
      },
    },
    {
      key: 'mock',
      icon: <ToolOutlined />,
      label: 'Mock（规则模式）',
      onClick: async () => {
        try {
          const s = await setLlmMode('mock')
          setLlm(s)
          message.success('已切换为 Mock 模式')
        } catch { message.error('切换失败') }
      },
    },
  ]

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* Navigation Bar */}
      <nav className="navbar">
        <div className="navbar-inner">
          <Link to="/" className="navbar-logo">
            <div className="navbar-logo-icon">
              <RobotOutlined />
            </div>
            <div>
              <div className="navbar-brand">ResumeMatch Pro</div>
              <div className="navbar-brand-sub">简历匹配 · 结构化面试助手</div>
            </div>
          </Link>
          <div className="navbar-right">
            {connected ? (
              <Dropdown menu={{ items: modeItems }} trigger={['click']} placement="bottomRight">
                <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <Badge status={llm?.hasApiKey ? 'success' : 'warning'} />
                  <Tag color={llm?.provider === 'openai' ? 'blue' : 'default'} style={{ margin: 0 }}>
                    {llm?.provider === 'openai' ? 'AI 已连接' : 'Mock 模式'} · {llm?.model}
                  </Tag>
                  <DownOutlined style={{ fontSize: 10, color: 'var(--color-text-tertiary)' }} />
                </div>
              </Dropdown>
            ) : (
              <Tooltip title="请先启动后端服务">
                <Badge status="error" />
                <Tag color="error" style={{ margin: 0 }}>后端未连接</Tag>
              </Tooltip>
            )}
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="main-container">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="footer">
        <div className="footer-inner">
          <span>ResumeMatch Pro · 简历匹配与结构化面试助手 Demo</span>
          <div className="footer-links">
            <span>Powered by React + Spring Boot + LLM</span>
            <a href="https://github.com" target="_blank" rel="noreferrer">
              <GithubOutlined /> GitHub
            </a>
          </div>
        </div>
      </footer>
    </div>
  )
}
