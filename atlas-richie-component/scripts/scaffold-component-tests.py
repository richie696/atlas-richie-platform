#!/usr/bin/env python3
"""
为 atlas-richie-component 下 jar 子模块批量生成单测 + 集测骨架（不覆盖已有 *IT / support）。
用法：python3 atlas-richie-component/scripts/scaffold-component-tests.py
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPONENT = ROOT / "atlas-richie-component"
SKIP_MODULES = {
    "atlas-richie-component-cache",
    "atlas-richie-component-dependencies",
    "atlas-richie-component-dao-tenant",  # 仅占位壳，无实现代码
}


def jar_modules() -> list[Path]:
    modules = []
    for pom in COMPONENT.rglob("pom.xml"):
        text = pom.read_text(encoding="utf-8")
        if "<packaging>jar</packaging>" not in text:
            continue
        artifact = re.search(r"<artifactId>([^<]+)</artifactId>", text)
        if not artifact:
            continue
        aid = artifact.group(1)
        if aid in SKIP_MODULES:
            continue
        modules.append(pom.parent)
    return sorted(modules)


def artifact_id(pom_dir: Path) -> str:
    text = (pom_dir / "pom.xml").read_text(encoding="utf-8")
    m = re.search(r"<artifactId>([^<]+)</artifactId>", text)
    return m.group(1) if m else pom_dir.name


def requires_redis(pom_dir: Path) -> bool:
    text = (pom_dir / "pom.xml").read_text(encoding="utf-8")
    return (
        "spring-boot-starter-data-redis" in text
        or "atlas-richie-component-cache" in text
    )


def base_package(pom_dir: Path) -> str | None:
    main = pom_dir / "src/main/java"
    if not main.exists():
        return None
    for java in main.rglob("*.java"):
        m = re.search(r"^package\s+([\w.]+);", java.read_text(encoding="utf-8"), re.M)
        if m:
            return m.group(1)
    return None


def find_auto_configurations(pom_dir: Path) -> list[str]:
    main = pom_dir / "src/main/java"
    if not main.exists():
        return []
    configs = []
    for java in main.rglob("*AutoConfiguration.java"):
        text = java.read_text(encoding="utf-8")
        pkg = re.search(r"^package\s+([\w.]+);", text, re.M)
        cls = java.stem
        if pkg:
            configs.append(f"{pkg.group(1)}.{cls}")
    return sorted(set(configs))


def env_prefix(artifact: str) -> str:
    core = artifact.removeprefix("atlas-richie-component-")
    return re.sub(r"[^A-Z0-9]", "_", core.replace("-", "_").upper())


def module_short(artifact: str) -> str:
    return artifact.removeprefix("atlas-richie-component-")


def type_prefix(artifact: str) -> str:
    """唯一类型前缀，如 atlas-richie-component-vector-neo4j → VectorNeo4j。"""
    parts = artifact.removeprefix("atlas-richie-component-").split("-")
    return "".join(p.capitalize() for p in parts)


def pkg_to_path(base_pkg: str) -> Path:
    return Path(*base_pkg.split("."))


def ensure_plugins(pom_dir: Path) -> bool:
    pom = pom_dir / "pom.xml"
    text = pom.read_text(encoding="utf-8")
    changed = False
    if "maven-failsafe-plugin" not in text:
        insert = """
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
            </plugin>"""
        text = text.replace(
            "<artifactId>maven-surefire-plugin</artifactId>\n            </plugin>",
            "<artifactId>maven-surefire-plugin</artifactId>\n            </plugin>" + insert,
            1,
        )
        changed = True
    if "jacoco-maven-plugin" not in text:
        insert = """
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
            </plugin>"""
        if "maven-failsafe-plugin" in text:
            text = text.replace(
                "<artifactId>maven-failsafe-plugin</artifactId>\n            </plugin>",
                "<artifactId>maven-failsafe-plugin</artifactId>\n            </plugin>" + insert,
                1,
            )
        changed = True
    if changed:
        pom.write_text(text, encoding="utf-8")
    return changed


def write_if_absent(path: Path, content: str) -> bool:
    if path.exists():
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return True


def scaffold_module(pom_dir: Path) -> list[str]:
    created: list[str] = []
    artifact = artifact_id(pom_dir)
    base_pkg = base_package(pom_dir)
    if not base_pkg:
        return [f"SKIP {artifact}: no java sources"]

    short = module_short(artifact)
    prefix = env_prefix(artifact)
    redis = requires_redis(pom_dir)
    configs = find_auto_configurations(pom_dir)
    support_pkg = f"{base_pkg}.support"
    integration_pkg = f"{base_pkg}.integration"
    test_root = pom_dir / "src/test/java" / pkg_to_path(support_pkg)
    it_root = pom_dir / "src/test/java" / pkg_to_path(integration_pkg)

    if ensure_plugins(pom_dir):
        created.append(f"{artifact}: pom plugins")

    # --- unit smoke（仅当模块尚无单测时） ---
    test_java = pom_dir / "src/test/java"
    has_unit_tests = test_java.exists() and any(test_java.rglob("*Test.java"))
    smoke_name = "".join(p.capitalize() for p in short.split("-")) + "ModuleSmokeTest"
    smoke_path = pom_dir / "src/test/java" / pkg_to_path(base_pkg) / f"{smoke_name}.java"
    if not has_unit_tests and write_if_absent(
        smoke_path,
        f"""package {base_pkg};

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class {smoke_name} {{

    @Test
    void modulePackage_shouldBeDefined() {{
        assertThat(getClass().getPackageName()).isEqualTo("{base_pkg}");
    }}
}}
""",
    ):
        created.append(str(smoke_path.relative_to(ROOT)))

    type_name = type_prefix(artifact)
    config_class = f"{type_name}IntegrationTestConfiguration"
    config_imports = "\n        ".join(f"{c}.class," for c in configs) if configs else ""

    if write_if_absent(
        test_root / f"{config_class}.java",
        f"""package {support_pkg};

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({{
        {config_imports}
}})
public class {config_class} {{
}}
""",
    ):
        created.append(str((test_root / f"{config_class}.java").relative_to(ROOT)))

    if redis:
        support_class = f"{type_name}RedisIntegrationTestSupport"
        if write_if_absent(
            test_root / f"{support_class}.java",
            f"""package {support_pkg};

import com.richie.testing.redis.GenericRedisIntegrationTestSupport;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import com.richie.testing.spring.PropertyContributor;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public final class {support_class} implements RedisIntegrationTestAccess {{

    private static final GenericRedisIntegrationTestSupport DELEGATE = GenericRedisIntegrationTestSupport.create(
            DockerImageName.parse("redis:7-alpine"),
            15,
            "Redis 集成测试需要 Docker。参见 atlas-richie-testing-support/README.md",
            {support_class}::appendComponentProperties,
            "{prefix}");

    private {support_class}() {{
    }}

    private static final {support_class} INSTANCE = new {support_class}();

    public static {support_class} getInstance() {{
        return INSTANCE;
    }}

    /** JUnit {{@code @EnabledIf}} 入口（不可与实例 {{@link #isEnabled()}} 同名）。 */
    public static boolean integrationTestsEnabled() {{
        return getInstance().isEnabled();
    }}

    @Override
    public boolean isEnabled() {{
        return DELEGATE.isEnabled();
    }}

    @Override
    public boolean isExternal() {{
        return DELEGATE.isExternal();
    }}

    @Override
    public void appendPropertyPairs(List<String> pairs) {{
        DELEGATE.appendPropertyPairs(pairs);
    }}

    private static void appendComponentProperties(List<String> pairs) {{
        // 本模块集测专属属性（按需补充）
    }}
}}
""",
        ):
            created.append(str((test_root / f"{support_class}.java").relative_to(ROOT)))

        init_class = f"{type_name}RedisIntegrationTestInitializer"
        if write_if_absent(
            test_root / f"{init_class}.java",
            f"""package {support_pkg};

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class {init_class}
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {{

    @Override
    public void initialize(ConfigurableApplicationContext context) {{
        SpringPropertyInitializer.applyIfAvailable(
                {support_class}::integrationTestsEnabled,
                pairs -> {support_class}.getInstance().appendPropertyPairs(pairs),
                context);
    }}
}}
""",
        ):
            created.append(str((test_root / f"{init_class}.java").relative_to(ROOT)))

        ann_class = f"{type_name}RedisIntegrationTest"
        if write_if_absent(
            test_root / f"{ann_class}.java",
            f"""package {support_pkg};

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = {config_class}.class)
@ContextConfiguration(initializers = {init_class}.class)
@EnabledIf("{support_pkg}.{support_class}#integrationTestsEnabled")
public @interface {ann_class} {{
}}
""",
        ):
            created.append(str((test_root / f"{ann_class}.java").relative_to(ROOT)))

        abstract_class = f"Abstract{type_name}RedisIntegrationTest"
        if write_if_absent(
            test_root / f"{abstract_class}.java",
            f"""package {support_pkg};

import com.richie.testing.redis.AbstractRedisIntegrationTestBase;
import com.richie.testing.redis.RedisIntegrationTestAccess;

import java.util.function.Supplier;

@{ann_class}
public abstract class {abstract_class} extends AbstractRedisIntegrationTestBase {{

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {{
        return {support_class}::getInstance;
    }}
}}
""",
        ):
            created.append(str((test_root / f"{abstract_class}.java").relative_to(ROOT)))

        it_class = f"RedisConnectivityIT"
        if write_if_absent(
            it_root / f"{it_class}.java",
            f"""package {integration_pkg};

import {support_pkg}.{abstract_class};
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class {it_class} extends {abstract_class} {{

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void redis_shouldPing() {{
        stringRedisTemplate.opsForValue().set("it:ping", "pong");
        assertThat(stringRedisTemplate.opsForValue().get("it:ping")).isEqualTo("pong");
    }}
}}
""",
        ):
            created.append(str((it_root / f"{it_class}.java").relative_to(ROOT)))
    else:
        ann_class = f"{type_name}IntegrationTest"
        support_class = f"{type_name}IntegrationTestSupport"
        if write_if_absent(
            test_root / f"{support_class}.java",
            f"""package {support_pkg};

public final class {support_class} {{

    private {support_class}() {{
    }}

    public static boolean isEnabled() {{
        return true;
    }}
}}
""",
        ):
            created.append(str((test_root / f"{support_class}.java").relative_to(ROOT)))

        if write_if_absent(
            test_root / f"{ann_class}.java",
            f"""package {support_pkg};

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = {config_class}.class)
@EnabledIf("{support_pkg}.{support_class}#integrationTestsEnabled")
public @interface {ann_class} {{
}}
""",
        ):
            created.append(str((test_root / f"{ann_class}.java").relative_to(ROOT)))

        it_class = "SpringContextIT"
        if write_if_absent(
            it_root / f"{it_class}.java",
            f"""package {integration_pkg};

import {support_pkg}.{ann_class};
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@{ann_class}
class {it_class} {{

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {{
        assertThat(applicationContext).isNotNull();
    }}
}}
""",
        ):
            created.append(str((it_root / f"{it_class}.java").relative_to(ROOT)))

    return created


def main() -> None:
    all_created: list[str] = []
    for module in jar_modules():
        all_created.extend(scaffold_module(module))
    print(f"Scaffold complete. Created/updated {len(all_created)} items.")
    for line in all_created:
        print(f"  - {line}")


if __name__ == "__main__":
    main()
