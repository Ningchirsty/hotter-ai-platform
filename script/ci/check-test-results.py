"""Fail CI when Surefire did not execute tests or reported unsuccessful tests."""

from pathlib import Path
import os
import xml.etree.ElementTree as ET


reports = sorted(Path('.').glob('**/target/surefire-reports/TEST-*.xml'))
totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, '0'))

executed = totals['tests'] - totals['skipped']
summary = (
    f"Surefire: {len(reports)} reports, {executed} executed, "
    f"{totals['skipped']} skipped, {totals['failures']} failures, "
    f"{totals['errors']} errors."
)
print(summary)
if summary_path := os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(summary_path, 'a', encoding='utf-8') as output:
        output.write(f'### Unit tests\n\n{summary}\n')

if executed <= 0 or totals['failures'] or totals['errors']:
    raise SystemExit('Expected at least one executed test and no failures/errors.')
