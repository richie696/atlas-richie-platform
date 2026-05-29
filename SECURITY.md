# Security Policy

**Languages:** [English](SECURITY.en.md) | [中文](SECURITY.md)

## Supported Versions

The following versions currently receive security updates (patches and fixes):

| Version   | Supported          |
|-----------|--------------------|
| 1.0.x     | :white_check_mark: |
| < 1.0     | :x:                |

> The current development baseline is `1.0.0-SNAPSHOT`. Official security advisories are based on published release tags.

## Reporting a Vulnerability

If you discover a security vulnerability in **Atlas Richie Platform**, please **do not** disclose exploit details, PoCs, or sensitive data in public Issues, Discussions, or Pull Requests.

Report privately via either:

1. **GitHub Security Advisories** (recommended)  
   Open the repository → **Security** → **Report a vulnerability**.

2. **Email the maintainers**  
   Send to: richie696@icloud.com  
   Suggested subject: `[SECURITY] atlas-richie-platform`

Please include when possible:

- Affected module/component (e.g. `atlas-richie-component-cache`)
- Version (Git tag, `revision`, or artifact version)
- Steps to reproduce and impact scope
- Possible mitigations (if any)

## What to Expect

- **Acknowledgement**: We aim to confirm receipt within **5 business days**.
- **Assessment & fix**: We prioritize fixes by severity and publish patch releases when ready.
- **Coordinated disclosure**: After a fix is available, we will coordinate public disclosure (e.g. GitHub Security Advisory and `CHANGELOG.md` / `CHANGELOG.en.md`).

## Out of Scope

The following are generally **not** in scope for this repository’s security response:

- Issues affecting only `atlas-richie-component-template` sample projects when not deployed for production
- Known CVEs in upstream third-party dependencies (we may address them via BOM upgrades, subject to upstream availability)
- Deployment misconfiguration such as missing authentication/authorization (harden in your deployment docs)

Thank you for helping keep the project secure through responsible disclosure.
