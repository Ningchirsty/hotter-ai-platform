package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitAttachment;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 招聘业务附件视图对象 hr_recruit_attachment。
 *
 * <p><b>安全约束</b>：本对象<b>不返回</b> {@code oss_id}（对象存储标识）与任何下载地址，
 * 页面只能通过 {@code /recruit/attachments/{id}/preview} 与 {@code /{id}/download}
 * 两个鉴权并审计的接口访问文件（设计文档 §11.1「附件下载不返回长期匿名地址」、
 * §15.3「禁止目录公共读和永久匿名链接」）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitAttachment.class)
public class RecruitAttachmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件ID
     */
    private Long attachmentId;

    /**
     * 业务类型（application/interview/background/offer/talent 等稳定编码）
     */
    private String bizType;

    /**
     * 业务对象ID
     */
    private Long bizId;

    /**
     * 附件类型（字典 recruit_attachment_type 编码）
     */
    private String fileType;

    /**
     * 附件类型标签（字典 recruit_attachment_type）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "fileType", other = "recruit_attachment_type")
    private String fileTypeLabel;

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
     * 附件版本号
     */
    private Integer versionNo;

    /**
     * 是否当前版本（0否 1是）
     */
    private String currentFlag;

    /**
     * 安全级别（字典 recruit_data_level 编码）
     */
    private String securityLevel;

    /**
     * 安全级别标签（字典 recruit_data_level）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "securityLevel", other = "recruit_data_level")
    private String securityLevelLabel;

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

}
