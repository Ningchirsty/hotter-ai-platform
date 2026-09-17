package org.dromara.talent.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前用户对单条人才的权限视图对象
 * <p>纯计算值，无对应实体，故不使用 {@code @AutoMapper}。</p>
 *
 * @author talent
 */
@Data
public class TalentPermissionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否可见
     */
    private Boolean view;

    /**
     * 是否可下载附件
     */
    private Boolean download;

    /**
     * 是否可查看完整手机号
     */
    private Boolean viewFullPhone;

}
