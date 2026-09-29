package org.dromara.creative.helper;

import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事实快照的公共读法（V0.2 R22）。
 *
 * <p>「已确认事实」这段映射原来在 DNA / 方向 / 分镜三个服务里各写了一遍（都叫 {@code confirmedFacts}）。
 * R22 的模块规划也要用（判断"所需事实"缺哪几个），与其再抄第四遍，不如放在这里共用。
 * 旧的三处暂不动（它们已被验收覆盖，改它们没有收益却有回归风险）。</p>
 *
 * @author creative
 */
public final class CreativeFacts {

    private CreativeFacts() {
    }

    /**
     * 取"已确认"的事实：字段码 → 值（同一字段码取第一条，与既有实现一致）。
     *
     * @param detail 内容任务详情（可空）
     * @return 已确认事实；没有返回空 Map（不是 null）
     */
    public static Map<String, String> confirmed(ContentTaskDetailVo detail) {
        Map<String, String> facts = new LinkedHashMap<>();
        if (detail == null || detail.getFacts() == null) {
            return facts;
        }
        for (CpFactSnapshotVo fact : detail.getFacts()) {
            if (ContentFactConfirmStatusEnum.CONFIRMED.getCode().equals(fact.getConfirmStatus())) {
                facts.putIfAbsent(fact.getFieldCode(), fact.getFieldValue());
            }
        }
        return facts;
    }
}
