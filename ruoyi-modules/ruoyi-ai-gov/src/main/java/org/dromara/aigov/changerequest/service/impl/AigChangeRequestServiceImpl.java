package org.dromara.aigov.changerequest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.changerequest.domain.AigChangeRequest;
import org.dromara.aigov.changerequest.domain.bo.AigChangeRequestBo;
import org.dromara.aigov.changerequest.domain.vo.AigChangeRequestVo;
import org.dromara.aigov.changerequest.enums.AigChangeRequestStatusEnum;
import org.dromara.aigov.changerequest.enums.AigChangeTypeEnum;
import org.dromara.aigov.changerequest.mapper.AigChangeRequestMapper;
import org.dromara.aigov.changerequest.service.IAigChangeRequestService;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.bo.AigRoutePolicyBo;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.service.IAigRoutePolicyService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 变更单实现（Q1C 完整版）。
 *
 * <h3>三条硬规矩（都写在代码里，不靠页面）</h3>
 * <ol>
 *     <li><b>申请人不得自批</b>：与 {@code aig_call_approval} 同规矩——一张单子要两个人才能成立；</li>
 *     <li><b>批准即执行，且执行前做乐观并发校验</b>：拿申请时的 {@code before_json} 和**当前行**
 *         逐字段比对。不一致就置 {@code CONFLICTED} 并说清是谁先改了——审批的意义不是"覆盖别人"；</li>
 *     <li><b>执行走既有服务方法</b>：{@code routePolicyService.create/update/remove}。
 *         因此 Q1C 那道闸门（{@code AigHighRiskGuard}）仍然生效——批准人持有 high-risk，闸门放行；
 *         这条路径<b>不提供任何绕过闸门的方式</b>。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigChangeRequestServiceImpl implements IAigChangeRequestService {

    /**
     * 快照里的字段名（before/after 用同一套键，页面对比时不需要翻译）。
     */
    private static final String K_DATA_LEVEL = "dataLevel";
    private static final String K_ALLOW_EXTERNAL = "allowExternal";
    private static final String K_PREFERRED = "preferredDeployment";
    private static final String K_REQUIRE_APPROVAL = "requireApproval";
    private static final String K_FALLBACK = "fallbackToManual";
    private static final String K_STATUS = "status";

    private final AigChangeRequestMapper changeRequestMapper;
    private final AigRoutePolicyMapper routePolicyMapper;
    private final IAigRoutePolicyService routePolicyService;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(AigChangeRequestBo bo, Long actorId, String actorName) {
        if (bo == null) {
            throw new ServiceException("变更单入参不能为空");
        }
        if (actorId == null) {
            throw new ServiceException("提交变更单需要明确的申请人");
        }
        AigChangeTypeEnum type = AigChangeTypeEnum.find(bo.getChangeType());
        if (type == null) {
            throw new ServiceException("非法的变更类型：" + bo.getChangeType()
                + "（只能是 POLICY_CREATE/POLICY_UPDATE/POLICY_DELETE）");
        }
        if (StringUtils.isBlank(bo.getCapabilityCode())) {
            throw new ServiceException("能力编码不能为空");
        }
        AigRoutePolicy current = null;
        if (type != AigChangeTypeEnum.POLICY_CREATE) {
            if (bo.getTargetRef() == null) {
                throw new ServiceException("修改/删除策略必须给出目标策略ID（targetRef）");
            }
            current = routePolicyMapper.selectById(bo.getTargetRef());
            if (current == null) {
                throw new ServiceException("目标策略不存在或已删除：" + bo.getTargetRef());
            }
        }
        Map<String, Object> after = type == AigChangeTypeEnum.POLICY_DELETE ? null : targetOf(bo, current);
        if (after != null && StringUtils.isBlank(str(after.get(K_DATA_LEVEL)))) {
            // 修改时数据等级沿用目标行，所以这里校验的是"最终能不能确定等级"而不是"入参有没有给"
            throw new ServiceException("无法确定数据等级：新增策略必须给出 dataLevel，"
                + "修改策略请确认目标行上已有数据等级");
        }

        AigChangeRequest entity = new AigChangeRequest();
        entity.setCapabilityCode(bo.getCapabilityCode().trim());
        entity.setChangeType(type.getCode());
        entity.setTargetRef(bo.getTargetRef());
        entity.setBeforeJson(current == null ? null : toJson(snapshotOf(current)));
        entity.setAfterJson(after == null ? null : toJson(after));
        entity.setReason(bo.getReason());
        entity.setStatus(AigChangeRequestStatusEnum.PENDING.getCode());
        entity.setRequesterId(actorId);
        entity.setRequesterName(actorName);
        entity.setDelFlag("0");
        entity.setRemark(bo.getRemark());
        changeRequestMapper.insert(entity);
        if (entity.getChangeRequestId() == null) {
            // 与其它模块同一取舍：拿不回主键说明写入没成功
            throw new ServiceException("变更单保存失败：未取回主键");
        }
        log.info("高风险变更单已提交, changeRequestId={}, capabilityCode={}, type={}, requesterId={}",
            entity.getChangeRequestId(), entity.getCapabilityCode(), entity.getChangeType(), actorId);
        return entity.getChangeRequestId();
    }

    @Override
    public PageResult<AigChangeRequestVo> queryPage(AigChangeRequestBo bo, PageQuery pageQuery) {
        AigChangeRequestBo query = bo == null ? new AigChangeRequestBo() : bo;
        LambdaQueryWrapper<AigChangeRequest> wrapper = new LambdaQueryWrapper<AigChangeRequest>()
            .eq(StringUtils.isNotBlank(query.getCapabilityCode()), AigChangeRequest::getCapabilityCode,
                query.getCapabilityCode())
            .eq(query.getRequesterId() != null, AigChangeRequest::getRequesterId, query.getRequesterId())
            .eq(StringUtils.isNotBlank(query.getFilterStatus()), AigChangeRequest::getStatus,
                query.getFilterStatus())
            .orderByDesc(AigChangeRequest::getCreateTime)
            .orderByDesc(AigChangeRequest::getChangeRequestId);
        Page<AigChangeRequestVo> voPage = changeRequestMapper.selectVoPage(pageQuery.build(), wrapper);
        for (AigChangeRequestVo row : voPage.getRecords()) {
            fillLabels(row);
        }
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public AigChangeRequestVo approve(Long changeRequestId, String remark, Long approverId, String approverName) {
        // ⚠️ **刻意不加 @Transactional**：冲突/失败都要留下那一行（留痕事实），
        //    而抛异常会把"写入 CONFLICTED"一起回滚——页面上就什么都没有了。
        //    （同类教训：知识索引的 FAILED 记录也曾被外层事务回滚掉。）
        AigChangeRequest row = requirePending(changeRequestId);
        if (approverId == null) {
            throw new ServiceException("批准需要明确的审批人");
        }
        if (approverId.equals(row.getRequesterId())) {
            // 与调用授权审批同规矩：申请人不得自批（服务端强制，不靠页面藏按钮）
            throw new ServiceException("申请人不得批准自己提交的变更单（需另一位持有「能力高风险变更」权限的同事批准）");
        }
        AigChangeTypeEnum type = AigChangeTypeEnum.find(row.getChangeType());
        if (type == null) {
            throw new ServiceException("变更单的类型无法识别：" + row.getChangeType());
        }
        // ① 乐观并发校验：现场必须仍等于申请时那一份（这一步在任何写之前）
        String conflict = detectConflict(row, type);
        if (conflict != null) {
            markFinished(row, AigChangeRequestStatusEnum.CONFLICTED, approverId, approverName, remark, conflict);
            log.warn("变更单批准时与现场冲突, changeRequestId={}, 原因={}", changeRequestId, conflict);
            return toVo(changeRequestMapper.selectById(changeRequestId));
        }
        // ② 执行（走既有服务方法 ⇒ Q1C 闸门仍生效）
        try {
            applyChange(row, type);
        } catch (Exception e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            markFinished(row, AigChangeRequestStatusEnum.CONFLICTED, approverId, approverName, remark,
                "执行失败：" + why);
            log.warn("变更单执行失败, changeRequestId={}, 原因={}", changeRequestId, why, e);
            return toVo(changeRequestMapper.selectById(changeRequestId));
        }
        // ③ 落生效记录
        row.setApproverId(approverId);
        row.setApproverName(approverName);
        row.setDecisionRemark(remark);
        row.setDecidedAt(LocalDateTime.now());
        row.setAppliedAt(LocalDateTime.now());
        markFinished(row, AigChangeRequestStatusEnum.APPLIED, approverId, approverName, remark, null);
        log.info("高风险变更单已批准并生效, changeRequestId={}, type={}, approverId={}",
            changeRequestId, row.getChangeType(), approverId);
        return toVo(changeRequestMapper.selectById(changeRequestId));
    }

    @Override
    public void reject(Long changeRequestId, String remark, Long approverId, String approverName) {
        if (StringUtils.isBlank(remark)) {
            throw new ServiceException("驳回必须写明理由：申请人需要知道该怎么改");
        }
        AigChangeRequest row = requirePending(changeRequestId);
        if (approverId == null) {
            throw new ServiceException("驳回需要明确的审批人");
        }
        markFinished(row, AigChangeRequestStatusEnum.REJECTED, approverId, approverName, remark, null);
    }

    @Override
    public void cancel(Long changeRequestId, Long actorId) {
        AigChangeRequest row = requirePending(changeRequestId);
        if (actorId == null || !actorId.equals(row.getRequesterId())) {
            throw new ServiceException("只有申请人本人可以撤回自己的变更单");
        }
        row.setStatus(AigChangeRequestStatusEnum.CANCELLED.getCode());
        row.setDecidedAt(LocalDateTime.now());
        changeRequestMapper.updateById(row);
    }

    /**
     * 载入变更单并校验"仍在待批准"。
     *
     * @param changeRequestId 变更单ID
     * @return 变更单
     */
    private AigChangeRequest requirePending(Long changeRequestId) {
        if (changeRequestId == null) {
            throw new ServiceException("变更单ID不能为空");
        }
        AigChangeRequest row = changeRequestMapper.selectById(changeRequestId);
        if (row == null) {
            throw new ServiceException("变更单不存在或已删除：" + changeRequestId);
        }
        if (!AigChangeRequestStatusEnum.PENDING.getCode().equals(row.getStatus())) {
            throw new ServiceException("这张变更单已经处理过（当前状态=" + row.getStatus() + "），不能重复操作");
        }
        return row;
    }

    /**
     * 乐观并发校验：现场与申请时是否一致。
     *
     * @param row  变更单
     * @param type 变更类型
     * @return 冲突说明；无冲突返回 null
     */
    private String detectConflict(AigChangeRequest row, AigChangeTypeEnum type) {
        if (type == AigChangeTypeEnum.POLICY_CREATE) {
            Map<String, Object> after = fromJson(row.getAfterJson());
            AigRoutePolicy exist = findPolicy(row.getCapabilityCode(), str(after.get(K_DATA_LEVEL)));
            return exist == null ? null
                : "该能力在 " + str(after.get(K_DATA_LEVEL)) + " 等级下已经有策略行（policyId="
                    + exist.getPolicyId() + "），申请时「该格没有策略」已不成立";
        }
        AigRoutePolicy current = routePolicyMapper.selectById(row.getTargetRef());
        if (current == null) {
            return "目标策略行已被删除（policyId=" + row.getTargetRef() + "）";
        }
        Map<String, Object> before = fromJson(row.getBeforeJson());
        Map<String, Object> now = snapshotOf(current);
        for (Map.Entry<String, Object> entry : before.entrySet()) {
            Object nowValue = now.get(entry.getKey());
            if (!String.valueOf(entry.getValue()).equals(String.valueOf(nowValue))) {
                return "策略行已被他人改动（" + entry.getKey() + "：" + entry.getValue() + " → " + nowValue + "）";
            }
        }
        return null;
    }

    /**
     * 执行变更（复用既有服务方法）。
     *
     * @param row  变更单
     * @param type 变更类型
     */
    private void applyChange(AigChangeRequest row, AigChangeTypeEnum type) {
        if (type == AigChangeTypeEnum.POLICY_DELETE) {
            routePolicyService.remove(row.getTargetRef());
            return;
        }
        Map<String, Object> after = fromJson(row.getAfterJson());
        AigRoutePolicyBo bo = new AigRoutePolicyBo();
        bo.setCapabilityCode(row.getCapabilityCode());
        bo.setDataLevel(str(after.get(K_DATA_LEVEL)));
        bo.setAllowExternal(str(after.get(K_ALLOW_EXTERNAL)));
        bo.setPreferredDeployment(str(after.get(K_PREFERRED)));
        bo.setRequireApproval(str(after.get(K_REQUIRE_APPROVAL)));
        bo.setFallbackToManual(str(after.get(K_FALLBACK)));
        bo.setStatus(str(after.get(K_STATUS)));
        if (type == AigChangeTypeEnum.POLICY_CREATE) {
            routePolicyService.create(bo);
        } else {
            bo.setPolicyId(row.getTargetRef());
            routePolicyService.update(bo);
        }
    }

    /**
     * 取策略行（能力 × 数据等级）。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      数据等级
     * @return 策略行；不存在返回 null
     */
    private AigRoutePolicy findPolicy(String capabilityCode, String dataLevel) {
        if (StringUtils.isBlank(capabilityCode) || StringUtils.isBlank(dataLevel)) {
            return null;
        }
        return routePolicyMapper.selectOne(new LambdaQueryWrapper<AigRoutePolicy>()
            .eq(AigRoutePolicy::getCapabilityCode, capabilityCode)
            .eq(AigRoutePolicy::getDataLevel, dataLevel)
            .last("limit 1"));
    }

    /**
     * 目标值（未给的列沿用当前行——PATCH 语义，避免"只改一个开关却把别的列清空"）。
     *
     * @param bo      入参
     * @param current 当前行（新增时为 null）
     * @return 目标值快照
     */
    private Map<String, Object> targetOf(AigChangeRequestBo bo, AigRoutePolicy current) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(K_DATA_LEVEL, choose(bo.getDataLevel(), current == null ? null : current.getDataLevel()));
        map.put(K_ALLOW_EXTERNAL,
            choose(bo.getAllowExternal(), current == null ? null : current.getAllowExternal()));
        map.put(K_PREFERRED,
            choose(bo.getPreferredDeployment(), current == null ? null : current.getPreferredDeployment()));
        map.put(K_REQUIRE_APPROVAL,
            choose(bo.getRequireApproval(), current == null ? null : current.getRequireApproval()));
        map.put(K_FALLBACK,
            choose(bo.getFallbackToManual(), current == null ? null : current.getFallbackToManual()));
        map.put(K_STATUS, choose(bo.getStatus(), current == null ? "0" : current.getStatus()));
        return map;
    }

    /**
     * 当前行快照。
     *
     * @param row 策略行
     * @return 快照
     */
    private Map<String, Object> snapshotOf(AigRoutePolicy row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(K_DATA_LEVEL, row.getDataLevel());
        map.put(K_ALLOW_EXTERNAL, row.getAllowExternal());
        map.put(K_PREFERRED, row.getPreferredDeployment());
        map.put(K_REQUIRE_APPROVAL, row.getRequireApproval());
        map.put(K_FALLBACK, row.getFallbackToManual());
        map.put(K_STATUS, row.getStatus());
        return map;
    }

    /**
     * 收口状态与审批信息（CONFLICTED / REJECTED 都走这里，避免两处写法不一致）。
     *
     * @param row          变更单
     * @param status       目标状态
     * @param approverId   审批人
     * @param approverName 审批人账号
     * @param remark       意见
     * @param resultRemark 执行结果说明（可空）
     */
    private void markFinished(AigChangeRequest row, AigChangeRequestStatusEnum status, Long approverId,
                              String approverName, String remark, String resultRemark) {
        row.setStatus(status.getCode());
        row.setApproverId(approverId);
        row.setApproverName(approverName);
        row.setDecisionRemark(remark);
        row.setDecidedAt(LocalDateTime.now());
        row.setResultRemark(resultRemark);
        changeRequestMapper.updateById(row);
    }

    /**
     * 实体 → 视图（填中文标签）。
     *
     * @param entity 实体
     * @return 视图
     */
    private AigChangeRequestVo toVo(AigChangeRequest entity) {
        AigChangeRequestVo vo = new AigChangeRequestVo();
        if (entity == null) {
            return vo;
        }
        vo.setChangeRequestId(entity.getChangeRequestId());
        vo.setCapabilityCode(entity.getCapabilityCode());
        vo.setChangeType(entity.getChangeType());
        vo.setTargetRef(entity.getTargetRef());
        vo.setBeforeJson(entity.getBeforeJson());
        vo.setAfterJson(entity.getAfterJson());
        vo.setReason(entity.getReason());
        vo.setStatus(entity.getStatus());
        vo.setRequesterId(entity.getRequesterId());
        vo.setRequesterName(entity.getRequesterName());
        vo.setApproverId(entity.getApproverId());
        vo.setApproverName(entity.getApproverName());
        vo.setDecidedAt(entity.getDecidedAt());
        vo.setDecisionRemark(entity.getDecisionRemark());
        vo.setAppliedAt(entity.getAppliedAt());
        vo.setResultRemark(entity.getResultRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        fillLabels(vo);
        return vo;
    }

    /**
     * 填中文标签（前端不再各写一份映射）。
     *
     * @param row 视图
     */
    private void fillLabels(AigChangeRequestVo row) {
        AigChangeRequestStatusEnum status = AigChangeRequestStatusEnum.find(row.getStatus());
        row.setStatusLabel(status == null ? row.getStatus() : status.getDesc());
        AigChangeTypeEnum type = AigChangeTypeEnum.find(row.getChangeType());
        row.setChangeTypeLabel(type == null ? row.getChangeType() : type.getDesc());
    }

    private static String choose(String candidate, String fallback) {
        return StringUtils.isBlank(candidate) ? fallback : candidate.trim();
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return jsonMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new ServiceException("变更单快照序列化失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = jsonMapper.readValue(json, Map.class);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            throw new ServiceException("变更单快照无法解析：" + e.getMessage());
        }
    }
}
