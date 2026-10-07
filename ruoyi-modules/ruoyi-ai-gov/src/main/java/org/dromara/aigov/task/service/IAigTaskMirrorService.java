package org.dromara.aigov.task.service;

import org.dromara.aigov.task.domain.bo.AigTaskMirrorQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorSourceVo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 统一任务视图的「存量只读」入口（设计 §9：新任务走 {@code aig_task}，存量只读镜像）。
 *
 * <p><b>这一层的存在意义</b>：迁移不可能一步到位——创作域已有在跑的生成记录，
 * 一次性搬进 {@code aig_task} 会打断它。于是口径是：<b>新任务只走 {@code aig_task}，
 * 存量只读镜像</b>。镜像意味着这些行在统一视图里<b>看得见、但改不了</b>；
 * 对它们的任何操作仍回到来源自己的接口与权限上。</p>
 *
 * @author ai-gov
 */
public interface IAigTaskMirrorService {

    /**
     * 可用的镜像来源清单。
     *
     * @return 来源列表（按编码排序，便于前端稳定渲染）
     */
    List<AigTaskMirrorSourceVo> listSources();

    /**
     * 分页查询某个来源的镜像行。
     *
     * @param bo        查询条件（{@code source} 必填）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigTaskMirrorVo> queryPage(AigTaskMirrorQueryBo bo, PageQuery pageQuery);

    /**
     * 取某个来源的单行详情。
     *
     * @param source 来源编码
     * @param refId  来源侧主键
     * @return 镜像行；来源不存在或行不存在时抛错/返回 null 语义见实现说明
     */
    AigTaskMirrorVo getDetail(String source, String refId);

}
