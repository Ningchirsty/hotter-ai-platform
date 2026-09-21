package org.dromara.hrtalent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolMemberBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolQueryBo;
import org.dromara.hrtalent.domain.entity.TalentPool;
import org.dromara.hrtalent.domain.entity.TalentPoolMember;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolMemberVo;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentPoolMemberStatusEnum;
import org.dromara.hrtalent.enums.TalentVisibilityTypeEnum;
import org.dromara.hrtalent.mapper.TalentPoolMapper;
import org.dromara.hrtalent.mapper.TalentPoolMemberMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.service.talent.ITalentPoolService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.dromara.system.api.model.LoginUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 人才池服务实现（SPEC-P4 §2.3 C 线，设计文档 §8.15、§11.1）。
 *
 * <p><b>实现要点</b>：</p>
 * <ul>
 *     <li><b>不做自写授权</b>：池的可见性与可维护性全部由
 *     {@link TalentScopeDomainService} 的 {@code visible(...)} / 角色判定产出，
 *     本类只调用不复制规则；成员的每个 {@code talentId} 还要经过
 *     {@link ITalentProfileService#requireVisible(Long)} 的资源级鉴权；</li>
 *     <li><b>幂等加入</b>：{@code (pool_id, talent_id)} 已存在时返回已有关系；
 *     若历史关系已「移出」，则恢复为「在池」并刷新加入人/加入时间，
 *     并发撞唯一键时重新读取已有关系返回，不向调用方抛异常；</li>
 *     <li><b>移出只结束关系</b>：只把 {@code member_status} 置为 {@code removed} 并记录
 *     移出时间与原因，<b>不删除</b>人才主档、成员关系行与任何简历/经历/标签数据；</li>
 *     <li><b>成员数冗余列</b>：{@code member_count} 由服务层按「在池」关系重算，
 *     保证与成员表一致；</li>
 *     <li><b>可见范围叠加</b>：成员列表先取可见人才ID集合，再以 {@code IN} 过滤，
 *     不可见的人才不会泄漏到池成员列表中（设计文档 §8.17、§11.1）。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentPoolServiceImpl implements ITalentPoolService {

    /**
     * 人才池不存在时的统一提示。
     */
    private static final String POOL_NOT_FOUND = "人才池不存在";

    /**
     * 无权查看该人才池时的统一提示。
     */
    private static final String POOL_DENY_VIEW = "无权查看该人才池";

    /**
     * 无权维护该人才池时的统一提示。
     */
    private static final String POOL_DENY_WRITE = "仅人才池管理员或集团级人才管理员可维护该人才池";

    /**
     * 人才池状态：启用。
     */
    private static final String STATUS_ACTIVE = "active";

    /**
     * 人才池状态：归档。
     */
    private static final String STATUS_ARCHIVED = "archived";

    /**
     * 人才池 Mapper。
     */
    private final TalentPoolMapper talentPoolMapper;

    /**
     * 人才池成员 Mapper。
     */
    private final TalentPoolMemberMapper talentPoolMemberMapper;

    /**
     * 人才主档 Mapper（只读：解析可见人才ID集合与回填成员姓名）。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 人才主档服务（只用于资源级鉴权，不重复写人才数据）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 人才可见范围领域服务（唯一授权入口）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 业务编号生成器（池编码唯一事实来源）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    @Override
    public PageResult<TalentPoolVo> queryPage(TalentPoolQueryBo bo, PageQuery pageQuery) {
        TalentScopeDomainService.ScopeCondition scope = talentScopeDomainService.currentScope();
        TalentPoolQueryBo query = bo == null ? new TalentPoolQueryBo() : bo;
        LambdaQueryWrapper<TalentPool> wrapper = new LambdaQueryWrapper<TalentPool>()
            .like(StringUtils.isNotBlank(query.getPoolName()), TalentPool::getPoolName, query.getPoolName())
            .like(StringUtils.isNotBlank(query.getPoolCode()), TalentPool::getPoolCode, query.getPoolCode())
            .eq(StringUtils.isNotBlank(query.getPoolType()), TalentPool::getPoolType, query.getPoolType())
            .eq(query.getManagerId() != null, TalentPool::getManagerId, query.getManagerId())
            .eq(query.getOwnerDeptId() != null, TalentPool::getOwnerDeptId, query.getOwnerDeptId())
            .eq(StringUtils.isNotBlank(query.getVisibilityType()), TalentPool::getVisibilityType, query.getVisibilityType())
            .eq(StringUtils.isNotBlank(query.getStatus()), TalentPool::getStatus, query.getStatus())
            .orderByDesc(TalentPool::getCreateTime);
        List<TalentPool> pools = talentPoolMapper.selectList(wrapper);
        if (CollUtil.isEmpty(pools)) {
            return PageResult.build(List.of(), 0L);
        }
        // 可见性判定复用 TalentScopeDomainService 的权威分支，不在本类重写规则（§11.1）
        List<TalentPool> visible = new ArrayList<>();
        for (TalentPool pool : pools) {
            if (talentScopeDomainService.visible(toScopeTarget(pool), scope)) {
                visible.add(pool);
            }
        }
        if (visible.isEmpty()) {
            return PageResult.build(List.of(), 0L);
        }
        // 池目录规模有限：按可见性与业务条件过滤后再分页，保证分页总数与可见集合一致
        PageResult<TalentPool> sliced = slice(visible, pageQuery);
        List<TalentPoolVo> rows = new ArrayList<>(sliced.getRows().size());
        for (TalentPool pool : sliced.getRows()) {
            TalentPoolVo vo = MapstructUtils.convert(pool, TalentPoolVo.class);
            if (vo != null) {
                rows.add(vo);
            }
        }
        return PageResult.build(rows, sliced.getTotal());
    }

    @Override
    public TalentPoolVo getDetail(Long poolId) {
        TalentPool pool = requirePool(poolId);
        checkPoolVisible(pool, talentScopeDomainService.currentScope());
        return MapstructUtils.convert(pool, TalentPoolVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TalentPoolBo bo) {
        if (bo == null) {
            throw new ServiceException("人才池入参不能为空");
        }
        validateVisibility(bo.getVisibilityType());
        TalentPool entity = MapstructUtils.convert(bo, TalentPool.class);
        if (entity == null) {
            entity = new TalentPool();
        }
        // 服务端权威字段：主键、编码、成员数、状态默认值均不接受前端写入
        entity.setPoolId(null);
        entity.setPoolCode(businessNoGenerator.nextPoolNo());
        entity.setMemberCount(0);
        entity.setStatus(StringUtils.isBlank(bo.getStatus()) ? STATUS_ACTIVE : bo.getStatus());
        entity.setVisibilityType(StringUtils.isBlank(bo.getVisibilityType())
            ? TalentVisibilityTypeEnum.OWNER.getCode() : bo.getVisibilityType());
        fillOwnerIfAbsent(entity);
        try {
            talentPoolMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.warn("人才池编码生成冲突, poolCode={}", entity.getPoolCode());
            throw new ServiceException("人才池编码生成冲突，请重试");
        }
        log.info("新增人才池, poolId={}, poolCode={}, managerId={}",
            entity.getPoolId(), entity.getPoolCode(), entity.getManagerId());
        return entity.getPoolId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TalentPoolBo bo) {
        if (bo == null || bo.getPoolId() == null) {
            throw new ServiceException("人才池ID不能为空");
        }
        TalentPool exist = requirePool(bo.getPoolId());
        checkPoolWritable(exist, talentScopeDomainService.currentScope());
        validateVisibility(bo.getVisibilityType());
        TalentPool update = MapstructUtils.convert(bo, TalentPool.class);
        if (update == null) {
            update = new TalentPool();
        }
        update.setPoolId(exist.getPoolId());
        // 成员数、编码由服务端维护，不允许经更新接口改写
        update.setMemberCount(null);
        update.setPoolCode(null);
        if (talentPoolMapper.updateById(update) == 0) {
            throw new ServiceException(POOL_NOT_FOUND);
        }
        log.info("更新人才池, poolId={}", exist.getPoolId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] poolIds) {
        if (poolIds == null || poolIds.length == 0) {
            return;
        }
        TalentScopeDomainService.ScopeCondition scope = talentScopeDomainService.currentScope();
        for (Long poolId : new LinkedHashSet<>(Arrays.asList(poolIds))) {
            if (poolId == null) {
                continue;
            }
            TalentPool pool = requirePool(poolId);
            checkPoolWritable(pool, scope);
            // 删除池只结束运营范围：把在池关系标记为已移出，绝不触碰人才主档
            talentPoolMemberMapper.update(null, new LambdaUpdateWrapper<TalentPoolMember>()
                .eq(TalentPoolMember::getPoolId, poolId)
                .eq(TalentPoolMember::getMemberStatus, TalentPoolMemberStatusEnum.ACTIVE.getCode())
                .set(TalentPoolMember::getMemberStatus, TalentPoolMemberStatusEnum.REMOVED.getCode())
                .set(TalentPoolMember::getRemovedTime, LocalDateTime.now())
                .set(TalentPoolMember::getRemovedReason, "人才池已删除，成员关系自动结束"));
            talentPoolMapper.deleteById(poolId);
            log.info("逻辑删除人才池, poolId={}", poolId);
        }
    }

    @Override
    public PageResult<TalentPoolMemberVo> queryMembers(Long poolId, String memberStatus, PageQuery pageQuery) {
        TalentPool pool = requirePool(poolId);
        checkPoolVisible(pool, talentScopeDomainService.currentScope());
        // 硬约束：成员列表必须叠加人才可见范围，不可见的人才不返回（§8.17、§11.1）
        List<Long> visibleTalentIds = resolveVisibleTalentIds();
        if (visibleTalentIds.isEmpty()) {
            return PageResult.build(List.of(), 0L);
        }
        LambdaQueryWrapper<TalentPoolMember> wrapper = new LambdaQueryWrapper<TalentPoolMember>()
            .eq(TalentPoolMember::getPoolId, poolId)
            .in(TalentPoolMember::getTalentId, visibleTalentIds)
            .eq(StringUtils.isNotBlank(memberStatus), TalentPoolMember::getMemberStatus, memberStatus)
            .orderByDesc(TalentPoolMember::getJoinedTime);
        PageQuery pq = pageQuery == null ? new PageQuery() : pageQuery;
        Page<TalentPoolMemberVo> page = talentPoolMemberMapper.selectVoPage(pq.build(), wrapper);
        return PageResult.build(fillTalentInfo(page.getRecords()), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TalentPoolMemberVo addMember(Long poolId, TalentPoolMemberBo bo) {
        if (bo == null || bo.getTalentId() == null) {
            throw new ServiceException("人才ID不能为空");
        }
        TalentPool pool = requirePool(poolId);
        checkPoolWritable(pool, talentScopeDomainService.currentScope());
        requirePoolEnabled(pool);
        // 资源级鉴权：人才必须对当前用户可见（唯一权威判定在 TalentScopeDomainService）
        talentProfileService.requireVisible(bo.getTalentId());
        String targetStatus = resolveMemberStatus(bo.getMemberStatus());
        TalentPoolMember exist = selectMember(poolId, bo.getTalentId());
        if (exist != null) {
            // 幂等：重复加入直接返回已有关系；已移出的关系恢复为在池
            if (TalentPoolMemberStatusEnum.REMOVED.getCode().equals(exist.getMemberStatus())) {
                reactivateMember(exist, bo, targetStatus);
                exist = talentPoolMemberMapper.selectById(exist.getMemberId());
            }
            log.info("人才重复加入人才池，返回已有关系, poolId={}, talentId={}, memberId={}",
                poolId, bo.getTalentId(), exist == null ? null : exist.getMemberId());
            refreshMemberCount(poolId);
            return toMemberVo(exist);
        }
        // 手工装配成员实体：主键、加入人与加入时间均为服务端权威字段，不接受前端写入
        TalentPoolMember entity = new TalentPoolMember();
        entity.setPoolId(poolId);
        entity.setTalentId(bo.getTalentId());
        entity.setFitLevel(bo.getFitLevel());
        entity.setRecommendedJob(bo.getRecommendedJob());
        entity.setJoinReason(bo.getJoinReason());
        entity.setNextContactTime(bo.getNextContactTime());
        entity.setMemberStatus(targetStatus);
        entity.setRemark(bo.getRemark());
        entity.setJoinedBy(currentUserId());
        entity.setJoinedTime(LocalDateTime.now());
        entity.setRemovedTime(null);
        entity.setRemovedReason(null);
        try {
            talentPoolMemberMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发下的幂等兜底：唯一索引 uk_hr_talent_pool_member 命中时返回已有关系
            TalentPoolMember concurrent = selectMember(poolId, bo.getTalentId());
            if (concurrent == null) {
                throw new ServiceException("该人才已在人才池中，请刷新后重试");
            }
            log.info("人才并发加入人才池，返回已有关系, poolId={}, talentId={}", poolId, bo.getTalentId());
            return toMemberVo(concurrent);
        }
        refreshMemberCount(poolId);
        log.info("人才加入人才池, poolId={}, talentId={}, memberId={}",
            poolId, bo.getTalentId(), entity.getMemberId());
        return toMemberVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long poolId, Long memberId, String reason) {
        TalentPool pool = requirePool(poolId);
        checkPoolWritable(pool, talentScopeDomainService.currentScope());
        if (memberId == null) {
            throw new ServiceException("成员关系ID不能为空");
        }
        TalentPoolMember member = talentPoolMemberMapper.selectById(memberId);
        if (member == null || !Objects.equals(poolId, member.getPoolId())) {
            throw new ServiceException("人才池成员关系不存在");
        }
        if (TalentPoolMemberStatusEnum.REMOVED.getCode().equals(member.getMemberStatus())) {
            throw new ServiceException("该成员已移出，无需重复操作");
        }
        // 只结束成员关系：更新状态与移出信息，不删除成员行，更不删除人才主档（§8.15）
        talentPoolMemberMapper.update(null, new LambdaUpdateWrapper<TalentPoolMember>()
            .eq(TalentPoolMember::getMemberId, memberId)
            .set(TalentPoolMember::getMemberStatus, TalentPoolMemberStatusEnum.REMOVED.getCode())
            .set(TalentPoolMember::getRemovedTime, LocalDateTime.now())
            .set(TalentPoolMember::getRemovedReason, reason));
        refreshMemberCount(poolId);
        log.info("移出人才池成员（仅结束关系，不删除人才主档）, poolId={}, memberId={}, talentId={}",
            poolId, memberId, member.getTalentId());
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 读取人才池并要求其存在。
     *
     * @param poolId 人才池ID
     * @return 人才池实体
     */
    private TalentPool requirePool(Long poolId) {
        if (poolId == null) {
            throw new ServiceException("人才池ID不能为空");
        }
        TalentPool pool = talentPoolMapper.selectById(poolId);
        if (pool == null) {
            throw new ServiceException(POOL_NOT_FOUND);
        }
        return pool;
    }

    /**
     * 校验人才池对当前用户可见（复用领域服务的权威判定）。
     *
     * @param pool  人才池实体
     * @param scope 当前可见范围条件
     */
    private void checkPoolVisible(TalentPool pool, TalentScopeDomainService.ScopeCondition scope) {
        if (!talentScopeDomainService.visible(toScopeTarget(pool), scope)) {
            log.warn("人才池可见性校验未通过, poolId={}, userId={}",
                pool.getPoolId(), scope == null ? null : scope.currentUserId());
            throw new ServiceException(POOL_DENY_VIEW);
        }
    }

    /**
     * 校验当前用户可维护该人才池：超级管理员、集团级人才管理员或该池管理员。
     *
     * <p>角色判定来自 {@link TalentScopeDomainService}，本类不自行解析角色标识。</p>
     *
     * @param pool  人才池实体
     * @param scope 当前可见范围条件
     */
    private void checkPoolWritable(TalentPool pool, TalentScopeDomainService.ScopeCondition scope) {
        if (scope != null && (scope.unlimited() || scope.groupLevelAdmin())) {
            return;
        }
        Long currentUserId = scope == null ? null : scope.currentUserId();
        if (currentUserId != null && currentUserId.equals(pool.getManagerId())) {
            return;
        }
        throw new ServiceException(POOL_DENY_WRITE);
    }

    /**
     * 校验人才池未归档。
     *
     * @param pool 人才池实体
     */
    private void requirePoolEnabled(TalentPool pool) {
        if (STATUS_ARCHIVED.equals(pool.getStatus())) {
            throw new ServiceException("人才池已归档，不能继续维护成员");
        }
    }

    /**
     * 把人才池映射为可见范围判定所需的最小快照。
     *
     * <p><b>注意</b>：{@code talentId} 固定传 {@code null}——
     * 池主键与人才主键是不同的 ID 空间，传入会导致
     * {@code explicit} 分支误查人才授权表（跨域越权），因此显式置空，
     * 使「显式授权」池对普通用户按 fail-safe 拒绝处理。</p>
     *
     * @param pool 人才池实体
     * @return 可见性快照
     */
    private TalentScopeDomainService.TalentScopeTarget toScopeTarget(TalentPool pool) {
        return new TalentScopeDomainService.TalentScopeTarget(
            null,
            pool.getManagerId(),
            pool.getOwnerDeptId(),
            pool.getVisibilityType(),
            null,
            pool.getDelFlag());
    }

    /**
     * 校验可见范围编码合法性。
     *
     * @param visibilityType 可见范围编码
     */
    private void validateVisibility(String visibilityType) {
        if (StringUtils.isNotBlank(visibilityType) && TalentVisibilityTypeEnum.find(visibilityType) == null) {
            throw new ServiceException("可见范围不合法，请使用字典 talent_visibility_type 的编码");
        }
    }

    /**
     * 池管理员未指定时回落为当前登录用户，归属部门回落为登录用户部门。
     *
     * @param entity 人才池实体
     */
    private void fillOwnerIfAbsent(TalentPool entity) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null) {
            return;
        }
        if (entity.getManagerId() == null) {
            entity.setManagerId(loginUser.getUserId());
        }
        if (entity.getOwnerDeptId() == null) {
            entity.setOwnerDeptId(loginUser.getDeptId());
        }
    }

    /**
     * 解析成员状态：未指定默认「在池」，未知编码拒绝（fail-safe）。
     *
     * @param memberStatus 成员状态编码
     * @return 合法成员状态编码
     */
    private String resolveMemberStatus(String memberStatus) {
        if (StringUtils.isBlank(memberStatus)) {
            return TalentPoolMemberStatusEnum.ACTIVE.getCode();
        }
        TalentPoolMemberStatusEnum target = TalentPoolMemberStatusEnum.find(memberStatus);
        if (target == null) {
            throw new ServiceException("未知的人才池成员状态：" + memberStatus);
        }
        return target.getCode();
    }

    /**
     * 按 {@code pool_id + talent_id} 读取成员关系（逻辑删除行自动过滤）。
     *
     * @param poolId   人才池ID
     * @param talentId 人才主档ID
     * @return 成员关系，不存在返回 null
     */
    private TalentPoolMember selectMember(Long poolId, Long talentId) {
        return talentPoolMemberMapper.selectOne(new LambdaQueryWrapper<TalentPoolMember>()
            .eq(TalentPoolMember::getPoolId, poolId)
            .eq(TalentPoolMember::getTalentId, talentId));
    }

    /**
     * 恢复已移出的成员关系为「在池」，并刷新加入信息。
     *
     * @param exist        已存在的成员关系
     * @param bo           成员入参
     * @param targetStatus 目标成员状态
     */
    private void reactivateMember(TalentPoolMember exist, TalentPoolMemberBo bo, String targetStatus) {
        talentPoolMemberMapper.update(null, new LambdaUpdateWrapper<TalentPoolMember>()
            .eq(TalentPoolMember::getMemberId, exist.getMemberId())
            .set(TalentPoolMember::getMemberStatus, targetStatus)
            .set(TalentPoolMember::getJoinedBy, currentUserId())
            .set(TalentPoolMember::getJoinedTime, LocalDateTime.now())
            .set(TalentPoolMember::getFitLevel, bo.getFitLevel())
            .set(TalentPoolMember::getRecommendedJob, bo.getRecommendedJob())
            .set(TalentPoolMember::getJoinReason, bo.getJoinReason())
            .set(TalentPoolMember::getNextContactTime, bo.getNextContactTime())
            .set(TalentPoolMember::getRemovedTime, null)
            .set(TalentPoolMember::getRemovedReason, null));
    }

    /**
     * 重算人才池成员数冗余列（口径：成员状态为「在池」的关系数）。
     *
     * @param poolId 人才池ID
     */
    private void refreshMemberCount(Long poolId) {
        long count = talentPoolMemberMapper.selectCount(new LambdaQueryWrapper<TalentPoolMember>()
            .eq(TalentPoolMember::getPoolId, poolId)
            .eq(TalentPoolMember::getMemberStatus, TalentPoolMemberStatusEnum.ACTIVE.getCode()));
        talentPoolMapper.update(null, new LambdaUpdateWrapper<TalentPool>()
            .eq(TalentPool::getPoolId, poolId)
            .set(TalentPool::getMemberCount, (int) count));
    }

    /**
     * 解析当前登录用户可见的人才ID集合（唯一接法见 {@code TalentProfileServiceImpl}）。
     *
     * @return 可见人才ID列表（不为 null，可能为空）
     */
    private List<Long> resolveVisibleTalentIds() {
        List<Long> ids = talentProfileMapper.selectVisibleTalentIds(
            talentScopeDomainService.visibleTalentWrapper());
        return ids == null ? List.of() : ids;
    }

    /**
     * 成员实体转 VO 并回填人才姓名与编号（人才必须对当前用户可见）。
     *
     * @param member 成员关系
     * @return 成员视图对象，入参为空时返回 null
     */
    private TalentPoolMemberVo toMemberVo(TalentPoolMember member) {
        if (member == null) {
            return null;
        }
        // 手工赋值而非 MapStruct 复制：只暴露视图需要的字段，避免把内部字段顺手带出
        TalentPoolMemberVo vo = new TalentPoolMemberVo();
        vo.setMemberId(member.getMemberId());
        vo.setPoolId(member.getPoolId());
        vo.setTalentId(member.getTalentId());
        vo.setFitLevel(member.getFitLevel());
        vo.setRecommendedJob(member.getRecommendedJob());
        vo.setJoinReason(member.getJoinReason());
        vo.setNextContactTime(member.getNextContactTime());
        vo.setMemberStatus(member.getMemberStatus());
        vo.setJoinedBy(member.getJoinedBy());
        vo.setJoinedTime(member.getJoinedTime());
        vo.setRemovedTime(member.getRemovedTime());
        vo.setRemovedReason(member.getRemovedReason());
        vo.setRemark(member.getRemark());
        vo.setCreateTime(member.getCreateTime());
        vo.setUpdateTime(member.getUpdateTime());
        TalentProfile profile = talentProfileMapper.selectById(member.getTalentId());
        if (profile != null) {
            vo.setTalentName(profile.getName());
            vo.setTalentNo(profile.getTalentNo());
        }
        return vo;
    }

    /**
     * 批量回填成员的人才姓名/编号。
     *
     * @param rows 成员视图列表
     * @return 回填后的列表
     */
    private List<TalentPoolMemberVo> fillTalentInfo(List<TalentPoolMemberVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }
        List<Long> talentIds = rows.stream()
            .map(TalentPoolMemberVo::getTalentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (talentIds.isEmpty()) {
            return rows;
        }
        Map<Long, TalentProfile> profiles = new HashMap<>(talentIds.size());
        for (TalentProfile profile : talentProfileMapper.selectByIds(talentIds)) {
            profiles.put(profile.getTalentId(), profile);
        }
        for (TalentPoolMemberVo vo : rows) {
            TalentProfile profile = profiles.get(vo.getTalentId());
            if (profile != null) {
                vo.setTalentName(profile.getName());
                vo.setTalentNo(profile.getTalentNo());
            }
        }
        return rows;
    }

    /**
     * 取当前登录用户ID；无登录态返回 null。
     *
     * @return 用户ID或 null
     */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 内存分页：调用方已按可见范围与业务条件过滤，这里只做切片，保证 total 与可见集合一致。
     *
     * @param all       已过滤的全量列表
     * @param pageQuery 分页参数，可为空（为空表示返回全部）
     * @param <T>       元素类型
     * @return 分页结果
     */
    private static <T> PageResult<T> slice(List<T> all, PageQuery pageQuery) {
        long total = all.size();
        if (pageQuery == null || pageQuery.getPageSize() == null || pageQuery.getPageSize() <= 0) {
            return PageResult.build(all, total);
        }
        int size = pageQuery.getPageSize();
        int num = pageQuery.getPageNum() == null || pageQuery.getPageNum() <= 0 ? 1 : pageQuery.getPageNum();
        long from = (long) (num - 1) * size;
        if (from >= total) {
            return PageResult.build(List.of(), total);
        }
        int to = (int) Math.min(total, from + size);
        return PageResult.build(new ArrayList<>(all.subList((int) from, to)), total);
    }

}
