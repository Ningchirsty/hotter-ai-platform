package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保存训练草稿入参（专题 C §C9 的 {@code PATCH /drafts/{id}}）。
 *
 * <p><b>{@code expectedRevision} 必填且是"调用方读到的那个修订号"</b>：
 * 草稿是多人可编辑的工作区，没有它就会出现"甲乙同时改、乙静默覆盖甲"——
 * 而覆盖之后没有任何痕迹能说明甲那段内容曾经存在过。所以本层用
 * {@code where latest_revision = expectedRevision} 做 CAS，冲突就报错让人刷新后重来。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioDraftSaveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草稿ID
     */
    @NotNull(message = "草稿ID不能为空")
    private Long draftId;

    /**
     * 期望的当前修订号（乐观锁；必须与库中一致）
     */
    @NotNull(message = "期望修订号不能为空（否则并发修改会静默覆盖）")
    private Integer expectedRevision;

    /**
     * 新的草稿内容 JSON
     */
    @NotNull(message = "草稿内容不能为空")
    private String contentJson;

    /**
     * 本次修订的说明（页面上那行"改了什么"）
     */
    @Size(max = 200, message = "修订说明长度不能超过 200")
    private String summary;

}
