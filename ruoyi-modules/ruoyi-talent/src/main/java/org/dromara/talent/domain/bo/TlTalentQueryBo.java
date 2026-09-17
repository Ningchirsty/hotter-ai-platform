package org.dromara.talent.domain.bo;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.dromara.common.core.validate.QueryGroup;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;

/**
 * 人才库查询业务对象
 * <p>仅承载查询条件，不承载任何数据范围语义。</p>
 *
 * @author talent
 */
@Data
public class TlTalentQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * <p><b>仅作为过滤条件，不构成数据范围</b>：数据范围永远由服务端 TalentScopeHelper
     * 计算出的可见区域列表决定，本字段只能在此范围内额外收窄。</p>
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
     * 是否仅查询存在重复预警的人才
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
