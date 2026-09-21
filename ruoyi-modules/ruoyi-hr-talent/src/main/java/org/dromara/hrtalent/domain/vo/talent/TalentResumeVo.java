package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentResume;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才简历版本视图对象 hr_talent_resume（SPEC-P4 §2.1 / 设计文档 §8.13）。
 *
 * <p><b>安全约束</b>：本对象<b>不返回</b> {@code oss_id}（对象存储对象键）与任何长期下载地址；
 * 页面只能通过鉴权并写审计的 {@code /talent/resumes/{id}/download} 接口读取文件（§8.8、§11.1）。
 * {@link #downloadApi} 只是<b>系统内受控访问路径</b>（仍需登录与权限），不是对象存储地址。</p>
 *
 * <p><b>重复文件提示</b>：{@link #duplicateResumeId} / {@link #duplicateFileHint} 仅在
 * 上传响应中填充，用于提示「该人才已存在内容完全相同的简历文件」，
 * <b>不阻断</b>新版本创建（{@code file_hash} 非唯一约束）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentResume.class)
public class TalentResumeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 简历版本ID
     */
    private Long resumeId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 简历版本号（同一人才内单调递增）
     */
    private Integer versionNo;

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
     * 文件哈希（SHA-256 十六进制，用于重复文件提示）
     */
    private String fileHash;

    /**
     * 是否当前版本（0否 1是）
     */
    private String currentFlag;

    /**
     * 文件安全扫描状态（pending/scanning/passed/failed 等稳定编码）
     */
    private String scanStatus;

    /**
     * 解析状态（字典 talent_resume_parse_status 编码：pending/processing/succeeded/failed/reviewing/confirmed）
     */
    private String parseStatus;

    /**
     * 解析状态标签（字典 <b>talent_resume_parse_status</b>）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "parseStatus", other = "talent_resume_parse_status")
    private String parseStatusLabel;

    /**
     * 复核状态（字典 <b>talent_resume_review_status</b> 编码：pending/reviewing/confirmed/rejected）
     */
    private String reviewStatus;

    /**
     * 复核状态标签（字典 <b>talent_resume_review_status</b>；与解析状态是两个不同的字典，勿混用）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "reviewStatus", other = "talent_resume_review_status")
    private String reviewStatusLabel;

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
     * 上传人昵称（由 {@link #uploadedBy} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "uploadedBy")
    private String uploadedByName;

    /**
     * 上传时间
     */
    private LocalDateTime uploadedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 受控下载路径（系统内地址，仍需登录与权限校验；<b>不是</b>对象存储地址）
     */
    private String downloadApi;

    /**
     * 同人才中内容完全相同的简历版本ID（仅上传响应填充，可为空）
     */
    private Long duplicateResumeId;

    /**
     * 重复文件中文提示（仅上传响应填充，可为空）
     */
    private String duplicateFileHint;

    /**
     * 构造受控下载路径。
     * <p>只依赖主键拼接系统内路径，不暴露对象键；MyBatis 映射时该属性无同名列，会被忽略写入。</p>
     *
     * @return 受控下载路径；{@code resumeId} 为空时返回 null
     */
    public String getDownloadApi() {
        if (downloadApi != null) {
            return downloadApi;
        }
        return resumeId == null ? null : "/talent/resumes/" + resumeId + "/download";
    }

}
