package org.dromara.content.domain;

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
 * 设计开工包对象 cp_work_package
 *
 * <p>对应设计文档 §7.3：让平面「无需重新翻找、核对和追问基础资料」。
 * {@code contentJson} 结构见 SPEC §4.5；其中 {@code immutableItems} 必须包含
 * 产品主体、Logo、包装文字——对应设计文档 §10「AI 生产 Agent 禁止自由重绘」。</p>
 *
 * @author content
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cp_work_package")
public class CpWorkPackage extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 开工包ID
     */
    @TableId(value = "package_id")
    private Long packageId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 签发时冻结的事实版本
     */
    private Integer snapshotVersion;

    /**
     * 开工包全文（结构见 SPEC §4.5）
     */
    private String contentJson;

    /**
     * 状态（DRAFT草稿 / ISSUED已签发）
     */
    private String status;

    /**
     * 生成人
     */
    private Long generatedBy;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 签发人
     */
    private Long issuedBy;

    /**
     * 签发时间
     */
    private LocalDateTime issuedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
