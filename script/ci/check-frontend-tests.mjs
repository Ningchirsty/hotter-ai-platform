/**
 * Fail CI when the frontend test run did not include every spec file that exists on disk.
 *
 * Why (2026-10-08): the frontend CI step ran vitest with
 * `--config preview/inspiration-vitest.config.mts`, whose `include` listed only 5 spec
 * files while the repository had 28. Measured: that config reported
 * "Test Files 5 passed (5) / Tests 33 passed (33)" whereas the full suite is
 * "28 files / 244 tests" -- so 23 files (211 tests) never ran in CI, including the
 * aigov UI tests. A test that exists but does not run looks exactly like a passing
 * test; this script makes that difference observable.
 *
 * Usage: node script/ci/check-frontend-tests.mjs <vitest-json-results> <frontend-root>
 */

import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';

const SPEC_PATTERN = /\.(test|spec)\.(ts|tsx|js|mjs|cjs)$/;
const resultsPath = process.argv[2] ?? 'frontend/vitest-results.json';
// Absolute, so `relative()` is correct regardless of the directory the guard is invoked from
// (vitest reports absolute suite paths).
const frontendRoot = resolve(process.argv[3] ?? 'frontend');

/** Every spec/test file under <frontendRoot>/src, as posix paths relative to frontendRoot. */
function collectSpecs(dir, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules') collectSpecs(full, found);
    } else if (SPEC_PATTERN.test(entry.name)) {
      found.push(full);
    }
  }
  return found;
}

const toPosix = (path) => path.split(sep).join('/');
const expected = collectSpecs(join(frontendRoot, 'src')).map((p) => toPosix(relative(frontendRoot, p))).sort();

const results = JSON.parse(readFileSync(resultsPath, 'utf8'));
const suites = results.testResults ?? [];
const ran = suites.map((suite) => toPosix(relative(frontendRoot, suite.name))).sort();

const missing = expected.filter((file) => !ran.includes(file));
const unexpected = ran.filter((file) => !expected.includes(file));
const total = results.numTotalTests ?? 0;
const failed = results.numFailedTests ?? 0;
const passed = results.numPassedTests ?? 0;

const summary =
  `Vitest: ${ran.length} of ${expected.length} spec file(s) ran, ` +
  `${passed}/${total} tests passed, ${failed} failed.`;
console.log(summary);

const lines = [summary, '', '| spec file | result |', '|---|---|'];
for (const suite of suites) {
  const file = toPosix(relative(frontendRoot, suite.name));
  const bad = (suite.assertionResults ?? []).filter((t) => t.status === 'failed').length;
  lines.push(`| \`${file}\` | ${bad === 0 ? 'pass' : `${bad} failed`} |`);
}
if (missing.length > 0) {
  lines.push('', '**Spec files on disk that did NOT run:**');
  lines.push(...missing.map((file) => `- \`${file}\``));
}
if (unexpected.length > 0) {
  lines.push('', `Note: ${unexpected.length} file(s) ran that are not under src/ spec discovery: ${unexpected.join(', ')}`);
}

if (process.env.GITHUB_STEP_SUMMARY) {
  writeFileSync(process.env.GITHUB_STEP_SUMMARY, `### Frontend unit tests\n\n${lines.join('\n')}\n`, { flag: 'a' });
}

const problems = [];
if (total <= 0) problems.push('no test was executed');
if (failed > 0 || results.success === false) problems.push(`${failed} test(s) failed`);
if (missing.length > 0) {
  problems.push(
    `${missing.length} spec file(s) exist on disk but did not run ` +
      '(the vitest include pattern no longer matches them): ' + missing.join(', '),
  );
}

if (problems.length > 0) {
  throw new Error(`Frontend test gate failed: ${problems.join('; ')}.`);
}
console.log(`Frontend test gate OK: all ${expected.length} spec file(s) ran.`);
