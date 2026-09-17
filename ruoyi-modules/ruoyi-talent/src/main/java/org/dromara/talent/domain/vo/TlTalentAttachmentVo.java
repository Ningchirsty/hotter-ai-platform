package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.talent.domain.TlTalentAttachment;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才附件视图对象 tl_talent_attachment
 * <p>不返回 object_key / bucket / 预签名 URL，下载只能走受控接口。</p>
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalentAttachment.class)
public class TlTalentAttachmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件ID
     */
    private Long attachmentId;

    /**
     * 人才ID
     */
    private Long talentId;

    /**
     * 附件类型（RESUME/ID_CARD/EDUCATION_CERT/OTHER，字典 tl_attachment_type）
     */
    private String attachmentType;

    /**
     * 附件类型标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "attachmentType", other = "tl_attachment_type")
    private String attachmentTypeLabel;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 扩展名（小写，白名单校验）
     */
    private String fileExt;

    /**
     * 字节数
     */
    private Long fileSize;

    /**
     * 文件大小展示文本（服务端格式化）
     */
    private String fileSizeText;

    /**
     * 版本号（同类型自增）
     */
    private Integer version;

    /**
     * 是否当前版本（1是 0历史）
     */
    private String isCurrent;

    /**
     * 扫描状态（PENDING/SCANNING/CLEAN/INFECTED/FAILED）
     */
    private String scanStatus;

    /**
     * 扫描状态说明（服务端按 ScanStatusEnum 生成）
     */
    private String scanStatusLabel;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 当前用户是否可下载（服务端按授权与扫描状态判定）
     */
    private Boolean downloadable;

}
