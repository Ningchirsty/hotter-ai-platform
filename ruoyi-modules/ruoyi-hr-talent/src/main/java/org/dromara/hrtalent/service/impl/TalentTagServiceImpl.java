package org.dromara.hrtalent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileTagBo;
import org.dromara.hrtalent.domain.bo.talent.TalentTagBo;
import org.dromara.hrtalent.domain.bo.talent.TalentTagQueryBo;
import org.dromara.hrtalent.domain.entity.TalentProfileTag;
import org.dromara.hrtalent.domain.entity.TalentTag;
import org.dromara.hrtalent.domain.vo.talent.TalentTagVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentTagCategoryEnum;
import org.dromara.hrtalent.mapper.TalentProfileTagMapper;
import org.dromara.hrtalent.mapper.TalentTagMapper;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.service.talent.ITalentTagService;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 人才标签服务实现（SPEC-P4 §2.3 C 线，设计文档 §8.15、§7.6.4、§11.1）。
 *
 * <p><b>本类承载的强制约束（禁止被后续实现删除或绕过）</b>：</p>
 * <ol>
 *     <li><b>禁止随意创建敏感或歧视性标签</b>（设计文档 §8.15）：标签名称命中
 *     {@link #SENSITIVE_TAG_WORDS} 敏感/歧视词库时，创建与修改一律拒绝并给出中文提示；</li>
 *     <li><b>敏感标签受控</b>：{@code sensitive_flag = '1'} 的标签只允许集团级人才管理员
 *     （{@link TalentScopeDomainService#currentScope()} 的 {@code groupLevelAdmin}）与超级管理员维护，
 *     并且在标签字典查询结果中对普通用户整体隐藏；</li>
 *     <li><b>敏感内容不得自动生成可被普通用户检索的标签</b>（设计文档 §7.6.4）：
 *     标签关系来源不是人工（{@code manual}）时，不允许挂敏感标签；
 *     背调失败、健康、家庭、年龄等内容既进不了标签字典，也挂不上人才档案；</li>
 *     <li><b>授权统一</b>：人才侧读写先经 {@link ITalentProfileService#requireVisible(Long)}，
 *     由 {@link TalentScopeDomainService} 完成资源级鉴权，本类不自行拼装授权规则。</li>
 * </ol>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentTagServiceImpl implements ITalentTagService {

    /**
     * 敏感/歧视性词库：标签名称命中任意一项即拒绝创建或修改。
     *
     * <p>覆盖设计文档 §7.6.4 明确点名的「背调失败 / 健康 / 家庭 / 年龄」，并补充
     * 常见歧视性维度（性别、婚育、民族、宗教、户籍、外貌、地域等）。
     * 该词库只做「禁止」用途，不参与任何展示或检索逻辑。</p>
     */
    private static final List<String> SENSITIVE_TAG_WORDS = List.of(
        "背调失败", "背调未通过", "背景调查未通过", "背调不合格",
        "健康", "疾病", "病史", "乙肝", "艾滋", "传染病", "残疾", "残障", "精神",
        "怀孕", "孕期", "生育", "婚育", "已婚", "未婚", "离婚", "婚姻", "家庭", "子女",
        "年龄", "大龄", "超龄", "35岁", "35以上", "四十岁",
        "性别", "男性", "女性", "民族", "少数民族", "宗教", "信仰",
        "户籍", "外地人", "本地人", "身高", "体重", "外貌", "容貌", "长相", "星座", "血型",
        "政治面貌", "地域歧视"
    );

    /**
     * 敏感标记：是。
     */
    private static final String SENSITIVE_YES = "1";

    /**
     * 敏感标记：否。
     */
    private static final String SENSITIVE_NO = "0";

    /**
     * 标签状态：启用。
     */
    private static final String STATUS_ACTIVE = "active";

    /**
     * 来源类型：人工。
     */
    private static final String SOURCE_MANUAL = "manual";

    /**
     * 标签字典 Mapper。
     */
    private final TalentTagMapper talentTagMapper;

    /**
     * 人才标签关系 Mapper。
     */
    private final TalentProfileTagMapper talentProfileTagMapper;

    /**
     * 人才主档服务（只用于资源级鉴权）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 人才可见范围领域服务（唯一授权入口）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 业务编号生成器（标签编码唯一事实来源）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    @Override
    public PageResult<TalentTagVo> queryPage(TalentTagQueryBo bo, PageQuery pageQuery) {
        TalentTagQueryBo query = bo == null ? new TalentTagQueryBo() : bo;
        boolean canSeeSensitive = canMaintainSensitiveTag();
        if (!canSeeSensitive && SENSITIVE_YES.equals(query.getSensitiveFlag())) {
            throw new ServiceException("敏感标签仅集团级人才管理员可查看");
        }
        LambdaQueryWrapper<TalentTag> wrapper = new LambdaQueryWrapper<TalentTag>()
            .like(StringUtils.isNotBlank(query.getTagName()), TalentTag::getTagName, query.getTagName())
            .like(StringUtils.isNotBlank(query.getTagCode()), TalentTag::getTagCode, query.getTagCode())
            .eq(StringUtils.isNotBlank(query.getTagCategory()), TalentTag::getTagCategory, query.getTagCategory())
            .eq(StringUtils.isNotBlank(query.getStatus()), TalentTag::getStatus, query.getStatus())
            .orderByAsc(TalentTag::getSortNo)
            .orderByDesc(TalentTag::getCreateTime);
        if (canSeeSensitive) {
            wrapper.eq(StringUtils.isNotBlank(query.getSensitiveFlag()), TalentTag::getSensitiveFlag, query.getSensitiveFlag());
        } else {
            // 敏感标签对普通用户整体隐藏，避免敏感内容被当作可检索标签（设计文档 §7.6.4）
            wrapper.eq(TalentTag::getSensitiveFlag, SENSITIVE_NO);
        }
        if (Boolean.TRUE.equals(query.getOnlyEnabled())) {
            wrapper.eq(TalentTag::getStatus, STATUS_ACTIVE);
        }
        Page<TalentTagVo> page = talentTagMapper.selectVoPage(
            pageQuery == null ? new PageQuery().build() : pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public TalentTagVo getDetail(Long tagId) {
        TalentTag tag = requireTag(tagId);
        requireSensitiveVisible(tag);
        return MapstructUtils.convert(tag, TalentTagVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TalentTagBo bo) {
        if (bo == null) {
            throw new ServiceException("标签入参不能为空");
        }
        validateTagName(bo.getTagName());
        validateCategory(bo.getTagCategory());
        String sensitiveFlag = resolveSensitiveFlag(bo.getSensitiveFlag());
        requireSensitiveWritable(sensitiveFlag);
        TalentTag entity = MapstructUtils.convert(bo, TalentTag.class);
        if (entity == null) {
            entity = new TalentTag();
        }
        // 服务端权威字段：主键与编码不接受前端写入
        entity.setTagId(null);
        entity.setTagCode(businessNoGenerator.nextTagNo());
        entity.setSensitiveFlag(sensitiveFlag);
        entity.setSortNo(bo.getSortNo() == null ? 0 : bo.getSortNo());
        entity.setStatus(StringUtils.isBlank(bo.getStatus()) ? STATUS_ACTIVE : bo.getStatus());
        try {
            talentTagMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.warn("标签编码或名称冲突, tagCode={}", entity.getTagCode());
            throw new ServiceException("标签编码生成冲突，请重试");
        }
        log.info("新增人才标签, tagId={}, tagCode={}, sensitiveFlag={}",
            entity.getTagId(), entity.getTagCode(), entity.getSensitiveFlag());
        return entity.getTagId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TalentTagBo bo) {
        if (bo == null || bo.getTagId() == null) {
            throw new ServiceException("标签ID不能为空");
        }
        TalentTag exist = requireTag(bo.getTagId());
        requireSensitiveVisible(exist);
        validateTagName(bo.getTagName());
        validateCategory(bo.getTagCategory());
        String sensitiveFlag = resolveSensitiveFlag(bo.getSensitiveFlag());
        // 由普通标签改为敏感标签、或修改既有敏感标签，都必须具备集团级权限
        requireSensitiveWritable(sensitiveFlag);
        requireSensitiveWritable(exist.getSensitiveFlag());
        TalentTag update = MapstructUtils.convert(bo, TalentTag.class);
        if (update == null) {
            update = new TalentTag();
        }
        update.setTagId(exist.getTagId());
        // 编码由服务端维护，不允许经更新接口改写
        update.setTagCode(null);
        update.setSensitiveFlag(sensitiveFlag);
        if (talentTagMapper.updateById(update) == 0) {
            throw new ServiceException("标签不存在");
        }
        log.info("更新人才标签, tagId={}, sensitiveFlag={}", exist.getTagId(), sensitiveFlag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] tagIds) {
        if (tagIds == null || tagIds.length == 0) {
            return;
        }
        for (Long tagId : new LinkedHashSet<>(Arrays.asList(tagIds))) {
            if (tagId == null) {
                continue;
            }
            TalentTag tag = requireTag(tagId);
            requireSensitiveVisible(tag);
            requireSensitiveWritable(tag.getSensitiveFlag());
            // 逻辑删除标签字典项；人才标签关系保留但不再可检索（不做物理删除）
            talentTagMapper.deleteById(tagId);
            log.info("逻辑删除人才标签, tagId={}", tagId);
        }
    }

    @Override
    public List<TalentTagVo> listProfileTags(Long talentId) {
        // 资源级鉴权：人才不可见时不返回任何标签
        talentProfileService.requireVisible(talentId);
        List<TalentProfileTag> relations = talentProfileTagMapper.selectList(
            new LambdaQueryWrapper<TalentProfileTag>().eq(TalentProfileTag::getTalentId, talentId));
        if (CollUtil.isEmpty(relations)) {
            return List.of();
        }
        List<Long> tagIds = relations.stream().map(TalentProfileTag::getTagId).filter(Objects::nonNull).distinct().toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        boolean canSeeSensitive = canMaintainSensitiveTag();
        List<TalentTag> tags = talentTagMapper.selectByIds(tagIds);
        List<TalentTagVo> rows = new ArrayList<>(tags.size());
        for (TalentTag tag : tags) {
            if (!canSeeSensitive && SENSITIVE_YES.equals(tag.getSensitiveFlag())) {
                continue;
            }
            TalentTagVo vo = MapstructUtils.convert(tag, TalentTagVo.class);
            if (vo != null) {
                rows.add(vo);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfileTags(Long talentId, TalentProfileTagBo bo) {
        // 资源级鉴权：由 TalentScopeDomainService 判定人才可见性（§11.1）
        talentProfileService.requireVisible(talentId);
        if (bo == null) {
            throw new ServiceException("标签关系入参不能为空");
        }
        String sourceType = StringUtils.isBlank(bo.getSourceType()) ? SOURCE_MANUAL : bo.getSourceType();
        Long[] rawTagIds = bo.getTagIds();
        List<Long> targetTagIds = rawTagIds == null ? List.of()
            : Arrays.stream(rawTagIds).filter(Objects::nonNull).distinct().toList();

        List<TalentTag> tags = targetTagIds.isEmpty() ? List.of() : talentTagMapper.selectByIds(targetTagIds);
        if (tags.size() != targetTagIds.size()) {
            throw new ServiceException("存在无效的标签ID，请刷新标签字典后重试");
        }
        for (TalentTag tag : tags) {
            if (!STATUS_ACTIVE.equals(tag.getStatus())) {
                throw new ServiceException("标签已停用，不能挂到人才档案：" + tag.getTagName());
            }
            // 敏感内容不得自动生成可被普通用户检索的标签（设计文档 §7.6.4）
            if (!SOURCE_MANUAL.equals(sourceType) && SENSITIVE_YES.equals(tag.getSensitiveFlag())) {
                throw new ServiceException("敏感标签不允许由解析或系统自动生成，请人工确认后再挂载");
            }
            if (SENSITIVE_YES.equals(tag.getSensitiveFlag()) && !canMaintainSensitiveTag()) {
                throw new ServiceException("敏感标签仅集团级人才管理员可挂载");
            }
        }

        List<TalentProfileTag> existing = talentProfileTagMapper.selectList(
            new LambdaQueryWrapper<TalentProfileTag>().eq(TalentProfileTag::getTalentId, talentId));
        List<Long> existingTagIds = existing.stream().map(TalentProfileTag::getTagId).filter(Objects::nonNull).toList();

        // 差集：移除不再需要的，补齐新增的（重复挂载按幂等处理）
        List<Long> toRemove = existingTagIds.stream().filter(id -> !targetTagIds.contains(id)).toList();
        if (CollUtil.isNotEmpty(toRemove)) {
            talentProfileTagMapper.delete(new LambdaQueryWrapper<TalentProfileTag>()
                .eq(TalentProfileTag::getTalentId, talentId)
                .in(TalentProfileTag::getTagId, toRemove));
        }
        Long operatorId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        for (Long tagId : targetTagIds) {
            if (existingTagIds.contains(tagId)) {
                continue;
            }
            TalentProfileTag relation = new TalentProfileTag();
            relation.setTalentId(talentId);
            relation.setTagId(tagId);
            relation.setSourceType(sourceType);
            relation.setConfirmedBy(SOURCE_MANUAL.equals(sourceType) ? operatorId : null);
            relation.setConfirmedTime(SOURCE_MANUAL.equals(sourceType) ? now : null);
            try {
                talentProfileTagMapper.insert(relation);
            } catch (DuplicateKeyException e) {
                // 逻辑删除行仍占用 uk_hr_talent_profile_tag，命中唯一键时恢复历史关系
                talentProfileTagMapper.restoreRelation(talentId, tagId, sourceType, operatorId, now);
            }
        }
        log.info("更新人才标签关系, talentId={}, sourceType={}, 目标标签数={}, 移除数={}",
            talentId, sourceType, targetTagIds.size(), toRemove.size());
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 读取标签并要求其存在且未删除。
     *
     * @param tagId 标签ID
     * @return 标签实体
     */
    private TalentTag requireTag(Long tagId) {
        if (tagId == null) {
            throw new ServiceException("标签ID不能为空");
        }
        TalentTag tag = talentTagMapper.selectById(tagId);
        if (tag == null) {
            throw new ServiceException("标签不存在");
        }
        return tag;
    }

    /**
     * 校验标签名称：非空、且不得命中敏感/歧视词库（设计文档 §8.15）。
     *
     * @param tagName 标签名称
     */
    private void validateTagName(String tagName) {
        if (StringUtils.isBlank(tagName)) {
            throw new ServiceException("标签名称不能为空");
        }
        String normalized = tagName.replaceAll("[\\s　_\\-]", "");
        for (String word : SENSITIVE_TAG_WORDS) {
            if (normalized.contains(word)) {
                // 提示中只回显命中的敏感词标签名，不回显任何人才数据
                throw new ServiceException("禁止创建敏感或歧视性标签：标签名称不得包含「" + word + "」等敏感内容");
            }
        }
    }

    /**
     * 校验标签分类必须是 {@code talent_tag_category} 的稳定编码。
     *
     * @param tagCategory 标签分类编码
     */
    private void validateCategory(String tagCategory) {
        if (StringUtils.isBlank(tagCategory) || TalentTagCategoryEnum.find(tagCategory) == null) {
            throw new ServiceException("标签分类不合法，请使用字典 talent_tag_category 的编码");
        }
    }

    /**
     * 解析敏感标记：仅接受 0/1，缺省为 0。
     *
     * @param sensitiveFlag 敏感标记
     * @return 归一化后的敏感标记
     */
    private String resolveSensitiveFlag(String sensitiveFlag) {
        if (StringUtils.isBlank(sensitiveFlag)) {
            return SENSITIVE_NO;
        }
        if (!SENSITIVE_NO.equals(sensitiveFlag) && !SENSITIVE_YES.equals(sensitiveFlag)) {
            throw new ServiceException("敏感标记只能为 0 或 1");
        }
        return sensitiveFlag;
    }

    /**
     * 校验当前用户是否有权操作敏感标签（仅集团级人才管理员与超级管理员）。
     *
     * @param sensitiveFlag 敏感标记
     */
    private void requireSensitiveWritable(String sensitiveFlag) {
        if (SENSITIVE_YES.equals(sensitiveFlag) && !canMaintainSensitiveTag()) {
            throw new ServiceException("敏感标签仅集团级人才管理员可维护，禁止随意创建敏感或歧视性标签");
        }
    }

    /**
     * 校验当前用户是否有权查看敏感标签。
     *
     * @param tag 标签实体
     */
    private void requireSensitiveVisible(TalentTag tag) {
        if (SENSITIVE_YES.equals(tag.getSensitiveFlag()) && !canMaintainSensitiveTag()) {
            throw new ServiceException("敏感标签仅集团级人才管理员可查看");
        }
    }

    /**
     * 当前用户是否具备敏感标签的查看与维护资格。
     *
     * <p>判定完全取自 {@link TalentScopeDomainService}（超级管理员或集团级人才管理员），
     * 本类不解析角色标识（设计文档 §11.1）。</p>
     *
     * @return 是否具备资格
     */
    private boolean canMaintainSensitiveTag() {
        TalentScopeDomainService.ScopeCondition scope = talentScopeDomainService.currentScope();
        return scope.unlimited() || scope.groupLevelAdmin();
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

}
