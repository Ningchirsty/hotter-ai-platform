package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 忽略疑似重复业务对象（SPEC-P4 §2.5 POST /talent/duplicates/{id}/ignore）。
 *
 * <p><b>语义</b>：人工判定「只是相似，不是同一人」或「暂不处理」，
 * 案件置为 {@code ignored}（{@code samePerson = false} 时置为 {@code not_same}），
 * 之后不再出现在待处理列表；<b>不改动任何人才资料</b>。</p>
 *
 * @author hr-talent
 */
@Data
public class DuplicateIgnoreBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 忽略原因（必填，弱匹配仅提示，忽略需要留痕）
     */
    private String reason;

    /**
     * 处理结论：是否判定为非同一人（true 非同一人 / false 仅本次忽略）
     */
    private Boolean notSamePerson;

}
