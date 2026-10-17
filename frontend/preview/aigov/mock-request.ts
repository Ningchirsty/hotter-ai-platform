/**
 * 治理台界面预览（**假数据，不连后端**）。
 *
 * 目的：本仓前端没有 DOM 测试环境，"观感"只能靠人眼。这里把真实页面组件
 * （`views/aigov/**`）挂到浏览器里，API 换成下面这份假数据，于是**不需要后端、不需要登录**就能看界面。
 *
 * 纪律：
 * - 只影响本预览入口（`aigov-preview.vite.config.ts` 把 `@/utils/request` 指到这里）；
 * - 生产入口、真实 API、权限指令一律不变；
 * - 写操作一律拒绝（预览不是环境），只回一个可读提示；
 * - 没被 mock 的 URL 不做臆测：返回空形状并在页面顶部列出"未 mock 的请求"，避免"看起来一切正常"。
 */

export interface UnmockedRecord {
  method: string;
  url: string;
}

/** 预览里发生的未 mock 请求（App.vue 顶部会显示） */
export const unmockedRequests: UnmockedRecord[] = [];

const ok = <T>(data: T) => Promise.resolve({ code: 200, msg: 'ok', data });

/** 假 ID：19 位雪花形状，用来验证长 ID 折叠 */
const sid = (n: number) => String(1763200000000000000n + BigInt(n));

const capabilities = [
  { capabilityId: sid(101), capabilityCode: 'talent_match', capabilityName: '人才匹配', requiredTags: 'TEXT,EMBEDDING', status: '0' },
  { capabilityId: sid(102), capabilityCode: 'brief_precheck', capabilityName: '需求预检', requiredTags: 'TEXT', status: '0' },
  { capabilityId: sid(103), capabilityCode: 'poster_copy', capabilityName: '海报文案', requiredTags: 'TEXT,IMAGE', status: '0' },
  { capabilityId: sid(104), capabilityCode: 'video_brief', capabilityName: '视频脚本', requiredTags: 'TEXT,VIDEO', status: '1' }
];

const providers = [
  { providerId: sid(201), providerName: '本地 vLLM', providerKey: 'local_vllm', isEnabled: true, modelCount: 3, description: '内网部署，不外发' },
  { providerId: sid(202), providerName: '阿里云百炼', providerKey: 'aliyun_bailian', isEnabled: true, modelCount: 5, description: '外部服务，按调用计费' },
  { providerId: sid(203), providerName: '停用的旧网关', providerKey: 'legacy_gw', isEnabled: false, modelCount: 1, description: '已停用，仅留历史' }
];

const models = [
  {
    modelId: sid(301), modelKey: 'qwen2.5-72b', modelName: '通义千问 72B', providerId: sid(201), providerName: '本地 vLLM',
    deploymentType: 'LOCAL', dataLevelMax: 'STRICT', lifecycleStatus: 'PRODUCTION', isEnabled: true,
    healthStatus: 'HEALTHY', secretRef: 'kms://ai/qwen', apiEndpoint: 'http://vllm.internal:8000/v1', isDefault: true,
    inputLimits: '32k', outputLimits: '4k', costLimit: '无', lastTestedAt: '2026-10-16 09:12:00'
  },
  {
    modelId: sid(302), modelKey: 'qwen-vl-max', modelName: '通义千问 VL', providerId: sid(202), providerName: '阿里云百炼',
    deploymentType: 'EXTERNAL_API', dataLevelMax: 'INTERNAL', lifecycleStatus: 'GRAY', isEnabled: true,
    healthStatus: 'UNHEALTHY', secretRef: 'kms://ai/bailian', apiEndpoint: 'https://dashscope.aliyuncs.com/v1', isDefault: false,
    inputLimits: '16k', outputLimits: '2k', costLimit: '200/日', lastTestedAt: '2026-10-16 08:40:00'
  },
  {
    modelId: sid(303), modelKey: 'bge-m3', modelName: 'BGE-M3 向量', providerId: sid(201), providerName: '本地 vLLM',
    deploymentType: 'LOCAL', dataLevelMax: 'RESTRICTED', lifecycleStatus: 'PRODUCTION', isEnabled: true,
    healthStatus: 'UNKNOWN', secretRef: '', apiEndpoint: '', isDefault: false,
    inputLimits: '8k', outputLimits: '—', costLimit: '无', lastTestedAt: ''
  },
  {
    modelId: sid(304), modelKey: 'legacy-llm-v1', modelName: '旧网关模型', providerId: sid(203), providerName: '停用的旧网关',
    deploymentType: 'EXTERNAL_ENTERPRISE', dataLevelMax: 'INTERNAL', lifecycleStatus: 'SUSPENDED', isEnabled: false,
    healthStatus: 'DOWN', secretRef: 'kms://ai/legacy', apiEndpoint: 'https://legacy.example.com/v1', isDefault: false,
    inputLimits: '4k', outputLimits: '1k', costLimit: '50/日', lastTestedAt: '2026-10-10 17:00:00'
  }
];

const routePolicies = [
  { policyId: sid(401), capabilityCode: 'talent_match', capabilityName: '人才匹配', dataLevel: 'STRICT', preferredDeployment: 'LOCAL', allowExternal: 'N', requireApproval: 'Y', fallbackToManual: 'Y', status: '0', remark: '人才数据只在本内网跑' },
  { policyId: sid(402), capabilityCode: 'talent_match', capabilityName: '人才匹配', dataLevel: 'INTERNAL', preferredDeployment: 'LOCAL', allowExternal: 'Y', requireApproval: 'Y', fallbackToManual: 'Y', status: '0', remark: '放宽：内部级可外发（需审批）' },
  { policyId: sid(403), capabilityCode: 'brief_precheck', capabilityName: '需求预检', dataLevel: 'PUBLIC', preferredDeployment: 'EXTERNAL_API', allowExternal: 'Y', requireApproval: 'N', fallbackToManual: 'Y', status: '0', remark: '公开级直接外发' },
  { policyId: sid(404), capabilityCode: 'poster_copy', capabilityName: '海报文案', dataLevel: 'INTERNAL', preferredDeployment: 'LOCAL', allowExternal: 'Y', requireApproval: 'N', fallbackToManual: 'N', status: '0', remark: '内部级外发，不要求审批' },
  { policyId: sid(405), capabilityCode: 'video_brief', capabilityName: '视频脚本', dataLevel: 'RESTRICTED', preferredDeployment: 'GROUP', allowExternal: 'N', requireApproval: 'Y', fallbackToManual: 'Y', status: '1', remark: '已停用（旧策略）' },
  { policyId: sid(406), capabilityCode: 'poster_copy', capabilityName: '海报文案', dataLevel: 'RESTRICTED', preferredDeployment: 'LOCAL', allowExternal: 'N', requireApproval: 'Y', fallbackToManual: 'Y', status: '0', remark: '' }
];

const audits = [
  { auditId: sid(501), traceId: 'a1b2c3d4e5f60718293a4b5c6d7e8f90', capabilityCode: 'talent_match', capabilityName: '人才匹配', callerName: '李四', dataLevel: 'STRICT', modelKey: 'qwen2.5-72b', modelVersion: 'v3', providerName: '本地 vLLM', externalCall: 'N', result: '0', latencyMs: 1820, cost: 0, manualDecision: 'APPROVED', operateTime: '2026-10-16 10:02:11', usageJson: '{"prompt_tokens":812,"completion_tokens":233}' },
  { auditId: sid(502), traceId: 'b2c3d4e5f60718293a4b5c6d7e8f90a1', capabilityCode: 'poster_copy', capabilityName: '海报文案', callerName: '王五', dataLevel: 'INTERNAL', modelKey: 'qwen-vl-max', modelVersion: 'v2', providerName: '阿里云百炼', externalCall: 'Y', result: '1', errorClass: 'PROVIDER_ERROR', errorSummary: '上游 429 限流，重试 2 次仍失败', latencyMs: 6120, cost: 0.12, operateTime: '2026-10-16 09:58:03', usageJson: '{"error":"429"}' },
  { auditId: sid(503), traceId: 'c3d4e5f60718293a4b5c6d7e8f90a1b2', capabilityCode: 'video_brief', capabilityName: '视频脚本', callerName: '赵六', dataLevel: 'RESTRICTED', modelKey: 'qwen2.5-72b', modelVersion: 'v3', providerName: '本地 vLLM', externalCall: 'N', result: '1', errorClass: 'POLICY_BLOCKED', errorSummary: '限制级数据禁止外发，候选全部被排除', latencyMs: 12, cost: 0, operateTime: '2026-10-16 09:31:40' },
  { auditId: sid(504), traceId: 'd4e5f60718293a4b5c6d7e8f90a1b2c3', capabilityCode: 'brief_precheck', capabilityName: '需求预检', callerName: '孙七', dataLevel: 'PUBLIC', modelKey: 'qwen-vl-max', modelVersion: 'v2', providerName: '阿里云百炼', externalCall: 'Y', result: '0', latencyMs: 930, cost: 0.03, operateTime: '2026-10-16 09:20:15' },
  { auditId: sid(505), traceId: 'e5f60718293a4b5c6d7e8f90a1b2c3d4', capabilityCode: 'talent_match', capabilityName: '人才匹配', callerName: '李四', dataLevel: 'STRICT', modelKey: 'qwen2.5-72b', modelVersion: 'v3', providerName: '本地 vLLM', externalCall: 'N', result: '0', latencyMs: 2210, cost: 0, manualDecision: 'PENDING', operateTime: '2026-10-16 08:55:02' }
];

const tools = [
  { toolCode: 'resume_parse', toolName: '简历解析', version: 'v2', operationType: 'READ', operationTypeDesc: '只读解析', requiresConfirmation: false, executable: true, permissionPoint: 'aig:tool:invoke', dataLevelMax: 'RESTRICTED' },
  { toolCode: 'poster_render', toolName: '海报渲染', version: 'v1', operationType: 'WRITE', operationTypeDesc: '写操作（产出文件）', requiresConfirmation: true, executable: true, permissionPoint: 'aig:tool:invoke', dataLevelMax: 'INTERNAL' },
  { toolCode: 'crm_update', toolName: 'CRM 回写', version: 'v1', operationType: 'WRITE', operationTypeDesc: '写外部系统', requiresConfirmation: true, executable: false, permissionPoint: 'aig:tool:invoke', dataLevelMax: 'INTERNAL' },
  { toolCode: 'zhihu_search', toolName: '知乎检索', version: 'v1', operationType: 'READ', operationTypeDesc: '外部检索', requiresConfirmation: false, executable: false, permissionPoint: 'aig:tool:invoke', dataLevelMax: 'PUBLIC' }
];

const roleVersions = [
  { roleVersionId: sid(601), roleId: sid(600), roleCode: 'copywriter', roleName: '文案岗', version: 'v4', releaseStatus: 'PUBLISHED', rolloutChannel: '全员', manifestSha256: 'a'.repeat(64), publishedAt: '2026-10-10 12:00:00', status: '0', visibleToEmployees: true, allowedTransitions: ['DISABLED', 'ARCHIVED'] },
  { roleVersionId: sid(602), roleId: sid(600), roleCode: 'copywriter', roleName: '文案岗', version: 'v5', releaseStatus: 'TESTING', rolloutChannel: '试点', manifestSha256: 'b'.repeat(64), status: '0', visibleToEmployees: false, allowedTransitions: ['SANDBOX_TESTED', 'ARCHIVED'] },
  { roleVersionId: sid(603), roleId: sid(600), roleCode: 'video_editor', roleName: '视频剪辑岗', version: 'v2', releaseStatus: 'DRAFT', rolloutChannel: '—', manifestSha256: 'c'.repeat(64), status: '0', visibleToEmployees: false, allowedTransitions: ['VALIDATED', 'ARCHIVED'] },
  { roleVersionId: sid(604), roleId: sid(600), roleCode: 'ops', roleName: '运营岗', version: 'v1', releaseStatus: 'DISABLED', rolloutChannel: '—', manifestSha256: 'd'.repeat(64), disabledAt: '2026-09-30 10:00:00', status: '0', visibleToEmployees: false, allowedTransitions: ['ARCHIVED'] }
];

const evaluationCases = [
  { caseId: sid(701), caseCode: 'CASE_TALENT_001', caseName: '人才匹配·硬性条件筛选', caseType: 'PLAN', scenarioCode: 'recruit', dataLevel: 'RESTRICTED', costMin: 0.01, costMax: 0.05, classification: 'REGRESSION', status: '0' },
  { caseId: sid(702), caseCode: 'CASE_POSTER_002', caseName: '海报文案·品牌语气', caseType: 'VISUAL_DNA', scenarioCode: 'marketing', dataLevel: 'INTERNAL', costMin: 0.02, costMax: 0.08, classification: 'REGRESSION', status: '0' },
  { caseId: sid(703), caseCode: 'CASE_IMAGE_003', caseName: '图片问答·素材合规', caseType: 'IMAGE_QA', scenarioCode: 'compliance', dataLevel: 'STRICT', costMin: 0.01, costMax: 0.03, classification: 'AD_HOC', status: '0' }
];

const evaluationRuns = [
  { runId: sid(801), runNo: 'RUN-20261016-01', targetType: 'AGENT_VERSION', caseId: sid(701), modelCode: 'qwen2.5-72b', executedBy: 'PLATFORM', externalCall: 'N', totalScore: 0.92, reviewResult: null, resultStatus: 'PASS', latencyMs: 2100, costAmount: 0.02, operateTime: '2026-10-16 09:00:00', traceId: 'f'.repeat(32) },
  { runId: sid(802), runNo: 'RUN-20261016-02', targetType: 'AGENT_VERSION', caseId: sid(702), modelCode: 'qwen-vl-max', executedBy: 'PLATFORM', externalCall: 'Y', totalScore: 0.61, reviewResult: null, resultStatus: 'FAIL', latencyMs: 3400, costAmount: 0.05, operateTime: '2026-10-16 09:05:00', traceId: 'e'.repeat(32) },
  { runId: sid(803), runNo: 'RUN-20261016-03', targetType: 'AGENT_VERSION', caseId: sid(703), modelCode: 'qwen-vl-max', executedBy: 'ADMIN', externalCall: 'Y', totalScore: 0, reviewResult: 'MANUAL', resultStatus: 'ERROR', latencyMs: 0, costAmount: 0, operateTime: '2026-10-16 09:07:00', traceId: 'd'.repeat(32) },
  { runId: sid(804), runNo: 'RUN-20261016-04', targetType: 'AGENT_VERSION', caseId: sid(701), modelCode: 'qwen2.5-72b', executedBy: 'PLATFORM', externalCall: 'N', totalScore: 0.88, reviewResult: 'PASS', resultStatus: 'PASS', latencyMs: 1900, costAmount: 0.02, operateTime: '2026-10-16 08:40:00', traceId: 'c'.repeat(32) }
];

const portalRoles = [
  { roleCode: 'copywriter', roleName: '文案岗', description: '写文案、做物料', version: 'v4', actionCount: 6, categories: [{ categoryCode: 'write', categoryName: '写作' }] },
  { roleCode: 'video_editor', roleName: '视频剪辑岗', description: '剪视频、配字幕', version: 'v2', actionCount: 4, categories: [{ categoryCode: 'video', categoryName: '视频' }] }
];

const portalTasks = [
  { taskId: sid(901), taskNo: 'T-20261016-001', taskType: 'GENERATE', capabilityCode: 'poster_copy', scenarioCode: 'marketing', status: 'RUNNING', statusLabel: '生成中', businessStatus: 'GENERATING', businessStatusLabel: '草稿已生成 待复核', progress: 60, dataLevel: 'INTERNAL', startedAt: '2026-10-16 09:00:00' },
  { taskId: sid(902), taskNo: 'T-20261016-002', taskType: 'GENERATE', capabilityCode: 'talent_match', scenarioCode: 'recruit', status: 'SUCCEEDED', statusLabel: '已完成', businessStatus: 'RESULT_CONFIRMED', businessStatusLabel: '已完成', progress: 100, dataLevel: 'STRICT', startedAt: '2026-10-16 08:10:00', finishedAt: '2026-10-16 08:14:20' },
  { taskId: sid(903), taskNo: 'T-20261016-003', taskType: 'GENERATE', capabilityCode: 'video_brief', scenarioCode: 'marketing', status: 'FAILED', statusLabel: '失败', businessStatus: 'FAILED', businessStatusLabel: '失败（限制级禁止外发）', progress: 10, dataLevel: 'RESTRICTED', startedAt: '2026-10-16 07:50:00', finishedAt: '2026-10-16 07:50:12' }
];

const knowledgeCollections = [
  { collectionId: sid(1001), collectionCode: 'product_docs', collectionName: '产品资料', dataLevel: 'STRICT', orgScope: '1', ownerId: sid(1101), status: '0' },
  { collectionId: sid(1002), collectionCode: 'brand_guide', collectionName: '品牌规范', dataLevel: 'INTERNAL', orgScope: '', ownerId: sid(1101), status: '0' }
];

const knowledgeDocumentStatus = [
  {
    documentId: sid(1201), sourceName: '产品手册.pdf', sourceType: 'PDF', ownerId: sid(1101),
    versions: [
      { versionId: sid(1301), version: '2', status: 'FAILED', effectiveFrom: '2026-10-01T00:00:00' },
      { versionId: sid(1302), version: '1', status: 'READY', effectiveFrom: '2026-09-01T00:00:00' }
    ]
  },
  {
    documentId: sid(1202), sourceName: 'FAQ.docx', sourceType: 'DOCX', ownerId: sid(1101),
    versions: [{ versionId: sid(1303), version: '1', status: 'READY', effectiveFrom: '2026-09-01T00:00:00' }]
  },
  {
    documentId: sid(1203), sourceName: '已过期政策.md', sourceType: 'MARKDOWN', ownerId: sid(1101),
    versions: [{ versionId: sid(1304), version: '1', status: 'READY', effectiveFrom: '2026-01-01T00:00:00', effectiveTo: '2026-06-30T00:00:00' }]
  },
  {
    documentId: sid(1204), sourceName: '解析中.txt', sourceType: 'TXT', ownerId: sid(1101),
    versions: [{ versionId: sid(1305), version: '1', status: 'PARSING' }]
  },
  {
    documentId: sid(1205), sourceName: '只登记未解析.pdf', sourceType: 'PDF', ownerId: sid(1101),
    versions: []
  }
];

const capabilityOverview = [
  { capabilityId: sid(101), capabilityCode: 'talent_match', capabilityName: '人才匹配', requiredTags: 'TEXT,EMBEDDING', status: '0', candidateCount: 2, primaryModelKey: 'qwen2.5-72b', candidateHealthAgg: 'HEALTHY', policyCoveredLevels: 4, policyTotalLevels: 4, sceneCount: 1, calls24h: 42, fails24h: 1, issueCodes: [] },
  { capabilityId: sid(102), capabilityCode: 'brief_precheck', capabilityName: '需求预检', requiredTags: 'TEXT', status: '0', candidateCount: 1, primaryModelKey: 'qwen-vl-max', candidateHealthAgg: 'UNHEALTHY', policyCoveredLevels: 3, policyTotalLevels: 4, sceneCount: 0, calls24h: 18, fails24h: 6, issueCodes: ['CANDIDATE_UNHEALTHY', 'POLICY_INCOMPLETE'] },
  { capabilityId: sid(103), capabilityCode: 'poster_copy', capabilityName: '海报文案', requiredTags: 'TEXT,IMAGE', status: '0', candidateCount: 0, candidateHealthAgg: 'NONE', policyCoveredLevels: 2, policyTotalLevels: 4, sceneCount: 0, calls24h: 0, fails24h: 0, issueCodes: ['NO_MODEL_CANDIDATE', 'NO_ROUTE_POLICY'] },
  { capabilityId: sid(104), capabilityCode: 'video_brief', capabilityName: '视频脚本', requiredTags: 'TEXT,VIDEO', status: '1', candidateCount: 1, candidateHealthAgg: 'UNKNOWN', policyCoveredLevels: 1, policyTotalLevels: 4, sceneCount: 0, calls24h: 3, fails24h: 3, issueCodes: ['POLICY_INCOMPLETE'] }
];

/** 写操作统一拒绝：预览不是环境，避免有人以为改动生效了 */
const rejectWrite = (method: string, url: string) =>
  Promise.resolve({ code: 500, msg: `界面预览：写操作已被拦截（${method} ${url}）`, data: null });

function matchNumber(url: string, pattern: RegExp): string {
  const m = url.match(pattern);
  return m ? m[1] : '';
}

export default function request(options: any) {
  const url = String(options?.url ?? '');
  const method = String(options?.method ?? 'get').toLowerCase();
  if (method !== 'get') {
    return rejectWrite(method.toUpperCase(), url);
  }

  // ---------- 能力 ----------
  if (url === '/aigov/capability/overview') return ok(capabilityOverview);
  if (url === '/aigov/capability/references') {
    return ok([
      { referenceType: 'AGENT_VERSION', referenceCount: 2, detail: '人才匹配Agent v3、v4', published: true },
      { referenceType: 'TASK', referenceCount: 42, detail: '历史任务', published: false },
      { referenceType: 'DRAFT', referenceCount: 1, detail: '训练台草稿', published: false }
    ]);
  }
  if (url === '/aigov/capability/list') return ok({ rows: capabilities, total: capabilities.length });
  if (/^\/aigov\/capability\/\d+$/.test(url)) {
    const id = matchNumber(url, /(\d+)$/);
    return ok(capabilities.find(item => String(item.capabilityId) === id) || capabilities[0]);
  }

  // ---------- 路由 ----------
  if (url === '/aigov/route/list') return ok({ rows: routePolicies, total: routePolicies.length });
  if (/^\/aigov\/route\/\d+$/.test(url)) return ok(routePolicies[0]);
  if (url === '/aigov/route/binding/list') {
    return ok({
      rows: [
        { bindId: sid(1401), scenarioCode: 'recruit', capabilityCode: 'talent_match', capabilityName: '人才匹配', providerId: sid(201), providerName: '本地 vLLM', priority: 10, status: '0' },
        { bindId: sid(1402), scenarioCode: 'marketing', capabilityCode: 'poster_copy', capabilityName: '海报文案', providerId: sid(202), providerName: '阿里云百炼', priority: 20, status: '0' }
      ],
      total: 2
    });
  }

  // ---------- 审计 ----------
  if (url === '/aigov/audit/list') return ok({ rows: audits, total: audits.length });

  // ---------- 模型 ----------
  if (url === '/aigov/model/list') return ok({ rows: models, total: models.length });
  if (url === '/aigov/model/providers/all') return ok(providers);
  if (url === '/aigov/model/providers') return ok({ rows: providers, total: providers.length });
  if (/^\/aigov\/model\/\d+$/.test(url)) return ok(models[0]);

  // ---------- 工具 ----------
  if (url === '/aigov/tools') return ok(tools);
  if (/^\/aigov\/tools\/versions\//.test(url)) {
    const code = decodeURIComponent(matchNumber(url, /\/versions\/([^/]+)/));
    const tool = tools.find(item => item.toolCode === code) || tools[0];
    return ok({
      ...tool,
      description: '把简历原文解析成结构化字段（教育/经历/技能），只读不写回',
      ownerId: sid(1101),
      dataPolicy: 'LOCAL_ONLY',
      executorRef: 'builtin:resume_parse',
      inputSchema: '{"type":"object","required":["text"]}',
      outputSchema: '{"type":"object"}',
      timeoutMs: 30000,
      maxAttempts: 2,
      remark: '待接入：当前只有定义'
    });
  }

  // ---------- 岗位包 ----------
  if (url === '/aigov/roles/list') return ok({ rows: roleVersions, total: roleVersions.length });
  if (/^\/aigov\/roles\/\d+\/versions$/.test(url)) return ok({ rows: roleVersions, total: roleVersions.length });
  if (/^\/aigov\/roles\/version\/\d+$/.test(url)) {
    return ok({
      ...roleVersions[0],
      cards: [
        { actionId: sid(1501), actionCode: 'write_copy', categoryCode: 'write', title: '写一条推广文案', description: '按品牌语气生成 3 版', capabilityCode: 'poster_copy' },
        { actionId: sid(1502), actionCode: 'tune_tone', categoryCode: 'write', title: '调语气', description: '改成更口语', capabilityCode: 'poster_copy' }
      ]
    });
  }

  // ---------- 评测 ----------
  if (url === '/aigov/evaluation/case/list') return ok(evaluationCases);
  if (url === '/aigov/evaluation/run/list') return ok(evaluationRuns);
  if (/^\/aigov\/evaluation\/case\/\d+$/.test(url)) {
    return ok({ ...evaluationCases[0], expectedJson: '{"passed":true}', rubricJson: '{"score":0.9}' });
  }
  if (url === '/aigov/evaluation/declared-cases') return ok(['CASE_TALENT_001', 'CASE_POSTER_002']);

  // ---------- 门户 ----------
  if (url === '/aigov/portal/roles') return ok(portalRoles);
  if (/^\/aigov\/portal\/roles\/.+\/home$/.test(url)) {
    return ok({
      ...portalRoles[0],
      actions: [
        { actionId: sid(1601), actionCode: 'write_copy', title: '写一条推广文案', description: '按品牌语气生成 3 版', categoryCode: 'write', capabilityCode: 'poster_copy' },
        { actionId: sid(1602), actionCode: 'edit_poster', title: '改海报文案', description: '针对已生成的物料微调', categoryCode: 'write', capabilityCode: 'poster_copy' }
      ]
    });
  }
  if (url === '/aigov/portal/my-tasks') return ok({ rows: portalTasks, total: portalTasks.length });
  if (url === '/aigov/portal/pref') return ok({ favorites: ['copywriter'], defaultRoleCode: 'copywriter' });
  if (url === '/aigov/portal/favorites') return ok({ favorites: ['copywriter'], defaultRoleCode: 'copywriter' });
  if (url === '/aigov/portal/my-artifacts') {
    return ok({
      rows: [
        { artifactId: sid(1701), taskId: sid(902), artifactType: 'DOCUMENT', name: '人才匹配结果.pdf', createdAt: '2026-10-16 08:14:20' },
        { artifactId: sid(1702), taskId: sid(901), artifactType: 'IMAGE', name: '海报初稿.png', createdAt: '2026-10-16 09:01:00' }
      ],
      total: 2
    });
  }
  if (url === '/aigov/portal/my-assets/page') {
    return ok({
      rows: [
        { assetId: sid(1801), assetType: 'AGENT_VERSION', name: '人才匹配 Agent v3', domain: 'recruit', updatedAt: '2026-10-15 12:00:00' },
        { assetId: sid(1802), assetType: 'KNOWLEDGE_COLLECTION', name: '产品资料', domain: 'knowledge', updatedAt: '2026-10-14 09:00:00' }
      ],
      total: 2
    });
  }
  if (url === '/aigov/portal/my-assets/recent') {
    return ok([
      { assetId: sid(1801), assetType: 'AGENT_VERSION', name: '人才匹配 Agent v3', domain: 'recruit', updatedAt: '2026-10-15 12:00:00' }
    ]);
  }
  if (url === '/aigov/portal/launch/template') {
    return ok({
      templateCode: 'TPL_GENERATE',
      fields: [
        { field: 'brief', label: '要做什么', type: 'TEXTAREA', required: true },
        { field: 'tone', label: '语气', type: 'SELECT', required: false }
      ]
    });
  }

  // ---------- 知识中心 ----------
  if (url === '/aigov/knowledge/collections') return ok(knowledgeCollections);
  if (/\/document-status$/.test(url)) return ok(knowledgeDocumentStatus);
  if (url === '/aigov/knowledge/documents') {
    return ok(
      knowledgeDocumentStatus.map(row => ({
        documentId: row.documentId,
        collectionId: knowledgeCollections[0].collectionId,
        sourceName: row.sourceName,
        sourceType: row.sourceType,
        ownerId: row.ownerId,
        status: '0'
      }))
    );
  }
  if (/\/document-versions\/\d+$/.test(url)) {
    const versionId = matchNumber(url, /(\d+)$/);
    const flat = knowledgeDocumentStatus.flatMap(row => row.versions);
    const version = flat.find(item => String(item.versionId) === versionId) || flat[0];
    return ok({ documentId: knowledgeDocumentStatus[0].documentId, sha256: 'a'.repeat(64), sourceRef: 'oss://ai-docs/x.pdf', ...version });
  }
  if (/\/chunks$/.test(url)) {
    return ok([
      { chunkId: sid(1901), ordinal: 1, locator: 'p.1', textExcerpt: '本合同适用期为自签署之日起一年……', hasFullText: true, indexed: true },
      { chunkId: sid(1902), ordinal: 2, locator: 'p.3', textExcerpt: '乙方应在收到通知后 5 个工作日内回复……', hasFullText: true, indexed: true }
    ]);
  }

  // ---------- 反馈（页面还没接，但保留形状便于以后加 tab） ----------
  if (url === '/aigov/operations/metrics') {
    return ok({ completedTasks: 128, confirmedResults: 96, adoptionRate: 0.75, firstPassRate: 0.68, reworkRate: 0.12, citationValidRate: 0, policyBlocked: 7, modelCost: 0 });
  }
  if (url === '/aigov/feedback/queue/page') {
    return ok({
      rows: [
        { feedbackId: sid(2001), taskId: sid(902), resultId: sid(2101), agentVersionId: sid(2201), knowledgeVersionId: sid(1303), reasonCode: 'CITATION_MISMATCH', verdict: 'PENDING', actionCode: '', handledBy: '', createTime: '2026-10-16 09:30:00' },
        { feedbackId: sid(2002), taskId: sid(901), resultId: sid(2102), agentVersionId: sid(2201), knowledgeVersionId: '', reasonCode: 'FACT_ERROR', verdict: 'CONFIRMED', actionCode: 'ADD_KNOWLEDGE', handledBy: '管理员', handledAt: '2026-10-16 10:00:00', createTime: '2026-10-16 08:20:00' }
      ],
      total: 2
    });
  }

  // 未 mock：如实记下来（页面顶部会列出来），不臆造数据
  unmockedRequests.push({ method: method.toUpperCase(), url });
  return ok([]);
}

// ---- 与真实 `@/utils/request` 对齐的具名导出（api 模块可能引用） ----
export const isRelogin = { show: false };
export function isHandledRequestError() {
  return false;
}
export async function extractErrorMessage(error: any) {
  return error?.message;
}
export const globalHeaders = () => ({});
export function download() {
  // 预览里不提供下载
}
