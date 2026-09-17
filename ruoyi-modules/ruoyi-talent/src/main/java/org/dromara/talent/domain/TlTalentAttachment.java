package org.dromara.talent.domain;

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
 * 人才附件对象 tl_talent_attachment
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tl_talent_attachment")
public class TlTalentAttachment extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件ID
     */
    @TableId(value = "attachment_id")
    private Long attachmentId;

    /**
     * 人才ID
     */
    private Long talentId;

    /**
     * 预留：RuoYi sys_oss 文件ID（默认不登记）
     */
    private Long ossId;

    /**
     * 存储桶（私有桶）
     */
    private String bucket;

    /**
     * 对象键 talent-private/{talentId}/{attachmentId}/v{version}/original{ext}
     */
    private String objectKey;

    /**
     * 附件类型（RESUME/ID_CARD/EDUCATION_CERT/OTHER，字典 tl_attachment_type）
     */
    private String attachmentType;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 扩展名（小写，白名单校验）
     */
    private String fileExt;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 字节数
     */
    private Long fileSize;

    /**
     * 文件 SHA-256（去重用）
     */
    private String fileHash;

    /**
     * 版本号（同类型自增）
     */
    private Integer version;

    /**
     * 是否当前版本（1是 0历史）
     */
    private String isCurrent;

    /**
     * 扫描状态（PENDING/SCANNING/CLEAN/INFECTED/FAILED），非 CLEAN 禁止下载
     */
    private String scanStatus;

    /**
     * 扫描完成时间
     */
    private LocalDateTime scanTime;

    /**
     * 扫描结果说明
     */
    private String scanRemark;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
