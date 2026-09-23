package org.dromara.hrtalent.domain.vo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入预检结果（上传阶段返回给前端）。
 *
 * <p><b>它存在的意义</b>：导入最怕「传上去才发现一堆行不合法，数据已经进去了」。
 * 预检把结果先摊开：总行数、可导入行数、错误与提示清单、以及前若干行的解析预览；
 * 用户在确认之前就能看清将要发生什么，也可以直接改文件重传。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitImportPreviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次ID（确认导入时回传）
     */
    private Long batchId;

    /**
     * 批次编号
     */
    private String batchNo;

    /**
     * 导入类型
     */
    private String importType;

    /**
     * 导入类型名称
     */
    private String importTypeName;

    /**
     * 源文件名称
     */
    private String sourceFileName;

    /**
     * 解析到的数据总行数（不含表头与空行）
     */
    private int totalCount;

    /**
     * 可导入行数（无 error 级问题的行）
     */
    private int validCount;

    /**
     * 错误行数（存在 error 级问题，不会被导入）
     */
    private int errorCount;

    /**
     * 提示数（warning 级，不影响导入）
     */
    private int warningCount;

    /**
     * 模板列说明（列名 → 是否必填/示例），供前端在预检结果里回显口径
     */
    private List<Map<String, Object>> columns = new ArrayList<>();

    /**
     * 问题明细（error 与 warning 都包含；最多返回 {@code MAX_RETURNED_ERRORS} 条）
     */
    private List<RecruitImportErrorVo> issues = new ArrayList<>();

    /**
     * 前若干行的解析预览（列名 → 值），让人确认「列有没有读串」
     */
    private List<Map<String, Object>> preview = new ArrayList<>();

    /**
     * 结果说明（给用户看的整句提示）
     */
    private String message;

    /**
     * 预览行示例用的空 Map（避免前端判空分支）
     *
     * @return 空 Map
     */
    public static Map<String, Object> emptyRow() {
        return new LinkedHashMap<>();
    }

}
