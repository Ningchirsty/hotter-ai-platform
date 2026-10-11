package org.dromara.aigov.studio.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 变更提案里的一处改动（增量 21／附件 07 增量 6）。
 *
 * <p><b>只有"设置"一种操作</b>：改动被表达成"把某个白名单路径设成某个值"。
 * 不提供 delete/insert 之类操作，是因为草稿内容是一个固定结构的对象
 * （见 {@code AigStudioDraftContent}），"删掉某个必填节"不是一种有意义的改动，
 * 只会把校验问题推迟到提交时才发现。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioProposalOperationBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * JSON Pointer 路径（如 {@code /promptSections/role}、{@code /objective}）。
     *
     * <p>必须是白名单内的路径——"改哪个字段"由平台定，不由提案方定。</p>
     */
    private String path;

    /**
     * 新值：文本路径要求字符串；清单路径要求字符串数组。
     */
    private Object value;

}
