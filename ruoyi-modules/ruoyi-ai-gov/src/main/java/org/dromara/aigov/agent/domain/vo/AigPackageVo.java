package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigPackage;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Package 列表视图。
 *
 * <p>字段与实体一一对应后再裁掉长文本：Package 主表没有 longtext，故这里全带。
 * 长文本（Manifest）在版本表，见 {@code AigPackageVersionVo} 的裁剪说明。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigPackage.class)
public class AigPackageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Package ID
     */
    private Long packageId;

    /**
     * 编码
     */
    private String packageCode;

    /**
     * 名称
     */
    private String packageName;

    /**
     * 类型
     */
    private String packageType;

    /**
     * 发布方
     */
    private String publisher;

    /**
     * 许可证
     */
    private String licenseCode;

    /**
     * 校验和
     */
    private String checksum;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 来源引用
     */
    private String sourceRef;

    /**
     * 说明
     */
    private String description;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

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
