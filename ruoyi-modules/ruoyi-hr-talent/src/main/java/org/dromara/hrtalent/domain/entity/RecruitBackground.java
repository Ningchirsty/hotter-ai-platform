package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.encrypt.annotation.EncryptField;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘背调记录对象 hr_recruit_background。
 * <p>字段严格对齐 {@code script/sql/hr_recruit.sql} 的建表语句，主键为表级名
 * {@code background_id}（SPEC-P3 §1）。</p>
 *
 * <p><b>敏感字段</b>：{@link #detailCipher} 是背调敏感说明，按设计文档 §15.1 属「高度敏感」，
 * 必须密文落库（{@link EncryptField}，由 {@code ruoyi-common-encrypt} 的 MyBatis 拦截器
 * 在入库前加密、查询后解密），并且 <b>只允许</b>在
 * {@code GET /recruit/background-checks/{id}/detail} 且已写敏感审计后返回给调用方；
 * 列表与普通详情一律不返回，日志一律不输出（设计文档 §8.7、§15.2、§21.9）。</p>
 *
 * <p><b>并发控制</b>：本表没有 {@code version} 列，故不启用乐观锁；同一应聘记录
 * 「只有一条当前有效背调」的不变式由服务层维护（新建时把旧记录置为
 * {@code status=cancelled}，见 {@code BackgroundStatusEnum}）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_background")
public class RecruitBackground extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 背调记录ID（主键）
     */
    @TableId(value = "background_id")
    private Long backgroundId;

    /**
     * 应聘记录ID（一条应聘记录仅一条当前有效背调）
     */
    private Long applicationId;

    /**
     * 是否已获得候选人授权（0否 1是）
     */
    private String authorizedFlag;

    /**
     * 授权时间
     */
    private LocalDateTime authorizeTime;

    /**
     * 背调负责人用户ID
     */
    private Long checkerId;

    /**
     * 背调完成时间
     */
    private LocalDateTime checkTime;

    /**
     * 背调开始日期
     */
    private LocalDate checkStartDate;

    /**
     * 背调结束日期
     */
    private LocalDate checkEndDate;

    /**
     * 背调结论（pending/pass/fail/waived，字典 recruit_background_result）
     */
    private String result;

    /**
     * 未通过原因编码（字典编码，<b>不存中文</b>）
     */
    private String failureReasonCode;

    /**
     * 背调核查项清单（设计文档 §8.7、§7.4）
     */
    private String checkItems;

    /**
     * 背调明细密文（禁止截断，禁止日志输出）
     */
    @EncryptField
    private String detailCipher;

    /**
     * 免背调授权原因（result=waived 时必填，设计文档 §7.2）
     */
    private String waiveReason;

    /**
     * 背调状态（draft/checking/finished/cancelled，见 BackgroundStatusEnum）
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
