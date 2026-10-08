"""Fail CI when Surefire did not execute tests, reported unsuccessful tests, or silently skipped a module.

Why the per-module rule exists (2026-10-08): this gate used to aggregate every
`target/surefire-reports/TEST-*.xml` in the repository and only require
`executed > 0` **in total**. That means a whole module's tests could stop running
-- deleted, excluded, or its module dropped from the reactor -- and CI would stay
green as long as any other module still ran something. That is the same
"silently did not run" failure mode as the `@EnableScheduling` gap this repo hit:
absence of evidence looked exactly like success.

So now: every module that HAS test sources must have produced at least one
Surefire report. Excluded-by-tag modules are fine (a report with skipped tests
still exists); what is no longer allowed is a test-bearing module that reports
nothing at all.
"""

from pathlib import Path
import os
import xml.etree.ElementTree as ET

# Modules that genuinely have no runnable tests today may be listed here, with a
# reason. Keep it empty if possible: an entry is a promise nobody is checking.
MODULES_ALLOWED_WITHOUT_REPORTS: dict[str, str] = {}


def module_root_of(path: Path) -> Path | None:
    """Walk up from a marker path to the nearest ancestor that contains pom.xml."""
    current = path
    while current != current.parent:
        if (current / 'pom.xml').is_file():
            return current
        current = current.parent
    return None


def modules_with_test_sources() -> dict[Path, int]:
    """Every Maven module that actually contains Java test sources, with its file count."""
    found: dict[Path, int] = {}
    for marker in Path('.').glob('**/src/test/java'):
        if not marker.is_dir():
            continue
        java_files = [p for p in marker.rglob('*.java') if p.is_file()]
        if not java_files:
            continue
        module = module_root_of(marker)
        if module is None:
            print(f'note: {marker} has test sources but no ancestor pom.xml; ignored')
            continue
        found[module] = found.get(module, 0) + len(java_files)
    return found


def report_module_of(report: Path) -> Path:
    """<module>/target/surefire-reports/TEST-x.xml -> <module>"""
    return report.parent.parent.parent


reports = sorted(Path('.').glob('**/target/surefire-reports/TEST-*.xml'))
totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
per_module: dict[Path, dict[str, int]] = {}
for report in reports:
    suite = ET.parse(report).getroot()
    module = report_module_of(report)
    bucket = per_module.setdefault(module, dict.fromkeys(totals, 0))
    for key in totals:
        value = int(suite.get(key, '0'))
        totals[key] += value
        bucket[key] += value

executed = totals['tests'] - totals['skipped']
summary = (
    f"Surefire: {len(reports)} reports from {len(per_module)} modules, "
    f"{executed} executed, {totals['skipped']} skipped, "
    f"{totals['failures']} failures, {totals['errors']} errors."
)
print(summary)

lines = [summary, '', '| module | reports tests | executed | skipped | failures | errors |', '|---|---|---|---|---|---|']
for module in sorted(per_module):
    bucket = per_module[module]
    lines.append(
        f"| `{module.as_posix()}` | {bucket['tests']} | {bucket['tests'] - bucket['skipped']} | "
        f"{bucket['skipped']} | {bucket['failures']} | {bucket['errors']} |"
    )

test_modules = modules_with_test_sources()
missing = sorted(
    module for module in test_modules
    if module not in per_module and str(module) not in MODULES_ALLOWED_WITHOUT_REPORTS
)
if test_modules:
    lines += ['', f'{len(test_modules)} module(s) contain test sources; {len(test_modules) - len(missing)} reported results.']
if missing:
    lines += ['', '**Modules with test sources but NO Surefire report:**']
    lines += [f"- `{module.as_posix()}` ({test_modules[module]} test file(s))" for module in missing]

if summary_path := os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(summary_path, 'a', encoding='utf-8') as output:
        output.write('### Unit tests\n\n' + '\n'.join(lines) + '\n')

problems: list[str] = []
if executed <= 0:
    problems.append('no test was executed')
if totals['failures'] or totals['errors']:
    problems.append(f"{totals['failures']} failure(s) and {totals['errors']} error(s) reported")
if missing:
    problems.append(
        'these modules have test sources but produced no Surefire report '
        '(excluded from the build, or their tests vanished): '
        + ', '.join(str(module) for module in missing)
    )

if problems:
    raise SystemExit('Test gate failed: ' + '; '.join(problems) + '.')
print(f'Test gate OK: {executed} executed, {len(per_module)} module(s) reported.')
