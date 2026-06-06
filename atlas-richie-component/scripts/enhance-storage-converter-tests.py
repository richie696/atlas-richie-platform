#!/usr/bin/env python3
"""Expand storage provider converter tests to cover all switch branches."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STORAGE = ROOT / "atlas-richie-component" / "atlas-richie-component-storage"

PROVIDERS = [
    "atlas-richie-component-storage-minio",
    "atlas-richie-component-storage-oss",
    "atlas-richie-component-storage-cos",
    "atlas-richie-component-storage-obs",
    "atlas-richie-component-storage-ks3",
    "atlas-richie-component-storage-tos",
]


def extract_switch_cases(java: str) -> list[str]:
    m = re.search(r"switch\s*\(\s*\w+\s*\)\s*\{([^}]+)\}", java, re.DOTALL)
    if not m:
        return []
    body = m.group(1)
    return [c.strip() for c in re.findall(r"case\s+([A-Z_][A-Z0-9_]*)", body)]


def converter_main(path: Path, class_name: str) -> Path:
    mod_root = path.parents[8]
    return mod_root / "src/main/java/com/richie/component/storage/converter" / f"{class_name}.java"


def patch_acl_test(path: Path, class_name: str) -> None:
    main = converter_main(path, class_name)
    if not main.exists():
        return
    cases = extract_switch_cases(main.read_text(encoding="utf-8"))
    if not cases:
        return
    names = ", ".join(f'"{c}"' for c in cases)
    content = f"""package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class {class_name}Test {{

    private final {class_name} converter = new {class_name}();

    @ParameterizedTest
    @EnumSource(value = AclTypeEnum.class, names = {{{names}}})
    void convert_supportedAclTypes(AclTypeEnum aclType) {{
        assertThat(converter.convertToEngineAcl(aclType)).isNotNull();
    }}

    @Test
    void getSupportedEngine_isDefined() {{
        assertThat(converter.getSupportedEngine()).isNotNull();
    }}

    @Test
    void convert_unsupportedAcl_throws() {{
        assertThatThrownBy(() -> converter.convertToEngineAcl(AclTypeEnum.LOG_DELIVERY_WRITE))
                .isInstanceOf(IllegalArgumentException.class);
    }}
}}
"""
    path.write_text(content, encoding="utf-8")
    print(f"  patched {path.relative_to(ROOT)}")


def patch_storage_test(path: Path, class_name: str) -> None:
    main = converter_main(path, class_name)
    if not main.exists():
        return
    cases = extract_switch_cases(main.read_text(encoding="utf-8"))
    if not cases:
        return
    names = ", ".join(f'"{c}"' for c in cases)
    content = f"""package com.richie.component.storage.converter;

import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class {class_name}Test {{

    private final {class_name} converter = new {class_name}();

    @ParameterizedTest
    @EnumSource(value = StorageTypeEnum.class, names = {{{names}}})
    void convert_supportedTypes(StorageTypeEnum type) {{
        assertThat(converter.convertToEngineType(type)).isNotBlank();
    }}

    @Test
    void getSupportedEngine_isDefined() {{
        assertThat(converter.getSupportedEngine()).isNotNull();
    }}

    @Test
    void convert_unsupportedType_throws() {{
        assertThatThrownBy(() -> converter.convertToEngineType(StorageTypeEnum.MULTI_AZ_STANDARD))
                .isInstanceOf(StorageTypeUnsupportedException.class);
    }}
}}
"""
    path.write_text(content, encoding="utf-8")
    print(f"  patched {path.relative_to(ROOT)}")


def main() -> None:
    for mod in PROVIDERS:
        test_root = STORAGE / mod / "src" / "test" / "java" / "com" / "richie" / "component" / "storage" / "converter"
        if not test_root.exists():
            continue
        print(mod)
        for test_file in test_root.glob("*AclTypeConverterTest.java"):
            patch_acl_test(test_file, test_file.stem.replace("Test", ""))
        for test_file in test_root.glob("*StorageTypeConverterTest.java"):
            patch_storage_test(test_file, test_file.stem.replace("Test", ""))


if __name__ == "__main__":
    main()
