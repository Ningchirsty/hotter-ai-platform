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
import org.dromara.hrtalent.domain.bo.talent.TalentGroupBo;
import org.dromara.hrtalent.domain.bo.talent.TalentGroupQueryBo;
import org.dromara.hrtalent.domain.entity.TalentGroup;
import org.dromara.hrtalent.domain.entity.TalentGroupMember;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupMemberVo;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentVisibilityTypeEnum;
import org.dromara.hrtalent.mapper.TalentGroupMapper;
import org.dromara.hrtalent.mapper.TalentGroupMemberMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.service.talent.ITalentGroupService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.dromara.system.api.model.LoginUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 人才分组服务实现（SPEC-P4 §2.3 C 线，设计文档 §8.15、§11.1）。
 *
 * <p><b>两类分组</b>：</p>
 * <ul>
 *     <li>{@code public} 公共分组：团队共同整理，由人才池管理员（集团级人才管理员）或分组负责人维护，
 *     可见范围经 {@link TalentScopeDomainService#visible} 判定；</li>
 *     <li>{@code personal} 个人收藏：仅本人可见可维护，<b>不改变人才数据权限</b>——
 *     收藏别人看不到的人才不会让收藏者获得查看权，成员列表仍叠加人才可见范围。</li>
 * </ul>
 *
 * <p><b>幂等</b>：{@code (group_id, talent_id)} 唯一，重复加入返回已有关系；
 * 逻辑删除行仍占用唯一索引，因此命中唯一键时先恢复历史行（{@code restoreMember}）再返回。</p>
 *
 * <p><b>移出只结束关系</b>：不删除人才主档与任何简历、经历、标签数据。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentGroupServiceImpl implements ITalentGroupService {

    /**
     * 分组不存在时的统一提示。
     */
    private static final String GROUP_NOT_FOUND = "人才分组不存在";

    /**
     * 无权查看该分组时的统一提示。
     */
    private static final String GROUP_DENY_VIEW = "无权查看该人才分组";

    /**
     * 无权维护该分组时的统一提示。
     */
    private static final String GROUP_DENY_WRITE = "无权维护该人才分组";

    /**
     * 分组类型：公共分组。
     */
    private static final String TYPE_PUBLIC = "public";

    /**
     * 分组类型：个人收藏。
     */
    private static final String TYPE_PERSONAL = "personal";

    /**
     * 分组状态：生效。
     */
    private static final String STATUS_ACTIVE = "active";

    /**
     * 人才分组 Mapper。
     */
    private final TalentGroupMapper talentGroupMapper;

    /**
     * 人才分组成员 Mapper。
     */
    private final TalentGroupMemberMapper talentGroupMemberMapper;

    /**
     * 人才主档 Mapper（只读：解析可见人才ID集合与回填成员姓名）。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 人才主档服务（只用于资源级鉴权）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 人才可见范围领域服务（唯一授权入口）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 业务编号生成器（分组编码来源）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    @Override
    public PageResult<TalentGroupVo> queryPage(TalentGroupQueryBo bo, PageQuery pageQuery) {
        TalentScopeDomainService.ScopeCondition scope = talentScopeDomainService.currentScope();
        TalentGroupQueryBo query = bo == null ? new TalentGroupQueryBo() : bo;
        LambdaQueryWrapper<TalentGroup> wrapper = new LambdaQueryWrapper<TalentGroup>()
            .like(StringUtils.isNotBlank(query.getGroupName()), TalentGroup::getGroupName, query.getGroupName())
            .like(StringUtils.isNotBlank(query.getGroupCode()), TalentGroup::getGroupCode, query.getGroupCode())
            .eq(StringUtils.isNotBlank(query.getGroupType()), TalentGroup::getGroupType, query.getGroupType())
            .eq(query.getOwnerId() != null, TalentGroup::getOwnerId, query.getOwnerId())
            .eq(query.getOwnerDeptId() != null, TalentGroup::getOwnerDeptId, query.getOwnerDeptId())
            .eq(StringUtils.isNotBlank(query.getVisibilityType()), TalentGroup::getVisibilityType, query.getVisibilityType())
            .eq(StringUtils.isNotBlank(query.getStatus()), TalentGroup::getStatus, query.getStatus())
            .orderByDesc(TalentGroup::getCreateTime);
        List<TalentGroup> groups = talentGroupMapper.selectList(wrapper);
        if (CollUtil.isEmpty(groups)) {
            return PageResult.build(List.of(), 0L);
        }
        List<TalentGroup> visible = new ArrayList<>();
        for (TalentGroup group : groups) {
            if (isGroupVisible(group, scope)) {
                visible.add(group);
            }
        }
        if (visible.isEmpty()) {
            return PageResult.build(List.of(), 0L);
        }
        // 分组数量有限：按可见性过滤后再分页，保证分页总数与可见集合一致
        PageResult<TalentGroup> sliced = slice(visible, pageQuery);
        List<TalentGroupVo> rows = new ArrayList<>(sliced.getRows().size());
        for (TalentGroup group : sliced.getRows()) {
            TalentGroupVo vo = MapstructUtils.convert(group, TalentGroupVo.class);
            if (vo != null) {
                rows.add(vo);
            }
        }
        return PageResult.build(rows, sliced.getTotal());
    }

    @Override
    public TalentGroupVo getDetail(Long groupId) {
        TalentGroup group = requireGroup(groupId);
        checkGroupVisible(group, talentScopeDomainService.currentScope());
        return MapstructUtils.convert(group, TalentGroupVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TalentGroupBo bo) {
        if (bo == null) {
            throw new ServiceException("分组入参不能为空");
        }
        String groupType = resolveGroupType(bo.getGroupType());
        validateVisibility(bo.getVisibilityType());
        if (TYPE_PUBLIC.equals(groupType)) {
            // 公共分组由人才池管理员维护（设计文档 §8.15），普通用户不得自行创建
            TalentScopeDomainService.ScopeCondition scope = talentScopeDomainService.currentScope();
            if (!scope.unlimited() && !scope.groupLevelAdmin()) {
                throw new ServiceException("公共分组由人才池管理员维护，无权创建");
            }
        }
        TalentGroup entity = MapstructUtils.convert(bo, TalentGroup.class);
        if (entity == null) {
            entity = new TalentGroup();
        }
        // 服务端权威字段：主键、编码、成员数、状态默认值不接受前端写入
        entity.setGroupId(null);
        entity.setGroupCode(businessNoGenerator.nextGroupNo());
        entity.setGroupType(groupType);
        entity.setTalentCount(0);
        entity.setStatus(StringUtils.isBlank(bo.getStatus()) ? STATUS_ACTIVE : bo.getStatus());
        entity.setVisibilityType(StringUtils.isBlank(bo.getVisibilityType())
            ? defaultVisibility(groupType) : bo.getVisibilityType());
        fillOwnerIfAbsent(entity);
        if (talentGroupMapper.insert(entity) == 0) {
            throw new ServiceException("分组创建失败，请重试");
        }
        log.info("新增人才分组, groupId={}, groupType={}, ownerId={}",
            entity.getGroupId(), entity.getGroupType(), entity.getOwnerId());
        return entity.getGroupId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TalentGroupBo bo) {
        if (bo == null || bo.getGroupId() == null) {
            throw new ServiceException("分组ID不能为空");
        }
        TalentGroup exist = requireGroup(bo.getGroupId());
        checkGroupWritable(exist, talentScopeDomainService.currentScope());
        // 分组类型创建后不可变更：公共分组与个人收藏的可见性口径完全不同
        if (StringUtils.isNotBlank(bo.getGroupType()) && !exist.getGroupType().equals(bo.getGroupType())) {
            throw new ServiceException("分组类型创建后不可变更");
        }
        validateVisibility(bo.getVisibilityType());
        TalentGroup update = MapstructUtils.convert(bo, TalentGroup.class);
        if (update == null) {
            update = new TalentGroup();
        }
        update.setGroupId(exist.getGroupId());
        // 编码、成员数与负责人由服务端维护
        update.setGroupCode(null);
        update.setTalentCount(null);
        update.setGroupType(null);
        update.setOwnerId(null);
        if (talentGroupMapper.updateById(update) == 0) {
            throw new ServiceException(GROUP_NOT_FOUND);
        }
        log.info("更新人才分组, groupId={}", exist.getGroupId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long groupId) {
        TalentGroup group = requireGroup(groupId);
        checkGroupWritable(group, talentScopeDomainService.currentScope());
        // 只删除分组与成员关系：不删除任何人才主档与关联资料（设计文档 §8.15）
        talentGroupMemberMapper.delete(new LambdaQueryWrapper<TalentGroupMember>()
            .eq(TalentGroupMember::getGroupId, groupId));
        talentGroupMapper.deleteById(groupId);
        log.info("逻辑删除人才分组, groupId={}", groupId);
    }

    @Override
    public PageResult<TalentGroupMemberVo> queryMembers(Long groupId, PageQuery pageQuery) {
        TalentGroup group = requireGroup(groupId);
        checkGroupVisible(group, talentScopeDomainService.currentScope());
        // 个人收藏不改变人才数据权限：成员列表同样必须叠加人才可见范围（§8.15、§11.1）
        List<Long> visibleTalentIds = resolveVisibleTalentIds();
        if (visibleTalentIds.isEmpty()) {
            return PageResult.build(List.of(), 0L);
        }
        LambdaQueryWrapper<TalentGroupMember> wrapper = new LambdaQueryWrapper<TalentGroupMember>()
            .eq(TalentGroupMember::getGroupId, groupId)
            .in(TalentGroupMember::getTalentId, visibleTalentIds)
            .orderByDesc(TalentGroupMember::getAddedTime);
        PageQuery pq = pageQuery == null ? new PageQuery() : pageQuery;
        Page<TalentGroupMemberVo> page = talentGroupMemberMapper.selectVoPage(pq.build(), wrapper);
        return PageResult.build(fillTalentInfo(page.getRecords()), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TalentGroupMemberVo addMember(Long groupId, Long talentId) {
        if (talentId == null) {
            throw new ServiceException("人才ID不能为空");
        }
        TalentGroup group = requireGroup(groupId);
        checkGroupWritable(group, talentScopeDomainService.currentScope());
        // 资源级鉴权：人才必须对当前用户可见（唯一权威判定在 TalentScopeDomainService）
        talentProfileService.requireVisible(talentId);
        TalentGroupMember exist = selectMember(groupId, talentId);
        if (exist != null) {
            log.info("人才重复加入分组，返回已有关系, groupId={}, talentId={}, memberId={}",
                groupId, talentId, exist.getMemberId());
            return toMemberVo(exist);
        }
        TalentGroupMember entity = new TalentGroupMember();
        entity.setGroupId(groupId);
        entity.setTalentId(talentId);
        entity.setAddedBy(currentUserId());
        entity.setAddedTime(LocalDateTime.now());
        try {
            talentGroupMemberMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 逻辑删除行仍占用 uk_hr_talent_group_member：恢复历史关系后按幂等返回
            talentGroupMemberMapper.restoreMember(groupId, talentId, entity.getAddedBy(), entity.getAddedTime());
            TalentGroupMember restored = selectMember(groupId, talentId);
            if (restored == null) {
                throw new ServiceException("该人才已在分组中，请刷新后重试");
            }
            log.info("人才重复加入分组，恢复历史关系, groupId={}, talentId={}", groupId, talentId);
            refreshTalentCount(groupId);
            return toMemberVo(restored);
        }
        refreshTalentCount(groupId);
        log.info("人才加入分组, groupId={}, talentId={}, memberId={}", groupId, talentId, entity.getMemberId());
        return toMemberVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long groupId, Long memberId) {
        TalentGroup group = requireGroup(groupId);
        checkGroupWritable(group, talentScopeDomainService.currentScope());
        if (memberId == null) {
            throw new ServiceException("成员关系ID不能为空");
        }
        TalentGroupMember member = talentGroupMemberMapper.selectById(memberId);
        if (member == null || !Objects.equals(groupId, member.getGroupId())) {
            throw new ServiceException("分组成员关系不存在");
        }
        // 只结束关系：逻辑删除成员行，绝不删除人才主档（设计文档 §8.15）
        talentGroupMemberMapper.deleteById(memberId);
        refreshTalentCount(groupId);
        log.info("移出分组成员（仅结束关系，不删除人才主档）, groupId={}, memberId={}, talentId={}",
            groupId, memberId, member.getTalentId());
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 读取分组并要求其存在。
     *
     * @param groupId 分组ID
     * @return 分组实体
     */
    private TalentGroup requireGroup(Long groupId) {
        if (groupId == null) {
            throw new ServiceException("分组ID不能为空");
        }
        TalentGroup group = talentGroupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException(GROUP_NOT_FOUND);
        }
        return group;
    }

    /**
     * 判定分组是否对当前用户可见。
     *
     * <p>公共分组复用 {@link TalentScopeDomainService#visible} 的权威分支
     * （{@code talentId} 固定为 {@code null}，避免与人才授权表 ID 空间串号）；
     * 个人收藏只对本人可见——它属于个人空间，不因角色提升而扩大可见范围。</p>
     *
     * @param group 分组实体
     * @param scope 当前可见范围条件
     * @return 是否可见
     */
    private boolean isGroupVisible(TalentGroup group, TalentScopeDomainService.ScopeCondition scope) {
        if (!TYPE_PUBLIC.equals(group.getGroupType())) {
            return scope != null && scope.currentUserId() != null
                && scope.currentUserId().equals(group.getOwnerId());
        }
        return talentScopeDomainService.visible(toScopeTarget(group), scope);
    }

    /**
     * 校验分组可见，不可见时抛出中文提示。
     *
     * @param group 分组实体
     * @param scope 当前可见范围条件
     */
    private void checkGroupVisible(TalentGroup group, TalentScopeDomainService.ScopeCondition scope) {
        if (!isGroupVisible(group, scope)) {
            log.warn("分组可见性校验未通过, groupId={}, userId={}",
                group.getGroupId(), scope == null ? null : scope.currentUserId());
            throw new ServiceException(GROUP_DENY_VIEW);
        }
    }

    /**
     * 校验当前用户可维护该分组。
     *
     * <p>个人收藏仅本人可维护；公共分组由集团级人才管理员、超级管理员或分组负责人维护。
     * 角色判定取自 {@link TalentScopeDomainService}，本类不解析角色标识。</p>
     *
     * @param group 分组实体
     * @param scope 当前可见范围条件
     */
    private void checkGroupWritable(TalentGroup group, TalentScopeDomainService.ScopeCondition scope) {
        Long currentUserId = scope == null ? null : scope.currentUserId();
        if (TYPE_PUBLIC.equals(group.getGroupType())) {
            if (scope != null && (scope.unlimited() || scope.groupLevelAdmin())) {
                return;
            }
            if (currentUserId != null && currentUserId.equals(group.getOwnerId())) {
                return;
            }
            throw new ServiceException(GROUP_DENY_WRITE);
        }
        if (scope != null && scope.unlimited()) {
            return;
        }
        if (currentUserId != null && currentUserId.equals(group.getOwnerId())) {
            return;
        }
        throw new ServiceException("个人收藏仅本人可维护");
    }

    /**
     * 把分组映射为可见范围判定所需的最小快照。
     *
     * @param group 分组实体
     * @return 可见性快照
     */
    private TalentScopeDomainService.TalentScopeTarget toScopeTarget(TalentGroup group) {
        return new TalentScopeDomainService.TalentScopeTarget(
            null,
            group.getOwnerId(),
            group.getOwnerDeptId(),
            group.getVisibilityType(),
            null,
            group.getDelFlag());
    }

    /**
     * 解析分组类型：仅接受 public/personal，缺省 personal。
     *
     * @param groupType 分组类型
     * @return 归一化后的分组类型
     */
    private String resolveGroupType(String groupType) {
        if (StringUtils.isBlank(groupType)) {
            return TYPE_PERSONAL;
        }
        if (!TYPE_PUBLIC.equals(groupType) && !TYPE_PERSONAL.equals(groupType)) {
            throw new ServiceException("分组类型不合法，只能为 public（公共分组）或 personal（个人收藏）");
        }
        return groupType;
    }

    /**
     * 分组类型对应的默认可见范围。
     *
     * @param groupType 分组类型
     * @return 默认可见范围编码
     */
    private String defaultVisibility(String groupType) {
        return TYPE_PUBLIC.equals(groupType)
            ? TalentVisibilityTypeEnum.DEPARTMENT.getCode()
            : TalentVisibilityTypeEnum.OWNER.getCode();
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
     * 按 {@code group_id + talent_id} 读取成员关系（逻辑删除行自动过滤）。
     *
     * @param groupId  分组ID
     * @param talentId 人才主档ID
     * @return 成员关系，不存在返回 null
     */
    private TalentGroupMember selectMember(Long groupId, Long talentId) {
        return talentGroupMemberMapper.selectOne(new LambdaQueryWrapper<TalentGroupMember>()
            .eq(TalentGroupMember::getGroupId, groupId)
            .eq(TalentGroupMember::getTalentId, talentId));
    }

    /**
     * 重算分组成员数冗余列。
     *
     * @param groupId 分组ID
     */
    private void refreshTalentCount(Long groupId) {
        long count = talentGroupMemberMapper.selectCount(new LambdaQueryWrapper<TalentGroupMember>()
            .eq(TalentGroupMember::getGroupId, groupId));
        talentGroupMapper.update(null, new LambdaUpdateWrapper<TalentGroup>()
            .eq(TalentGroup::getGroupId, groupId)
            .set(TalentGroup::getTalentCount, (int) count));
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
    private TalentGroupMemberVo toMemberVo(TalentGroupMember member) {
        if (member == null) {
            return null;
        }
        // 手工赋值而非 MapStruct 复制：只暴露视图需要的字段
        TalentGroupMemberVo vo = new TalentGroupMemberVo();
        vo.setMemberId(member.getMemberId());
        vo.setGroupId(member.getGroupId());
        vo.setTalentId(member.getTalentId());
        vo.setAddedBy(member.getAddedBy());
        vo.setAddedTime(member.getAddedTime());
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
    private List<TalentGroupMemberVo> fillTalentInfo(List<TalentGroupMemberVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }
        List<Long> talentIds = rows.stream()
            .map(TalentGroupMemberVo::getTalentId)
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
        for (TalentGroupMemberVo vo : rows) {
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
     * 内存分页：调用方已按可见范围与业务条件过滤，这里只做切片。
     *
     * @param all       已过滤的全量列表
     * @param pageQuery 分页参数，可为空
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

    /**
     * 负责人未指定时回落为当前登录用户，归属部门回落为登录用户部门。
     *
     * @param entity 分组实体
     */
    private void fillOwnerIfAbsent(TalentGroup entity) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null) {
            return;
        }
        if (entity.getOwnerId() == null) {
            entity.setOwnerId(loginUser.getUserId());
        }
        if (entity.getOwnerDeptId() == null) {
            entity.setOwnerDeptId(loginUser.getDeptId());
        }
    }

}
