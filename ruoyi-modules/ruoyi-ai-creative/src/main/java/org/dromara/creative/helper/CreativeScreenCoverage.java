package org.dromara.creative.helper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeStoryboardService;

import java.util.ArrayList;
import java.util.List;

/**
 * 「这一版交付到底覆没覆盖所有屏」的判据与确认闸（内测 S21 / C9）。
 *
 * <p><b>为什么必须只有一处</b>：这条产线有<b>两条</b>交付收尾路径——
 * 长图类走「上传精修最终版」（{@code uploadFinal}），多图/海报类走「确认交付」（{@code confirm}）。
 * 内测时只修了长图那条，于是"空屏也能完成"在另一条路上原样存在。
 * 判据抄成两份的代价不是重复几行代码，而是**修了一条、以为两条都好了**。</p>
 *
 * <p>判据本身只有一句话：某一屏若没有 <b>status=APPROVED</b> 的产出，它就没有内容可交付。
 * 「已选定」取最新一行——重出单屏后旧的那行会变成历史，取最新才符合人的直觉。</p>
 *
 * <p><b>为什么是"确认"而不是"拦截"</b>：先交做好的部分是这条产线上的真实业务
 * （设计不必等所有屏都出完才交付）。要拦的是"没人注意到缺屏"，所以这里只要求
 * 明确表态，并把表态连同缺屏清单一起交给调用方去留痕。</p>
 *
 * @author creative
 */
public final class CreativeScreenCoverage {

    private CreativeScreenCoverage() {
    }

    /**
     * 取某屏「已选定」的产出。
     *
     * @param taskId           项目ID
     * @param screenId         屏ID（为空视为没有产出）
     * @param generationMapper 产出 Mapper
     * @return 已选定的产出；没有则 {@code null}
     */
    public static DpGeneration approvedGeneration(Long taskId, Long screenId, DpGenerationMapper generationMapper) {
        if (screenId == null) {
            return null;
        }
        List<DpGeneration> rows = generationMapper.selectList(new LambdaQueryWrapper<DpGeneration>()
            .eq(DpGeneration::getTaskId, taskId)
            .eq(DpGeneration::getScreenId, screenId)
            .eq(DpGeneration::getStatus, DpGenerationStatusEnum.APPROVED.getCode())
            .orderByDesc(DpGeneration::getId)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 还没有「已选定产出」的屏号。
     *
     * <p>没有分镜（或分镜没有屏）时返回空列表：那意味着"还没开始"，不是"缺屏"——
     * 把它算成缺屏会让刚建的项目一上来就被提示"缺 N 屏"。</p>
     *
     * @param taskId           项目ID
     * @param storyboardService 分镜服务
     * @param generationMapper 产出 Mapper
     * @return 缺屏的屏号（按分镜顺序；没有缺屏时为空列表）
     */
    public static List<String> missingScreens(Long taskId, ICreativeStoryboardService storyboardService,
                                             DpGenerationMapper generationMapper) {
        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        List<String> missing = new ArrayList<>();
        if (storyboard == null || storyboard.getScreens() == null) {
            return missing;
        }
        for (DpStoryboardScreenVo screen : storyboard.getScreens()) {
            if (approvedGeneration(taskId, screen.getId(), generationMapper) == null) {
                missing.add(screen.getScreenNo());
            }
        }
        return missing;
    }

    /**
     * 缺屏清单 → 可读片段（用在备注与事件里，保持两条路径措辞一致）。
     *
     * @param missing 缺屏清单
     * @return 形如「带空屏交付，缺屏：S03、S04」；没有缺屏时返回空串
     */
    public static String describe(List<String> missing) {
        return missing == null || missing.isEmpty()
            ? ""
            : "带空屏交付，缺屏：" + String.join("、", missing);
    }

    /**
     * 空屏交付的确认闸：缺屏且未确认时拒绝，并把缺哪些屏说清楚。
     *
     * @param missing      缺屏清单
     * @param acknowledged 调用方是否已取得"我知道缺屏、仍要交付"的明确表态
     * @param action       本次动作的中文名（如「上传精修最终版」「确认交付」），用于把话说具体
     * @throws ServiceException 缺屏且未确认
     */
    public static void requireAcknowledged(List<String> missing, boolean acknowledged, String action) {
        if (missing == null || missing.isEmpty() || acknowledged) {
            return;
        }
        throw new ServiceException("还有 " + missing.size() + " 屏没有已选定的产出图（屏号："
            + String.join("、", missing) + "）。现在" + action + "等于「带空屏交付」——"
            + "交付物里这几屏是空白的。如确认就要这样交付，请在确认提示后重新提交；"
            + "如不是，请先回分镜页逐屏出图并选定候选。");
    }
}
