package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.talent.domain.TlTalent;

import java.io.Serial;
import java.util.List;

/**
 * 人才详情视图对象
 * <p>在 {@link TlTalentVo} 基础上补充附件、联系记录与当前用户权限。
 * 完整手机号（脱敏策略）由父类 {@code phone} 字段承载。</p>
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = TlTalent.class)
public class TlTalentDetailVo extends TlTalentVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 附件列表（不含 object_key / bucket / 预签名 URL）
     */
    private List<TlTalentAttachmentVo> attachments;

    /**
     * 联系跟进记录列表
     */
    private List<TlTalentContactVo> contacts;

    /**
     * 当前用户对该人才的权限（VIEW/DOWNLOAD/VIEW_FULL_PHONE）
     */
    private TalentPermissionVo permissions;

}
