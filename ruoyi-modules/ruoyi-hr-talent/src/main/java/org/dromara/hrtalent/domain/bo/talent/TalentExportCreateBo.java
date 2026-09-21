package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 人才导出创建业务对象（SPEC-P4 §2.6 F 线、设计文档 §8.20）。
 *
 * <p><b>用途（purpose）</b>：{@code purpose} <b>不</b>在参数校验层强制，
 * 由服务层判定——敏感台账为空时先写 {@code denied} 审计再拒绝，
 * 保证「被拒绝的导出」同样留痕（与简历受控下载同一姿态）。</p>
 *
 * <p><b>字段清单（fields）</b>：只接受服务端白名单内的字段名；为空时按导出类型取默认全字段。
 * 字段清单会随任务一并快照到 {@code hr_talent_export_task.fields_json}，便于事后追溯。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentExportCreateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导出类型（normal普通台账/sensitive敏感台账）
     */
    private String exportType;

    /**
     * 筛选条件（设计文档 §8.17 组合检索；可见范围由服务端叠加，不接受前端传入）。
     *
     * <p>与 {@code GET /talent/profiles} 复用<b>同一个</b> {@link TalentProfileQueryBo}，
     * 保证「页面上能搜到的」与「导出能导出的」条件口径完全一致，避免两套检索逻辑分叉。</p>
     */
    private TalentProfileQueryBo filters;

    /**
     * 导出字段清单（服务端白名单内的字段名，为空表示按导出类型的默认字段集）
     */
    private List<String> fields;

    /**
     * 导出用途/原因（敏感台账必填）
     */
    private String purpose;

    /**
     * 备注（写入导出任务记录，可为空）
     */
    private String remark;

}
