package org.dromara.aigov.task.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.config.AigArtifactProperties;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskArtifact;
import org.dromara.aigov.task.domain.bo.AigTaskArtifactBo;
import org.dromara.aigov.task.domain.vo.AigTaskArtifactVo;
import org.dromara.aigov.task.enums.AigArtifactValidationStatusEnum;
import org.dromara.aigov.task.enums.AigTaskEventTypeEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.helper.AigArtifactValidator;
import org.dromara.aigov.task.mapper.AigTaskArtifactMapper;
import org.dromara.aigov.task.service.IAigTaskArtifactService;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务制品账本实现。
 *
 * <p><b>登记顺序（为什么这样排）</b>：先确认任务存在 → 再确认任务还在收货 →
 * 再校验制品 → 最后才写库。反过来（先写再校验）就会出现「库里有一行、结论却没定」；
 * 而把「任务存在」放在最前面，是因为制品挂在任务上，任务不存在时任何后续结论都无法归因。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigTaskArtifactServiceImpl implements IAigTaskArtifactService {

    /**
     * 平台是否回读重算过哈希。
     *
     * <p>v1 一律 {@code N}：平台只收到生产方声明的 sha256，从不回读对象存储重算。
     * 这个值必须<b>显式写</b>而不是留给 DDL 默认值——默认值会让人以为「有哈希就是验过」。</p>
     */
    private static final String HASH_VERIFIED_NO = "N";

    /**
     * 拒收制品的任务状态：已取消/已拒绝的任务不再接受产出。
     *
     * <p>只列这两个状态是刻意的：异步生产方可能在平台判超时之后才把产物推上来，
     * 那种产出<b>是真的</b>，丢掉它反而让「任务失败但对方其实出了图」无从查证；
     * 而「用户已经取消/业务方已拒绝」的任务再收制品没有意义。</p>
     */
    private static final List<AigTaskStatusEnum> NOT_ACCEPTING = List.of(
        AigTaskStatusEnum.CANCELLED, AigTaskStatusEnum.REJECTED);

    private final AigTaskArtifactMapper artifactMapper;
    private final IAigTaskService taskService;
    private final AigArtifactProperties properties;
    private final AigArtifactRejectionRecorder rejectionRecorder;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigTaskArtifactVo register(AigTaskArtifactBo bo) {
        if (bo == null || bo.getTaskId() == null) {
            throw new ServiceException("任务ID不能为空：制品必须挂在一条具体任务上，否则无法归因");
        }
        AigTask task = taskService.getTask(bo.getTaskId());
        assertAcceptingArtifacts(task);

        AigArtifactValidator.AigArtifactCheck check = AigArtifactValidator.check(bo, properties);
        if (!check.passed()) {
            // 证据走独立事务：调用方（多半在事务里）会因为下面这个异常回滚，
            // 而「对方交了什么、为什么没收」必须留下来
            rejectionRecorder.record(bo, check.detail());
            throw new ServiceException("制品校验失败（" + AigErrorClassEnum.ARTIFACT_INVALID.getCode()
                + "）：" + check.detail() + "；taskId=" + bo.getTaskId()
                + "。已留被拒记录（aig_task_artifact.validation_status=FAIL），请修正后重新登记");
        }

        AigTaskArtifact existing = findSameArtifact(bo.getTaskId(), check.storageRef(), check.sha256());
        if (existing != null) {
            log.info("制品重复登记，返回既有制品ID, taskId={}, artifactId={}, storageRef={}",
                bo.getTaskId(), existing.getArtifactId(), check.storageRef());
            return toVo(existing);
        }

        AigTaskArtifact row = new AigTaskArtifact();
        row.setTaskId(bo.getTaskId());
        row.setAttemptNo(bo.getAttemptNo() != null ? bo.getAttemptNo()
            : (task.getAttemptNo() == null ? 0 : task.getAttemptNo()));
        row.setResultId(bo.getResultId());
        row.setArtifactType(check.artifactType());
        row.setMimeType(check.mimeType());
        row.setSizeBytes(bo.getSizeBytes());
        row.setSha256(check.sha256());
        row.setStorageRef(check.storageRef());
        row.setHashVerified(HASH_VERIFIED_NO);
        row.setValidationStatus(AigArtifactValidationStatusEnum.PASS.getCode());
        row.setValidationDetail(null);
        row.setDelFlag("0");
        row.setRemark(bo.getRemark());
        artifactMapper.insert(row);
        if (row.getArtifactId() == null) {
            throw new ServiceException("制品登记失败：未取回主键, taskId=" + bo.getTaskId());
        }

        // 事件与制品行同一个事务：制品入了库却没有事件，事件流就不再是「发生了什么」的完整记录
        taskService.recordEvent(bo.getTaskId(), AigTaskEventTypeEnum.AI_TASK_ARTIFACT_ADDED,
            buildEventDetail(row), buildEventPayload(row));
        log.info("制品入库, taskId={}, artifactId={}, type={}, mime={}, size={}",
            bo.getTaskId(), row.getArtifactId(), row.getArtifactType(), row.getMimeType(), row.getSizeBytes());
        return toVo(row);
    }

    @Override
    public List<AigTaskArtifactVo> listByTask(Long taskId, boolean includeRejected) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        List<AigTaskArtifact> rows = artifactMapper.selectList(new LambdaQueryWrapper<AigTaskArtifact>()
            .eq(AigTaskArtifact::getTaskId, taskId)
            .eq(!includeRejected, AigTaskArtifact::getValidationStatus,
                AigArtifactValidationStatusEnum.PASS.getCode())
            .orderByDesc(AigTaskArtifact::getCreateTime)
            .orderByDesc(AigTaskArtifact::getArtifactId));
        List<AigTaskArtifactVo> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (AigTaskArtifact row : rows) {
            list.add(toVo(row));
        }
        return list;
    }

    /**
     * 任务是否还接受制品。
     *
     * @param task 任务
     */
    private void assertAcceptingArtifacts(AigTask task) {
        AigTaskStatusEnum status = AigTaskStatusEnum.find(task.getStatus());
        if (status != null && NOT_ACCEPTING.contains(status)) {
            throw new ServiceException("任务处于 " + status.getCode()
                + "，不再接受制品登记：taskId=" + task.getTaskId()
                + "（已取消/已拒绝的任务其产出不再计为交付物）");
        }
    }

    /**
     * 查同一任务内是否已有相同对象引用与哈希的通过行（幂等）。
     *
     * @param taskId     任务ID
     * @param storageRef 对象引用
     * @param sha256     内容哈希
     * @return 既有制品；无则 null
     */
    private AigTaskArtifact findSameArtifact(Long taskId, String storageRef, String sha256) {
        return artifactMapper.selectOne(new LambdaQueryWrapper<AigTaskArtifact>()
            .eq(AigTaskArtifact::getTaskId, taskId)
            .eq(AigTaskArtifact::getStorageRef, storageRef)
            .eq(AigTaskArtifact::getSha256, sha256)
            .eq(AigTaskArtifact::getValidationStatus, AigArtifactValidationStatusEnum.PASS.getCode())
            .orderByAsc(AigTaskArtifact::getArtifactId)
            .last("limit 1"));
    }

    /**
     * 事件说明（可读，含「哈希是声明值」这句）。
     *
     * @param row 制品行
     * @return 说明文本
     */
    private String buildEventDetail(AigTaskArtifact row) {
        return "制品入库 artifactId=" + row.getArtifactId()
            + "，类型=" + row.getArtifactType()
            + "，MIME=" + row.getMimeType()
            + "，大小=" + row.getSizeBytes() + " 字节"
            + "，sha256=" + row.getSha256() + "（生产方声明值，平台未回读重算）";
    }

    /**
     * 事件载荷（契约 {@code execution-event.schema.json} 的 payload：{@code artifactIds} 数组）。
     *
     * @param row 制品行
     * @return JSON 文本；序列化失败时返回 null（事件本身仍要写，说明里已有全部事实）
     */
    private String buildEventPayload(AigTaskArtifact row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactIds", List.of(String.valueOf(row.getArtifactId())));
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("组装制品事件载荷失败, artifactId={}", row.getArtifactId(), e);
            return null;
        }
    }

    /**
     * 实体 → 视图（含两个可读标签）。
     *
     * <p>标签在服务层统一补：只给原始码的话，页面上一定有人把
     * {@code hashVerified=N} 读成「已校验」。用同一处转换也保证列表与单条返回一致。</p>
     *
     * @param row 制品行
     * @return 视图
     */
    private AigTaskArtifactVo toVo(AigTaskArtifact row) {
        AigTaskArtifactVo vo = new AigTaskArtifactVo();
        BeanUtil.copyProperties(row, vo);
        AigArtifactValidationStatusEnum status = AigArtifactValidationStatusEnum.find(row.getValidationStatus());
        vo.setValidationStatusLabel(status == null ? StringUtils.blankToDefault(row.getValidationStatus(), "未知")
            : status.getDesc());
        vo.setHashVerifiedLabel(HASH_VERIFIED_NO.equalsIgnoreCase(row.getHashVerified())
            ? "生产方声明值（平台未回读重算）" : "平台回读重算");
        return vo;
    }

}
