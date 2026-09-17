package org.dromara.talent.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.encrypt.annotation.EncryptField;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人才主档对象 tl_talent
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tl_talent")
public class TlTalent extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才ID
     */
    @TableId(value = "talent_id")
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
     * 出生日期（年龄实时计算，不单独维护）
     */
    private LocalDate birthDate;

    /**
     * 仅识别到的年龄（无出生日期时使用）
     */
    private Integer ageOnly;

    /**
     * 识别年龄对应的识别日期
     */
    private LocalDate ageSourceDate;

    /**
     * 手机号密文：写入加密、按主键读解密；禁止作为查询条件
     */
    @EncryptField
    private String phoneCipher;

    /**
     * 手机号标准化哈希（SHA-256，用于重复预检）
     */
    private String phoneHash;

    /**
     * 手机号后四位（弱匹配用，非敏感）
     */
    private String phoneTail4;

    /**
     * 学历（字典 tl_education）
     */
    private String education;

    /**
     * 期望薪资下限（整数元/月）
     */
    private Integer expectSalaryMin;

    /**
     * 期望薪资上限（整数元/月）
     */
    private Integer expectSalaryMax;

    /**
     * 应聘/意向岗位
     */
    private String position;

    /**
     * 联系日期
     */
    private LocalDate contactDate;

    /**
     * 归属区域（GROUP/SZ/ST，字典 tl_region，服务端枚举双重限制）
     */
    private String regionCode;

    /**
     * 人才状态（字典 tl_talent_status）
     */
    private String status;

    /**
     * 共享范围（REGION区域共享 GROUP全集团 GRANT_ONLY仅授权）
     */
    private String shareScope;

    /**
     * 来源（字典 tl_source）
     */
    private String source;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 完整手机号（仅内存态，用于脱敏输出；不落库）
     */
    @TableField(exist = false)
    private String phone;

}
