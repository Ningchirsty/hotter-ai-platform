package org.dromara.aigov.studio.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 草稿预检结论（专题 C §C7 "本地静态校验"）。
 *
 * <p><b>为什么返回"问题清单"而不是一个布尔/抛异常</b>：预检是给正在编辑的人看的——
 * 他需要知道<b>哪里</b>不合格才能改。抛异常只能告诉第一个人错，剩下的要来回试。
 * 因此一次把所有问题列全，且 {@link #problems} 里每条都写清楚"哪一节/哪个字段、为什么"。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioValidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草稿ID
     */
    private Long draftId;

    /**
     * 被预检的修订号
     */
    private Integer revision;

    /**
     * 预检内容哈希（证明"检的是这一版"）
     */
    private String contentHash;

    /**
     * 是否全部通过
     */
    private Boolean passed;

    /**
     * 问题清单（人话；通过时为空）
     */
    private List<String> problems = new ArrayList<>();

}
