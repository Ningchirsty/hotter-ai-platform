package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent Package 主定义 aig_package（设计 §6.1 身份与来源）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_agent_registry.sql} 的列注释为准</b>，
 * 此处只补「为什么这么设计」。</p>
 *
 * <p><b>为什么 Package 与 Agent 是两级、而不是一个字段</b>：一个 Package 可以同时带入
 * 多个 Agent 与 Skill（设计 §6.1 的 package_type 允许 MIXED），并作为<b>一起安装、
 * 一起回滚</b>的单位。把「谁发布的、哪个许可证、校验和是多少」放在 Package 上，
 * 而不是每个 Agent 各存一份：同一批内容来自同一个来源，来源信息本该只有一处，
 * 否则三个 Agent 里有一个填错，安装时按谁算都说不清。</p>
 *
 * <p><b>状态只有「记录状态」</b>（0正常/1停用）：发布状态属于<b>版本</b>，
 * 见 {@code AigPackageVersion#releaseStatus}——同一 Package 完全可能
 * v1 已 STABLE、v2 还在 DRAFT。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_package")
public class AigPackage extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Package ID
     */
    @TableId(value = "package_id")
    private Long packageId;

    /**
     * Package 编码（唯一，跨版本稳定）
     */
    private String packageCode;

    /**
     * Package 名称
     */
    private String packageName;

    /**
     * 类型（AGENT/SKILL/MIXED，{@code AigPackageTypeEnum}）
     */
    private String packageType;

    /**
     * 发布方（来源明确是 §6.2 的拒绝项之一）
     */
    private String publisher;

    /**
     * 许可证（不明确即拒绝安装）
     */
    private String licenseCode;

    /**
     * 包体/Manifest SHA-256（校验和不明确即拒绝安装）
     */
    private String checksum;

    /**
     * 来源类型（UPLOAD 上传 / TRUSTED_SOURCE 登记可信来源）
     */
    private String sourceType;

    /**
     * 来源引用（上传存储键或可信来源地址）
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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
