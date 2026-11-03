#!/usr/bin/env python3
import hashlib
import io
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile
from html.parser import HTMLParser

root = Path(__file__).resolve().parents[1]
required = {
    "CourseExtractionAcceptanceTest", "StagedCoverageAcceptanceTest",
    "CredentialRedactionAcceptanceTest", "CourseLibraryTest", "CourseRetrievalFixtureTest",
    "GeneratedTestTest", "GitSnapshotTest", "FolioTasksTest", "ProviderAccessTest",
    "StreamingAcceptanceTest", "FolioTaskEventsTest", "HttpChatStreamingTest",
    "AskerStreamingTest", "ConversationIdentityAcceptanceTest", "CapturedSourceWriteTest",
    "FolioSessionTest", "FolioProtocolTest", "FolioBrowserTest",
}


def frontend_result(name):
    path = root / "web/test-results" / (name + ".xml")
    document = ET.parse(path).getroot()
    cases = list(document.iter("testcase"))
    if not cases:
        raise ValueError(f"Empty frontend test report: {path}")
    for suite in document.iter():
        if suite.tag not in ("testsuite", "testsuites"):
            continue
        if any(int(suite.attrib.get(key, "0")) for key in ("failures", "errors", "skipped")):
            raise ValueError(f"Unsuccessful frontend suite: {name}: {suite.attrib}")
        if "tests" in suite.attrib and int(suite.attrib["tests"]) != len(list(suite.iter("testcase"))):
            raise ValueError(f"Frontend test count differs from test cases: {name}")
    for case in cases:
        if any(case.find(key) is not None for key in ("failure", "error", "skipped")):
            raise ValueError(f"Unsuccessful frontend test: {name}: {case.attrib.get('name')}")
    return {"tests": len(cases), "failures": 0, "errors": 0, "skipped": 0}


class EntryAssets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "script":
            if not values.get("src"):
                raise ValueError("Frontend contains an inline script")
            self.paths.append(values["src"])
        if tag == "link" and values.get("rel") in ("stylesheet", "modulepreload", "icon"):
            self.paths.append(values.get("href", ""))


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
    frontend = None
    with zipfile.ZipFile(archive) as plugin:
        if plugin.testzip() is not None:
            raise ValueError("Plugin ZIP is corrupt")
        for entry in plugin.namelist():
            if entry.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(plugin.read(entry))) as jar:
                    packaged.update(jar.namelist())
                    descriptor |= "META-INF/plugin.xml" in jar.namelist()
                    if "folio-web/asset-manifest.json" in jar.namelist():
                        if frontend is not None:
                            raise ValueError("More than one packaged frontend")
                        manifest = json.loads(jar.read("folio-web/asset-manifest.json"))
                        if not isinstance(manifest, list) or not manifest or len(set(manifest)) != len(manifest):
                            raise ValueError("Frontend asset manifest must be a nonempty unique list")
                        frontend = {}
                        for name in manifest:
                            if not isinstance(name, str) or not name or name.startswith("/") or ".." in Path(name).parts or "\\" in name:
                                raise ValueError(f"Unsafe packaged frontend asset: {name!r}")
                            data = jar.read("folio-web/" + name)
                            source = root / "web/dist" / name
                            if data != source.read_bytes():
                                raise ValueError(f"Packaged frontend differs from build output: {name}")
                            frontend[name] = hashlib.sha256(data).hexdigest()
                        if "index.html" not in frontend or not any(name.endswith(".js") for name in frontend) or not any(name.endswith(".css") for name in frontend):
                            raise ValueError("Frontend is missing its entry, JavaScript or stylesheet")
                        parser = EntryAssets()
                        parser.feed(jar.read("folio-web/index.html").decode("utf-8"))
                        for name in parser.paths:
                            if name.removeprefix("./") not in {*frontend, "boot.js"}:
                                raise ValueError(f"Frontend entry uses an unbundled asset: {name}")
                        declaration = ET.fromstring(jar.read("META-INF/plugin.xml"))
                        if "com.intellij.modules.jcef" not in [node.text for node in declaration.findall("depends")]:
                            raise ValueError("Plugin descriptor is missing its JCEF dependency")
    expected = {"cst3492/courseta/" + name + ".class" for name in (
        "FolioSession", "FolioBrowser", "FolioTasks", "CapturedCode", "ProviderAccess", "ProviderConnection", "Conversation",
        "drill/Drill",
        "index/CourseLibrary", "index/CourseSearch", "junitgen/GeneratedTest",
        "ask/HttpChatClient", "commit/GitDiffs",
    )}
    if not descriptor or expected - packaged:
        raise ValueError(f"Plugin is missing its descriptor or classes: {sorted(expected - packaged)}")
    legacy = {"cst3492/courseta/" + name + ".class" for name in ("FolioPanel", "FolioChrome", "FolioState", "FolioMotion")}
    if legacy & packaged:
        raise ValueError(f"Replaced Swing frontend is still packaged: {sorted(legacy & packaged)}")
    if frontend is None:
        raise ValueError("Plugin is missing its bundled HeroUI frontend")
    web_results = {name: frontend_result(name) for name in ("unit", "e2e")}
    summary = {
        "tests": sum(s["tests"] for s in suites.values()),
        "failures": 0, "errors": 0, "skipped": 0, "suites": suites,
        "plugin": str(archive.relative_to(root)), "bytes": archive.stat().st_size,
        "sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
        "frontend": {"reports": web_results, "assets": frontend},
    }
    destination = root / "build/verification/summary.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(summary, indent=2) + "\n")
    print(f"Verified {summary['tests']} tests in {len(suites)} suites and {archive.name} ({summary['bytes']} bytes).")
    print(f"Evidence: {destination.relative_to(root)}")
except (OSError, ValueError, KeyError, TypeError, ET.ParseError, zipfile.BadZipFile) as error:
    print(f"Verification failed: {error}", file=sys.stderr)
    sys.exit(1)
