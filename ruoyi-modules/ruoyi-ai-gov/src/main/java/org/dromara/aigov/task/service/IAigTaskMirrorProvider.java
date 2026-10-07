package org.dromara.aigov.task.service;

import org.dromara.aigov.task.domain.bo.AigTaskMirrorQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 存量任务只读镜像的提供者（SPI，由各业务域实现）。
 *
 * <p><b>为什么是 SPI 而不是治理层直接去读业务表</b>：治理层与业务域之间只经接口与 DTO 通信，
 * 禁止互相注入 Mapper。统一任务视图要能看到「创作域的生成记录」这类存量数据，
 * 但治理层一旦 import 了创作域的 Mapper，两者就绑死了——这正是设计里
 * 「子包即边界、便于整体搬迁」要防的事。故方向反过来：由拥有数据的业务域实现本接口并注册。</p>
 *
 * <p><b>实现者必须遵守的契约</b>：</p>
 * <ol>
 *     <li>只读：实现里<b>不得</b>写任何数据，也不得触发状态推进（例如为了让状态好看而刷新内核）。
 *         列表为「盯着跑」顺手刷一次状态是可以的——那是读模型的一部分；
 *         但不得写库、不得推进状态机；</li>
 *     <li>状态原样透传：{@code status}/{@code statusLabel} 必须来自来源自己的枚举，
 *         并在 {@code stateMachine} 里写明是哪一套状态机，<b>不要映射成 {@code aig_task} 的状态</b>；</li>
 *     <li>过滤条件要么真支持、要么别声明：{@link AigTaskMirrorQueryBo} 里的每个条件
 *         实现都必须真的生效。做不到就在实现里明确拒绝，而不是静默忽略
 *         （静默忽略会让使用者以为筛过了，看到的却是全量）。</li>
 * </ol>
 *
 * <p><b>新增一个来源要做的事</b>：实现本接口并在实现类上加 {@code @Component}，
 * 治理层会自动收集到（见 {@code AigTaskMirrorServiceImpl}）。</p>
 *
 * @author ai-gov
 */
public interface IAigTaskMirrorProvider {

    /**
     * 来源编码（唯一；重复注册会在启动期失败）。
     *
     * @return 来源编码，如 {@code DP_GENERATION}
     */
    String source();

    /**
     * 来源展示名。
     *
     * @return 展示名，如「创作域生成记录」
     */
    String label();

    /**
     * 说明：该来源的状态机口径与只读边界（写入来源选择器的提示）。
     *
     * @return 说明文本
     */
    String description();

    /**
     * 分页查询镜像行。
     *
     * @param bo        查询条件（{@code source} 由上层保证与 {@link #source()} 一致，实现无需再校验）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigTaskMirrorVo> queryPage(AigTaskMirrorQueryBo bo, PageQuery pageQuery);

    /**
     * 取单行详情。
     *
     * @param refId 来源侧主键
     * @return 镜像行；不存在返回 null
     */
    AigTaskMirrorVo getDetail(String refId);

}
