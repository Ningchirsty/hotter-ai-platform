package org.dromara.content.constant;

/**
 * 内容生产协同模块常量。
 * <p>权限标识与 {@code script/sql/cp_content_menu.sql} 中的 perms 一一对应，
 * 改这里必须同步改菜单 SQL，否则接口会因权限不匹配而 403。</p>
 *
 * @author content
 */
public interface ContentConstants {

    /**
     * 模块权限前缀
     */
    String PERM_PREFIX = "content:";

    // ---------------- 内容任务 ----------------

    /**
     * 内容任务-列表
     */
    String PERM_TASK_LIST = "content:task:list";
    /**
     * 内容任务-查询
     */
    String PERM_TASK_QUERY = "content:task:query";
    /**
     * 内容任务-新增
     */
    String PERM_TASK_ADD = "content:task:add";
    /**
     * 内容任务-编辑（含上传附件、触发解析、触发预检、重算闸门）
     */
    String PERM_TASK_EDIT = "content:task:edit";
    /**
     * 内容任务-删除
     */
    String PERM_TASK_REMOVE = "content:task:remove";

    // ---------------- 互动确认卡 ----------------

    /**
     * 互动卡-列表
     */
    String PERM_CARD_LIST = "content:card:list";
    /**
     * 互动卡-处理
     */
    String PERM_CARD_HANDLE = "content:card:handle";

    // ---------------- 设计开工包 ----------------

    /**
     * 开工包-列表
     */
    String PERM_PACKAGE_LIST = "content:package:list";
    /**
     * 开工包-生成
     */
    String PERM_PACKAGE_GENERATE = "content:package:generate";
    /**
     * 开工包-签发
     */
    String PERM_PACKAGE_ISSUE = "content:package:issue";

    // ---------------- 产品与SKU ----------------

    /**
     * 产品-列表
     */
    String PERM_PRODUCT_LIST = "content:product:list";
    /**
     * 产品-查询
     */
    String PERM_PRODUCT_QUERY = "content:product:query";
    /**
     * 产品-新增
     */
    String PERM_PRODUCT_ADD = "content:product:add";
    /**
     * 产品-编辑
     */
    String PERM_PRODUCT_EDIT = "content:product:edit";
    /**
     * 产品-删除
     */
    String PERM_PRODUCT_REMOVE = "content:product:remove";

    // ---------------- 闸门规则 ----------------

    /**
     * 闸门规则-列表
     */
    String PERM_GATE_LIST = "content:gateRule:list";
    /**
     * 闸门规则-查询
     */
    String PERM_GATE_QUERY = "content:gateRule:query";
    /**
     * 闸门规则-新增
     */
    String PERM_GATE_ADD = "content:gateRule:add";
    /**
     * 闸门规则-编辑
     */
    String PERM_GATE_EDIT = "content:gateRule:edit";
    /**
     * 闸门规则-删除
     */
    String PERM_GATE_REMOVE = "content:gateRule:remove";

    // ---------------- 治理层能力编码（设计文档 §9.1） ----------------

    /**
     * 资料解析能力编码
     */
    String CAP_DOCUMENT_PARSE = "document_parse";
    /**
     * 资料预检能力编码
     */
    String CAP_BRIEF_PRECHECK = "brief_precheck";

    // ---------------- 其它 ----------------

    /**
     * 是
     */
    String YES = "Y";
    /**
     * 否
     */
    String NO = "N";

    /**
     * 单文件大小上限（字节）。与前端提示、附件校验保持一致。
     */
    long MAX_FILE_SIZE = 50L * 1024 * 1024;

}
