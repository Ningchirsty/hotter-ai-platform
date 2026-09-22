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
  /** 品牌（如 趣往） */
  brand?: string;
  /** 二级分类（如 解构花园-静态花） */
  subCategory?: string;
  /** 产品图引用（对象存储键或 URL；业务库不存文件本体） */
  productImage?: string;
  /** 主推说明（原表该列常填售卖形态/口径说明） */
  mainPush?: string;
  /** 产品经理 */
  productManager?: string;
  /** 尺寸规格（如 257.60*149.30；多形态用换行分隔） */
  sizeSpec?: string;
  /** 价格（元） */
  price?: number | string;
  /** 结构/工艺（如 UV+喷漆、喷漆+镀铬） */
  craft?: string;
  /** 设计灵感 */
  designInspiration?: string;
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
  brand?: string;
  subCategory?: string;
  productImage?: string;
  mainPush?: string;
  productManager?: string;
  sizeSpec?: string;
  price?: number | string;
  craft?: string;
  designInspiration?: string;
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
