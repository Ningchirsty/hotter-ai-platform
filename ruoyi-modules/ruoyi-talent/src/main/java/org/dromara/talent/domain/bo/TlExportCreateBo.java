package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.talent.domain.TlExportTask;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;

/**
 * 人才台账导出任务创建业务对象
 * <p>查询条件字段与 {@link TlTalentQueryBo} 保持一致，便于前端直接扁平提交。</p>
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlExportTask.class, reverseConvertGenerate = false)
public class TlExportCreateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导出任务名称
     */
    @NotBlank(message = "任务名称不能为空", groups = {AddGroup.class})
    @Size(max = 128, message = "任务名称长度不能超过128个字符", groups = {AddGroup.class})
    private String taskName;

    /**
     * 姓名（模糊匹配）
     */
    private String name;

    /**
     * 手机号后四位（弱匹配）
     */
    private String phoneTail4;

    /**
     * 归属区域（GROUP/SZ/ST）
     * <p><b>仅作为过滤条件，不构成数据范围</b>：导出范围同样由服务端按当前操作人的可见区域强制收窄。</p>
     */
    @Pattern(regexp = "^(GROUP|SZ|ST)?$", groups = {QueryGroup.class},
        message = "归属区域只能为 GROUP/SZ/ST")
    private String regionCode;

    /**
     * 人才状态（字典 tl_talent_status）
     */
    private String status;

    /**
     * 学历（字典 tl_education）
     */
    private String education;

    /**
     * 应聘/意向岗位
     */
    private String position;

    /**
     * 来源（字典 tl_source）
     */
    private String source;

    /**
     * 联系日期起（含）
     */
    private LocalDate contactDateStart;

    /**
     * 联系日期止（含）
     */
    private LocalDate contactDateEnd;

    /**
     * 是否仅导出存在重复预警的人才
     */
    private Boolean duplicateOnly;

    /**
     * 人才编号（精确/模糊匹配）
     */
    private String talentNo;

    /**
     * 请求参数（用于扩展的日期范围等，RuoYi 通用约定）
     */
    private Map<String, Object> params;

}
