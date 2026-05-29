# Contributing to atlas-richie-platform

**Languages:** [English](CONTRIBUTING.md) | [中文](CONTRIBUTING.zh.md)

Thank you for your interest in contributing to `atlas-richie-platform`.

## Principles

- Contributions should align with project goals and respect module boundaries.
- Ensure the code compiles, tests pass, and documentation is updated before submitting.
- All contributions are licensed under the repository root [LICENSE](./LICENSE) (Apache License 2.0).

## Contribution Workflow

1. Fork the repository and create a feature branch (e.g. `feature/<name>` or `fix/<name>`).
2. Develop and self-test locally; avoid introducing build failures.
3. Open a Pull Request describing:
   - Motivation
   - Main changes
   - How you verified the change
   - Compatibility impact (if any)
4. Iterate based on review feedback until merged.

## Commit Guidelines

- Use clear, imperative commit messages; explain **why** before **what**.
- Update README or module docs when public APIs, configuration, or behavior changes.
- New configuration options should have sensible defaults and examples to avoid breaking existing users.

## Code of Conduct & Security

- Be professional, respectful, and constructive. See [CODE_OF_CONDUCT.en.md](./CODE_OF_CONDUCT.en.md).
- Do **not** disclose security issues publicly. Follow [SECURITY.en.md](./SECURITY.en.md).

## Local Build

```bash
# Full build (excluding sample projects)
mvn clean verify -DskipTests -pl '!atlas-richie-component-template' -am
```

## Related Documents

- [SECURITY.md](./SECURITY.md) / [SECURITY.en.md](./SECURITY.en.md) — vulnerability reporting and supported versions
- [CHANGELOG.md](./CHANGELOG.md) / [CHANGELOG.en.md](./CHANGELOG.en.md) — release notes
- [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) / [CODE_OF_CONDUCT.en.md](./CODE_OF_CONDUCT.en.md) — community standards
