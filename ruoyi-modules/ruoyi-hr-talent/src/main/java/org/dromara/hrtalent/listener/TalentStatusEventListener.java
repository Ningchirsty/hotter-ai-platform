package org.dromara.hrtalent.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hrtalent.enums.TalentStatusEnum;
import org.dromara.hrtalent.event.ApplicationStageChangedEvent;
import org.dromara.hrtalent.event.CandidateArrivedEvent;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 人才状态事件监听器（设计文档 §7.6.2 人才生命周期、§7.6.4 招聘结束后的自动动作、§21.6）。
 *
 * <p><b>职责</b>：消费应聘领域的两个事件，把「由应聘记录共同决定」的人才状态刷新到位：</p>
 * <ul>
 *     <li>{@link ApplicationStageChangedEvent}：应聘开始/进行中 → 人才状态置 {@code recruiting}；</li>
 *     <li>{@link CandidateArrivedEvent}：实际报到 → 人才状态置 {@code hired}（不自动创建系统账号）。</li>
 * </ul>
 *
 * <p><b>边界</b>：</p>
 * <ul>
 *     <li>事件只做<b>后续派生刷新</b>，必须原子完成的写操作不交给事件（§21.6）；</li>
 *     <li>监听器<b>不直接改</b> {@code hr_talent_profile} 表，统一经
 *     {@link ITalentProfileService#refreshStatusByEvent}，由服务层负责终态保护与变更历史；</li>
 *     <li>异常只记日志，不影响已提交的应聘事务。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentStatusEventListener {

    /**
     * 应聘开始/进行中的人才状态原因。
     */
    private static final String REASON_RECRUITING = "存在进行中的应聘记录";

    /**
     * 已报到的人才状态原因。
     */
    private static final String REASON_HIRED = "候选人已实际报到";

    /**
     * 人才主档服务。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 处理应聘阶段变化事件：进行中的应聘把人才状态刷新为「招聘中」。
     *
     * @param event 应聘阶段变化事件
     */
    @EventListener
    public void onApplicationStageChanged(ApplicationStageChangedEvent event) {
        if (event == null || event.talentId() == null) {
            return;
        }
        try {
            talentProfileService.refreshStatusByEvent(event.talentId(),
                TalentStatusEnum.RECRUITING.getCode(), REASON_RECRUITING);
        } catch (Exception e) {
            // 事件消费失败不得影响已提交的应聘事务，只记录人才ID与异常类型
            log.error("人才状态刷新失败(阶段变化), talentId={}, exception={}",
                event.talentId(), e.getClass().getSimpleName());
        }
    }

    /**
     * 处理候选人报到事件：人才状态刷新为「已入职」。
     *
     * @param event 候选人报到事件
     */
    @EventListener
    public void onCandidateArrived(CandidateArrivedEvent event) {
        if (event == null || event.talentId() == null) {
            return;
        }
        try {
            talentProfileService.refreshStatusByEvent(event.talentId(),
                TalentStatusEnum.HIRED.getCode(), REASON_HIRED);
        } catch (Exception e) {
            log.error("人才状态刷新失败(报到), talentId={}, exception={}",
                event.talentId(), e.getClass().getSimpleName());
        }
    }

}
