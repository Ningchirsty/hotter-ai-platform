package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门户里的"我的产物"（主文档线增量 6）。
 *
 * <h3>为什么这一条做得成、而跨域资产没做</h3>
 * <p>{@code aig_task_artifact} 是**平台自己的产物台账**（ADR-010），与任务域在同一个模块里，
 * 而且"这份产物的任务是谁提交的"就是它的可见性口径——不需要猜任何别域的规则。
 * 各域的资产表（`image_asset` / `video_asset` / `cp_task_file`）则不是：它们的可见性规则在各自模块里，
 * 从本模块直连等于把那些规则复制一份（详见文档 §6.1）。</p>
 *
 * <h3>刻意不带的东西</h3>
 * <ul>
 *     <li>{@code storage_ref}：对象存储键是**内部引用**，对外给了等于把存储布局写进对外契约；</li>
 *     <li>{@code sha256} / {@code hash_verified}：完整性证据是平台自查用的事实，员工不需要，
 *         放出去还会让人误以为"哈希对得上就等于内容可对外"；</li>
 *     <li>{@code validation_detail} / {@code result_id} / {@code attempt_no}：字段级校验明细与
 *         内部结果/尝试编号都属排查视角。</li>
 * </ul>
 *
 * <p><b>本 VO 也不提供下载直链</b>：下载要走专业台/任务域，那里的权限仍然生效。
 * 在这里给一个直链，等于在门户开了一条绕过那些权限的通道。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPortalArtifactVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产物ID
     */
    private Long artifactId;

    /**
     * 所属任务ID
     */
    private Long taskId;

    /**
     * 所属任务编号（员工沟通时引用）
     */
    private String taskNo;

    /**
     * 产物类型（契约里是**开放字符串**，如 IMAGE / DESIGN_DOCUMENT；不当作封闭枚举）
     */
    private String artifactType;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 字节数
     */
    private Long sizeBytes;

    /**
     * 入库时间
     */
    private LocalDateTime createTime;

}
