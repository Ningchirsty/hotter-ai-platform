/**
 * 人才共享授权 域类型定义
 *
 * 契约来源（按优先级）：
 * 1. SPEC-P4 §2.4 D 线（docs/hr-talent/SPEC-P4-人才管理.md）
 * 2. 后端已实现类：TalentScopeGrantController（/talent/grants、/talent/profiles/{id}/grants）
 *    + TalentScopeGrantVo、TalentScopeGrantBo、TalentScopeGrantQueryBo、support/GrantSubject
 * 3. DDL：script/sql/hr_talent.sql（hr_talent_scope_grant）
 *
 * ⚠️ 安全口径（设计文档 §8.19）：共享授权**只扩大查看范围**，
 * **不自动授予电话明文、附件下载、背调和导出权限**——上述动作各有独立按钮权限，
 * 并由 TalentScopeDomainService#checkPermissionLevel 单独判定。
 *
 * 字段名 = 后端 VO/BO 字段名逐字对齐；bigint 一律用 string | number。
 */

/** 被授权主体类型（对齐后端 support/GrantSubject.TYPE_*） */
export type GrantSubjectType = 'user' | 'role' | 'company_dept' | 'dept';

/** 授权级别（字典 talent_permission_level：summary < detail < attachment） */
export type TalentPermissionLevel = 'summary' | 'detail' | 'attachment';

/** 共享授权视图（TalentScopeGrantVo） */
export interface HrTalentScopeGrantVO extends BaseEntity {
  /** 授权ID */
  grantId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 被授权主体类型（user/role/company_dept/dept） */
  granteeType?: string;
  /** 被授权主体类型中文标签（后端只读 getter） */
  granteeTypeLabel?: string;
  /** 被授权主体ID */
  granteeId?: string | number;
  /** 被授权主体为「用户」时的昵称（@Translation 回填，其它主体类型为 null） */
  granteeName?: string;
  /** 授权级别编码（字典 talent_permission_level） */
  permissionLevel?: string;
  /** 授权级别中文标签（字典 talent_permission_level） */
  permissionLevelLabel?: string;
  /** 授权有效期起（为空表示立即生效） */
  validFrom?: string;
  /** 授权有效期止（为空表示长期有效） */
  validTo?: string;
  /** 授权事由（必填） */
  grantReason?: string;
  /** 授权人用户ID */
  grantedBy?: string | number;
  /** 授权人昵称（@Translation 回填） */
  grantedByName?: string;
  /** 授权时间 */
  grantedTime?: string;
  /** 是否已撤销（0否 1是） */
  revokeFlag?: string;
  /** 撤销人用户ID */
  revokedBy?: string | number;
  /** 撤销时间 */
  revokedTime?: string;
  remark?: string;
}

/** 共享授权检索条件（TalentScopeGrantQueryBo + PageQuery） */
export interface HrTalentScopeGrantQuery extends PageQuery {
  /** 人才主档ID */
  talentId?: string | number;
  /** 被授权主体类型（user/role/company_dept/dept） */
  granteeType?: string;
  /** 被授权主体ID */
  granteeId?: string | number;
  /** 授权级别（字典 talent_permission_level） */
  permissionLevel?: string;
  /** 是否已撤销（0否 1是） */
  revokeFlag?: string;
  /** 是否只返回当前有效授权 */
  onlyActive?: boolean;
}

/**
 * 共享授权新增表单（TalentScopeGrantBo）
 *
 * 授权 ID、授权人、授权时间、撤销标记均由服务端维护，不在表单内。
 */
export interface HrTalentScopeGrantForm {
  /** 人才主档ID（新增必填；POST /talent/profiles/{id}/grants 的路径参数会覆盖为路径值） */
  talentId?: string | number;
  /** 被授权主体类型（必填） */
  granteeType?: string;
  /** 被授权主体ID（必填，与类型成对） */
  granteeId?: string | number;
  /** 授权级别（必填，字典 talent_permission_level） */
  permissionLevel?: string;
  /** 授权有效期起（yyyy-MM-dd HH:mm:ss，为空表示立即生效） */
  validFrom?: string;
  /** 授权有效期止（yyyy-MM-dd HH:mm:ss，为空表示长期有效；不为空时必须晚于有效期起） */
  validTo?: string;
  /** 授权事由（必填，最长 500） */
  grantReason?: string;
  /** 备注（最长 500） */
  remark?: string;
}
