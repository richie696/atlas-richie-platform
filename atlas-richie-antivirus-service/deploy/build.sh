#!/usr/bin/env bash
# ==============================================================================
#  build.sh — 一键打包 atlas-richie-antivirus-service
# ==============================================================================
#  用法：
#    ./build.sh                              本机模式：jib:dockerBuild 装到本机 Docker
#    ./build.sh --harbor registry.example.com
#                                             Harbor 模式：jib:build 推送到指定 registry
#    ./build.sh --harbor registry.example.com --tag v1.0.0
#                                             自定义 tag
#    ./build.sh --no-run                     本机模式只打包，不启动容器
#    ./build.sh --module atlas-richie-antivirus-service
#                                             显式指定 Maven 模块（默认就是这个）
#
#  环境变量：
#    HARBOR_USER    Harbor 用户名（默认：admin）
#    HARBOR_PASS    Harbor 密码（默认从 ~/.docker/config.json 读取）
#    NACOS_SERVER_ADDR  启动容器时传给服务的 Nacos 地址（默认：host.docker.internal:8848）
#    DESKTOP_DIR    宿主机桌面目录，用于本地模式挂载（默认：$HOME/Desktop）
#    BASE_IMAGE_DIGEST  覆盖 base image digest（一般不需要）
#
#  示例：
#    # 本地开发
#    ./build.sh
#
#    # 发布到生产 harbor
#    HARBOR_USER=admin HARBOR_PASS='xxx' \
#      ./build.sh --harbor registry.new.richie.cn --tag v1.2.3
# ==============================================================================

set -euo pipefail

# =========================== 默认值 ===========================
MODULE="atlas-richie-antivirus-service"
HARBOR=""
TAG="latest"
AUTO_RUN="true"
ARCH_OVERRIDE=""
SKIP_TESTS="true"

# 与 jib Maven profile 配合：arm64 / x86_64 / windows
# Base image digest 锁定平台，避免 jib 3.5.1 解析 manifest list 失败
#   - arm64: 来自 atlas-richie-antivirus-runtime:clamav-debian13-slim-jre25（已验证）
#   - x86_64: 同一镜像的 amd64 子镜像 digest；如未预先推送，构建会失败
# bash 3.2 (macOS 默认) 不支持关联数组，用函数替代
get_base_digest() {
  case "$1" in
    arm64) echo "7e6f783e1246a13d3a107977f1fcea14d73977b5716251bdf035154dae22fafc" ;;
    amd64) echo "7e6f783e1246a13d3a107977f1fcea14d73977b5716251bdf035154dae22fafc" ;;  # 占位，需后续验证
    *) return 1 ;;
  esac
}

get_jib_profile() {
  case "$1" in
    arm64) echo "jib-platform-arm64" ;;
    amd64) echo "jib-platform-x86_64" ;;
    *) return 1 ;;
  esac
}
# 保留函数仅为向后兼容；新逻辑用 JIB_PLATFORMS 直接传，profile 由 os.arch 自动激活
true

# 本机部署参数
CONTAINER_NAME="atlas-richie-antivirus-service"
LOCAL_IMAGE="atlas-richie-antivirus-service:local"
REST_PORT_HOST=18100
REST_PORT_CONT=9600
GRPC_PORT_HOST=19601
GRPC_PORT_CONT=9601

# =========================== 参数解析 ===========================
print_usage() {
  sed -n '3,30p' "$0"
  exit 0
}

while [[ $# -gt 0 ]]; do
  case $1 in
    --harbor)        HARBOR="$2"; shift 2 ;;
    --tag)           TAG="$2"; shift 2 ;;
    --no-run)        AUTO_RUN="false"; shift ;;
    --module)        MODULE="$2"; shift 2 ;;
    --arch)          ARCH_OVERRIDE="$2"; shift 2 ;;
    --no-skip-tests) SKIP_TESTS="false"; shift ;;
    -h|--help)       print_usage ;;
    *) echo "未知参数: $1" >&2; echo "运行 -h 查看帮助"; exit 1 ;;
  esac
done

# =========================== 架构检测 ===========================
if [[ -n "$ARCH_OVERRIDE" ]]; then
  ARCH="$ARCH_OVERRIDE"
elif [[ "$(uname -m)" == "arm64" || "$(uname -m)" == "aarch64" ]]; then
  ARCH="arm64"
else
  ARCH="amd64"
fi

BASE_DIGEST="${BASE_IMAGE_DIGEST:-$(get_base_digest "$ARCH")}"

if [[ -z "$BASE_DIGEST" ]]; then
  echo "❌ 不支持的架构: $ARCH" >&2
  echo "   可选: arm64, amd64（设置 ARCH 环境变量覆盖）" >&2
  exit 1
fi

# jib.container.platforms 锁定平台，digest 锁定 base image manifest
case "$ARCH" in
  arm64) JIB_PLATFORMS="linux/arm64" ;;
  amd64) JIB_PLATFORMS="linux/amd64" ;;
esac

# =========================== 派生参数 ===========================
if [[ -n "$HARBOR" ]]; then
  # Harbor 模式：推送镜像
  HARBOR="${HARBOR%/}"  # 去掉末尾 /
  FROM_IMAGE="${HARBOR}/platform/atlas-richie-antivirus-runtime@sha256:${BASE_DIGEST}"
  TO_IMAGE="${HARBOR}/platform/${MODULE}:${TAG}"
  JIB_GOAL="jib:build"
  REGISTRY_TYPE="harbor"
  # Harbor 凭据：jib 从 -Djib.to.auth.username / password 读取
  JIB_AUTH_USER="${HARBOR_USER:-admin}"
  JIB_AUTH_PASS="${HARBOR_PASS:-}"
else
  # 本机模式：直接 load 到 Docker daemon
  # Base image 来自本地 registry（localhost:55000），可绕过 harbor 网络
  FROM_IMAGE="localhost:55000/atlas-richie-antivirus-runtime@sha256:${BASE_DIGEST}"
  TO_IMAGE="${LOCAL_IMAGE}"
  JIB_GOAL="jib:dockerBuild"
  REGISTRY_TYPE="local"
fi

# =========================== 打印计划 ===========================
cat <<EOF
╔════════════════════════════════════════════════════════════╗
║         Atlas Richie 一键打包脚本                           ║
╠════════════════════════════════════════════════════════════╣
║ 模块      : ${MODULE}
║ 架构      : ${ARCH}
║ 目标 registry : ${REGISTRY_TYPE}
║ Base image  : ${FROM_IMAGE}
║ 输出 image : ${TO_IMAGE}
║ jib goal   : ${JIB_GOAL}
║ Platform   : ${JIB_PLATFORMS}
EOF
if [[ -n "$HARBOR" ]]; then
  cat <<EOF
║ Harbor    : ${HARBOR} (user=${JIB_AUTH_USER})
EOF
fi
echo "╚════════════════════════════════════════════════════════════╝"
echo

# =========================== 校验 ===========================
ensure_base_image_in_local_registry() {
  local base_local="atlas-richie-antivirus-runtime:clamav-debian13-slim-jre25"
  local base_remote="localhost:55000/atlas-richie-antivirus-runtime:clamav-debian13-slim-jre25"

  if ! curl -s --max-time 3 http://localhost:55000/v2/ >/dev/null 2>&1; then
    echo "📦 local registry (localhost:55000) 未启动，正在启动 registry:2 容器..."
    if ! docker run -d --name local-registry -p 55000:5000 --restart=always registry:2 >/dev/null 2>&1; then
      echo "❌ 启动 local-registry 失败（端口 55000 被占用？手动 docker start local-registry）" >&2
      exit 1
    fi
    for i in 1 2 3 4 5 6 7 8 9 10; do
      if curl -s --max-time 2 http://localhost:55000/v2/ >/dev/null 2>&1; then
        echo "   ✓ registry 已就绪"
        break
      fi
      sleep 1
    done
  fi

  REGISTRY_BODY="$(curl -s --max-time 5 http://localhost:55000/v2/_catalog || true)"
  if echo "$REGISTRY_BODY" | grep -q "atlas-richie-antivirus-runtime"; then
    echo "✓ 本地 registry 已有 base image"
    return 0
  fi

  echo "📥 本地 registry 缺少 base image，开始构建并推送..."
  if ! docker images --format '{{.Repository}}:{{.Tag}}' | grep -q "^${base_local}$"; then
    if ! docker build -t "$base_local" -f "${MODULE}/container/runtime-base/Dockerfile" "${MODULE}/container/runtime-base/"; then
      echo "❌ 构建 base image 失败" >&2
      exit 1
    fi
  else
    echo "   ✓ 本地已有 ${base_local}，跳过 docker build"
  fi

  if ! docker tag "$base_local" "$base_remote"; then
    echo "❌ docker tag 失败" >&2
    exit 1
  fi
  if ! docker push "$base_remote" 2>&1 | tail -3; then
    echo "❌ docker push 到 localhost:55000 失败" >&2
    exit 1
  fi
  echo "   ✓ base image 已就绪: ${base_remote}"
}

if [[ "$REGISTRY_TYPE" == "local" ]]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "❌ docker 命令未找到" >&2
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker daemon 未运行（提示：Docker Desktop 启动后重试）" >&2
    exit 1
  fi
  ensure_base_image_in_local_registry
fi

# =========================== Maven 构建 ===========================
MVN_FLAGS=()
MVN_FLAGS+=("-B")
if [[ "$SKIP_TESTS" == "true" ]]; then
  MVN_FLAGS+=("-DskipTests")
fi
MVN_FLAGS+=("-Djib.skip=false")
MVN_FLAGS+=("-Djib.container.platforms=${JIB_PLATFORMS}")
MVN_FLAGS+=("-Djib.from.image=${FROM_IMAGE}")
MVN_FLAGS+=("-Djib.to.image=${TO_IMAGE}")

if [[ "$REGISTRY_TYPE" == "harbor" ]]; then
  # Harbor 模式：可能需要 insecure + auth
  MVN_FLAGS+=("-Djib.allow.insecure.registries=true")
  if [[ -n "$JIB_AUTH_PASS" ]]; then
    MVN_FLAGS+=("-Djib.to.auth.username=${JIB_AUTH_USER}")
    MVN_FLAGS+=("-Djib.to.auth.password=${JIB_AUTH_PASS}")
  fi
fi

echo "🚀 启动 Maven ${JIB_GOAL} ..."
echo
# 注意：不加 -am，避免从根 pom 找 jib 插件失败；用 -f 直接进入子模块
mvn "${MVN_FLAGS[@]}" -f "${MODULE}/pom.xml" "${JIB_GOAL}"

# =========================== 结果 ===========================
echo
if [[ "$REGISTRY_TYPE" == "harbor" ]]; then
  echo "✅ 镜像已推送到 Harbor: ${TO_IMAGE}"
  echo "   拉取命令: docker pull ${TO_IMAGE}"
  echo "   k8s 部署: helm install --set image.repository=${HARBOR}/platform/${MODULE} --set image.tag=${TAG} ..."
  exit 0
fi

# ===== 本机模式：可选启动容器 =====
echo "✅ 镜像已加载到 Docker: ${TO_IMAGE}"

if [[ "$AUTO_RUN" != "true" ]]; then
  echo "（--no-run 模式：跳过容器启动）"
  exit 0
fi

echo
echo "🚀 启动本地容器 ..."
DESKTOP_DIR="${DESKTOP_DIR:-$HOME/Desktop}"
NACOS_ADDR="${NACOS_SERVER_ADDR:-host.docker.internal:8848}"

# 校验挂载源
if [[ ! -d "$DESKTOP_DIR" ]]; then
  echo "⚠️  桌面目录不存在: $DESKTOP_DIR" >&2
  echo "   跳过容器启动；手动执行：docker run -v \$HOME/Desktop:/data/desktop:ro ..." >&2
  exit 0
fi

# 移除旧容器（如果存在）
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

docker run -d \
  --name "$CONTAINER_NAME" \
  -p ${REST_PORT_HOST}:${REST_PORT_CONT} \
  -p ${GRPC_PORT_HOST}:${GRPC_PORT_CONT} \
  -v "${DESKTOP_DIR}:/data/desktop:ro" \
  -e NACOS_SERVER_ADDR="$NACOS_ADDR" \
  "$TO_IMAGE"

echo
echo "✅ 容器已启动"
echo "   名称  : $CONTAINER_NAME"
echo "   REST  : http://127.0.0.1:${REST_PORT_HOST}/internal/v1/scans"
echo "   gRPC  : 127.0.0.1:${GRPC_PORT_HOST}"
echo "   挂载  : $DESKTOP_DIR → /data/desktop"
echo "   Nacos : $NACOS_ADDR"
echo
echo "查看日志: docker logs -f $CONTAINER_NAME"
echo "停止容器: docker stop $CONTAINER_NAME"
