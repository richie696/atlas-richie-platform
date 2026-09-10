# VikingDB Provider

VikingDB hybrid 仅适用于 `hnsw-hybrid` Store：必须声明 scalar ACL 索引并配置命名 sparse 编码器。Provider 将统一的 dense/sparse 请求映射为 Atlas Richie AI VikingDB 插件的原生 `HYBRID` 请求，并在执行前附加强制结构化 Filter；只有这些前置条件校验通过才声明 `ACL_SAFE_HYBRID`、`execution=native`。

dense-only 仍是默认行为。生命周期操作还需要 control-plane endpoint。VikingDB 建索引为异步过程：创建请求被接受不等于已可查询，`RESOURCE_NOT_READY` 应按调用方的 ready 重试策略处理。真实云端 E2E 仍缺少当前环境凭据与细粒度 IAM 验证，本文档不宣称已通过。
