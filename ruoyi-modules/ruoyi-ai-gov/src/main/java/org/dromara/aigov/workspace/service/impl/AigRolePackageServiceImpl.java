package org.dromara.aigov.workspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.domain.AigRoleAction;
import org.dromara.aigov.workspace.domain.AigRolePackageDraft;
import org.dromara.aigov.workspace.domain.AigRoleProfile;
import org.dromara.aigov.workspace.domain.AigRoleVersion;
import org.dromara.aigov.workspace.domain.AigScenario;
import org.dromara.aigov.workspace.domain.AigScenarioVersion;
import org.dromara.aigov.workspace.domain.bo.AigRolePackageQueryBo;
import org.dromara.aigov.workspace.domain.bo.AigRolePackageSaveBo;
import org.dromara.aigov.workspace.domain.vo.AigRoleActionVo;
import org.dromara.aigov.workspace.domain.vo.AigRolePackageValidateVo;
import org.dromara.aigov.workspace.domain.vo.AigRoleVersionDetailVo;
import org.dromara.aigov.workspace.domain.vo.AigRoleVersionVo;
import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.dromara.aigov.workspace.helper.AigRoleManifestHasher;
import org.dromara.aigov.workspace.helper.AigRolePackageEditPolicy;
import org.dromara.aigov.workspace.helper.AigRolePackageLookup;
import org.dromara.aigov.workspace.helper.AigRolePackageManifest;
import org.dromara.aigov.workspace.helper.AigRolePackageValidator;
import org.dromara.aigov.workspace.helper.AigRoleReleaseTransition;
import org.dromara.aigov.workspace.mapper.AigRoleActionMapper;
import org.dromara.aigov.workspace.mapper.AigRoleProfileMapper;
import org.dromara.aigov.workspace.mapper.AigRoleVersionMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioVersionMapper;
import org.dromara.aigov.workspace.service.IAigRolePackageService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位包服务实现（主文档线增量 1b）。
 *
 * <h3>四条不可让步的行为</h3>
 * <ol>
 *     <li><b>发布后不可变</b>：非 DRAFT 版本一律拒绝保存（只改状态，不改清单）。</li>
 *     <li><b>离开 DRAFT 必须先校验通过</b>：把一份自己都配错的岗位包发给员工，
 *         表现是"卡片点了没反应"——用户只会以为系统坏了。</li>
 *     <li><b>覆盖草稿要带 CAS</b>：两个人同时编辑时，后保存的不能默默盖掉先保存的。</li>
 *     <li><b>流转只走边表</b>：允许的边写在 {@code AigRoleReleaseTransition}，这里只执行判定结果。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigRolePackageServiceImpl implements IAigRolePackageService {

    /**
     * 记录状态：正常
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 启用
     */
    private static final String ENABLED_YES = "Y";

    /**
     * 未启用
     */
    private static final String ENABLED_NO = "N";

    private final AigRoleProfileMapper roleProfileMapper;
    private final AigRoleVersionMapper roleVersionMapper;
    private final AigRoleActionMapper roleActionMapper;
    private final AigScenarioMapper scenarioMapper;
    private final AigScenarioVersionMapper scenarioVersionMapper;

    @Override
    public PageResult<AigRoleVersionVo> queryPage(AigRolePackageQueryBo bo, PageQuery pageQuery) {
        AigRolePackageQueryBo query = bo == null ? new AigRolePackageQueryBo() : bo;
        List<Long> roleIds = filterRoleIds(query);
        if (roleIds != null && roleIds.isEmpty()) {
            // 按岗位编码/名称过滤但一个岗位都没命中：直接给空页，
            // 而不是"忽略这个条件"——那会让用户以为筛选没生效
            return PageResult.build(List.of(), 0L);
        }
        LambdaQueryWrapper<AigRoleVersion> wrapper = Wrappers.<AigRoleVersion>lambdaQuery()
            .eq(StringUtils.isNotBlank(query.getReleaseStatus()),
                AigRoleVersion::getReleaseStatus, query.getReleaseStatus())
            .in(roleIds != null, AigRoleVersion::getRoleId, roleIds == null ? List.of() : roleIds)
            .orderByDesc(AigRoleVersion::getUpdateTime)
            .orderByDesc(AigRoleVersion::getRoleVersionId);
        Page<AigRoleVersion> page = roleVersionMapper.selectPage(pageQuery.build(), wrapper);
        Map<Long, AigRoleProfile> profiles = loadProfiles(page.getRecords());
        List<AigRoleVersionVo> rows = new ArrayList<>();
        for (AigRoleVersion version : page.getRecords()) {
            rows.add(toVo(version, profiles.get(version.getRoleId())));
        }
        return PageResult.build(rows, page.getTotal());
    }

    @Override
    public List<AigRoleVersionVo> listVersions(Long roleId) {
        if (roleId == null) {
            throw new ServiceException("岗位ID不能为空");
        }
        AigRoleProfile profile = roleProfileMapper.selectById(roleId);
        if (profile == null) {
            throw new ServiceException("岗位不存在：" + roleId);
        }
        List<AigRoleVersion> versions = roleVersionMapper.selectList(Wrappers.<AigRoleVersion>lambdaQuery()
            .eq(AigRoleVersion::getRoleId, roleId)
            .orderByDesc(AigRoleVersion::getRoleVersionId));
        List<AigRoleVersionVo> rows = new ArrayList<>();
        for (AigRoleVersion version : versions) {
            rows.add(toVo(version, profile));
        }
        return rows;
    }

    @Override
    public AigRoleVersionDetailVo getVersionDetail(Long roleVersionId) {
        AigRoleVersion version = requireVersion(roleVersionId);
        return detail(version);
    }

    @Override
    public AigRolePackageValidateVo validate(AigRolePackageSaveBo bo) {
        if (bo == null) {
            throw new ServiceException("岗位包入参不能为空");
        }
        AigRolePackageDraft draft = toDraft(bo);
        String manifest = AigRolePackageManifest.toJson(draft);
        AigRolePackageValidateVo vo = new AigRolePackageValidateVo();
        vo.setManifestSha256(AigRoleManifestHasher.hash(manifest));
        List<String> problems = AigRolePackageValidator.validate(draft, lookup());
        vo.setProblems(problems);
        vo.setPassed(problems.isEmpty());
        return vo;
    }

    @Override
    public AigRolePackageValidateVo validateStored(Long roleVersionId) {
        AigRoleVersion version = requireVersion(roleVersionId);
        AigRolePackageValidateVo vo = new AigRolePackageValidateVo();
        vo.setManifestSha256(version.getManifestSha256());
        List<String> problems = storedProblems(version);
        vo.setProblems(problems);
        vo.setPassed(problems.isEmpty());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigRoleVersionDetailVo saveDraft(AigRolePackageSaveBo bo, Long actorId) {
        requireActor(actorId);
        if (bo == null) {
            throw new ServiceException("岗位包入参不能为空");
        }
        AigRolePackageDraft draft = toDraft(bo);
        String manifest = AigRolePackageManifest.toJson(draft);
        String hash = AigRoleManifestHasher.hash(manifest);

        if (bo.getRoleVersionId() != null) {
            return overwriteDraft(bo, draft, manifest, hash, actorId);
        }
        return createDraft(bo, draft, manifest, hash, actorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigRoleVersionDetailVo advance(Long roleVersionId, String targetStatus, String remark, Long actorId) {
        requireActor(actorId);
        AigRoleVersion version = requireVersion(roleVersionId);
        AigRoleReleaseStatusEnum from = AigRoleReleaseStatusEnum.find(version.getReleaseStatus());
        AigRoleReleaseStatusEnum to = AigRoleReleaseStatusEnum.find(targetStatus);
        if (to == null) {
            throw new ServiceException("目标发布状态非法：" + targetStatus
                + "（只允许 DRAFT/TESTING/PUBLISHED/DISABLED）");
        }
        if (!AigRoleReleaseTransition.canTransition(from, to)) {
            throw new ServiceException("不允许的发布流转：" + (from == null ? "未知" : from.getCode())
                + " → " + to.getCode() + "（从当前状态只能去："
                + AigRoleReleaseTransition.describeAllowed(from) + "）");
        }
        if (AigRolePackageEditPolicy.requiresValidationPass(to)) {
            // 门槛在这里：不能把一份自己都配错的岗位包发给员工或测试账号
            List<String> problems = storedProblems(version);
            if (!problems.isEmpty()) {
                throw new ServiceException("岗位包校验未通过，不能流转到 " + to.getCode()
                    + "：共 " + problems.size() + " 个问题——" + String.join("；", problems));
            }
        }
        LocalDateTime now = LocalDateTime.now();
        AigRoleVersion update = new AigRoleVersion();
        update.setRoleVersionId(version.getRoleVersionId());
        update.setReleaseStatus(to.getCode());
        if (to == AigRoleReleaseStatusEnum.PUBLISHED) {
            // 发布时间只记第一次：DISABLED→PUBLISHED 的"重新启用"不改写"当初是什么时候发的"
            if (version.getPublishedAt() == null) {
                update.setPublishedAt(now);
            }
            update.setDisabledAt(null);
        }
        if (to == AigRoleReleaseStatusEnum.DISABLED) {
            update.setDisabledAt(now);
        }
        if (to == AigRoleReleaseStatusEnum.TESTING) {
            // 重新进入测试：仍在 DRAFT 的语义之外，但记录这次是谁、为什么发的
            update.setDisabledAt(null);
        }
        if (StringUtils.isNotBlank(remark)) {
            String merged = mergeRemark(version.getRemark(), remark);
            update.setRemark(merged);
        }
        roleVersionMapper.updateById(update);
        log.info("岗位包发布流转：roleVersionId={} {}→{} actorId={}", roleVersionId,
            from == null ? "未知" : from.getCode(), to.getCode(), actorId);
        return detail(requireVersion(roleVersionId));
    }

    /**
     * 覆盖一个 DRAFT 版本（CAS 保护）。
     *
     * @param bo       入参
     * @param draft    草稿
     * @param manifest 清单 JSON
     * @param hash     清单哈希
     * @param actorId  操作者
     * @return 保存后的详情
     */
    private AigRoleVersionDetailVo overwriteDraft(AigRolePackageSaveBo bo, AigRolePackageDraft draft,
                                                  String manifest, String hash, Long actorId) {
        AigRoleVersion existing = requireVersion(bo.getRoleVersionId());
        AigRoleReleaseStatusEnum status = AigRoleReleaseStatusEnum.find(existing.getReleaseStatus());
        if (!AigRolePackageEditPolicy.isEditable(status)) {
            throw new ServiceException("岗位版本 " + existing.getVersion() + " 当前是 "
                + (status == null ? existing.getReleaseStatus() : status.getCode())
                + "：发布后不可改，要改请出新版本（否则「这个版本当时是什么」将无法回答）");
        }
        if (StringUtils.isBlank(bo.getExpectedManifestSha256())) {
            throw new ServiceException("覆盖草稿必须带 expectedManifestSha256：否则并发编辑会被静默覆盖");
        }
        if (!AigRolePackageEditPolicy.casMatches(bo.getExpectedManifestSha256(), existing.getManifestSha256())) {
            throw new ServiceException("草稿已被他人修改（期望哈希 " + bo.getExpectedManifestSha256()
                + "，实际 " + existing.getManifestSha256() + "）：请刷新后重新编辑");
        }
        AigRoleProfile profile = roleProfileMapper.selectById(existing.getRoleId());
        if (profile == null) {
            throw new ServiceException("岗位不存在：" + existing.getRoleId());
        }
        if (!profile.getRoleCode().equals(draft.roleCode())) {
            throw new ServiceException("岗位编码不能修改（库中=" + profile.getRoleCode()
                + "，入参=" + draft.roleCode() + "）：编码是跨版本稳定的标识");
        }
        AigRoleVersion update = new AigRoleVersion();
        update.setRoleVersionId(existing.getRoleVersionId());
        update.setManifestJson(manifest);
        update.setManifestSha256(hash);
        update.setRemark(StringUtils.isNotBlank(bo.getRemark()) ? bo.getRemark() : existing.getRemark());
        roleVersionMapper.updateById(update);
        replaceActions(existing.getRoleVersionId(), draft.actions());
        return detail(requireVersion(existing.getRoleVersionId()));
    }

    /**
     * 新建 DRAFT 版本（必要时先建岗位定义）。
     *
     * @param bo       入参
     * @param draft    草稿
     * @param manifest 清单 JSON
     * @param hash     清单哈希
     * @param actorId  操作者
     * @return 保存后的详情
     */
    private AigRoleVersionDetailVo createDraft(AigRolePackageSaveBo bo, AigRolePackageDraft draft,
                                               String manifest, String hash, Long actorId) {
        AigRoleProfile profile = resolveOrCreateProfile(bo);
        boolean duplicated = roleVersionMapper.exists(Wrappers.<AigRoleVersion>lambdaQuery()
            .eq(AigRoleVersion::getRoleId, profile.getRoleId())
            .eq(AigRoleVersion::getVersion, draft.version()));
        if (duplicated) {
            throw new ServiceException("岗位 " + profile.getRoleCode() + " 已存在版本 " + draft.version()
                + "：版本号不能重复（已发布版本不可覆盖，请换一个版本号）");
        }
        AigRoleVersion version = new AigRoleVersion();
        version.setRoleId(profile.getRoleId());
        version.setVersion(draft.version());
        version.setReleaseStatus(AigRoleReleaseStatusEnum.DRAFT.getCode());
        // 显式给通道，不依赖 DDL 默认值：默认值只在"忘了给"时生效，而那时没人知道给了什么
        version.setRolloutChannel("TESTING");
        version.setManifestJson(manifest);
        version.setManifestSha256(hash);
        version.setStatus(STATUS_NORMAL);
        version.setRemark(bo.getRemark());
        roleVersionMapper.insert(version);
        insertActions(version.getRoleVersionId(), draft.actions());
        log.info("岗位包草稿已创建：roleId={} version={} actorId={}", profile.getRoleId(),
            draft.version(), actorId);
        return detail(requireVersion(version.getRoleVersionId()));
    }

    /**
     * 解析目标岗位：给了 roleId 就用它（并核对编码），否则按编码找，找不到就新建。
     *
     * @param bo 入参
     * @return 岗位定义
     */
    private AigRoleProfile resolveOrCreateProfile(AigRolePackageSaveBo bo) {
        if (bo.getRoleId() != null) {
            AigRoleProfile profile = roleProfileMapper.selectById(bo.getRoleId());
            if (profile == null) {
                throw new ServiceException("岗位不存在：" + bo.getRoleId());
            }
            if (!profile.getRoleCode().equals(bo.getRoleCode().trim())) {
                throw new ServiceException("岗位编码与岗位ID不匹配（id=" + bo.getRoleId()
                    + " 的编码是 " + profile.getRoleCode() + "，入参=" + bo.getRoleCode() + "）");
            }
            return profile;
        }
        AigRoleProfile existing = roleProfileMapper.selectOne(Wrappers.<AigRoleProfile>lambdaQuery()
            .eq(AigRoleProfile::getRoleCode, bo.getRoleCode().trim())
            .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        AigRoleProfile profile = new AigRoleProfile();
        profile.setRoleCode(bo.getRoleCode().trim());
        profile.setRoleName(bo.getRoleName().trim());
        profile.setDescription(bo.getDescription());
        profile.setStatus(STATUS_NORMAL);
        roleProfileMapper.insert(profile);
        return profile;
    }

    /**
     * 详情视图（含卡片、清单原文与当前校验结论）。
     *
     * @param version 版本
     * @return 详情
     */
    private AigRoleVersionDetailVo detail(AigRoleVersion version) {
        AigRoleProfile profile = roleProfileMapper.selectById(version.getRoleId());
        AigRoleVersionDetailVo vo = new AigRoleVersionDetailVo();
        fill(vo, version, profile);
        if (profile != null) {
            vo.setRoleDescription(profile.getDescription());
        }
        vo.setManifestJson(version.getManifestJson());
        List<AigRoleActionVo> actions = new ArrayList<>();
        for (AigRoleAction action : listActionRows(version.getRoleVersionId())) {
            actions.add(toActionVo(action));
        }
        vo.setActions(actions);
        vo.setProblems(storedProblems(version));
        return vo;
    }

    /**
     * 校验"库里的那一份"：既跑静态规则，也核对清单与记录的完整性。
     *
     * <p>后两条是本方法<b>独有</b>的价值：清单被改过、或清单与版本行不一致，
     * 只有读库才看得出来。</p>
     *
     * @param version 版本
     * @return 问题清单
     */
    private List<String> storedProblems(AigRoleVersion version) {
        List<String> problems = new ArrayList<>();
        String manifestJson = version.getManifestJson();
        AigRolePackageDraft draft;
        try {
            draft = AigRolePackageManifest.fromJson(manifestJson, toActionDrafts(version.getRoleVersionId()));
        } catch (IllegalArgumentException e) {
            // 清单都读不出来，就别再叠加一堆"缺字段"的二次报错
            problems.add("岗位包清单无法读取：" + e.getMessage());
            return problems;
        }
        if (StringUtils.isNotBlank(manifestJson)) {
            String actualHash = AigRoleManifestHasher.hash(manifestJson);
            if (!actualHash.equals(version.getManifestSha256())) {
                problems.add("清单哈希与记录不符：记录=" + version.getManifestSha256()
                    + "，实际=" + actualHash + "（已发布版本的清单被改过？）");
            }
        }
        if (StringUtils.isNotBlank(draft.version()) && !draft.version().equals(version.getVersion())) {
            problems.add("清单里的版本号（" + draft.version() + "）与库中记录（"
                + version.getVersion() + "）不一致：清单被改过？");
        }
        problems.addAll(AigRolePackageValidator.validate(draft, lookup()));
        return problems;
    }

    /**
     * 场景版本存在性查询（校验器只问"存在吗"，由这里回答）。
     *
     * @return 查询实现
     */
    private AigRolePackageLookup lookup() {
        return (scenarioCode, scenarioVersion) -> {
            AigScenario scenario = scenarioMapper.selectOne(Wrappers.<AigScenario>lambdaQuery()
                .eq(AigScenario::getScenarioCode, scenarioCode)
                .last("limit 1"));
            if (scenario == null) {
                return false;
            }
            return scenarioVersionMapper.exists(Wrappers.<AigScenarioVersion>lambdaQuery()
                .eq(AigScenarioVersion::getScenarioId, scenario.getScenarioId())
                .eq(AigScenarioVersion::getVersion, scenarioVersion));
        };
    }

    /**
     * 把入参转成校验器要的草稿。
     *
     * @param bo 入参
     * @return 草稿
     */
    private AigRolePackageDraft toDraft(AigRolePackageSaveBo bo) {
        List<AigRolePackageDraft.CategoryDraft> categories = new ArrayList<>();
        if (bo.getCategories() != null) {
            for (AigRolePackageSaveBo.Category category : bo.getCategories()) {
                if (category == null) {
                    continue;
                }
                categories.add(new AigRolePackageDraft.CategoryDraft(category.getCode(), category.getName()));
            }
        }
        List<AigRolePackageDraft.ActionDraft> actions = new ArrayList<>();
        if (bo.getActions() != null) {
            for (AigRolePackageSaveBo.Action action : bo.getActions()) {
                if (action == null) {
                    continue;
                }
                actions.add(new AigRolePackageDraft.ActionDraft(
                    action.getCode(), action.getCategoryCode(), action.getTitle(), action.getLaunchMode(),
                    action.getTargetType(), action.getTargetRef(), action.getStudioRouteKey(),
                    action.getRequiredContext(),
                    action.getEnabled() == null || action.getEnabled()));
            }
        }
        return new AigRolePackageDraft(bo.getRoleCode() == null ? null : bo.getRoleCode().trim(),
            bo.getRoleName(), bo.getVersion() == null ? null : bo.getVersion().trim(),
            categories, bo.getDefaultCategory(), actions, bo.getAudienceScope(),
            bo.getDefaultDataLevel(), bo.getMaxDataLevel());
    }

    /**
     * 读出某版本的卡片行（按分类/排序）。
     *
     * @param roleVersionId 版本ID
     * @return 行
     */
    private List<AigRoleAction> listActionRows(Long roleVersionId) {
        return roleActionMapper.selectList(Wrappers.<AigRoleAction>lambdaQuery()
            .eq(AigRoleAction::getRoleVersionId, roleVersionId)
            .orderByAsc(AigRoleAction::getCategoryCode)
            .orderByAsc(AigRoleAction::getSortOrder)
            .orderByAsc(AigRoleAction::getActionId));
    }

    /**
     * 把卡片行还原成校验输入。
     *
     * @param roleVersionId 版本ID
     * @return 卡片草稿
     */
    private List<AigRolePackageDraft.ActionDraft> toActionDrafts(Long roleVersionId) {
        List<AigRolePackageDraft.ActionDraft> drafts = new ArrayList<>();
        for (AigRoleAction action : listActionRows(roleVersionId)) {
            drafts.add(new AigRolePackageDraft.ActionDraft(action.getActionCode(), action.getCategoryCode(),
                action.getTitle(), action.getLaunchMode(), action.getTargetType(), action.getTargetRef(),
                action.getStudioRouteKey(), action.getRequiredContext(),
                ENABLED_YES.equals(action.getEnabled())));
        }
        return drafts;
    }

    /**
     * 重建某版本的卡片行（草稿覆盖）。
     *
     * @param roleVersionId 版本ID
     * @param actions       卡片
     */
    private void replaceActions(Long roleVersionId, List<AigRolePackageDraft.ActionDraft> actions) {
        roleActionMapper.delete(Wrappers.<AigRoleAction>lambdaQuery()
            .eq(AigRoleAction::getRoleVersionId, roleVersionId));
        insertActions(roleVersionId, actions);
    }

    /**
     * 写入卡片行。
     *
     * @param roleVersionId 版本ID
     * @param actions       卡片
     */
    private void insertActions(Long roleVersionId, List<AigRolePackageDraft.ActionDraft> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        int index = 0;
        for (AigRolePackageDraft.ActionDraft action : actions) {
            AigRoleAction row = new AigRoleAction();
            row.setRoleVersionId(roleVersionId);
            row.setActionCode(action.code() == null ? null : action.code().trim());
            row.setCategoryCode(action.categoryCode() == null ? null : action.categoryCode().trim());
            row.setTitle(action.title());
            row.setLaunchMode(action.launchMode());
            row.setTargetType(action.targetType());
            row.setTargetRef(action.targetRef());
            row.setStudioRouteKey(action.studioRouteKey());
            row.setRequiredContext(action.requiredContext());
            row.setSortOrder(index);
            row.setEnabled(action.enabled() ? ENABLED_YES : ENABLED_NO);
            row.setStatus(STATUS_NORMAL);
            roleActionMapper.insert(row);
            index++;
        }
    }

    /**
     * 取版本，取不到就抛。
     *
     * @param roleVersionId 版本ID
     * @return 版本
     */
    private AigRoleVersion requireVersion(Long roleVersionId) {
        if (roleVersionId == null) {
            throw new ServiceException("岗位版本ID不能为空");
        }
        AigRoleVersion version = roleVersionMapper.selectById(roleVersionId);
        if (version == null) {
            throw new ServiceException("岗位版本不存在：" + roleVersionId);
        }
        return version;
    }

    /**
     * 组装版本视图（含服务端算好的流转/可见性）。
     *
     * @param version 版本
     * @param profile 岗位（可空）
     * @return 视图
     */
    private AigRoleVersionVo toVo(AigRoleVersion version, AigRoleProfile profile) {
        AigRoleVersionVo vo = new AigRoleVersionVo();
        fill(vo, version, profile);
        return vo;
    }

    /**
     * 公共字段填充。
     *
     * @param vo      目标视图
     * @param version 版本
     * @param profile 岗位（可空）
     */
    private void fill(AigRoleVersionVo vo, AigRoleVersion version, AigRoleProfile profile) {
        vo.setRoleVersionId(version.getRoleVersionId());
        vo.setRoleId(version.getRoleId());
        if (profile != null) {
            vo.setRoleCode(profile.getRoleCode());
            vo.setRoleName(profile.getRoleName());
        }
        vo.setVersion(version.getVersion());
        vo.setReleaseStatus(version.getReleaseStatus());
        vo.setRolloutChannel(version.getRolloutChannel());
        vo.setManifestSha256(version.getManifestSha256());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setDisabledAt(version.getDisabledAt());
        vo.setStatus(version.getStatus());
        vo.setRemark(version.getRemark());
        vo.setUpdateTime(version.getUpdateTime());
        AigRoleReleaseStatusEnum status = AigRoleReleaseStatusEnum.find(version.getReleaseStatus());
        // 按枚举声明序输出：Set.of 的迭代顺序未定义，直接输出会让同一个版本
        // 在不同机器上给出不同的数组（本仓 CI 上就因此翻过车）
        List<String> allowed = new ArrayList<>();
        for (AigRoleReleaseStatusEnum item : AigRoleReleaseStatusEnum.values()) {
            if (AigRoleReleaseTransition.allowedFrom(status).contains(item)) {
                allowed.add(item.getCode());
            }
        }
        vo.setAllowedTransitions(allowed);
        vo.setVisibleToEmployees(AigRoleReleaseTransition.visibleToEmployees(status));
    }

    /**
     * 卡片行 → 视图。
     *
     * @param action 行
     * @return 视图
     */
    private AigRoleActionVo toActionVo(AigRoleAction action) {
        AigRoleActionVo vo = new AigRoleActionVo();
        vo.setActionId(action.getActionId());
        vo.setActionCode(action.getActionCode());
        vo.setCategoryCode(action.getCategoryCode());
        vo.setTitle(action.getTitle());
        vo.setDescription(action.getDescription());
        vo.setLaunchMode(action.getLaunchMode());
        vo.setTargetType(action.getTargetType());
        vo.setTargetRef(action.getTargetRef());
        vo.setStudioRouteKey(action.getStudioRouteKey());
        vo.setRequiredContext(action.getRequiredContext());
        vo.setSortOrder(action.getSortOrder());
        vo.setEnabled(action.getEnabled());
        return vo;
    }

    /**
     * 按编码/名称筛出岗位ID（无该条件时返回 null）。
     *
     * @param query 查询条件
     * @return 岗位ID；未使用该条件时返回 null
     */
    private List<Long> filterRoleIds(AigRolePackageQueryBo query) {
        boolean byCode = StringUtils.isNotBlank(query.getRoleCode());
        boolean byName = StringUtils.isNotBlank(query.getRoleName());
        if (!byCode && !byName) {
            return null;
        }
        List<AigRoleProfile> profiles = roleProfileMapper.selectList(Wrappers.<AigRoleProfile>lambdaQuery()
            .like(byCode, AigRoleProfile::getRoleCode, query.getRoleCode())
            .like(byName, AigRoleProfile::getRoleName, query.getRoleName()));
        List<Long> ids = new ArrayList<>();
        for (AigRoleProfile profile : profiles) {
            ids.add(profile.getRoleId());
        }
        return ids;
    }

    /**
     * 批量取岗位（避免逐行查询）。
     *
     * @param versions 版本行
     * @return roleId → 岗位
     */
    private Map<Long, AigRoleProfile> loadProfiles(List<AigRoleVersion> versions) {
        Map<Long, AigRoleProfile> result = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        for (AigRoleVersion version : versions) {
            if (version.getRoleId() != null && !result.containsKey(version.getRoleId())) {
                result.put(version.getRoleId(), null);
                ids.add(version.getRoleId());
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        for (AigRoleProfile profile : roleProfileMapper.selectList(Wrappers.<AigRoleProfile>lambdaQuery()
            .in(AigRoleProfile::getRoleId, ids))) {
            result.put(profile.getRoleId(), profile);
        }
        return result;
    }

    /**
     * 合并备注（新说明追加在老说明之后，不覆盖"这个版本当初是为什么发的"）。
     *
     * @param oldRemark 原备注
     * @param remark    新说明
     * @return 合并结果
     */
    private String mergeRemark(String oldRemark, String remark) {
        if (StringUtils.isBlank(oldRemark)) {
            return remark;
        }
        String merged = oldRemark + " | " + remark;
        return merged.length() > 500 ? merged.substring(0, 500) : merged;
    }

    /**
     * 操作者不能缺：流转记录里"是谁做的"若为 NULL，事后无法交代。
     *
     * @param actorId 操作者
     */
    private void requireActor(Long actorId) {
        if (actorId == null) {
            throw new ServiceException("岗位包操作需要登录用户：无法确定这次变更由谁发起");
        }
    }

}
