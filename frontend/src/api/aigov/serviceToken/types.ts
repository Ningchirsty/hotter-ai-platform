/**
 * 服务令牌（机器身份）类型：对齐后端 AigServiceTokenVo / AigServiceTokenIssuedVo。
 *
 * 一句话记住边界：**明文令牌只出现在"签发"的响应里一次**；
 * 清单接口既不含明文、也不含哈希（哈希是可离线爆破的校验物，发给前端毫无用处）。
 */

/** 令牌清单行 */
export interface AigServiceTokenVO extends BaseEntity {
  tokenId?: string | number;
  /** 服务名（未删除的令牌里唯一），principal 记为 service:<name> */
  name?: string;
  /** 令牌前缀，仅用于人工指认是哪一把（熵不足，不能用于认证） */
  tokenPrefix?: string;
  /** 逗号分隔的权限码；空 = 不授予任何操作（默认拒绝，不是默认全给） */
  scopes?: string;
  /** 到期时间；空 = 不过期 */
  expiresAt?: string;
  /** 最近一次成功认证时间（用来发现"僵尸令牌"与"还在用的令牌"） */
  lastUsedAt?: string;
  /** 最近一次成功认证来源 IP */
  lastUsedIp?: string;
  /** 0 正常 1 停用（停用不删行，保留审计痕迹） */
  status?: string;
  /** 备注：给谁用、为什么需要这些 scope */
  remark?: string;
}

/** 签发入参（入参里没有明文：明文由服务端生成，避免把凭据强度交给调用方决定） */
export interface AigServiceTokenIssueForm {
  name?: string;
  scopes?: string;
  /** 留空 = 不过期 */
  expiresAt?: string | null;
  remark?: string;
}

/** 签发结果：明文 **只此一次** */
export interface AigServiceTokenIssuedVO {
  tokenId?: string | number;
  name?: string;
  /** 明文令牌（平台不留底、无法找回） */
  token?: string;
  tokenPrefix?: string;
  /** 归一化后真正落库的授权范围 */
  scopes?: string;
  expiresAt?: string;
  /** 后端给的使用提示：请求头名、clientid 约定，以及**本环境开关是否打开** */
  usageHint?: string;
}
