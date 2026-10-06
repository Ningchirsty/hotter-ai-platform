package org.dromara.creative.service;

import org.dromara.creative.domain.bo.CreativeScreenBo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;

import java.util.List;

/**
 * 分镜服务。
 *
 * <p>分镜把「一版视觉方案」拆成逐屏可执行规格：每屏讲什么、画面单独要说什么、用什么规格出图。
 * 它是「视觉门」审核的对象，也是后续批量出图的输入。</p>
 *
 * @author creative
 */
public interface ICreativeStoryboardService {

    /**
     * 生成一版分镜（须已有锁定基因；已选定方向时按方向定制，否则用基因默认）。
     *
     * @param taskId 项目ID
     * @return 新分镜（含屏列表）
     */
    DpStoryboardVo generate(Long taskId);

    /**
     * 最新一版分镜（含屏列表）。
     *
     * @param taskId 项目ID
     * @return 最新分镜；从未生成过返回 null
     */
    DpStoryboardVo latest(Long taskId);

    /**
     * 版本列表（倒序，不含屏）。
     *
     * @param taskId 项目ID
     * @return 版本列表
     */
    List<DpStoryboardVo> versions(Long taskId);

    /**
     * 编辑一屏。
     *
     * <p>未锁定可直接改；分镜已锁定时拒绝并提示先生成新版本——锁定版不可改的纪律与视觉基因一致。</p>
     *
     * @param taskId 项目ID
     * @param bo     编辑内容
     * @return 编辑后的屏
     */
    DpStoryboardScreenVo updateScreen(Long taskId, CreativeScreenBo bo);

    /**
     * 锁定分镜（锁定前要求每屏都有画面独白——没有独白的屏等于没想清楚）。
     *
     * <p>v1 裁定 ④ 起，整版锁定同时把这一版**所有屏**标成已锁定（它们今天事实上就是冻结的）；
     * 出图闸门仍然只认整版锁定——单屏锁定是草稿期的防误改，不是逐屏放行出图。</p>
     *
     * @param taskId       项目ID
     * @param storyboardId 分镜ID（可空＝最新一版）
     * @return 锁定后的分镜
     */
    DpStoryboardVo lock(Long taskId, Long storyboardId);

    /**
     * 单独锁定/解锁一屏（v1 裁定 ④：「可以原地锁定一个屏幕，但其余可以自定义」）。
     *
     * <p>锁定的这一屏冻结（文案/取景/保真等级/Workflow 都不能改），其余屏照旧可改。
     * 锁定的前置条件是这一屏有<b>画面独白</b>（与整版锁定同一条要求，只是下移到屏级）。</p>
     *
     * <p>整版已锁定时拒绝：那时"逐屏锁/解锁"没有意义（要改就重新生成一版）。</p>
     *
     * @param taskId   项目ID
     * @param screenId 屏ID
     * @param locked   true＝锁定这一屏，false＝解锁这一屏
     * @return 这一屏（含最新的锁定状态与是否可改）
     */
    DpStoryboardScreenVo lockScreen(Long taskId, Long screenId, boolean locked);

    /**
     * 在某一屏之后插入一屏（v1 裁定 ④：「屏数由使用人说了算」）。
     *
     * <p><b>只允许在整版锁定之前</b>：屏集合一旦锁定就是约定，要改屏数请重新生成分镜得到新版本。</p>
     *
     * <p>新屏是<b>人工新增的屏</b>：屏类型/保真等级/Workflow/取景沿用参照屏（同一份基因与方向），
     * 但标题、正文与画面独白**留空**——不替人编一句（页面会提示这一屏待填写，锁定前必须补画面独白）。
     * 它不属于任何模块行，因此 spec 里不带 {@code moduleCode}，只留一个"人工新增"的标记。</p>
     *
     * @param taskId        项目ID
     * @param afterScreenId 插在这一屏之后（可空＝追加到最后一屏之后）
     * @return 新增的屏
     */
    DpStoryboardScreenVo addScreen(Long taskId, Long afterScreenId);

    /**
     * 删除一屏（v1 裁定 ④；同样只允许在整版锁定之前）。
     *
     * <p>已单独锁定的屏不能删（先解锁）；最后一屏不能删（分镜至少要有一屏）；
     * 已经有出图记录的屏不能删（那会让已有候选对不上屏）。删完屏号重排 S01..S0N。</p>
     *
     * @param taskId   项目ID
     * @param screenId 屏ID
     */
    void deleteScreen(Long taskId, Long screenId);

}
