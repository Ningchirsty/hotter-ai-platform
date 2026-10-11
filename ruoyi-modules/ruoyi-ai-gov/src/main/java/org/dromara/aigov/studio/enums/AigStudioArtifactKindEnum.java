package org.dromara.aigov.studio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.StringJoiner;

/**
 * 「草案生成器」的三类草案（附件 07 增量 7／ST-005/006/007）。
 *
 * <h3>为什么三类各自落表</h3>
 * <p>三者的字段确实不一样：技能草案要知道"绑哪个技能编码、它声明了哪些能力"；
 * 子 Agent 草案要知道"挂在哪个父 Agent 下、属于哪个类别"；快捷指令草案要知道
 * "启动方式与启动目标是什么"。合成一张表就得给每类留一半空列，而空列会让人以为
 * "这个字段本来该填"——三类各自的必填项也会因此失守。</p>
 *
 * <h3>三者的共同点（这才是把它们放在一个生成器里的理由）</h3>
 * <p>它们都<b>只产草案、不等同可执行</b>（附件 07 §286）：生成物是一份
 * <b>可提交的训练台草稿内容</b>（{@code AigStudioDraftContent}），
 * 要不要提交、提交成什么版本，仍由人在训练台里决定并走既有五道门槛。
 * 因此三类共用一条生成通道与一套校验，只是各自填充的字段不同。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigStudioArtifactKindEnum {

    /**
     * 技能草案（Skill）：给 Agent 挂上一个技能编码与它的能力声明
     */
    SKILL("SKILL", "技能草案", "skill"),

    /**
     * 子 Agent 草案：一份完整的 Agent 定义（含类别与父 Agent 引用）
     */
    CHILD_AGENT("CHILD_AGENT", "子 Agent 草案", "child-agent"),

    /**
     * 快捷指令草案（Action）：面向岗位卡片的轻量指令
     */
    ACTION("ACTION", "快捷指令草案", "action");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 接口路径片段（小写连字符；页面/接口都用它，避免大小写与下划线两种写法各来一套）
     */
    private final String pathSegment;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigStudioArtifactKindEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigStudioArtifactKindEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按路径片段查找，找不到返回 null。
     *
     * @param pathSegment 路径片段
     * @return 枚举；未命中返回 null
     */
    public static AigStudioArtifactKindEnum findByPath(String pathSegment) {
        if (pathSegment == null) {
            return null;
        }
        for (AigStudioArtifactKindEnum item : values()) {
            if (item.pathSegment.equalsIgnoreCase(pathSegment.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 供报错文案列出可选值。
     *
     * @return 形如 {@code SKILL（技能草案） / CHILD_AGENT（子 Agent 草案） / ACTION（快捷指令草案）}
     */
    public static String describe() {
        StringJoiner joiner = new StringJoiner(" / ");
        for (AigStudioArtifactKindEnum item : values()) {
            joiner.add(item.code + "（" + item.desc + "）");
        }
        return joiner.toString();
    }

}
