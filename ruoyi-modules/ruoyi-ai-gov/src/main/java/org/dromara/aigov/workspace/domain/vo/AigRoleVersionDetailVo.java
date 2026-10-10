package org.dromara.aigov.workspace.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 岗位包版本详情（主文档线增量 1b）。
 *
 * <p>带 {@link #problems}：读一份版本时<b>同时告诉你它现在合法不合法</b>。
 * 发布前必须由服务端判定，但管理员在列表里点开时也应该立刻看到结论——
 * 否则只能在"按下发布然后被拒绝"的那一刻才知道哪里配错了。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AigRoleVersionDetailVo extends AigRoleVersionVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位简介
     */
    private String roleDescription;

    /**
     * 清单 JSON（规范化后的原文）
     */
    private String manifestJson;

    /**
     * 卡片清单（按分类/排序）
     */
    private List<AigRoleActionVo> actions;

    /**
     * 静态校验结论（为空即通过）
     */
    private List<String> problems;

}
