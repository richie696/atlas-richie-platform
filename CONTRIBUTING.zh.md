# Contributing to atlas-richie-platform

**Languages:** [English](CONTRIBUTING.md) | [中文](CONTRIBUTING.zh.md)

感谢你对 `atlas-richie-platform` 的关注与贡献。

## 基本原则

- 贡献内容应与项目目标一致，并保持模块边界清晰。
- 提交前请确保代码可编译、测试通过、文档同步更新。
- 所有贡献默认按仓库根目录 `LICENSE`（Apache License 2.0）授权。

## 贡献流程

1. Fork 仓库并创建功能分支（建议命名：`feature/<name>` 或 `fix/<name>`）。
2. 完成开发与自测，确保不引入新的构建错误。
3. 提交 Pull Request，并清晰描述：
   - 变更动机
   - 主要改动
   - 验证方式
   - 兼容性影响（如有）
4. 根据评审意见迭代，直至合并。

## 提交建议

- commit message 建议使用简洁祈使句，表达“为什么改”优先于“改了什么”。
- 涉及公共 API、配置项、行为变化时，请同步更新 README 或模块文档。
- 新增配置项应提供默认值与示例，避免破坏现有用户行为。

## 行为规范

- 保持专业、尊重、建设性的沟通方式，参见 [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md)。
- 对安全问题请勿公开提交细节，请遵循 [SECURITY.md](./SECURITY.md) 中的报告流程。

## 本地构建建议

```bash
# 全量构建（排除示例工程）
mvn clean verify -DskipTests -pl '!atlas-richie-component-template' -am
```

## 相关文档

- [SECURITY.md](./SECURITY.md) / [SECURITY.en.md](./SECURITY.en.md) — 安全漏洞报告与支持版本
- [CHANGELOG.md](./CHANGELOG.md) / [CHANGELOG.en.md](./CHANGELOG.en.md) — 版本变更记录
- [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) / [CODE_OF_CONDUCT.en.md](./CODE_OF_CONDUCT.en.md) — 社区行为准则

