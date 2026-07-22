#!/usr/bin/env bash
# 从仓库根目录加载 .env 后启动后端
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
API_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
  echo "已加载 $ROOT_DIR/.env"
else
  echo "未找到 .env，将使用环境变量 / 默认配置（无 Key 则走 Mock）"
fi

echo "LLM: mode=${LLM_MODE:-auto} base=${OPENAI_BASE_URL:-https://hub.shhdpz.cn} model=${OPENAI_MODEL:-gpt-5.5}"
cd "$API_DIR"
exec mvn spring-boot:run
