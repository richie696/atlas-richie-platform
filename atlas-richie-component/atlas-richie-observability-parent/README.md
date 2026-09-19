# Atlas Richie Observability

`atlas-richie-observability-parent` is the full application observability component for the Atlas Richie platform.

It combines OpenTelemetry tracing, Micrometer/Actuator runtime metrics, structured logs, JVM/container diagnostics, protocol propagation, and a single application-level enable switch.

For the complete architecture, package responsibilities, configuration contract, migration plan, and acceptance checklist, see [README.zh.md](./README.zh.md), [docs/OBSERVABILITY_DESIGN.md](./docs/OBSERVABILITY_DESIGN.md), and the phase acceptance records.

Phase 3 and Phase 4 implementation progress is tracked in [docs/PHASE_3_PROGRESS.md](./docs/PHASE_3_PROGRESS.md) and [docs/PHASE_4_PROGRESS.md](./docs/PHASE_4_PROGRESS.md).

The application-facing dependency is:

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>atlas-richie-observability-spring-boot-starter</artifactId>
</dependency>
```

The Phase 1/2 runtime foundation, Phase 3 protocol migration, Phase 4 Foundry application migration, Service Map, Trace-to-Logs/Metrics, unified disable switch, and final real Gateway/Orchestrator E2E have all been implemented and accepted.
