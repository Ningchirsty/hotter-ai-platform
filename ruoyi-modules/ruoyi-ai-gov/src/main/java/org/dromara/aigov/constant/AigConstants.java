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

}
