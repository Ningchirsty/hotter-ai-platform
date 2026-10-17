/**
 * 预览用的两处依赖替身（只在本预览入口生效）：
 * - `useDict`：真实实现要请求 `/system/dict/data/type/**`，预览没有后端 ⇒ 这里给静态字典；
 * - `checkPermi` / `checkRole`：预览要"看得见所有按钮"，否则大半界面会被权限挡掉，看不出设计。
 *
 * ⚠️ 这不是在放宽权限：预览入口没有登录态、也没有任何真实数据，生产构建不包含本文件。
 */

export interface PreviewDictItem {
  label: string;
  value: string;
  elTagType?: string;
  elTagClass?: string;
}

const DICTS: Record<string, PreviewDictItem[]> = {
  aig_data_level: [
    { label: '公开', value: 'PUBLIC', elTagType: 'success' },
    { label: '内部', value: 'INTERNAL', elTagType: 'info' },
    { label: '限制', value: 'RESTRICTED', elTagType: 'warning' },
    { label: '严格', value: 'STRICT', elTagType: 'danger' }
  ],
  aig_deployment_type: [
    { label: '本地私有', value: 'LOCAL', elTagType: 'success' },
    { label: '集团共享', value: 'GROUP', elTagType: 'info' },
    { label: '外部企业服务', value: 'EXTERNAL_ENTERPRISE', elTagType: 'warning' },
    { label: '外部API', value: 'EXTERNAL_API', elTagType: 'danger' }
  ],
  aig_lifecycle_status: [
    { label: '候选', value: 'CANDIDATE', elTagType: 'info' },
    { label: '试验', value: 'TRIAL', elTagType: 'warning' },
    { label: '灰度', value: 'GRAY', elTagType: 'primary' },
    { label: '生产', value: 'PRODUCTION', elTagType: 'success' },
    { label: '暂停', value: 'SUSPENDED', elTagType: 'info' },
    { label: '退役', value: 'RETIRED', elTagType: 'info' }
  ],
  aig_data_policy: [
    { label: '仅本地', value: 'LOCAL_ONLY' },
    { label: '本地优先', value: 'LOCAL_FIRST' },
    { label: '允许外部', value: 'EXTERNAL_ALLOWED' }
  ],
  aig_audit_level: [
    { label: '摘要', value: 'SUMMARY' },
    { label: '完整输出', value: 'FULL' },
    { label: '仅哈希', value: 'HASH_ONLY' }
  ],
  sys_normal_disable: [
    { label: '正常', value: '0', elTagType: 'success' },
    { label: '停用', value: '1', elTagType: 'danger' }
  ]
};

/** 与真实 `useDict` 同形状：返回 `{ [key]: 字典项数组 }` */
export function useDict(...args: string[]): Record<string, PreviewDictItem[]> {
  const result: Record<string, PreviewDictItem[]> = {};
  for (const key of args) {
    result[key] = DICTS[key] ?? [];
  }
  return result;
}

/** 预览：一律放行（为了看见全部按钮与状态） */
export function checkPermi(_value?: unknown): boolean {
  return true;
}

export function checkRole(_value?: unknown): boolean {
  return true;
}
