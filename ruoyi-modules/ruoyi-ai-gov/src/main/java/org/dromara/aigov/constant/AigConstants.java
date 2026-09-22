package org.dromara.aigov.constant;

/**
 * AI 治理模块通用常量
 * <p>权限标识与菜单 SQL（{@code script/sql/aig_ai_gov_menu.sql}）中的 {@code perms} 一一对应，
 * 修改时必须同步菜单数据，否则前端按钮与后端鉴权会不一致。</p>
 *
 * @author ai-gov
 */
public interface AigConstants {

    /**
     * 能力目录-列表
     */
    String PERM_CAPABILITY_LIST = "aig:capability:list";
    /**
     * 能力目录-查询
     */
    String PERM_CAPABILITY_QUERY = "aig:capability:query";
    /**
     * 能力目录-新增
     */
    String PERM_CAPABILITY_ADD = "aig:capability:add";
    /**
     * 能力目录-修改
     */
    String PERM_CAPABILITY_EDIT = "aig:capability:edit";
    /**
     * 能力目录-删除
     */
    String PERM_CAPABILITY_REMOVE = "aig:capability:remove";
    /**
     * 模型治理-列表
     */
    String PERM_MODEL_LIST = "aig:model:list";
    /**
     * 模型治理-查询
     */
    String PERM_MODEL_QUERY = "aig:model:query";
    /**
     * 模型治理-新增模型（登记 sai_model_config 主数据 + 首份治理属性）
     */
    String PERM_MODEL_ADD = "aig:model:add";
    /**
     * 模型治理-修改
     */
    String PERM_MODEL_EDIT = "aig:model:edit";
    /**
     * 模型治理-密钥引用查看（控制 secretRef / api_endpoint 下发）
     */
    String PERM_MODEL_SECRET = "aig:model:secret";
    /**
     * 路由策略-列表
     */
    String PERM_ROUTE_LIST = "aig:route:list";
    /**
     * 路由策略-查询
     */
    String PERM_ROUTE_QUERY = "aig:route:query";
    /**
     * 路由策略-新增
     */
    String PERM_ROUTE_ADD = "aig:route:add";
    /**
     * 路由策略-修改
     */
    String PERM_ROUTE_EDIT = "aig:route:edit";
    /**
     * 路由策略-删除
     */
    String PERM_ROUTE_REMOVE = "aig:route:remove";
    /**
     * 调用审计-列表
     */
    String PERM_AUDIT_LIST = "aig:audit:list";

    /**
     * 阶段1 首个能力编码：人才能力匹配
     */
    String CAP_TALENT_MATCH = "talent_match";

    /**
     * 审计摘要最大长度
     */
    int AUDIT_SUMMARY_MAX = 500;

    /**
     * 模型标识的合法形态（新增与编辑共用，避免两处漂移）。
     *
     * <p>必须允许斜杠、冒号与开头的波浪号：主流聚合网关（如 OpenRouter）的模型 ID 是
     * {@code vendor/model}，免费档带 {@code :free}，浮动别名以 {@code ~} 开头。
     * 实测其公开目录 443 个 ID 全部含斜杠，收窄会整类挡掉。</p>
     */
    String MODEL_KEY_PATTERN = "^[A-Za-z0-9~][A-Za-z0-9._:/-]*$";

    /**
     * 模型标识校验失败时的提示（与 {@link #MODEL_KEY_PATTERN} 配套）。
     */
    String MODEL_KEY_PATTERN_MESSAGE =
        "模型标识只能由字母、数字、点、下划线、中划线、冒号、斜杠组成，且以字母、数字或波浪号开头"
            + "（如 glm-5.1、openrouter/free、deepseek/deepseek-r1:free）";

}
