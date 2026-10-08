/**
 * Skill 注册类型定义（对齐后端 AigSkillVo / AigSkillVersionVo）。
 */

/** Skill 清单行 */
export interface AigSkillVO extends BaseEntity {
  skillId?: string | number;
  skillCode?: string;
  skillName?: string;
  /** 能力清单（逗号分隔的 Provider 能力编码） */
  capabilities?: string;
  /** 是否平台内置（Y/N） */
  builtin?: string;
  description?: string;
  status?: string;
  remark?: string;
}

/** Skill 清单查询 */
export interface AigSkillQuery extends PageQuery {
  skillCode?: string;
  skillName?: string;
  builtin?: string;
  capabilities?: string;
  status?: string;
  params?: Record<string, any>;
}

/** Skill 版本行 */
export interface AigSkillVersionVO extends BaseEntity {
  skillVersionId?: string | number;
  skillId?: string | number;
  version?: string;
  releaseStatus?: string;
  releaseChannel?: string;
  /**
   * 声明的 Provider 能力编码。
   *
   * <p><b>仅是声明，不参与路由选型</b>——全仓库没有任何路由/调用代码读这个字段
   * （已核实）。实际调用哪个模型由「能力 × 模型绑定」（`aig_capability_model`）决定。
   * 界面上因此标注为「声明能力」，避免被读成"这个 Skill 走哪个模型"。</p>
   */
  providerCapability?: string;
  allowExternal?: string;
  /** 来源 Package 版本（本轮补的列：第三方带入时不为空） */
  packageVersionId?: string | number;
  approvedBy?: string | number;
  approvedAt?: string;
  status?: string;
  remark?: string;
}

/** Skill 版本清单查询 */
export interface AigSkillVersionQuery extends PageQuery {
  skillId?: string | number;
  releaseStatus?: string;
  releaseChannel?: string;
  providerCapability?: string;
  version?: string;
  params?: Record<string, any>;
}
