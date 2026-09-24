package org.dromara.creative.constant;

/**
 * AI 视觉工厂模块常量。
 *
 * <p>权限标识与 {@code script/sql/dp_creative_menu.sql} 中的 perms 一一对应，
 * 改这里必须同步改菜单 SQL，否则接口会因权限不匹配而 403（与内容模块同一条纪律）。</p>
 *
 * @author creative
 */
public interface CreativeConstants {

    /**
     * 模块权限前缀
     */
    String PERM_PREFIX = "creative:";

    /**
     * 本项目只处理电商详情页；项目本体是内容协同的 cp_task。
     */
    String DELIVERABLE_ECOM_DETAIL = "ECOM_DETAIL";

    // ---------------- 视觉项目（= cp_task） ----------------

    String PERM_PROJECT_LIST = "creative:project:list";
    String PERM_PROJECT_QUERY = "creative:project:query";
    String PERM_PROJECT_ADD = "creative:project:add";
    String PERM_PROJECT_EDIT = "creative:project:edit";
    String PERM_PROJECT_REMOVE = "creative:project:remove";
    String PERM_PROJECT_UPLOAD = "creative:project:upload";

    // ---------------- 视觉基因 DNA ----------------

    String PERM_DNA_LIST = "creative:dna:list";
    String PERM_DNA_ANALYZE = "creative:dna:analyze";
    String PERM_DNA_EDIT = "creative:dna:edit";
    String PERM_DNA_LOCK = "creative:dna:lock";

    // ---------------- 视觉方向与分镜 ----------------

    String PERM_STORYBOARD_LIST = "creative:storyboard:list";
    String PERM_DIRECTION_GENERATE = "creative:direction:generate";
    String PERM_DIRECTION_SELECT = "creative:direction:select";
    String PERM_STORYBOARD_GENERATE = "creative:storyboard:generate";
    String PERM_STORYBOARD_EDIT = "creative:storyboard:edit";
    String PERM_STORYBOARD_REGENERATE = "creative:storyboard:regenerate";

    // ---------------- AI 生产中心 ----------------

    String PERM_PRODUCTION_LIST = "creative:production:list";
    String PERM_PRODUCTION_START = "creative:production:start";
    String PERM_PRODUCTION_RETRY = "creative:production:retry";
    String PERM_PRODUCTION_CANCEL = "creative:production:cancel";
    String PERM_PRODUCTION_SELECT = "creative:production:select";
    String PERM_QA_RUN = "creative:qa:run";

    // ---------------- 详情页与审核 ----------------

    String PERM_REVIEW_LIST = "creative:review:list";
    String PERM_GATE_SUBMIT = "creative:gate:submit";
    String PERM_GATE_REVIEW = "creative:gate:review";
    String PERM_GATE_LOCK = "creative:gate:lock";
    String PERM_LAYOUT_RENDER = "creative:layout:render";
    String PERM_FINAL_SUBMIT = "creative:final:submit";
    String PERM_FINAL_REVIEW = "creative:final:review";

    // ---------------- 视觉模板库 ----------------

    String PERM_TEMPLATE_LIST = "creative:template:list";
    String PERM_TEMPLATE_ADD = "creative:template:add";
    String PERM_TEMPLATE_EDIT = "creative:template:edit";
    String PERM_TEMPLATE_REMOVE = "creative:template:remove";

    // ---------------- 默认出图能力（R0：HERO 主图） ----------------

    /**
     * 默认出图工作流：图生图（已发布）。
     *
     * <p>R0 用既有已发布契约出 HERO 主图；专用模板 WF-HERO-001 需要新 ComfyUI 模板 +
     * 实机验收 + 发布评审，放在 R1（见上线记录「R0 范围调整」）。</p>
     */
    String DEFAULT_HERO_WORKFLOW = "wf-i2i-qwen21";

    /**
     * 默认出图能力编码
     */
    String DEFAULT_HERO_CAPABILITY = "I2I";

    // ---------------- 本地 LLM 创作能力（R4） ----------------

    /**
     * 能力编码：视觉方向草稿（治理台需注册同名能力 + 路由策略 + 模型绑定）。
     *
     * <p>没有注册或没有可用模型时，调用会被治理层明确拒绝（{@code decision≠MODEL}），
     * 业务侧如实回落到参数化模板并把来源标成 TEMPLATE——不存在静默假装用了模型。</p>
     */
    String CAP_DIRECTION_DRAFT = "creative_direction_draft";

    /**
     * 能力编码：分镜草稿
     */
    String CAP_STORYBOARD_DRAFT = "creative_storyboard_draft";

    /**
     * 来源：AI 模型产出（有可用模型且逐字段验收通过时才用）
     */
    String SOURCE_MODEL = "MODEL";

    /**
     * 来源：模板派生（含参数化模板；未使用模型）
     */
    String SOURCE_TEMPLATE = "TEMPLATE";

}
