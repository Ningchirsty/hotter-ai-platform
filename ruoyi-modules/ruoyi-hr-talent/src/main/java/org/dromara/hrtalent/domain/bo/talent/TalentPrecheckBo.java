package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才重复预检入参（SPEC-P3 §3.1、设计文档 §21.15「人才重复预检」）。
 *
 * <p>预检<b>不落库</b>，只按下列字段计算哈希并做分级匹配：</p>
 * <ul>
 *     <li>强匹配：{@code phone} 或 {@code email} 哈希命中；</li>
 *     <li>中匹配：{@code name} +（{@code currentCompany} 或 {@code schoolName} 或 {@code resumeHash}）；</li>
 *     <li>弱匹配：{@code name} + {@code expectedPosition}。</li>
 * </ul>
 *
 * <p><b>硬约束</b>：{@code name} 单独命中不构成任何级别的匹配（设计文档 §8.4）。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentPrecheckBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 姓名（必填，但不作为唯一判断条件）
     */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64")
    private String name;

    /**
     * 电话明文（服务端标准化后计算哈希做强匹配）
     */
    @Size(max = 64, message = "电话长度不能超过 64")
    private String phone;

    /**
     * 邮箱明文（服务端小写标准化后计算哈希做强匹配）
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /**
     * 当前公司（中匹配条件之一）
     */
    @Size(max = 200, message = "当前公司长度不能超过 200")
    private String currentCompany;

    /**
     * 毕业学校（中匹配条件之一；与教育经历表按学校名称匹配）
     */
    @Size(max = 200, message = "学校名称长度不能超过 200")
    private String schoolName;

    /**
     * 简历文件哈希（SHA-256 小写十六进制，中匹配条件之一；与简历版本表 file_hash 匹配）
     */
    @Size(max = 64, message = "简历哈希长度不能超过 64")
    private String resumeHash;

    /**
     * 期望岗位（弱匹配条件）
     */
    @Size(max = 200, message = "期望岗位长度不能超过 200")
    private String expectedPosition;

}
