package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 岗位执行项状态枚举。
 * <p>对应数据字典 {@code recruit_job_status}，取值 {@code draft/open/paused/closed}。</p>
 *
 * <p><b>字典来源说明</b>：设计文档 §10 的数据字典表未为岗位状态单独定义字典类型，本组字典是在 P3 阶段
 * 依据 {@code hr_recruit_job.status} 的建表注释补齐的（字典组由 23 增至 29 的一部分），
 * 详见 {@code docs/hr-talent/P2-决策与缺口台账.md}。</p>
 *
 * <p><b>说明</b>：入库一律保存 {@link #getCode()} 稳定编码，中文名称仅用于服务端提示与页面兜底展示；
 * 编码值一旦投入使用不得随意变更（设计文档 §10 末段）。</p>
 *
 * <p>状态流转（{@code POST /recruit/jobs/{id}/actions/{action}} 与更新接口共用同一套校验）：</p>
 * <ul>
 *     <li>草稿 → 招聘中（发布）</li>
 *     <li>招聘中 ⇄ 已暂停</li>
 *     <li>草稿 / 招聘中 / 已暂停 → 已关闭（仅关闭动作，需 {@code PERM_JOB_CLOSE}）</li>
 *     <li>已关闭 → 招聘中（重新开放）</li>
 * </ul>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum JobStatusEnum {

    /**
     * 草稿：新建岗位的初始状态，尚未对外发布
     */
    DRAFT("draft", "草稿"),
    /**
     * 招聘中：岗位已发布，可接收应聘记录
     */
    OPEN("open", "招聘中"),
    /**
     * 已暂停：岗位暂停招聘，不再接收新的应聘记录，可恢复为招聘中
     */
    PAUSED("paused", "已暂停"),
    /**
     * 已关闭：岗位结束招聘，只能通过「重新开放」动作回到招聘中
     */
    CLOSED("closed", "已关闭");

    /**
     * 编码（入库值，投入使用后不得随意变更）
     */
    private final String code;

    /**
     * 描述（中文名称，页面展示可由字典或枚举兜底转换）
     */
    private final String desc;

    /**
     * 按编码查找。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static JobStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (JobStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按编码取中文名称。
     *
     * @param code 编码
     * @return 中文名称，未命中返回 null
     */
    public static String labelOf(String code) {
        JobStatusEnum item = find(code);
        return item == null ? null : item.desc;
    }

    /**
     * 是否为合法编码。
     *
     * @param code 编码
     * @return 是否合法
     */
    public static boolean isValid(String code) {
        return find(code) != null;
    }

    /**
     * 判断从当前状态到目标状态是否为允许的人工流转。
     * <p>已关闭状态只能由「重新开放」动作回到招聘中，不通过更新接口直接改写。</p>
     *
     * @param from 当前状态编码
     * @param to   目标状态编码
     * @return 是否允许
     */
    public static boolean canTransfer(String from, String to) {
        JobStatusEnum source = find(from);
        JobStatusEnum target = find(to);
        if (source == null || target == null || source == target) {
            return false;
        }
        return switch (source) {
            case DRAFT -> target == OPEN || target == CLOSED;
            case OPEN -> target == PAUSED || target == CLOSED;
            case PAUSED -> target == OPEN || target == CLOSED;
            case CLOSED -> target == OPEN;
        };
    }

}
