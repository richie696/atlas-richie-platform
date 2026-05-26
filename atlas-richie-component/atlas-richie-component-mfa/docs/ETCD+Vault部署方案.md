# ETCD + Vault 部署方案

## 📋 概述

本文档提供ETCD + HashiCorp Vault组合方案的完整部署指南，包括硬件要求、部署步骤和docker-compose配置。

## 🖥️ 硬件要求

### ETCD 硬件要求

#### 生产环境（推荐配置）

| 资源 | 最小配置 | 推荐配置 | 高负载配置 |
|------|---------|---------|-----------|
| **CPU** | 2-4 核 | 4-8 核 | 8-16 核 |
| **内存** | 8 GB | 16 GB | 32-64 GB |
| **磁盘** | 50 IOPS<br>7200 RPM | 500 IOPS<br>SSD | 1000+ IOPS<br>NVMe SSD |
| **磁盘容量** | 50 GB | 100 GB | 200+ GB |
| **网络带宽** | 1 GbE | 1 GbE | 10 GbE |
| **集群节点数** | 3 节点（最小） | 5 节点（推荐） | 5-7 节点 |

#### 开发/测试环境

| 资源 | 配置 |
|------|------|
| **CPU** | 2 核 |
| **内存** | 4 GB |
| **磁盘** | 20 GB（SSD推荐） |
| **网络带宽** | 1 GbE |
| **集群节点数** | 1 节点（单机）或 3 节点 |

**重要提示**：
- 生产环境必须使用**奇数节点**（3、5、7）以确保集群一致性
- 磁盘性能对ETCD至关重要，**强烈推荐使用SSD**
- 网络延迟应保持在1ms以内

### Vault 硬件要求

#### 生产环境（推荐配置）

| 资源 | 小型部署 | 大型部署 |
|------|---------|---------|
| **CPU** | 2-4 核 | 4-8 核 |
| **内存** | 8-16 GB | 32-64 GB |
| **磁盘** | 100+ GB<br>3000+ IOPS<br>75+ MB/s | 200+ GB<br>10000+ IOPS<br>250+ MB/s |
| **网络带宽** | 1 GbE | 10 GbE |
| **集群节点数** | 3 节点（HA） | 5 节点（HA） |

#### 开发/测试环境

| 资源 | 配置 |
|------|------|
| **CPU** | 2 核 |
| **内存** | 4 GB |
| **磁盘** | 50 GB |
| **网络带宽** | 1 GbE |
| **集群节点数** | 1 节点（单机） |

## 🐳 Docker Compose 部署方案

### 方案1：开发/测试环境（单节点）

适用于开发、测试或小规模部署。

#### docker-compose.yml

```yaml
version: '3.8'

services:
  # ==================== ETCD 服务 ====================
  etcd:
    image: quay.io/coreos/etcd:v3.6.7
    container_name: etcd
    environment:
      - ETCD_NAME=etcd1
      - ETCD_DATA_DIR=/etcd-data
      - ETCD_LISTEN_CLIENT_URLS=http://0.0.0.0:2379
      - ETCD_ADVERTISE_CLIENT_URLS=http://etcd:2379
      - ETCD_LISTEN_PEER_URLS=http://0.0.0.0:2380
      - ETCD_INITIAL_ADVERTISE_PEER_URLS=http://etcd:2380
      - ETCD_INITIAL_CLUSTER=etcd1=http://etcd:2380
      - ETCD_INITIAL_CLUSTER_TOKEN=etcd-cluster-1
      - ETCD_INITIAL_CLUSTER_STATE=new
      # 认证配置（开发环境可关闭）
      # - ETCD_ROOT_PASSWORD=changeme
    ports:
      - "2379:2379"  # 客户端端口
      - "2380:2380"  # 节点间通信端口
    volumes:
      - etcd-data:/etcd-data
    networks:
      - kms-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 10s
      timeout: 5s
      retries: 3

  # ==================== Vault 服务 ====================
  vault:
    image: hashicorp/vault:1.21.2
    container_name: vault
    environment:image.png
      - VAULT_ADDR=http://0.0.0.0:8200
      - VAULT_DEV_ROOT_TOKEN_ID=myroot  # 仅用于开发环境
      - VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200
      # 使用ETCD作为存储后端
      - VAULT_STORAGE_TYPE=etcd
      - VAULT_ETCD_ADDRESS=http://etcd:2379
      - VAULT_ETCD_API=v3
      - VAULT_ETCD_PATH=/vault/
      - VAULT_ETCD_HA_ENABLED=false  # 单节点模式
    ports:
      - "8200:8200"
    volumes:
      - vault-data:/vault/data
      - vault-config:/vault/config
    networks:
      - kms-network
    depends_on:
      etcd:
        condition: service_healthy
    restart: unless-stopped
    cap_add:
      - IPC_LOCK
    command: vault server -config=/vault/config/vault.hcl

volumes:
  etcd-data:
    driver: local
  vault-data:
    driver: local
  vault-config:
    driver: local

networks:
  kms-network:
    driver: bridge
```

#### Vault 配置文件（vault.hcl）

创建 `vault-config/vault.hcl`：

```hcl
storage "etcd" {
  address  = "http://etcd:2379"
  etcd_api = "v3"
  path     = "/vault/"        
  ha_enabled = false
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1  # 开发环境禁用TLS，生产环境必须启用
}

api_addr = "http://vault:8200"
cluster_addr = "http://vault:8201"
ui = true  # 启用Web UI，访问地址：http://localhost:8200/ui
```

#### 启动命令

```bash
# 1. 创建配置目录
mkdir -p vault-config

# 2. 创建vault.hcl配置文件
cat > vault-config/vault.hcl << 'EOF'
storage "etcd" {
  address  = "http://etcd:2379"
  etcd_api = "v3"
  path     = "/vault/"
  ha_enabled = false
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1
}

api_addr = "http://vault:8200"
cluster_addr = "http://vault:8201"
ui = true
EOF

# 3. 启动服务
docker-compose up -d

# 4. 检查服务状态
docker-compose ps

# 5. 初始化Vault（仅首次部署）
docker exec -it vault vault operator init -key-shares=5 -key-threshold=3

# 6. 解封Vault（使用初始化时生成的unseal keys）
docker exec -it vault vault operator unseal <unseal-key-1>
docker exec -it vault vault operator unseal <unseal-key-2>
docker exec -it vault vault operator unseal <unseal-key-3>
```

### 方案2：生产环境（高可用集群）

适用于生产环境，提供高可用性和数据持久化。

#### docker-compose.yml（3节点ETCD + 3节点Vault）

```yaml
version: '3.8'

services:
  # ==================== ETCD 集群（3节点）====================
  etcd1:
    image: quay.io/coreos/etcd:v3.5.9
    container_name: etcd1
    environment:
      - ETCD_NAME=etcd1
      - ETCD_DATA_DIR=/etcd-data
      - ETCD_LISTEN_CLIENT_URLS=http://0.0.0.0:2379
      - ETCD_ADVERTISE_CLIENT_URLS=http://etcd1:2379
      - ETCD_LISTEN_PEER_URLS=http://0.0.0.0:2380
      - ETCD_INITIAL_ADVERTISE_PEER_URLS=http://etcd1:2380
      - ETCD_INITIAL_CLUSTER=etcd1=http://etcd1:2380,etcd2=http://etcd2:2380,etcd3=http://etcd3:2380
      - ETCD_INITIAL_CLUSTER_TOKEN=etcd-cluster-1
      - ETCD_INITIAL_CLUSTER_STATE=new
      # TLS配置（生产环境必须启用）
      - ETCD_CLIENT_CERT_AUTH=true
      - ETCD_PEER_CLIENT_CERT_AUTH=true
      - ETCD_TRUSTED_CA_FILE=/etc/etcd/ssl/ca.pem
      - ETCD_CERT_FILE=/etc/etcd/ssl/etcd1.pem
      - ETCD_KEY_FILE=/etc/etcd/ssl/etcd1-key.pem
      - ETCD_PEER_TRUSTED_CA_FILE=/etc/etcd/ssl/ca.pem
      - ETCD_PEER_CERT_FILE=/etc/etcd/ssl/etcd1.pem
      - ETCD_PEER_KEY_FILE=/etc/etcd/ssl/etcd1-key.pem
    ports:
      - "2379:2379"
      - "2380:2380"
    volumes:
      - etcd1-data:/etcd-data
      - ./etcd-ssl:/etc/etcd/ssl:ro
    networks:
      - kms-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "etcdctl", "--endpoints=http://localhost:2379", "endpoint", "health"]
      interval: 10s
      timeout: 5s
      retries: 3

  etcd2:
    image: quay.io/coreos/etcd:v3.5.9
    container_name: etcd2
    environment:
      - ETCD_NAME=etcd2
      - ETCD_DATA_DIR=/etcd-data
      - ETCD_LISTEN_CLIENT_URLS=http://0.0.0.0:2379
      - ETCD_ADVERTISE_CLIENT_URLS=http://etcd2:2379
      - ETCD_LISTEN_PEER_URLS=http://0.0.0.0:2380
      - ETCD_INITIAL_ADVERTISE_PEER_URLS=http://etcd2:2380
      - ETCD_INITIAL_CLUSTER=etcd1=http://etcd1:2380,etcd2=http://etcd2:2380,etcd3=http://etcd3:2380
      - ETCD_INITIAL_CLUSTER_TOKEN=etcd-cluster-1
      - ETCD_INITIAL_CLUSTER_STATE=new
      - ETCD_CLIENT_CERT_AUTH=true
      - ETCD_PEER_CLIENT_CERT_AUTH=true
      - ETCD_TRUSTED_CA_FILE=/etc/etcd/ssl/ca.pem
      - ETCD_CERT_FILE=/etc/etcd/ssl/etcd2.pem
      - ETCD_KEY_FILE=/etc/etcd/ssl/etcd2-key.pem
      - ETCD_PEER_TRUSTED_CA_FILE=/etc/etcd/ssl/ca.pem
      - ETCD_PEER_CERT_FILE=/etc/etcd/ssl/etcd2.pem
      - ETCD_PEER_KEY_FILE=/etc/etcd/ssl/etcd2-key.pem
    ports:
      - "2479:2379"
      - "2480:2380"
    volumes:
      - etcd2-data:/etcd-data
      - ./etcd-ssl:/etc/etcd/ssl:ro
    networks:
      - kms-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "etcdctl", "--endpoints=http://localhost:2379", "endpoint", "health"]
      interval: 10s
      timeout: 5s
      retries: 3

  etcd3:
    image: quay.io/coreos/etcd:v3.5.9
    container_name: etcd3
    environment:
      - ETCD_NAME=etcd3
      - ETCD_DATA_DIR=/etcd-data
      - ETCD_LISTEN_CLIENT_URLS=http://0.0.0.0:2379
      - ETCD_ADVERTISE_CLIENT_URLS=http://etcd3:2379
      - ETCD_LISTEN_PEER_URLS=http://0.0.0.0:2380
      - ETCD_INITIAL_ADVERTISE_PEER_URLS=http://etcd3:2380
      - ETCD_INITIAL_CLUSTER=etcd1=http://etcd1:2380,etcd2=http://etcd2:2380,etcd3=http://etcd3:2380
      - ETCD_INITIAL_CLUSTER_TOKEN=etcd-cluster-1
      - ETCD_INITIAL_CLUSTER_STATE=new
      - ETCD_CLIENT_CERT_AUTH=true
      - ETCD_PEER_CLIENT_CERT_AUTH=true
      - ETCD_TRUSTED_CA_FILE=/etc/etcd/ssl/ca.pem
      - ETCD_CERT_FILE=/etc/etcd/ssl/etcd3.pem
      - ETCD_KEY_FILE=/etc/etcd/ssl/etcd3-key.pem
      - ETCD_PEER_TRUSTED_CA_FILE=/etc/etcd/ssl/ca.pem
      - ETCD_PEER_CERT_FILE=/etc/etcd/ssl/etcd3.pem
      - ETCD_PEER_KEY_FILE=/etc/etcd/ssl/etcd3-key.pem
    ports:
      - "2579:2379"
      - "2580:2380"
    volumes:
      - etcd3-data:/etcd-data
      - ./etcd-ssl:/etc/etcd/ssl:ro
    networks:
      - kms-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "etcdctl", "--endpoints=http://localhost:2379", "endpoint", "health"]
      interval: 10s
      timeout: 5s
      retries: 3

  # ==================== Vault 集群（3节点）====================
  vault1:
    image: hashicorp/vault:1.15.2
    container_name: vault1
    environment:
      - VAULT_ADDR=http://0.0.0.0:8200
      - VAULT_CLUSTER_ADDR=http://vault1:8201
      - VAULT_API_ADDR=http://vault1:8200
    ports:
      - "8200:8200"
      - "8201:8201"
    volumes:
      - vault1-data:/vault/data
      - vault1-config:/vault/config
      - ./vault-ssl:/vault/ssl:ro
    networks:
      - kms-network
    depends_on:
      - etcd1
      - etcd2
      - etcd3
    restart: unless-stopped
    cap_add:
      - IPC_LOCK
    command: vault server -config=/vault/config/vault.hcl

  vault2:
    image: hashicorp/vault:1.15.2
    container_name: vault2
    environment:
      - VAULT_ADDR=http://0.0.0.0:8200
      - VAULT_CLUSTER_ADDR=http://vault2:8201
      - VAULT_API_ADDR=http://vault2:8200
    ports:
      - "8202:8200"
      - "8203:8201"
    volumes:
      - vault2-data:/vault/data
      - vault2-config:/vault/config
      - ./vault-ssl:/vault/ssl:ro
    networks:
      - kms-network
    depends_on:
      - etcd1
      - etcd2
      - etcd3
    restart: unless-stopped
    cap_add:
      - IPC_LOCK
    command: vault server -config=/vault/config/vault.hcl

  vault3:
    image: hashicorp/vault:1.15.2
    container_name: vault3
    environment:
      - VAULT_ADDR=http://0.0.0.0:8200
      - VAULT_CLUSTER_ADDR=http://vault3:8201
      - VAULT_API_ADDR=http://vault3:8200
    ports:
      - "8204:8200"
      - "8205:8201"
    volumes:
      - vault3-data:/vault/data
      - vault3-config:/vault/config
      - ./vault-ssl:/vault/ssl:ro
    networks:
      - kms-network
    depends_on:
      - etcd1
      - etcd2
      - etcd3
    restart: unless-stopped
    cap_add:
      - IPC_LOCK
    command: vault server -config=/vault/config/vault.hcl

volumes:
  etcd1-data:
    driver: local
  etcd2-data:
    driver: local
  etcd3-data:
    driver: local
  vault1-data:
    driver: local
  vault2-data:
    driver: local
  vault3-data:
    driver: local
  vault1-config:
    driver: local
  vault2-config:
    driver: local
  vault3-config:
    driver: local

networks:
  kms-network:
    driver: bridge
```

#### Vault 配置文件（生产环境，vault.hcl）

```hcl
storage "etcd" {
  address     = "http://etcd1:2379,http://etcd2:2379,http://etcd3:2379"
  etcd_api    = "v3"
  path        = "/vault/"
  ha_enabled  = true
  # TLS配置（生产环境必须启用）
  # tls_ca_file   = "/vault/ssl/ca.pem"
  # tls_cert_file = "/vault/ssl/vault.pem"
  # tls_key_file  = "/vault/ssl/vault-key.pem"
}

listener "tcp" {
  address       = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"
  # 生产环境必须启用TLS
  tls_cert_file = "/vault/ssl/vault.pem"
  tls_key_file  = "/vault/ssl/vault-key.pem"
  tls_min_version = "tls12"
}

api_addr = "http://vault1:8200"
cluster_addr = "http://vault1:8201"
ui = true
log_level = "INFO"
log_format = "json"
```

## 🔐 安全配置

### 1. 生成TLS证书（生产环境）

#### ETCD证书

```bash
# 创建证书目录
mkdir -p etcd-ssl

# 生成CA证书
openssl genrsa -out etcd-ssl/ca-key.pem 2048
openssl req -x509 -new -nodes -key etcd-ssl/ca-key.pem \
  -days 3650 -out etcd-ssl/ca.pem \
  -subj "/CN=etcd-ca"

# 为每个ETCD节点生成证书
for i in 1 2 3; do
  openssl genrsa -out etcd-ssl/etcd${i}-key.pem 2048
  openssl req -new -key etcd-ssl/etcd${i}-key.pem \
    -out etcd-ssl/etcd${i}.csr \
    -subj "/CN=etcd${i}"
  openssl x509 -req -in etcd-ssl/etcd${i}.csr \
    -CA etcd-ssl/ca.pem -CAkey etcd-ssl/ca-key.pem \
    -CAcreateserial -out etcd-ssl/etcd${i}.pem \
    -days 3650
done
```

#### Vault证书

```bash
# 创建证书目录
mkdir -p vault-ssl

# 生成CA证书
openssl genrsa -out vault-ssl/ca-key.pem 2048
openssl req -x509 -new -nodes -key vault-ssl/ca-key.pem \
  -days 3650 -out vault-ssl/ca.pem \
  -subj "/CN=vault-ca"

# 生成Vault证书
openssl genrsa -out vault-ssl/vault-key.pem 2048
openssl req -new -key vault-ssl/vault-key.pem \
  -out vault-ssl/vault.csr \
  -subj "/CN=vault"
openssl x509 -req -in vault-ssl/vault.csr \
  -CA vault-ssl/ca.pem -CAkey vault-ssl/ca-key.pem \
  -CAcreateserial -out vault-ssl/vault.pem \
  -days 3650 \
  -extensions v3_req -extfile <(echo "[v3_req]"; echo "subjectAltName=DNS:vault,DNS:vault1,DNS:vault2,DNS:vault3")
```

### 2. ETCD认证配置

```bash
# 创建ETCD用户
docker exec -it etcd1 etcdctl user add mfa-service
# 输入密码

# 创建角色
docker exec -it etcd1 etcdctl role add mfa-kms-role

# 授予权限
docker exec -it etcd1 etcdctl role grant-permission mfa-kms-role \
  --path /vault/* \
  --prefix=true \
  readwrite

# 将角色分配给用户
docker exec -it etcd1 etcdctl user grant-role mfa-service mfa-kms-role

# 启用认证
docker exec -it etcd1 etcdctl auth enable
```

### 3. Vault初始化

```bash
# 初始化Vault（仅首次部署）
docker exec -it vault1 vault operator init \
  -key-shares=5 \
  -key-threshold=3 \
  -stored-shares=0

# 保存输出的Unseal Keys和Root Token（非常重要！）

# 解封所有Vault节点
for vault_node in vault1 vault2 vault3; do
  docker exec -it $vault_node vault operator unseal <unseal-key-1>
  docker exec -it $vault_node vault operator unseal <unseal-key-2>
  docker exec -it $vault_node vault operator unseal <unseal-key-3>
done

# 登录Vault
docker exec -it vault1 vault login <root-token>
```

## 📝 部署步骤

### 开发/测试环境

```bash
# 1. 创建项目目录
mkdir -p vault-kms-deployment
cd vault-kms-deployment

# 2. 创建docker-compose.yml（使用方案1）

# 3. 创建Vault配置文件
mkdir -p vault-config
# 创建vault.hcl（参考上面的配置）

# 4. 启动服务
docker-compose up -d

# 5. 检查服务状态
docker-compose ps
docker-compose logs -f

# 6. 初始化Vault（仅首次）
docker exec -it vault vault operator init -key-shares=5 -key-threshold=3

# 7. 解封Vault
docker exec -it vault vault operator unseal <unseal-key-1>
docker exec -it vault vault operator unseal <unseal-key-2>
docker exec -it vault vault operator unseal <unseal-key-3>

# 8. 验证部署
docker exec -it vault vault status
docker exec -it etcd etcdctl endpoint health
```

### 生产环境

```bash
# 1. 准备证书
# 按照"安全配置"章节生成TLS证书

# 2. 创建docker-compose.yml（使用方案2）

# 3. 创建Vault配置文件（每个节点）
# 为vault1、vault2、vault3分别创建配置文件

# 4. 启动ETCD集群
docker-compose up -d etcd1 etcd2 etcd3

# 5. 等待ETCD集群就绪
docker-compose logs -f etcd1

# 6. 配置ETCD认证
# 按照"安全配置"章节配置ETCD认证

# 7. 启动Vault集群
docker-compose up -d vault1 vault2 vault3

# 8. 初始化Vault（仅首次）
docker exec -it vault1 vault operator init \
  -key-shares=5 \
  -key-threshold=3

# 9. 解封所有Vault节点
# 按照"安全配置"章节解封Vault

# 10. 验证部署
docker exec -it vault1 vault status
docker exec -it etcd1 etcdctl endpoint health --endpoints=http://etcd1:2379,http://etcd2:2379,http://etcd3:2379
```

## 🔍 验证和监控

### 检查ETCD集群状态

```bash
# 检查集群健康状态
docker exec -it etcd1 etcdctl endpoint health \
  --endpoints=http://etcd1:2379,http://etcd2:2379,http://etcd3:2379

# 查看集群成员
docker exec -it etcd1 etcdctl member list

# 查看集群状态
docker exec -it etcd1 etcdctl endpoint status \
  --endpoints=http://etcd1:2379,http://etcd2:2379,http://etcd3:2379
```

### 检查Vault状态

```bash
# 检查Vault状态
docker exec -it vault1 vault status

# 检查Vault集群状态
docker exec -it vault1 vault operator members

# 访问Vault UI
# 浏览器打开：http://localhost:8200/ui
```

## 🖥️ 可视化管理界面

### HashiCorp Vault Web UI（官方）

**Vault提供官方的Web UI**，功能完整，推荐使用。

#### 访问方式

1. **开发/测试环境**：
   - 访问地址：`http://localhost:8200/ui`
   - 使用Root Token登录（开发环境：`myroot`）

2. **生产环境**：
   - 访问地址：`https://vault-host:8200/ui`（需配置TLS）
   - 使用Root Token或用户Token登录

#### 启用UI

在Vault配置文件中设置 `ui = true`（已在docker-compose配置中启用）：

```hcl
ui = true
```

#### UI功能

- ✅ **密钥管理**：创建、查看、更新、删除密钥
- ✅ **策略管理**：创建和管理访问策略
- ✅ **认证方法**：配置多种认证方式（Token、LDAP、OIDC等）
- ✅ **审计日志**：查看操作审计日志
- ✅ **集群管理**：查看集群状态和成员信息
- ✅ **密钥引擎**：启用和管理各种密钥引擎（KV、Transit、PKI等）

#### 使用示例

```bash
# 1. 启动服务后，访问UI
# 浏览器打开：http://localhost:8200/ui

# 2. 使用Root Token登录
# Token: <root-token>

# 3. 在UI中可以：
#    - 浏览密钥路径
#    - 创建新的密钥
#    - 配置策略
#    - 查看审计日志
```

### ETCD 可视化管理工具

**ETCD本身没有官方Web UI**，但可以使用第三方工具。

#### 方案1：ETCD Manager（推荐）

**ETCD Manager** 是一个跨平台的GUI客户端，支持桌面和Web版本。

**特性**：
- ✅ 树形和列表视图
- ✅ 密钥的增删改查
- ✅ TTL管理
- ✅ 版本管理和回滚
- ✅ 多集群管理
- ✅ 认证支持
- ✅ 租约和用户管理
- ✅ 实时监控

**Docker部署**（可选，添加到docker-compose.yml）：

```yaml
  etcd-manager:
    image: icellmobilsoft/etcdmanager:latest
    container_name: etcd-manager
    ports:
      - "8080:8080"
    environment:
      - ETCD_ENDPOINTS=http://etcd:2379
      # 如果ETCD启用了认证
      # - ETCD_USERNAME=mfa-service
      # - ETCD_PASSWORD=${ETCD_PASSWORD}
    networks:
      - kms-network
    depends_on:
      - etcd
    restart: unless-stopped
```

**访问方式**：
- Web版本：`http://localhost:8080`
- 桌面版本：从 [etcdmanager.io](https://etcdmanager.io/) 下载

#### 方案2：ETCD Browser（轻量级）

**ETCD Browser** 是一个轻量级的Web工具，适合简单查看和编辑。

**Docker部署**（可选）：

```yaml
  etcd-browser:
    image: buddho/etcd-browser:latest
    container_name: etcd-browser
    ports:
      - "8000:8000"
    environment:
      - ETCD_HOST=etcd
      - ETCD_PORT=2379
    networks:
      - kms-network
    depends_on:
      - etcd
    restart: unless-stopped
```

**访问方式**：`http://localhost:8000`

#### 方案3：命令行工具（etcdctl）

如果没有UI需求，可以使用命令行工具：

```bash
# 查看所有键
docker exec -it etcd etcdctl get --prefix ""

# 查看特定路径
docker exec -it etcd etcdctl get --prefix "/vault/"

# 设置键值
docker exec -it etcd etcdctl put /test/key "value"

# 删除键
docker exec -it etcd etcdctl del /test/key
```

### 访问方式对比

| 工具 | 类型 | 访问方式 | 适用场景 |
|------|------|---------|---------|
| **Vault UI** | 官方Web UI | `http://localhost:8200/ui` | ✅ **推荐**：功能完整，官方支持 |
| **ETCD Manager** | 第三方GUI | Web/桌面客户端 | 需要ETCD可视化管理 |
| **ETCD Browser** | 第三方Web UI | `http://localhost:8000` | 轻量级ETCD查看工具 |
| **命令行/API** | CLI/API | `etcdctl` / `vault` CLI | 自动化脚本、CI/CD |

### 完整docker-compose示例（包含UI工具）

```yaml
version: '3.8'

services:
  # ... ETCD和Vault配置（见上文）...

  # ETCD Manager（可选）
  etcd-manager:
    image: icellmobilsoft/etcdmanager:latest
    container_name: etcd-manager
    ports:
      - "8080:8080"
    environment:
      - ETCD_ENDPOINTS=http://etcd:2379
    networks:
      - kms-network
    depends_on:
      - etcd
    restart: unless-stopped

  # ETCD Browser（可选，二选一）
  # etcd-browser:
  #   image: buddho/etcd-browser:latest
  #   container_name: etcd-browser
  #   ports:
  #     - "8000:8000"
  #   environment:
  #     - ETCD_HOST=etcd
  #     - ETCD_PORT=2379
  #   networks:
  #     - kms-network
  #   depends_on:
  #     - etcd
  #   restart: unless-stopped
```

### 安全注意事项

1. **生产环境**：
   - Vault UI必须通过HTTPS访问
   - 配置防火墙，限制UI访问IP
   - 使用强密码和Token轮换

2. **ETCD UI工具**：
   - 如果ETCD启用了认证，UI工具需要配置认证信息
   - 生产环境建议通过VPN或内网访问

3. **访问控制**：
   - 为UI访问配置适当的策略
   - 定期审查访问日志

## ⚠️ 注意事项

1. **数据持久化**：确保Docker volumes正确挂载，避免数据丢失
2. **备份策略**：定期备份ETCD和Vault数据
3. **密钥管理**：安全保存Unseal Keys和Root Token。MFA 组件的用户 TOTP 密钥由 **SecretKeyManager** 管理，可存储于 Vault（Transit/KV）或 Redis，不写入 `mfa_user_info` 表，详见 [MFA组件完整设计方案 - 5.1 密钥管理](./MFA组件完整设计方案.md#51-密钥管理)。
4. **网络隔离**：生产环境建议使用专用网络
5. **监控告警**：配置监控和告警，及时发现异常
6. **定期更新**：及时更新ETCD和Vault版本，修复安全漏洞

## 📚 参考资源

- [ETCD官方文档](https://etcd.io/docs/)
- [HashiCorp Vault文档](https://developer.hashicorp.com/vault/docs)
- [Vault ETCD存储后端](https://developer.hashicorp.com/vault/docs/configuration/storage/etcd)
