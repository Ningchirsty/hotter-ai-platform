package org.dromara.aigov.workspace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 岗位包预检结论（主文档线增量 1b）。
 *
 * <p>{@link #problems} <b>一次列全</b>而不是只报第一个错：配置岗位包的人需要一次看到全部问题。
 * 这个 shape 与 {@code AigStudioValidateVo} 有意保持一致——同一类结论在两处长得不一样，
 * 前端就得写两套渲染。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRolePackageValidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 清单哈希（保存时可用于 CAS；预检未落库时也给出，便于"校验后立刻保存"）
     */
    private String manifestSha256;

    /**
     * 是否通过
     */
    private Boolean passed;

    /**
     * 问题清单（人话；通过时为空）
     */
    private List<String> problems;

}
