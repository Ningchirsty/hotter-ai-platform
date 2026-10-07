package org.dromara.aigov.task.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不可变输入快照 aig_task_snapshot（设计 §9.3）。
 *
 * <p><b>为什么必须冻结而不是执行时现取</b>：执行期间配置一直在变——Product Truth 被更新、
 * 品牌规则调整、模板换版、路由策略改动。若执行时才去读这些配置，
 * 同一个任务在重试或事后复现时会得到不同结果，而「为什么不一致」永远查不出来。
 * 因此创建时把「当时的一切」冻结成 JSON，执行与审核<b>只引用快照</b>。</p>
 *
 * <p><b>{@link #snapshotHash}</b> 是这份不可变性的凭据：执行前后各算一次 SHA-256，
 * 不一致就说明有人改过快照（或序列化不稳定），此时宁可失败也不能继续——
 * 「结果无法复现」比「任务失败」更难收拾。</p>
 *
 * <p>本表刻意<b>不</b>提供修改接口（没有 update_* 列，也不继承 BaseEntity）：
 * 快照一旦冻结就只增不改，新需求走新版本（{@code snapshot_version} 递增）。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_task_snapshot")
public class AigTaskSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 快照ID
     */
    @TableId(value = "snapshot_id")
    private Long snapshotId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 快照版本（同任务内递增）
     */
    private Integer snapshotVersion;

    /**
     * 冻结内容：事实/品牌/文案版本 + Agent/Prompt/Workflow/Template/Provider 路由版本 +
     * 参考资产版本 + 预算与负面约束
     */
    private String snapshotJson;

    /**
     * 快照内容 SHA-256（校验执行期间未被改写）
     */
    private String snapshotHash;

    /**
     * 数据等级
     */
    private String dataLevel;

    /**
     * 外发许可（Y/N）
     */
    private String allowExternal;

    /**
     * 预算上限（算不出留空）
     */
    private BigDecimal budgetAmount;

    /**
     * 负面约束（禁止项/禁改项，执行时强制带入）
     */
    private String negativeConstraints;

    /**
     * 冻结时间
     */
    private LocalDateTime frozenAt;

    /**
     * 创建者
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
