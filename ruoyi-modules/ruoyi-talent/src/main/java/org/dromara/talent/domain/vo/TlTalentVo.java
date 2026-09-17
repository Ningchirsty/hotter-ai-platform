package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.sensitive.annotation.Sensitive;
import org.dromara.common.sensitive.core.SensitiveStrategy;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.TlTalent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人才列表行视图对象 tl_talent
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalent.class)
public class TlTalentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才ID
     */
    private Long talentId;

    /**
     * 人才编号（对外展示，导出用）
     */
    private String talentNo;

    /**
     * 姓名（明文业务字段，按权限显示）
     */
    private String name;

    /**
     * 性别（0未知 1男 2女，字典 tl_gender）
     */
    private String gender;

    /**
     * 性别标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "gender", other = "tl_gender")
    private String genderLabel;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 年龄（服务端实时计算）
     */
    private Integer age;

    /**
     * 仅识别到的年龄（无出生日期时使用）
     */
    private Integer ageOnly;

    /**
     * 识别年龄对应的识别日期（与 ageOnly 配对展示，提示待补出生日期）
     */
    private LocalDate ageSourceDate;

    /**
     * 学历（字典 tl_education）
     */
    private String education;

    /**
     * 学历标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "education", other = "tl_education")
    private String educationLabel;

    /**
     * 期望薪资下限（整数元/月）
     */
    private Integer expectSalaryMin;

    /**
     * 期望薪资上限（整数元/月）
     */
    private Integer expectSalaryMax;

    /**
     * 期望薪资展示文本（服务端格式化为 8.5K 形式）
     */
    private String expectSalaryText;

    /**
     * 应聘/意向岗位
     */
    private String position;

    /**
     * 联系日期
     */
    private LocalDate contactDate;

    /**
     * 归属区域（GROUP/SZ/ST，字典 tl_region）
     */
    private String regionCode;

    /**
     * 归属区域标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "regionCode", other = "tl_region")
    private String regionLabel;

    /**
     * 人才状态（字典 tl_talent_status）
     */
    private String status;

    /**
     * 人才状态标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "status", other = "tl_talent_status")
    private String statusLabel;

    /**
     * 共享范围（REGION区域共享 GROUP全集团 GRANT_ONLY仅授权）
     */
    private String shareScope;

    /**
     * 来源（字典 tl_source）
     */
    private String source;

    /**
     * 来源标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "source", other = "tl_source")
    private String sourceLabel;

    /**
     * 备注
     */
    private String remark;

    /**
     * 完整手机号（仅 JSON 响应场景按 talent:profile:phone 权限脱敏，非 String 场景不输出）
     */
    @Sensitive(strategy = SensitiveStrategy.PHONE, perms = {TalentConstants.PERM_PROFILE_PHONE})
    private String phone;

    /**
     * 服务端预脱敏手机号（138****1234），用于非 JSON 场景兜底展示，不含完整号码
     */
    private String phoneMasked;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
