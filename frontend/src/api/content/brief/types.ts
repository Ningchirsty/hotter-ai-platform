/**
 * 品牌要求（Brief）类型 —— 与后端 `BrandBriefVo` / 表 `dp_brand_brief` 对齐。
 *
 * <p><b>为什么从创作域搬到内容域</b>：这份要求是<b>品牌部</b>在内容生产协同里录入并确认的
 * （「必须怎么做」），平面设计部在 AI 视觉工厂按此创作、那边只读。
 * 后端接口也一并搬到 `/content/task/{taskId}/brand-brief`，创作域的那三个接口已删除，
 * 所以这里不是"复制一份"，而是唯一的类型定义处。</p>
 *
 * <p>字段名与原创作域定义**逐字一致**，避免两个模块对同一份数据出现两套命名。</p>
 */

/** 品牌 Brief 状态 → 展示文案 */
export const BRAND_BRIEF_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认'
};

/**
 * 参考风格图片最多几张（与后端 `ContentBrandBriefServiceImpl.MAX_STYLE_IMAGES` 一致）。
 * 前后端都挡一次：前端先挡是为了给可读提示，后端那道才是真正的边界。
 */
export const BRAND_BRIEF_STYLE_MAX = 6;

/**
 * 「申请修改品牌要求」落在互动确认卡上时用的 field_code 标记。
 *
 * <p>为什么要有这个常量：视觉项目页要判断"这条任务有没有待处理的修改申请"，
 * 只能靠 {@code cardType=SUPPLEMENT + fieldCode=brand_brief} 这对取值筛；
 * 写成魔法字符串的话，后端哪天改了标记，页面会静默查不到（看起来"没有申请"）。</p>
 */
export const BRAND_BRIEF_CHANGE_FIELD_CODE = 'brand_brief';

/** 品牌 Brief 状态 → 标签颜色（ElTagType 的取值域） */
export const BRAND_BRIEF_STATUS_TYPES: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  DRAFT: 'warning',
  CONFIRMED: 'success'
};

/**
 * 参考风格图片明细（后端按 `styleRefFiles` 里的 id 解析出文件名，解析不到的会跳过）。
 *
 * <p>预览地址不在数据里给：内容任务页与视觉项目页各有自己的"附件取字节"接口
 * （前者 `/content/task/.../files/{id}/content`，后者走创作域的代理），
 * 由页面各自拼，避免后端替前端决定走哪条鉴权通路。</p>
 */
export interface BrandBriefImage {
  fileId?: string | number;
  fileName?: string;
}

/**
 * 品牌要求（Brief）。
 *
 * <p>它与 `cp_fact_snapshot` 里的品牌调性事实<b>并存而不合并</b>：
 * 事实是「从产品资料里解析并确认」的客观信息，Brief 是「品牌方自己填的要求」，
 * 两者冲突时同时展示、由人裁定。</p>
 *
 * <p>任务还没填过时后端也返回对象（{@code configured=false}、{@code status='DRAFT'}，其余为 null），
 * 页面据此显示「未填写」，而不是把 null 当成"没这个功能"。</p>
 */
export interface BrandBriefVO {
  taskId?: string | number;
  /** 该任务是否已有 Brief 记录（后端在有记录时置 true） */
  configured?: boolean;
  /** DRAFT 草稿 / CONFIRMED 品牌方已确认 */
  status?: string;
  brandTone?: string;
  /** 必显信息，一行一条（品牌名/logo/口号/资质） */
  mustShow?: string;
  /** 禁用词与合规红线，一行一条 */
  forbiddenWords?: string;
  targetAudience?: string;
  /** 主推卖点与优先级，一行一条，行首数字即优先级 */
  mainPush?: string;
  sizeSpecReq?: string;
  styleRef?: string;
  /** 参考风格图片的附件ID串（逗号分隔）——后端原样回传，用于判断"改没改" */
  styleRefFiles?: string;
  /** 参考风格图片明细（缩略图展示用） */
  styleRefImages?: BrandBriefImage[];
  remark?: string;
  confirmedBy?: string | number;
  /** 后端若一并返回确认人姓名则用它，否则退化为显示 confirmedBy 的ID（不猜名字） */
  confirmedByName?: string;
  confirmedAt?: string;
  updateTime?: string;
}

/** 品牌 Brief 保存表单（保存草稿用；状态由 confirm 接口推进，这里不传） */
export interface BrandBriefForm {
  brandTone?: string;
  mustShow?: string;
  forbiddenWords?: string;
  targetAudience?: string;
  mainPush?: string;
  sizeSpecReq?: string;
  styleRef?: string;
  /** 参考风格图片的附件ID串（逗号分隔，最多 6 张） */
  styleRefFiles?: string;
  remark?: string;
}

/**
 * 一个 Brief 字段的元信息：标签、行数、长度上限（与 `dp_brand_brief` 列宽一致）、填什么。
 *
 * <p>放在类型文件里而不是各页面各写一份：内容任务页与视觉项目页要展示同一批字段，
 * 两处各定义一次迟早会出现"这边有必显、那边没有"的偏差。</p>
 */
export interface BrandBriefField {
  key: BrandBriefFieldKey;
  label: string;
  rows: number;
  max: number;
  placeholder: string;
  hint: string;
}

export type BrandBriefFieldKey =
  | 'brandTone'
  | 'mustShow'
  | 'forbiddenWords'
  | 'targetAudience'
  | 'mainPush'
  | 'sizeSpecReq'
  | 'styleRef'
  | 'remark';

/** 品牌要求的字段定义（顺序即页面顺序；长度上限照抄数据库列宽，先挡住超长不让后端拒） */
export const BRAND_BRIEF_FIELDS: BrandBriefField[] = [
  {
    key: 'brandTone',
    label: '品牌调性',
    rows: 2,
    max: 500,
    placeholder: '如：清新、治愈、自然；克制不喧哗',
    hint: '品牌方希望的调性。与事实里的 brand_tone 并存，冲突时以人裁定（页面不会自动合并两处）。'
  },
  {
    key: 'mustShow',
    label: '必显信息',
    rows: 3,
    max: 2000,
    placeholder: '一行一条，如：\n品牌名「趣往」\n「每日一枝，治愈生活」\n有机认证标志',
    hint: '必须出现在成品里的内容（品牌名 / logo / 口号 / 资质），一行一条；会进 AI 视觉工厂出图的正向提示词。'
  },
  {
    key: 'forbiddenWords',
    label: '禁用词与红线',
    rows: 3,
    max: 1000,
    placeholder: '一行一条，如：\n最\n第一\n治疗失眠',
    hint: '合规红线与禁用词，一行一条；会作为出图的负向提示词与文案校验依据。'
  },
  {
    key: 'mainPush',
    label: '主推卖点与优先级',
    rows: 3,
    max: 2000,
    placeholder: '一行一条，行首写优先级，如：\n1 单枝直发，48小时新鲜到家\n2 花苞大，开瓶率高',
    hint: '行首的 1/2/3 就是优先级（1 最高）；会进 AI 视觉工厂出图的正向提示词。'
  },
  {
    key: 'targetAudience',
    label: '目标人群',
    rows: 2,
    max: 500,
    placeholder: '如：25-35 岁都市女性，悦己消费',
    hint: '卖给谁。影响文案口吻与画面调性。'
  },
  {
    key: 'sizeSpecReq',
    label: '尺寸与规范',
    rows: 2,
    max: 1000,
    placeholder: '如：详情页宽 750px；主图 1:1；正文不小于 14px',
    hint: '画布比例、留白、字号、平台规范等硬要求。'
  },
  {
    key: 'styleRef',
    label: '参考风格',
    rows: 2,
    max: 1000,
    placeholder: '如：参考图 2 的自然光；无印良品式的留白',
    hint: '参考图 / 参考品牌 / 风格描述，帮助统一画面取向；也可以直接上传参考风格图片（下方）。'
  },
  {
    key: 'remark',
    label: '其它说明',
    rows: 2,
    max: 500,
    placeholder: '其它要交代的要求（可空）',
    hint: '上面没覆盖到的要求写这里。'
  }
];

/** 空表单（8 项全空串）：后端列都是 varchar，空串与 null 语义相同 */
export function emptyBrandBriefForm(): Record<BrandBriefFieldKey, string> {
  return {
    brandTone: '',
    mustShow: '',
    forbiddenWords: '',
    targetAudience: '',
    mainPush: '',
    sizeSpecReq: '',
    styleRef: '',
    remark: ''
  };
}

/**
 * 表单 → 提交体（8 个要求字段 + 参考风格图片ID串；状态由 confirm 接口推进，这里不传 status）。
 *
 * @param form           8 个文本字段
 * @param styleRefFiles  参考风格图片ID串（逗号分隔）。
 *                       - **传字符串**（哪怕空串）：表示"这就是我要的完整状态"，用于上传/移除后的即时保存；
 *                       - **不传（undefined）**：**不带这个字段**，后端保持原值不变。
 *                         新建/编辑任务弹窗走这条路：那个表单里没有图片控件，不该因为保存文字
 *                         而把图片引用改掉（显式不带字段，比"传空串然后指望后端跳过 null"可靠）。
 */
export function formToBriefPayload(
  form: Record<BrandBriefFieldKey, string>,
  styleRefFiles?: string
): BrandBriefForm {
  const payload: BrandBriefForm = {
    brandTone: form.brandTone,
    mustShow: form.mustShow,
    forbiddenWords: form.forbiddenWords,
    targetAudience: form.targetAudience,
    mainPush: form.mainPush,
    sizeSpecReq: form.sizeSpecReq,
    styleRef: form.styleRef,
    remark: form.remark
  };
  if (styleRefFiles !== undefined) {
    payload.styleRefFiles = styleRefFiles;
  }
  return payload;
}

/** 把后端返回的ID串拆成数组（非数字片段丢掉：脏数据不该让页面整块失败） */
export function parseStyleRefFileIds(raw?: string): string[] {
  if (!raw) return [];
  return raw
    .split(/[,，、\s]+/)
    .map((id) => id.trim())
    .filter((id) => id.length > 0 && !Number.isNaN(Number(id)));
}

/** 把ID数组拼成后端要的串（去重、保持顺序） */
export function joinStyleRefFileIds(ids: Array<string | number>): string {
  const seen: string[] = [];
  ids.forEach((id) => {
    const value = String(id ?? '').trim();
    if (value && !seen.includes(value)) seen.push(value);
  });
  return seen.join(',');
}

/** 8 项是否至少有一项非空（确认前的最小校验：全空确认没有意义） */
export function briefFormHasContent(form: Record<BrandBriefFieldKey, string>): boolean {
  return BRAND_BRIEF_FIELDS.some((field) => (form[field.key] ?? '').trim().length > 0);
}

/**
 * 状态徽标文案（两个页面共用，避免一处写"已确认（人 · 时间）"另一处只写"已确认"）。
 *
 * @param brief       服务端返回的 Brief（可为空）
 * @param loaded      是否成功取过：没取到就不下「未填写」的结论
 * @param formatTime  时间格式化函数（各页面的工具函数不同，由调用方传入）
 */
export function briefStatusText(
  brief: BrandBriefVO | null | undefined,
  loaded: boolean,
  formatTime: (value?: string) => string
): string {
  if (!loaded) return '未加载';
  if (!brief?.configured) return '未填写';
  if (brief.status === 'CONFIRMED') {
    const who = brief.confirmedByName || brief.confirmedBy;
    const when = formatTime(brief.confirmedAt);
    // 确认人与时间可能缺一（历史数据/接口未返回姓名）：有什么说什么，不编造
    if (who && when) return `已确认（${who} · ${when}）`;
    if (when) return `已确认（${when}）`;
    if (who) return `已确认（${who}）`;
    return '已确认';
  }
  return BRAND_BRIEF_STATUS_LABELS[brief.status || 'DRAFT'] || '草稿';
}
