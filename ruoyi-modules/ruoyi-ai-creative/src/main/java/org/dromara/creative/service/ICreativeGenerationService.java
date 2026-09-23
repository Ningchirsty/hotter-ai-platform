package org.dromara.creative.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.creative.domain.bo.CreativeHeroBo;
import org.dromara.creative.domain.vo.DpGenerationVo;

import java.util.List;
import java.util.Map;

/**
 * 出图生产服务（视觉工厂的「AI 生产中心」后端）。
 *
 * <p>出图本身完全交给 {@code ruoyi-ai} 的图像内核（契约、模板、ComfyUI、资产落盘都在那边），
 * 本服务只做视觉领域的三件事：挑出这一屏要用的参考图、把视觉参数翻成内核能吃的字段、
 * 记下「哪一屏第几次候选」并跟踪内核状态。</p>
 *
 * <p><b>状态同步方式</b>：R0 不做常驻调度器，采用「读时刷新」——列表/详情被访问时，
 * 对未结束的候选向内核拉一次状态并回写。这样没有后台线程与重启补偿的负担，
 * 也保证页面上看到的就是内核里的真实状态（而不是我们猜的）。</p>
 *
 * @author creative
 */
public interface ICreativeGenerationService {

    /**
     * 提交一次 HERO 主图出图（R0 的最小闭环）。
     *
     * @param taskId 项目ID
     * @param bo     出图参数
     * @return 生成记录（含状态）
     */
    DpGenerationVo submitHero(Long taskId, CreativeHeroBo bo);

    /**
     * 项目的出图候选列表（先刷新状态再返回，按时间倒序）。
     *
     * @param taskId 项目ID
     * @return 候选列表
     */
    List<DpGenerationVo> listByProject(Long taskId);

    /**
     * 重试一次失败的候选。
     *
     * <p>不复活原内核任务（内核状态机不允许 FAILED→RUNNING），而是用同样的输入
     * 新建一次候选，候选序号递增——这样「第几次尝试」是可数的，历史也不会被覆盖。</p>
     *
     * @param generationId 生成记录ID
     * @return 新建的生成记录
     */
    DpGenerationVo retry(Long generationId);

    /**
     * 候选缩略图（列表用）。
     *
     * @param generationId 生成记录ID
     * @return JPEG 字节
     */
    byte[] thumbnail(Long generationId);

    /**
     * 候选原图（预览用）。
     *
     * @param generationId 生成记录ID
     * @return 图片字节
     */
    byte[] preview(Long generationId);

    /**
     * 刷新项目下未结束候选的状态。
     *
     * @param taskId 项目ID
     * @return 本次真正发生状态变化的条数
     */
    int refresh(Long taskId);

    /**
     * 可用的出图工作流（供页面列出可选能力；只返回可联调/已发布的）。
     *
     * @return 每个元素含 workflowCode / capabilityCode / modelCode / version / status / defaultSize
     */
    List<Map<String, Object>> availableWorkflows();

    /**
     * 跨项目的候选分页（AI 生产中心列表）。
     *
     * <p>列表里的未结束候选会被顺手刷新一次内核状态——生产中心是「盯着跑」的页面，
     * 显示一个已经失败却还写着「出图中」的候选比不显示更糟。</p>
     *
     * @param status    状态过滤（可空）
     * @param pageQuery 分页参数
     * @return 分页结果（含项目名）
     */
    PageResult<DpGenerationVo> queryPage(String status, PageQuery pageQuery);

}
