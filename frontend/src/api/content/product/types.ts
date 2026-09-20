/**
 * 轻量产品/SKU 类型定义（对齐后端 CpProductVo / ContentProductBo）。
 *
 * 产品只服务于「事实快照 + 版本影响面追溯」，不是产品主数据平台（SPEC 基线 B5）。
 */

/** 产品列表/详情行 */
export interface CpProductVO extends BaseEntity {
  productId?: string | number;
  /** 产品编码，唯一 */
  productCode?: string;
  productName?: string;
  /** SKU 编码，可空（产品级） */
  skuCode?: string;
  skuName?: string;
  /** 品类，首期试点：积木花 */
  category?: string;
  /** 产品版本，用于影响面追溯 */
  version?: string;
  /** 状态（0正常 1停用），对应字典 sys_normal_disable */
  status?: string;
  remark?: string;
}

/** 新增/编辑表单 */
export interface CpProductForm {
  productId?: string | number;
  productCode?: string;
  productName?: string;
  skuCode?: string;
  skuName?: string;
  category?: string;
  version?: string;
  status?: string;
  remark?: string;
}

/** 查询条件（后端 ContentProductBo：关键字字段名为 keyword） */
export interface CpProductQuery extends PageQuery {
  /** 关键字（产品编码/名称/SKU，后端模糊匹配） */
  keyword?: string;
  /** 状态（0正常 1停用） */
  status?: string;
  params?: Record<string, any>;
}
