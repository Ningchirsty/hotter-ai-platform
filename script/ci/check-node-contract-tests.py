"""Fail CI when the Node contract tests did not really run.

Why this exists (2026-10-09). The CI step used to run two hardcoded commands:

    node --test script/video/workflows/import-h3.test.mjs
    node --test script/image/workflows/image-contracts.test.mjs

and assert nothing about the result. `node --test` exits **0** when a file defines or
skips zero tests, so deleting the bodies (or commenting a file's tests out) left CI green
while nothing was verified. That is the same "silently did not run" failure mode this repo
already hit twice: the Surefire gate (`check-test-results.py`) and the missing
`@EnableScheduling` (a scheduled job that never ran and never complained).

Two holes are closed here:

1. **a test file that stopped covering anything** -> caught, but note *how* (measured
   2026-10-09 on Node 25): a `*.test.mjs` whose `test()` calls are deleted is counted by
   `node --test` as **one passing test** (the file itself), so `tests == 0` alone would
   never fire. What actually catches it is the per-file **floor** below (1 < floor), and
   `pass == 0` catches the "everything is `test.skip`" shape. Both shapes were verified by
   injecting a probe file and watching this gate fail.
2. **a new `*.test.mjs` file that nobody wired into CI** -> it is discovered automatically
   and MUST appear in the floor table below, so it cannot be left out silently.

Floors are **ratchets, not exact counts**: adding tests needs no edit here, while deleting
tests requires lowering the number — a deliberate act with a visible diff.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

# 每个契约测试文件的通过数下限（棘轮）。新增文件必须在这里登记，否则本守卫直接失败。
TEST_FLOORS: dict[str, int] = {
    'script/image/workflows/image-contracts.test.mjs': 13,
    'script/video/workflows/import-h3.test.mjs': 3,
}

SEARCH_ROOT = Path('script')
SUMMARY_RE = re.compile(r'^#\s+(tests|pass|fail|skipped|cancelled)\s+(\d+)\s*$', re.MULTILINE)


def discover() -> list[Path]:
    """Every *.test.mjs under script/ — the point is that CI cannot miss a new one."""
    return sorted(p for p in SEARCH_ROOT.rglob('*.test.mjs') if p.is_file())


def run_one(path: Path) -> tuple[dict[str, int], str]:
    """Run one file with the TAP reporter and parse its summary block."""
    # 必须显式按 UTF-8 解码：text=True 在 Windows 上会用本地代码页（GBK）解子进程输出，
    # 测试名里有中文时直接 UnicodeDecodeError，于是"没读到摘要"会被误判成"零测试"。
    # CI 是 Linux/UTF-8 不受影响，但守卫本身不该因为跑在哪儿而给出不同结论。
    proc = subprocess.run(
        ['node', '--test', '--test-reporter=tap', str(path)],
        capture_output=True, text=True, encoding='utf-8', errors='replace',
    )
    output = (proc.stdout or '') + (proc.stderr or '')
    counts = {name: int(value) for name, value in SUMMARY_RE.findall(output)}
    if proc.returncode != 0 and not counts:
        counts['fail'] = counts.get('fail', 0) + 1
    return counts, output


def main() -> int:
    # 控制台代码页可能是 GBK（本机就是），打印子进程里的中文会抛 UnicodeEncodeError；
    # 显式 reconfigure 成 UTF-8 + replace，跑在哪儿都不会因为"打印"而失败。
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    except Exception:  # noqa: BLE001 - 老版本 Python 没有 reconfigure 时不影响判定
        pass

    files = discover()
    problems: list[str] = []
    lines = ['| file | tests | pass | fail | skipped | floor |', '|---|---|---|---|---|---|']

    if not files:
        problems.append('no *.test.mjs file found under script/ (were they moved?)')

    undeclared = [p.as_posix() for p in files if p.as_posix() not in TEST_FLOORS]
    if undeclared:
        problems.append(
            'these contract-test files are not registered in TEST_FLOORS, so CI would '
            'silently stop covering them: ' + ', '.join(undeclared)
        )

    total_pass = 0
    for path in files:
        key = path.as_posix()
        floor = TEST_FLOORS.get(key)
        counts, output = run_one(path)
        tests = counts.get('tests', 0)
        passed = counts.get('pass', 0)
        failed = counts.get('fail', 0)
        skipped = counts.get('skipped', 0)
        total_pass += passed
        lines.append(f"| `{key}` | {tests} | {passed} | {failed} | {skipped} | {floor if floor is not None else '—'} |")

        if tests == 0 or passed == 0:
            problems.append(f'{key}: no test ran (tests={tests}, pass={passed}) — '
                            f'"did not run" must not look like success')
        if failed:
            problems.append(f'{key}: {failed} failing test(s)')
        if floor is not None and passed < floor:
            problems.append(f'{key}: pass={passed} is below the declared floor {floor} — '
                            f'if tests were removed on purpose, lower the floor in this file')

        print(f'--- {key} ---')
        print('\n'.join(line for line in output.splitlines()
                        if line.startswith('# ') or line.startswith('not ok')))

    stale = [key for key in TEST_FLOORS if not Path(key).is_file()]
    if stale:
        problems.append('TEST_FLOORS lists files that no longer exist: ' + ', '.join(sorted(stale)))

    summary = (f"Node contract tests: {len(files)} file(s), {total_pass} passing test(s), "
               f"floors declared for {len(TEST_FLOORS)} file(s).")
    print(summary)
    if summary_path := os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(summary_path, 'a', encoding='utf-8') as output:
            output.write('### Node contract tests\n\n' + summary + '\n\n' + '\n'.join(lines) + '\n')

    if problems:
        print('\nNode contract-test gate failed:')
        for problem in problems:
            print(f'  - {problem}')
        return 1
    print('Node contract-test gate OK.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
