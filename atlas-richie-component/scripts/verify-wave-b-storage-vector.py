#!/usr/bin/env python3
"""Batch mvn verify for Wave B storage+vector modules; prints PASS/FAIL and coverage."""

from __future__ import annotations

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

MODULES = [
    ("atlas-richie-component-storage-core", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-core", False),
    ("atlas-richie-component-storage-local", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-local", True),
    ("atlas-richie-component-storage-s3", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-s3", False),
    ("atlas-richie-component-storage-minio", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-minio", False),
    ("atlas-richie-component-storage-oss", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-oss", False),
    ("atlas-richie-component-storage-cos", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-cos", False),
    ("atlas-richie-component-storage-obs", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-obs", False),
    ("atlas-richie-component-storage-ks3", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-ks3", False),
    ("atlas-richie-component-storage-tos", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-tos", False),
    ("atlas-richie-component-storage-azure", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-azure", False),
    ("atlas-richie-component-storage-ftp", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-ftp", False),
    ("atlas-richie-component-storage-sftp", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-sftp", False),
    ("atlas-richie-component-storage-smb", "atlas-richie-component/atlas-richie-component-storage/atlas-richie-component-storage-smb", False),
    ("atlas-richie-component-vector-core", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-core", False),
    ("atlas-richie-component-vector-redis", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-redis", True),
    ("atlas-richie-component-vector-elasticsearch", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-elasticsearch", False),
    ("atlas-richie-component-vector-milvus", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-milvus", False),
    ("atlas-richie-component-vector-qdrant", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-qdrant", False),
    ("atlas-richie-component-vector-weaviate", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-weaviate", False),
    ("atlas-richie-component-vector-neo4j", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-neo4j", False),
    ("atlas-richie-component-vector-postgresql", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-postgresql", False),
    ("atlas-richie-component-vector-mongodb-atlas", "atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-mongodb-atlas", False),
]


def coverage_pct(artifact: str) -> str:
    jacoco = ROOT / "coverage-reports" / artifact / "jacoco.xml"
    if not jacoco.exists():
        return "n/a"
    root = ET.parse(jacoco).getroot()
    for c in root.findall("counter[@type='LINE']"):
        missed = int(c.get("missed", 0))
        covered = int(c.get("covered", 0))
        total = missed + covered
        if total:
            return f"{covered / total:.1%}"
    return "n/a"


def failure_hint(artifact: str, log: str) -> str:
    if "jacoco-check" in log and "Coverage checks" in log:
        return "jacoco < 85%"
    if "COMPILATION ERROR" in log:
        m = re.search(r"\[ERROR\].*\.java:\[(\d+)", log)
        return f"compile error line {m.group(1)}" if m else "compile error"
    if "There are test failures" in log or "<<< FAILURE!" in log or "<<< ERROR!" in log:
        m = re.search(r"(\w+Test|\w+IT) -- Time elapsed.*<<< (FAILURE|ERROR)", log)
        return m.group(1) if m else "test failure"
    if "Failed to load ApplicationContext" in log:
        return "spring context IT"
    return log.splitlines()[-1][:80] if log else "unknown"


def main() -> int:
    print("Installing dependencies (skip tests)...")
    subprocess.run(
        ["mvn", "install", "-DskipTests", "-Djacoco.skip=true", "-q"],
        cwd=ROOT,
        check=False,
    )

    results = []
    for artifact, rel_path, need_docker in MODULES:
        env = {**dict(**{"IT_REQUIRE_DOCKER": "true"} if need_docker else {})}
        cmd = ["mvn", "-pl", rel_path, "verify", "-q"]
        proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, env={**__import__("os").environ, **env})
        log = (proc.stdout or "") + (proc.stderr or "")
        ok = proc.returncode == 0
        cov = coverage_pct(artifact) if ok else coverage_pct(artifact)
        hint = "" if ok else failure_hint(artifact, log)
        results.append((artifact, ok, cov, hint))
        status = "PASS" if ok else "FAIL"
        print(f"{status:4} {artifact:45} cov={cov:>6} {hint}")

    passed = sum(1 for _, ok, _, _ in results if ok)
    print(f"\nSummary: {passed}/{len(results)} PASS")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
