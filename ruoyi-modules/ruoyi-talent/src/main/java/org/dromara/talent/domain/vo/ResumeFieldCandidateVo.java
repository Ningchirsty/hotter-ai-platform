package org.dromara.talent.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 简历导入单字段候选值视图对象。
 * <p>
 * 抽取结果一律作为<b>候选值</b>返回，必须由前端写回表单并经人工确认后才写入主档，
 * 服务端不做任何静默覆盖。
 *
 * @author talent
 */
@Data
public class ResumeFieldCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字段名（与 tl_talent 列名 camelCase 对齐；email / experienceText 为非主档字段）
     */
    private String field;

    /**
     * 字段中文名（前端展示用）
     */
    private String label;

    /**
     * 候选值（统一为字符串；日期为 yyyy-MM-dd，字典字段为字典码）
     */
    private String value;

    /**
     * 置信度 0-1；来源为 DEFAULT 的字段为 null
     */
    private Double confidence;

    /**
     * 来源（FILENAME 文件名 / TEXT 简历正文 / DEFAULT 服务端缺省）
     */
    private String source;

    /**
     * 抽取说明（提示人工核对的原因，不含简历正文）
     */
    private String hint;

}
