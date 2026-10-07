package org.dromara.aigov.agent.domain;

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
 * Package 版本 aig_package_version（设计 §6.1 Manifest + §6.2 拒绝规则 + §5.4 状态机）。
 *
 * <p><b>发布状态在版本上</b>：{@link #releaseStatus} 走 {@code AigReleaseStateMachine}
 * 的七态；父表只有记录状态。</p>
 *
 * <p><b>{@link #manifestHash} 必须对「原样入库的那串字节」计算</b>，理由与
 * {@code aig_task_snapshot.snapshot_hash} 完全相同：若先反序列化再序列化后算哈希，
 * 字段顺序、null 处理、数字格式都会影响结果，于是「Manifest 有没有被改过」这个判断
 * 会时好时坏，最后没人再信它。因此这里存 hash、并用它校验，
 * <b>不做任何「规范化后再算」的处理</b>。</p>
 *
 * <p><b>{@link #scanDetail} 在拒绝时必须写明命中哪一条拒绝规则</b>：设计 §6.2 列了五类
 * 拒绝项（DB 直连/Shell/SSH/Docker Socket、未声明外网或数据等级、任意代码执行、
 * 来源或校验和不明、绕过平台链路）。只说「扫描不通过」等于没说——
 * 作者不知道改哪里，复核者也无法判断是否已修复。</p>
 *
 * <p><b>并发</b>：本表刻意没有乐观锁列。发布状态的推进用
 * <b>「按当前状态做条件更新」</b>（{@code update ... where package_version_id=? and release_status=?}），
 * 这比通用乐观锁更精确：它同时表达了「必须是那个状态」与「只能推进到下一个状态」，
 * 冲突时影响行数为 0，由服务层据此报「状态已被并发修改」。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_package_version")
public class AigPackageVersion extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Package 版本ID
     */
    @TableId(value = "package_version_id")
    private Long packageVersionId;

    /**
     * 所属 Package
     */
    private Long packageId;

    /**
     * 版本号（同 Package 内唯一）
     */
    private String version;

    /**
     * Manifest 原文（声明式：身份/能力/依赖/权限/质量；不含可执行代码）
     */
    private String manifestJson;

    /**
     * Manifest 原文 SHA-256（对原样入库字节计算）
     */
    private String manifestHash;

    /**
     * 包体对象键（{@code aigov.package.store-body=true} 时写入）
     *
     * <p>为空 = 该版本未留存包体（未开开关，或本列上线前的历史版本）。对象在私有前缀下、
     * <b>不登记 {@code sys_oss}</b>，只由治理模块按键访问。</p>
     */
    private String bodyRef;

    /**
     * 拒绝规则扫描结论（PASS/REJECT/PENDING，设计 §6.2）
     */
    private String scanResult;

    /**
     * 扫描说明（拒绝时必须写明命中哪一条）
     */
    private String scanDetail;

    /**
     * 发布状态机（{@code AigReleaseStatusEnum}）
     */
    private String releaseStatus;

    /**
     * 发布通道/可见范围（{@code AigReleaseChannelEnum}）
     */
    private String releaseChannel;

    /**
     * 沙箱运行所用测试项目（禁止把沙箱结果写进正式资产）
     */
    private Long sandboxProjectId;

    /**
     * 最近一次评测运行ID
     */
    private Long evaluationRunId;

    /**
     * 回滚目标版本
     */
    private Long rollbackTargetVersionId;

    /**
     * 审批人（业务 Owner / AI 管理员 / 平台管理员三方）
     */
    private Long approvedBy;

    /**
     * 审批时间
     */
    private LocalDateTime approvedAt;

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
     * 版本说明
     */
    private String remark;

}
