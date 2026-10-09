package org.dromara.aigov.task.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.AigTaskArtifact;
import org.dromara.aigov.task.domain.bo.AigTaskArtifactBo;
import org.dromara.aigov.task.enums.AigArtifactValidationStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskArtifactMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 被拒制品登记（<b>独立事务</b>）。
 *
 * <p><b>为什么必须单独一个 Bean、且 REQUIRES_NEW</b>：制品被拒时调用方会拿到异常，
 * 而它十有八九正处在自己的事务里（执行链路本来就在事务里跑）。若把被拒记录和调用方写在
 * 同一个事务，异常一抛、整个事务回滚——<b>证据跟着一起没了</b>，库里只剩一个错误码，
 * 事后谁也说不清对方到底交了什么。这正是「失败必须响亮」在存储层的落点：
 * 结论要响亮（抛异常），证据要独立存活（新事务提交）。</p>
 *
 * <p><b>写入前一律截断到列宽</b>：被拒的行往往<b>正是</b>因为字段过长/形态不对
 * （比如 sha256 传了 200 个字符）。不截断就会让「记录拒绝原因」这一句 SQL 自己报
 * {@code Data too long}，于是干净的业务拒绝变成 500，而且证据依然没落库。
 * 截断只影响证据行（{@code validation_status=FAIL}），不会污染任何通过行的值。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigArtifactRejectionRecorder {

    /**
     * 列宽（与 {@code script/sql/aig_task_artifact.sql} 一致）。
     */
    private static final int TYPE_MAX = 32;
    private static final int MIME_MAX = 128;
    private static final int SHA256_MAX = 64;
    private static final int STORAGE_REF_MAX = 512;
    private static final int DETAIL_MAX = 1000;

    private final AigTaskArtifactMapper artifactMapper;

    /**
     * 落一条被拒记录。
     *
     * @param bo     原始入参（可能整体为 null 或字段缺失）
     * @param detail 字段级原因（一次列全）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(AigTaskArtifactBo bo, String detail) {
        AigTaskArtifact row = new AigTaskArtifact();
        row.setTaskId(bo == null ? null : bo.getTaskId());
        row.setAttemptNo(bo == null || bo.getAttemptNo() == null ? 0 : bo.getAttemptNo());
        row.setResultId(bo == null ? null : bo.getResultId());
        row.setArtifactType(truncate(bo == null ? null : bo.getArtifactType(), TYPE_MAX));
        row.setMimeType(truncate(bo == null ? null : bo.getMimeType(), MIME_MAX));
        row.setSizeBytes(bo == null || bo.getSizeBytes() == null ? 0L : bo.getSizeBytes());
        row.setSha256(truncate(bo == null ? null : bo.getSha256(), SHA256_MAX));
        row.setStorageRef(truncate(bo == null ? null : bo.getStorageRef(), STORAGE_REF_MAX));
        // 声明值从未被平台核对过，被拒行同样如此——这一列必须显式写，不能靠 DDL 默认值兜底
        row.setHashVerified("N");
        row.setValidationStatus(AigArtifactValidationStatusEnum.FAIL.getCode());
        row.setValidationDetail(truncate(detail, DETAIL_MAX));
        row.setDelFlag("0");
        // 备注里留一句「这是被拒记录」，避免有人把 FAIL 行当成生产方的正常提交
        row.setRemark(truncate("制品被拒（未入库）：" + detail, 500));
        artifactMapper.insert(row);
        log.warn("制品被拒并留痕, taskId={}, artifactId={}, 原因={}",
            row.getTaskId(), row.getArtifactId(), row.getValidationDetail());
    }

    /**
     * 截断到列宽（null 安全）。
     *
     * @param value 原值
     * @param max   列宽
     * @return 截断后的值（null 输入返回空串：这几列都是 NOT NULL）
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

}
