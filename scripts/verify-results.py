#!/usr/bin/env python3
import hashlib
import io
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
required = {
    "CourseExtractionAcceptanceTest", "StagedCoverageAcceptanceTest",
    "CredentialRedactionAcceptanceTest", "CourseLibraryTest", "CourseRetrievalFixtureTest",
    "GeneratedTestTest", "GitSnapshotTest", "FolioTasksTest", "ProviderAccessTest",
    "PanelWorkflowTest",
    "StreamingAcceptanceTest", "SidebarDisclosureAcceptanceTest", "FolioTaskEventsTest",
    "HttpChatStreamingTest", "FolioSidebarTest", "FolioSidebarRenderTest",
    "AskerStreamingTest", "SidebarResizeAcceptanceTest", "FenceRenderingAcceptanceTest",
    "FenceLineEndingTest",
    "InteractionAcceptanceTest", "FolioInteractionTest", "FolioMotionTest",
}
try:
    suites = {}
    for path in sorted((root / "build/test-results/test").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        name = suite.attrib["name"]
        counts = {key: int(suite.attrib.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        if counts["tests"] <= 0 or any(counts[key] for key in ("failures", "errors", "skipped")):
            raise ValueError(f"Unsuccessful or empty test suite: {name}: {counts}")
        if counts["tests"] != len(suite.findall("testcase")):
            raise ValueError(f"Test count differs from actual test cases: {name}")
        suites[name] = counts
    missing = required - {name.rsplit(".", 1)[-1] for name in suites}
    if missing:
        raise ValueError(f"Missing required test suites: {sorted(missing)}")
    archives = list((root / "build/distributions").glob("folio-*.zip"))
    if len(archives) != 1:
        raise ValueError(f"Expected one plugin ZIP, found {len(archives)}")
    archive = archives[0]
    packaged = set()
    descriptor = False
    with zipfile.ZipFile(archive) as plugin:
        if plugin.testzip() is not None:
            raise ValueError("Plugin ZIP is corrupt")
        for entry in plugin.namelist():
            if entry.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(plugin.read(entry))) as jar:
                    packaged.update(jar.namelist())
                    descriptor |= "META-INF/plugin.xml" in jar.namelist()
    expected = {"cst3492/courseta/" + name + ".class" for name in (
        "FolioPanel", "FolioTasks", "ProviderAccess", "ProviderConnection", "Conversation",
        "index/CourseLibrary", "index/CourseSearch", "junitgen/GeneratedTest",
        "ask/HttpChatClient", "commit/GitDiffs",
    )}
    if not descriptor or expected - packaged:
        raise ValueError(f"Plugin is missing its descriptor or classes: {sorted(expected - packaged)}")
    summary = {
        "tests": sum(s["tests"] for s in suites.values()),
        "failures": 0, "errors": 0, "skipped": 0, "suites": suites,
        "plugin": str(archive.relative_to(root)), "bytes": archive.stat().st_size,
        "sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
    }
    destination = root / "build/verification/summary.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(summary, indent=2) + "\n")
    print(f"Verified {summary['tests']} tests in {len(suites)} suites and {archive.name} ({summary['bytes']} bytes).")
    print(f"Evidence: {destination.relative_to(root)}")
except (OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile) as error:
    print(f"Verification failed: {error}", file=sys.stderr)
    sys.exit(1)
