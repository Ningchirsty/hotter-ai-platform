package org.dromara.aigov.workspace.domain;

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
 * 岗位可见性绑定 {@code aig_role_binding}（附件 §4、§10.2）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_role_workspace.sql} 的列注释为准</b>。</p>
 *
 * <p><b>它只是"谁能看到卡片"，不是授权表</b>（F-05 已冻）：只存 {@code sys_dept.dept_id} 与
 * 业务侧的 {@code brand_id}，不做外键、不建第三套授权体系；真实数据访问仍由 RuoYi 数据权限
 * 与治理层 {@code org_scope} 决定。把这张表当授权表用，就会出现"岗位可见＝数据可见"的越权。</p>
 *
 * <p><b>与 {@code aig_agent_binding} 的区别</b>：后者今天只是"发布许可证"、无运行时消费方（F-06），
 * 所以岗位的运行时可见性必须由本模块自己实现并测试——不能假设绑定表已经解决了这件事。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_role_binding")
public class AigRoleBinding extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID
     */
    @TableId(value = "binding_id")
    private Long bindingId;

    /**
     * 岗位版本
     */
    private Long roleVersionId;

    /**
     * 组织（sys_dept.dept_id）
     */
    private Long orgId;

    /**
     * 品牌（业务主表 ID；只存 ID，无外键）
     */
    private Long brandId;

    /**
     * 通道（{@code AigReleaseChannelEnum}）
     */
    private String channel;

    /**
     * 是否生效（Y/N）
     */
    private String enabled;

    /**
     * 生效起（空=立即）
     */
    private LocalDateTime effectiveFrom;

    /**
     * 生效止（空=长期）
     */
    private LocalDateTime effectiveTo;

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
