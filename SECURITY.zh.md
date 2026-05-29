# Security Policy

**Languages:** [English](SECURITY.en.md) | [中文](SECURITY.md)

## Supported Versions

以下版本目前接受安全更新（补丁与修复）：

| Version   | Supported          |
|-----------|--------------------|
| 1.0.x     | :white_check_mark: |
| < 1.0     | :x:                |

> 当前开发基线为 `1.0.0-SNAPSHOT`，正式安全公告以已发布的 Release Tag 为准。

## Reporting a Vulnerability

如发现 **Atlas Richie Platform** 的安全漏洞，请**不要**在公开 Issue、Discussion 或 Pull Request 中披露可利用细节、PoC 或敏感数据。

请通过以下任一方式私下报告：

1. **GitHub Security Advisories**（推荐）  
   在本仓库页面选择 **Security → Report a vulnerability** 提交私密报告。

2. **邮件联系维护者**  
   发送至：richie696@icloud.com  
   邮件主题建议包含：`[SECURITY] atlas-richie-platform`

请尽量提供：

- 受影响模块/组件（如 `atlas-richie-component-cache`）
- 版本信息（Git tag、`revision` 或制品版本）
- 复现步骤与影响范围
- 可能的缓解措施（如有）

## What to Expect

- **确认收到**：我们将在 **5 个工作日内** 确认收到报告。
- **评估与修复**：根据严重程度安排修复优先级，并在修复后发布补丁版本。
- **披露协调**：修复完成后，我们会与您协调公开披露时间（如通过 GitHub Security Advisory 与 `CHANGELOG.md`）。

## Out of Scope

以下情况通常不在本仓库安全响应范围内：

- 仅影响 `atlas-richie-component-template` 示例工程、且未按生产规范部署的配置问题
- 上游第三方依赖的已知 CVE（我们会评估是否通过 BOM 升级修复，但修复节奏可能依赖上游）
- 未启用认证/鉴权导致的部署侧误配置（请在业务部署文档中加固）

感谢你对项目安全的支持与负责任披露。
