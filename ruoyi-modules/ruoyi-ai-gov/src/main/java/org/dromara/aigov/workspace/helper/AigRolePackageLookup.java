package org.dromara.aigov.workspace.helper;

/**
 * 岗位包校验需要问外面的三件事（做成接口是为了让校验保持纯函数、可单测）。
 *
 * <p><b>为什么不在这里注入 Mapper</b>：校验规则是"这份配置对不对"，
 * 它不该知道数据怎么存。把"某个场景版本/Agent 版本存在吗"抽象成一个问题，
 * 校验器就能在没有数据库的情况下被逐条单测——而配错了本身是不会报错的，
 * 只有测试能拦住。</p>
 *
 * @author ai-gov
 */
public interface AigRolePackageLookup {

    /**
     * 某个场景版本是否存在（且未被删除）。
     *
     * @param scenarioCode 场景编码
     * @param version      版本号
     * @return 存在返回 true
     */
    boolean scenarioExists(String scenarioCode, String version);

    /**
     * 某个 Agent 版本是否存在（且未被删除）。
     *
     * <p><b>发布时只要求"引用存在"</b>（与场景同口径）：草稿引用的版本可能还没走到
     * {@code STABLE}，但引用本身是对的；"员工点下去那一刻必须就绪"由启动链校验
     * （见 {@code AigLaunchChecklist}）。</p>
     *
     * @param agentVersionId Agent 版本ID
     * @return 存在返回 true
     */
    boolean agentVersionExists(Long agentVersionId);

}
