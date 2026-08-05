import type { DimensionScore } from '../api'

/**
 * SVG-based Radar Chart for visualizing dimension scores.
 * No external dependencies needed.
 */

type RadarChartProps = {
  dimensions: DimensionScore[]
  size?: number
}

export function RadarChart({ dimensions, size = 280 }: RadarChartProps) {
  const cx = size / 2
  const cy = size / 2
  const radius = size / 2 - 50
  const levels = 4
  const angleStep = (Math.PI * 2) / dimensions.length

  // Calculate polygon points for each level
  const getLevelPoints = (level: number) => {
    const r = (radius * level) / levels
    return dimensions
      .map((_, i) => {
        const angle = -Math.PI / 2 + i * angleStep
        return `${cx + r * Math.cos(angle)},${cy + r * Math.sin(angle)}`
      })
      .join(' ')
  }

  // Calculate data points
  const dataPoints = dimensions.map((d, i) => {
    const angle = -Math.PI / 2 + i * angleStep
    const r = (radius * d.score) / 100
    return {
      x: cx + r * Math.cos(angle),
      y: cy + r * Math.sin(angle),
      labelX: cx + (radius + 24) * Math.cos(angle),
      labelY: cy + (radius + 24) * Math.sin(angle),
      name: d.name,
      score: d.score,
    }
  })

  const dataPolygon = dataPoints.map((p) => `${p.x},${p.y}`).join(' ')

  return (
    <svg width={size} height={size} style={{ overflow: 'visible' }}>
      {/* Grid levels */}
      {Array.from({ length: levels }, (_, i) => i + 1).map((level) => (
        <polygon
          key={level}
          points={getLevelPoints(level)}
          fill="none"
          stroke="#e2e8f0"
          strokeWidth={1}
        />
      ))}

      {/* Axis lines */}
      {dimensions.map((_, i) => {
        const angle = -Math.PI / 2 + i * angleStep
        return (
          <line
            key={i}
            x1={cx}
            y1={cy}
            x2={cx + radius * Math.cos(angle)}
            y2={cy + radius * Math.sin(angle)}
            stroke="#e2e8f0"
            strokeWidth={1}
          />
        )
      })}

      {/* Data area */}
      <polygon
        points={dataPolygon}
        fill="rgba(43, 90, 237, 0.15)"
        stroke="#2b5aed"
        strokeWidth={2}
      />

      {/* Data points */}
      {dataPoints.map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r={4} fill="#2b5aed" />
      ))}

      {/* Labels */}
      {dataPoints.map((p, i) => (
        <g key={i}>
          <text
            x={p.labelX}
            y={p.labelY}
            textAnchor={p.labelX > cx + 5 ? 'start' : p.labelX < cx - 5 ? 'end' : 'middle'}
            dominantBaseline="middle"
            fontSize={12}
            fontWeight={600}
            fill="#1e293b"
          >
            {p.name}
          </text>
          <text
            x={p.labelX}
            y={p.labelY + 14}
            textAnchor={p.labelX > cx + 5 ? 'start' : p.labelX < cx - 5 ? 'end' : 'middle'}
            dominantBaseline="middle"
            fontSize={11}
            fill="#64748b"
          >
            {p.score.toFixed(0)}分
          </text>
        </g>
      ))}
    </svg>
  )
}
