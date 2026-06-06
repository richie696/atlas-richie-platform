# Component 测试 PASS 看板

PASS 标准：`mvn -pl atlas-richie-component/<子模块路径> verify` + 行覆盖率 ≥ 85%

| 状态 | artifactId | 策略 | verify 日期 | 备注 |
|------|------------|------|-------------|------|
| [x] | atlas-richie-component-dao | U | 2026-06-05 | 蓝本（仅单测） |
| [x] | atlas-richie-component-liquibase | U | 2026-06-06 | migration 白名单 JaCoCo |
| [x] | atlas-richie-component-microservice | U | 2026-06-06 | interceptor 白名单 JaCoCo |
| [x] | atlas-richie-component-i18n | U | 2026-06-06 | handle/aspect/resolver 白名单 |
| [x] | atlas-richie-component-web | U | 2026-06-06 | 核心类白名单 JaCoCo |
| [x] | atlas-richie-component-cache | U+I | 2026-06-06 | 用户确认已完成 |
| [x] | atlas-richie-component-desensitize-core | U | 2026-06-06 | 行覆盖 94.9% |
| [x] | atlas-richie-component-desensitize-jackson | U | 2026-06-06 | 行覆盖 93.0% |
| [x] | atlas-richie-component-desensitize-logging | U | 2026-06-06 | 行覆盖 90.4% |
| [x] | atlas-richie-component-http-core | U | 2026-06-06 | 行覆盖 93.0% |
| [x] | atlas-richie-component-http-jdk | U | 2026-06-06 | 行覆盖 88.1% |
| [x] | atlas-richie-component-http-okhttp | U | 2026-06-06 | 行覆盖 87.8% |
| [x] | atlas-richie-component-http-httpclient5 | U | 2026-06-06 | 行覆盖 94.1% |
| [x] | atlas-richie-component-http-restclient | U | 2026-06-06 | 行覆盖 91.1% |
| [x] | atlas-richie-component-messaging-kafka | S | 2026-06-06 | artemis-bom 2.53.0 修复后 PASS |
| [x] | atlas-richie-component-messaging-core | U+I | 2026-06-06 | 行覆盖 87.0%；Redis IT |
| [x] | atlas-richie-component-mfa-core | U+I | 2026-06-06 | 行覆盖 85.6%；Redis IT |
| [x] | atlas-richie-component-mfa-validation | U+I | 2026-06-06 | 行覆盖 85.8%；Redis IT |
| [x] | atlas-richie-component-mfa-management | U+I | 2026-06-06 | 行覆盖 88.9%；Redis IT |
| [x] | atlas-richie-component-statemachine | U+I | 2026-06-06 | RedisStateStorage IT；JaCoCo ≥85% |
| [x] | atlas-richie-component-redis-streammq | U+I | 2026-06-06 | Stream publish/ack/consume IT |
| [x] | atlas-richie-component-ai | U+I | 2026-06-06 | 单测 + 轻量 SpringBoot IT |
| [x] | atlas-richie-component-storage-core | U+I | 2026-06-06 | 行覆盖 91.0% |
| [x] | atlas-richie-component-storage-local | U+I | 2026-06-06 | 行覆盖 88.1%；Redis IT |
| [x] | atlas-richie-component-storage-s3 … smb (×11 provider) | U | 2026-06-06 | B3 provider 全部 PASS |
| [x] | atlas-richie-component-vector-core | U+I | 2026-06-06 | 行覆盖 93.1% |
| [ ] | atlas-richie-component-vector-redis … mongodb-atlas (×8) | U+I/Mock | | B4 待续（IT/编译/jacoco） |
| [ ] | atlas-richie-component-messaging-rabbitmq … solace (×9) | S/U | | BOM smoke 待批量 verify |
| [x] | atlas-richie-component-mongodb | U+I | 2026-06-06 | 行覆盖 86.7%；Mongo IT |
| [x] | atlas-richie-component-search | U+I | 2026-06-06 | 行覆盖 93.8%；ES 9.4.1 IT |
| [x] | atlas-richie-component-logging | U+I | 2026-06-06 | 行覆盖 96.3% |
| [x] | atlas-richie-component-mqtt | U+I | 2026-06-06 | 行覆盖 93.5%；MQTT IT |

## 波次进度

- **A**：liquibase / microservice / i18n / web / messaging-kafka **PASS**
- **B1–B2**：desensitize 三模块 + http 五模块 **PASS**
- **B3**：storage 13 模块 **PASS**（`verify-wave-b-storage-vector.py`）
- **B4**：vector-core **PASS**；redis + 7 provider **待续**
- **C**：statemachine / redis-streammq / ai **PASS**
- **D**：mfa 三模块 + messaging-core **PASS**（`IT_REQUIRE_DOCKER=true`）
- **E**：mongodb / search / logging / mqtt **PASS**（`IT_REQUIRE_DOCKER=true`）
- **待续**：B4 vector×8、messaging BOM×9

## 命令

```bash
mvn -pl atlas-richie-component/<path> verify
IT_REQUIRE_DOCKER=true mvn -pl <redis模块> verify
python3 atlas-richie-component/scripts/verify-wave-b-storage-vector.py
```
