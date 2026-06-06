#!/usr/bin/env python3
"""为 messaging BOM 壳子模块（无 main 源码）生成 ModuleSmokeTest。"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MESSAGING = ROOT / "atlas-richie-component" / "atlas-richie-component-messaging"

BOM_MODULES = [
    ("atlas-richie-component-messaging-kafka", "kafka", "Kafka"),
    ("atlas-richie-component-messaging-rabbitmq", "rabbitmq", "Rabbitmq"),
    ("atlas-richie-component-messaging-rocketmq", "rocketmq", "Rocketmq"),
    ("atlas-richie-component-messaging-kinesis", "kinesis", "Kinesis"),
    ("atlas-richie-component-messaging-gcp-pubsub", "gcp.pubsub", "GcpPubsub"),
    ("atlas-richie-component-messaging-eventhubs", "eventhubs", "Eventhubs"),
    ("atlas-richie-component-messaging-servicebus", "servicebus", "Servicebus"),
    ("atlas-richie-component-messaging-sqs", "sqs", "Sqs"),
    ("atlas-richie-component-messaging-sns", "sns", "Sns"),
    ("atlas-richie-component-messaging-solace", "solace", "Solace"),
]


def write_smoke(module_dir: Path, pkg: str, class_prefix: str) -> None:
    test_dir = module_dir / "src/test/java" / Path(*pkg.split("."))
    test_dir.mkdir(parents=True, exist_ok=True)
    test_file = test_dir / f"{class_prefix}ModuleSmokeTest.java"
    if test_file.exists():
        return
    test_file.write_text(
        f"""package {pkg};

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class {class_prefix}ModuleSmokeTest {{

    @Test
    void modulePackage_shouldBeDefined() {{
        assertThat(getClass().getPackageName()).isEqualTo("{pkg}");
    }}
}}
""",
        encoding="utf-8",
    )
    print(f"created {test_file.relative_to(ROOT)}")


def main() -> None:
    for artifact, subpkg, prefix in BOM_MODULES:
        module_dir = MESSAGING / artifact
        if not module_dir.is_dir():
            continue
        pkg = f"com.richie.component.messaging.{subpkg}"
        write_smoke(module_dir, pkg, prefix)


if __name__ == "__main__":
    main()
