package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才简历版本对象 hr_talent_resume（设计文档 §8.13、§8.8、§8.21）。
 *
 * <p><b>版本约定</b>：一个人才允许多份简历，同一人才 {@code talent_id + version_no} 唯一；
 * 上传新简历<b>默认创建新版本，绝不覆盖旧文件</b>，旧版本 {@link #currentFlag} 置 {@code 0}，
 * 同一人才只允许一条 {@code current_flag = '1'}（§8.13、§21.7）。</p>
 *
 * <p><b>存储约定</b>：{@link #ossId} 只保存对象存储的<b>对象键</b>，
 * <b>不保存</b>永久公共下载地址；需要访问时先做人才资源级鉴权再流式读取（§8.8、§11.1）。</p>
 *
 * <p><b>与通用附件表的关系</b>：简历使用本版本表管理，<b>不</b>在 {@code hr_recruit_attachment}
 * 中重复保存一份业务记录（§8.8）。</p>
 *
 * <p><b>解析与复核状态使用两个不同的字典</b>（勿混用）：
 * {@link #parseStatus} 取字典 {@code talent_resume_parse_status}
 * （{@code pending/processing/succeeded/failed/reviewing/confirmed}，描述解析任务进度）；
 * {@link #reviewStatus} 取字典 {@code talent_resume_review_status}
 * （{@code pending/reviewing/confirmed/rejected}，描述人工复核结论）；
 * {@link #scanStatus} 为文件安全扫描状态（{@code pending/scanning/passed/failed}），
 * 生产环境建议接入恶意文件扫描（§8.8）。</p>
 *
 * <p>字段与 {@code script/sql/hr_talent.sql} 的建表语句逐列一致；主键列为 {@code resume_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_resume")
public class TalentResume extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 简历版本ID（主键）
     */
    @TableId(value = "resume_id")
    private Long resumeId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 简历版本号（非负整数，从1递增；同一人才内单调递增且不复用）
     */
    private Integer versionNo;

    /**
     * 简历文件 OSS 对象ID（对象键，受控访问，不存长期公网URL）
     */
    private String ossId;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件后缀（小写、不含点）
     */
    private String fileSuffix;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件哈希（SHA-256 十六进制，用于重复文件提示，非唯一约束）
     */
    private String fileHash;

    /**
     * 是否当前版本（0否 1是，同一人才仅一个当前版本）
     */
    private String currentFlag;

    /**
     * 文件安全扫描状态（pending/scanning/passed/failed 等稳定编码）
     */
    private String scanStatus;

    /**
     * 解析状态（pending/processing/succeeded/failed/reviewing/confirmed，字典 talent_resume_parse_status）
     */
    private String parseStatus;

    /**
     * 复核状态（pending/reviewing/confirmed/rejected，字典 talent_resume_review_status；
     * 与 {@link #parseStatus} 分属两个字典）
     */
    private String reviewStatus;

    /**
     * 解析器版本
     */
    private String parserVersion;

    /**
     * 简历来源（upload/import/mail/application 等稳定编码）
     */
    private String sourceType;

    /**
     * 上传人用户ID
     */
    private Long uploadedBy;

    /**
     * 上传时间
     */
    private LocalDateTime uploadedTime;

    /**
     * 删除标志（0代表存在 1代表删除；简历一律逻辑删除，不物理删除对象存储文件）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
