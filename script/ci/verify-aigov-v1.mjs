#!/usr/bin/env node
/**
 * v1.0 优化分支的验收脚本：一条命令跑完"评审要看的东西"，并逐项给出结论。
 *
 * 为什么要有它（而不是把命令写在文档里）：
 *   这批交付的验证散成五六条命令，每条都有自己的坑——根 pom 默认 `maven.test.skip=true`
 *   （不显式关掉，后端测试会**静默跳过**并显示 BUILD SUCCESS）、前端测试必须传全部 .vue
 *   给 `check-script-setup.mjs` 才算覆盖达标、`vitest` 要跑全量而不是某个 preview 配置。
 *   评审人照着文档一条条敲，任何一个坑都会得到"看着通过了"的假结论。
 *   这里把它们固化成可执行的一步，并且**复用仓里已有的守卫脚本**（不另写一份判定逻辑）。
 *
 * 覆盖的 7 步：①后端测试（含全部守卫）②后端台账守卫（仅 --full-reactor）
 *   ③前端全量单测 ④spec 覆盖守卫 ⑤前端 lint ⑥**前端生产构建（vite build，与 CI 同一条命令）**
 *   ⑦全部 .vue 的 `<script setup>` 守卫 + 文档口径检查。
 *   ⑥ 是后补的：单测只加载"测试引用到的文件"，所以断掉的相对 import、模板多根节点这类问题
 *   在单测+lint 全绿时依然存在（本分支实测被它抓到两次）——CI 的前端镜像构建步骤才发现。
 *
 * 用法：
 *   node script/ci/verify-aigov-v1.mjs                 # 全跑（后端只跑治理模块，快）
 *   node script/ci/verify-aigov-v1.mjs --full-reactor  # 后端跑整个反应堆（与 CI 同口径，慢）
 *   node script/ci/verify-aigov-v1.mjs --offline       # Maven 加 -o（依赖已缓存时更快）
 *   node script/ci/verify-aigov-v1.mjs --java-home D:\path\to\jdk-21
 *   node script/ci/verify-aigov-v1.mjs --skip-backend  # 只跑前端与静态检查
 *   node script/ci/verify-aigov-v1.mjs --keep-reports  # 保留 vitest JSON 报告
 *
 * 它顺带跑 `check-aigov-docs.mjs`：核对文档里写的类名/脚本清单/测试数是否还和代码一致
 * （放在最后一步，因为它要吃前两步刚产出的报告）。
 *
 * 后端两种口径的差别：v1.0 的改动集中在 `ruoyi-ai-gov` 与前端，默认只跑那个模块（几十秒）；
 * `--full-reactor`（先 clean 再按 CI 的 `-Dtest.groups=!exclude` 跑整棵反应堆）会额外启用
 * "每个有测试的模块都必须产出 Surefire 报告"的台账守卫。
 *
 * ⚠️ `--full-reactor` 的前提与 CI 相同：**媒体工具（ffmpeg/ffprobe 等）要在 PATH 上**——
 *   CI 在跑测试前有一个 "verified-media-tools" 前置步骤；没有它们时 `ruoyi-modules/ruoyi-ai`
 *   会有 4 个媒体相关用例报"图片格式转换服务不可用"（CloudImageRendererTest 3 + 
 *   TemplateVideoPreviewCacheTest 1）。那是环境前提缺失，**不是本分支的回归**；
 *   脚本会在开跑前探测并提示。
 *
 * 退出码：全部通过 0；有任何一项失败 1。
 */

import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const IS_WINDOWS = process.platform === 'win32';
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..', '..');
const FRONTEND = join(ROOT, 'frontend');
const VITEST_JSON = join(FRONTEND, 'vitest-results.json');

const argv = process.argv.slice(2);
const flag = (name) => argv.includes(name);
const option = (name) => {
  const index = argv.indexOf(name);
  return index >= 0 ? argv[index + 1] : undefined;
};

const OFFLINE = flag('--offline');
const FULL_REACTOR = flag('--full-reactor');
const SKIP_BACKEND = flag('--skip-backend');
const SKIP_FRONTEND = flag('--skip-frontend');
const KEEP_REPORTS = flag('--keep-reports');

/** 结果台账 */
const results = [];

/**
 * 找一个可用的 JDK 21：显式参数 → 环境变量 → 仓库内/同级工作区里的 .tools/jdk-*。
 *
 * 本仓的 CI 与开发机都把 JDK 放在 `.tools/jdk-<version>`（仓库根或它的上一级），
 * 而 `JAVA_HOME` 常常没设——不自动找，"跑不起来"会被误读成"测试失败"。
 *
 * @returns JDK 目录或 undefined
 */
function findJavaHome() {
  const explicit = option('--java-home');
  if (explicit && existsSync(explicit)) {
    return resolve(explicit);
  }
  if (process.env.JAVA_HOME && existsSync(process.env.JAVA_HOME)) {
    return process.env.JAVA_HOME;
  }
  const bases = [ROOT, dirname(ROOT)];
  for (const base of bases) {
    const tools = join(base, '.tools');
    if (!existsSync(tools)) continue;
    const jdk = readdirSync(tools).find((name) => name.startsWith('jdk-'));
    if (jdk) return join(tools, jdk);
  }
  return undefined;
}

const JAVA_HOME = findJavaHome();

/** 子进程环境（把探到的 JDK 传给 Maven） */
const ENV = { ...process.env };
if (JAVA_HOME) {
  ENV.JAVA_HOME = JAVA_HOME;
  // ⚠️ Windows 上 `process.env` 的键是 **`Path`** 而不是 `PATH`（键名保留系统原始大小写）。
  //    直接写 `ENV.PATH = ...` 会多出一个 `PATH=undefined` 的键、而真正的 `Path` 没被改到；
  //    实测后果是子进程 PATH 变成 `<jdk>\bin;undefined`，于是 `mvnw.cmd` 找不到它依赖的
  //    `powershell`，报 "Cannot start maven from wrapper"——看起来像 Maven 坏了，其实是环境被写坏。
  const pathKey = Object.keys(ENV).find((key) => key.toLowerCase() === 'path') ?? 'PATH';
  ENV[pathKey] = `${join(JAVA_HOME, 'bin')}${IS_WINDOWS ? ';' : ':'}${ENV[pathKey] ?? ''}`;
}

/** 本机命令（Windows 上 mvnw 是 .cmd，只能经 cmd.exe 跑；其余一律用 node + JS 入口，避开 shell 与路径转义） */
const MVNW = IS_WINDOWS ? join(ROOT, 'mvnw.cmd') : join(ROOT, 'mvnw');
const VITEST_MJS = join(FRONTEND, 'node_modules', 'vitest', 'vitest.mjs');
const OXLINT_JS = join(FRONTEND, 'node_modules', 'oxlint', 'bin', 'oxlint');
const VITE_JS = join(FRONTEND, 'node_modules', 'vite', 'bin', 'vite.js');

/**
 * 跑一步并记账。
 *
 * @param name     步骤名（打印用）
 * @param why      这一步在防什么（打印用；评审人据此判断失败要不要拦住）
 * @param command  可执行文件
 * @param args     参数
 * @param extra    额外选项：{ cwd, optional }
 * @returns 是否成功
 */
function run(name, why, command, args, extra = {}) {
  const started = Date.now();
  console.log(`\n${'='.repeat(78)}\n▶ ${name}\n  防的是：${why}\n$ ${command} ${args.join(' ')}\n`);
  const result = spawnSync(command, args, {
    cwd: extra.cwd ?? ROOT,
    env: ENV,
    // stdio: 'inherit' 而不是管道：每一步的输出都是评审要看的证据，而且管道在受限沙箱里会 EPERM
    stdio: 'inherit',
    // 只有 Windows 上的 mvnw.cmd（批处理）需要 shell；JS 入口一律直接 exec，避免路径/引号问题
    shell: extra.shell ?? false
  });
  const seconds = ((Date.now() - started) / 1000).toFixed(1);
  const failed = result.error !== undefined || result.status !== 0;
  results.push({
    name,
    state: failed ? 'FAIL' : 'PASS',
    seconds,
    // 找不到可执行文件（例如前端没装依赖）与"跑失败"要分开说：前者是环境问题
    detail: result.error ? `${result.error.code ?? result.error.message}` : `exit=${result.status}`
  });
  if (failed) {
    console.log(`\n✗ ${name} 失败（${seconds}s, ${result.error ? result.error.message : `exit=${result.status}`}）`);
  } else {
    console.log(`\n✓ ${name} 通过（${seconds}s）`);
  }
  return !failed;
}

/** 跳过一步（记进台账，让"没跑"在结论里显式可见，而不是悄悄少一行） */
function skip(name, reason) {
  console.log(`\n${'='.repeat(78)}\n○ 跳过：${name}\n  原因：${reason}`);
  results.push({ name, state: 'SKIP', seconds: '-', detail: reason });
}

console.log('v1.0 优化分支验收');
console.log(`  仓库根：${ROOT}`);
console.log(`  JDK：${JAVA_HOME ?? '（未探到，依赖系统 PATH 上的 java）'}`);
console.log(`  Maven 离线模式：${OFFLINE ? '开（--offline）' : '关'}`);
console.log(`  后端范围：${FULL_REACTOR ? '整棵反应堆（先 clean，按 CI 的 test.groups 跑）' : '仅治理模块 ruoyi-ai-gov'}`);

// 环境前提探测：全反应堆模式会跑 ruoyi-ai 的媒体用例，需要 CI 前置步骤装好的 ffprobe/ffmpeg
if (FULL_REACTOR) {
  const probe = spawnSync('ffprobe', ['-version'], { stdio: 'ignore', shell: false });
  if (probe.error || probe.status !== 0) {
    console.log('  ⚠️ 没探到 ffprobe（CI 在跑测试前会装"verified-media-tools"）：'
      + 'ruoyi-modules/ruoyi-ai 会有 4 个媒体用例失败，属环境前提缺失，不是本分支的回归。');
  } else {
    console.log('  媒体工具：ffprobe 已就绪');
  }
}

let failed = false;

// ── 1) 后端：治理模块全量测试 ──────────────────────────────────────────────
if (SKIP_BACKEND) {
  skip('后端测试（ruoyi-ai-gov）', '--skip-backend');
} else {
  const args = [
    OFFLINE ? '-o' : null,
    // 默认只跑治理模块（v1.0 的改动都在这儿），用 -Plocal；全反应堆模式照 CI 的跑法：
    // CI 用的是 `-Dtest.groups=!exclude`（不带 -Plocal），两者会选中不同的测试集——
    // 实测 -Plocal 下 ruoyi-modules/ruoyi-ai 一份报告都不产出，台账守卫会因此判失败，
    // 而那不是代码问题、是"跑法不同"。要复现 CI 结论就必须用 CI 的跑法。
    ...(FULL_REACTOR ? ['-Dtest.groups=!exclude'] : ['-Plocal', '-pl', 'ruoyi-modules/ruoyi-ai-gov', '-am']),
    // ★ 全反应堆模式先 clean：台账守卫读的是**磁盘上所有** Surefire 报告，
    //   不清干净的话，上一次用别的 profile 跑出来的旧失败会被算进这一次
    //   （实测：两份来自更早构建的 XML 让守卫报「1 failure 1 error」，而本次 BUILD SUCCESS）。
    ...(FULL_REACTOR ? ['clean'] : []),
    'test',
    // ⚠️ 根 pom 默认 maven.test.skip=true：不显式关掉，测试会静默跳过且 BUILD SUCCESS
    '-Dmaven.test.skip=false'
  ].filter(Boolean);
  const backendName = FULL_REACTOR
    ? '后端测试（整棵反应堆，含全部守卫）'
    : '后端测试（ruoyi-ai-gov，含全部守卫）';
  if (!run(backendName,
    '根 pom 默认 maven.test.skip=true，不显式关掉会「0 测试但 BUILD SUCCESS」', MVNW, args,
    { shell: IS_WINDOWS })) {
    failed = true;
  } else if (!FULL_REACTOR) {
    skip('后端测试台账守卫（per-module Surefire 报告）',
      '它要求"每个有测试的模块都产出报告"，前提是跑整棵反应堆；'
      + '本脚本默认只跑治理模块（快）。要这条守卫请加 --full-reactor，或交给 CI');
  } else if (existsSync(join(ROOT, 'script', 'ci', 'check-test-results.py'))) {
    // 复用仓里的守卫：它防的是"某个模块的测试整体没跑"（缺 Surefire 报告）
    if (!run('后端测试台账守卫（per-module Surefire 报告）',
      '某个模块的测试整体没跑时，聚合看总数仍然绿',
      'python', [join('script', 'ci', 'check-test-results.py')], { optional: true })) {
      failed = true;
    }
  }
}

// ── 2) 前端：全量单测（JSON 报告 + 覆盖守卫） ────────────────────────────────
if (SKIP_FRONTEND) {
  skip('前端单测与 lint', '--skip-frontend');
} else {
  rmSync(VITEST_JSON, { force: true });
  const vitestOk = run('前端单测（vitest 全量）',
    '用 preview 配置跑只会覆盖少数 spec：测试存在但没跑，看起来和通过一样',
    process.execPath,
    [VITEST_MJS, 'run', '--reporter=default', '--reporter=json', '--outputFile', VITEST_JSON],
    { cwd: FRONTEND });
  if (!vitestOk) {
    failed = true;
  }
  // 复用仓里的守卫：核对"磁盘上的 spec 全都跑了"
  if (existsSync(VITEST_JSON)) {
    if (!run('前端 spec 覆盖守卫（check-frontend-tests）',
      '磁盘上存在但没被执行的 spec 文件（include 配置漂移）',
      process.execPath,
      [join('script', 'ci', 'check-frontend-tests.mjs'), VITEST_JSON, FRONTEND])) {
      failed = true;
    }
  } else {
    skip('前端 spec 覆盖守卫（check-frontend-tests）', 'vitest 没产出 JSON 报告（上一步可能已失败）');
  }

  if (!run('前端 lint（oxlint src）', '未使用变量/危险写法等会在构建期之外漏掉',
    process.execPath, [OXLINT_JS, 'src'], { cwd: FRONTEND })) {
    failed = true;
  }

  // 生产构建（与 CI 的 `pnpm build:prod` 同一条命令）。
  // 为什么必须有这一步：vitest 只加载"测试引用到的文件"，sfcCompile 只编译 SFC 自身、
  // 不解析它的 import —— 于是"相对路径写错（断掉的 import）"和"模板多根节点"这两类问题
  // 在单测 + lint + 全部守卫全绿的情况下依然存在，只有 vite build 会失败。
  // （本分支实测被它抓到两次：TaskFlowPanel 的 `./flow` 应为 `../flow`、
  //   ProposalPanel 的 el-card + el-dialog 双根节点；两者都在 CI 的前端镜像构建步骤才暴露。）
  if (!run('前端生产构建（vite build，同 CI）',
    '断掉的 import / 模板多根节点等「只有构建期才会失败」的问题：单测与 lint 都看不见',
    process.execPath, [VITE_JS, 'build', '--mode', 'production'], { cwd: FRONTEND })) {
    failed = true;
  }

  // 全部 .vue 都要传：脚本自己会判"覆盖度不足"（少传就是假通过）。
  // Windows 上 200+ 个路径会超出命令行长度上限，所以走 --files-from 列表文件。
  const vueFiles = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir)) {
      const full = join(dir, entry);
      if (statSync(full).isDirectory()) {
        if (entry !== 'node_modules') walk(full);
      } else if (entry.endsWith('.vue')) {
        vueFiles.push(full.slice(FRONTEND.length + 1).split('\\').join('/'));
      }
    }
  };
  walk(join(FRONTEND, 'src'));
  const listFile = join(tmpdir(), 'hotter-vue-files.txt');
  // 路径写成"仓库根相对"（frontend/src/...），与守卫从仓库根扫描得到的集合同形
  writeFileSync(listFile, vueFiles.map((file) => `frontend/${file}`).join('\n'));
  try {
    if (!run('前端 <script setup> 守卫（全部 .vue）',
      '少传文件时脚本会判覆盖度不足；只传改动文件也会失败',
      process.execPath,
      [join('script', 'ci', 'check-script-setup.mjs'), '--files-from', listFile], { cwd: ROOT })) {
      failed = true;
    }
  } finally {
    rmSync(listFile, { force: true });
  }
}

// ── 3) 文档口径：文档里写的类/脚本/测试数是否与代码和刚跑出来的报告一致 ────────
//   放在最后一步，因为它的"数字"检查要吃前两步刚产出的报告（surefire XML 与 vitest JSON）
if (!run('文档口径检查（check-aigov-docs）',
  '文档里的类名/脚本清单/测试数会静默漂移：代码改名或测试数前移都不会让任何测试变红',
  process.execPath, [join('script', 'ci', 'check-aigov-docs.mjs')])) {
  failed = true;
}

if (!KEEP_REPORTS) {
  rmSync(VITEST_JSON, { force: true });
}
// ── 结论 ───────────────────────────────────────────────────────────────────
console.log(`\n${'='.repeat(78)}\n验收结论\n`);
console.log('| 步骤 | 结论 | 耗时(s) | 说明 |');
console.log('| --- | --- | --- | --- |');
for (const item of results) {
  console.log(`| ${item.name} | ${item.state} | ${item.seconds} | ${item.detail} |`);
}
const passed = results.filter((item) => item.state === 'PASS').length;
const skipped = results.filter((item) => item.state === 'SKIP').length;
const failedCount = results.filter((item) => item.state === 'FAIL').length;
console.log(`\n合计：${passed} 通过 / ${failedCount} 失败 / ${skipped} 跳过`);
if (failedCount > 0) {
  console.log('\n❌ 有步骤失败：请按上面的输出逐项排查（不要只看最后一行 BUILD SUCCESS）。');
  process.exit(1);
}
console.log('\n✅ 全部通过。注意：界面「观感」不在这条命令的覆盖范围内——');
console.log('   训练台三栏布局等视觉项需要人眼看一眼（本仓前端没有 DOM 测试环境）。');
