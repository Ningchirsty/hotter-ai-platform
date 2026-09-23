package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitStandardBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitStandardQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitStandard;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitStandardVo;
import org.dromara.hrtalent.mapper.RecruitStandardMapper;
import org.dromara.hrtalent.service.recruitment.IRecruitStandardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 招聘期限标准服务实现。
 *
 * <p>状态取值只认 {@code active}/{@code inactive}；生效期与失效日必须成序。
 * 这两条都在服务层拦住，避免把「写错的编码/倒挂的日期」留给下游去猜。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitStandardServiceImpl implements IRecruitStandardService {

    /**
     * 生效
     */
    private static final String STATUS_ACTIVE = "active";

    /**
     * 停用
     */
    private static final String STATUS_INACTIVE = "inactive";

    /**
     * 标准 Mapper
     */
    private final RecruitStandardMapper standardMapper;

    @Override
    public PageResult<RecruitStandardVo> queryPage(RecruitStandardQueryBo bo, PageQuery pageQuery) {
        RecruitStandardQueryBo query = bo == null ? new RecruitStandardQueryBo() : bo;
        LambdaQueryWrapper<RecruitStandard> wrapper = new LambdaQueryWrapper<RecruitStandard>()
            .like(StringUtils.isNotBlank(query.getJobName()), RecruitStandard::getJobName, query.getJobName())
            .eq(query.getCompanyDeptId() != null, RecruitStandard::getCompanyDeptId, query.getCompanyDeptId())
            .eq(StringUtils.isNotBlank(query.getStatus()), RecruitStandard::getStatus, query.getStatus())
            .isNull(Boolean.TRUE.equals(query.getGroupOnly()), RecruitStandard::getCompanyDeptId)
            .orderByAsc(RecruitStandard::getJobName)
            .orderByDesc(RecruitStandard::getEffectiveDate);
        var page = standardMapper.selectVoPage(pageQuery.build(), wrapper);
        for (RecruitStandardVo vo : page.getRecords()) {
            fillDerived(vo);
        }
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public RecruitStandardVo getDetail(Long standardId) {
        RecruitStandardVo vo = standardMapper.selectVoById(load(standardId).getStandardId());
        if (vo == null) {
            throw new ServiceException("招聘期限标准不存在");
        }
        fillDerived(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RecruitStandardBo bo) {
        String jobName = normalizeJobName(bo.getJobName());
        checkDates(bo);
        String status = normalizeStatus(bo.getStatus());
        // 业务键重复时拒绝并指路：这类数据一旦有两条，下游按哪个算就说不清了
        RecruitStandard exist = findByKey(jobName, bo.getCompanyDeptId(), bo.getEffectiveDate());
        if (exist != null) {
            throw new ServiceException("同一岗位、同一公司、同一生效日期已存在标准（天数 "
                + exist.getStandardDays() + " 天），请直接编辑该条");
        }
        RecruitStandard entity = new RecruitStandard();
        entity.setJobName(jobName);
        entity.setCompanyDeptId(bo.getCompanyDeptId());
        entity.setCompanyName(bo.getCompanyName());
        entity.setStandardDays(bo.getStandardDays());
        entity.setEffectiveDate(bo.getEffectiveDate());
        entity.setExpiryDate(bo.getExpiryDate());
        entity.setStatus(status);
        entity.setRemark(bo.getRemark());
        standardMapper.insert(entity);
        log.info("新增招聘期限标准, standardId={}, jobName={}, companyDeptId={}, days={}",
            entity.getStandardId(), jobName, bo.getCompanyDeptId(), bo.getStandardDays());
        return entity.getStandardId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RecruitStandardBo bo) {
        RecruitStandard exist = load(bo.getStandardId());
        checkDates(bo);
        String jobName = StringUtils.isNotBlank(bo.getJobName()) ? normalizeJobName(bo.getJobName()) : exist.getJobName();
        LocalDate effective = bo.getEffectiveDate() == null ? exist.getEffectiveDate() : bo.getEffectiveDate();
        Long company = bo.getCompanyDeptId() == null ? exist.getCompanyDeptId() : bo.getCompanyDeptId();
        // 改动后的业务键若与另一条相同，同样要拦住
        RecruitStandard conflict = findByKey(jobName, company, effective);
        if (conflict != null && !conflict.getStandardId().equals(exist.getStandardId())) {
            throw new ServiceException("同一岗位、同一公司、同一生效日期已存在标准（天数 "
                + conflict.getStandardDays() + " 天），请改生效日期或编辑那一条");
        }
        standardMapper.update(null, new LambdaUpdateWrapper<RecruitStandard>()
            .eq(RecruitStandard::getStandardId, exist.getStandardId())
            .set(RecruitStandard::getJobName, jobName)
            .set(RecruitStandard::getCompanyDeptId, company)
            .set(RecruitStandard::getCompanyName, bo.getCompanyName())
            .set(RecruitStandard::getStandardDays, bo.getStandardDays())
            .set(RecruitStandard::getEffectiveDate, effective)
            .set(RecruitStandard::getExpiryDate, bo.getExpiryDate())
            .set(RecruitStandard::getStatus, normalizeStatus(bo.getStatus()))
            .set(RecruitStandard::getRemark, bo.getRemark()));
        log.info("修改招聘期限标准, standardId={}", exist.getStandardId());
    }

    @Override
    public void remove(Long standardId) {
        load(standardId);
        standardMapper.deleteById(standardId);
    }

    @Override
    public RecruitStandard findByKey(String jobName, Long companyDeptId, LocalDate effectiveDate) {
        if (StringUtils.isBlank(jobName)) {
            return null;
        }
        LambdaQueryWrapper<RecruitStandard> wrapper = new LambdaQueryWrapper<RecruitStandard>()
            .eq(RecruitStandard::getJobName, jobName.trim());
        // companyDeptId 可空，null 表示「集团通用」。这里必须走 IS NULL：
        // 写成 eq(..., null) 生成的是 `company_dept_id = null`，SQL 里恒为假，
        // 后果是「集团通用标准永远匹配不到自己」——重复导入会一条条插成重复数据，
        // 手工新增也拦不住重复。生效日期同样是可空列，处理方式一致。
        if (companyDeptId == null) {
            wrapper.isNull(RecruitStandard::getCompanyDeptId);
        } else {
            wrapper.eq(RecruitStandard::getCompanyDeptId, companyDeptId);
        }
        if (effectiveDate == null) {
            wrapper.isNull(RecruitStandard::getEffectiveDate);
        } else {
            wrapper.eq(RecruitStandard::getEffectiveDate, effectiveDate);
        }
        return standardMapper.selectOne(wrapper.last("limit 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upsertByImport(RecruitStandardBo bo) {
        String jobName = normalizeJobName(bo.getJobName());
        checkDates(bo);
        String status = normalizeStatus(bo.getStatus());
        RecruitStandard exist = findByKey(jobName, bo.getCompanyDeptId(), bo.getEffectiveDate());
        if (exist == null) {
            RecruitStandard entity = new RecruitStandard();
            entity.setJobName(jobName);
            entity.setCompanyDeptId(bo.getCompanyDeptId());
            entity.setCompanyName(bo.getCompanyName());
            entity.setStandardDays(bo.getStandardDays());
            entity.setEffectiveDate(bo.getEffectiveDate());
            entity.setExpiryDate(bo.getExpiryDate());
            entity.setStatus(status);
            entity.setRemark(bo.getRemark());
            standardMapper.insert(entity);
            return entity.getStandardId();
        }
        // 命中即覆盖：导入常用于「同一批标准的修订版」，报错让人一条条改反而更糟。
        // 覆盖这件事由导入预检以 OVERWRITE_EXIST 提示的形式提前告知用户
        // （见 RecruitImportServiceImpl#parseStandardRow），确认之前用户看得到。
        standardMapper.update(null, new LambdaUpdateWrapper<RecruitStandard>()
            .eq(RecruitStandard::getStandardId, exist.getStandardId())
            .set(RecruitStandard::getCompanyName, bo.getCompanyName())
            .set(RecruitStandard::getStandardDays, bo.getStandardDays())
            .set(RecruitStandard::getExpiryDate, bo.getExpiryDate())
            .set(RecruitStandard::getStatus, status)
            .set(RecruitStandard::getRemark, bo.getRemark()));
        log.info("导入覆盖招聘期限标准, standardId={}, jobName={}, days={}",
            exist.getStandardId(), jobName, bo.getStandardDays());
        return exist.getStandardId();
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 加载标准。
     *
     * @param standardId 标准ID
     * @return 实体
     */
    private RecruitStandard load(Long standardId) {
        if (standardId == null) {
            throw new ServiceException("标准ID不能为空");
        }
        RecruitStandard entity = standardMapper.selectById(standardId);
        if (entity == null) {
            throw new ServiceException("招聘期限标准不存在");
        }
        return entity;
    }

    /**
     * 归一岗位名称（去空白）。
     *
     * @param jobName 原始岗位名称
     * @return 归一后的名称
     */
    private String normalizeJobName(String jobName) {
        if (StringUtils.isBlank(jobName)) {
            throw new ServiceException("岗位名称不能为空");
        }
        return jobName.trim();
    }

    /**
     * 归一状态：空取 active，非法值拒绝。
     *
     * @param status 原始状态
     * @return 归一后的状态
     */
    private String normalizeStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return STATUS_ACTIVE;
        }
        String value = status.trim().toLowerCase();
        if (!STATUS_ACTIVE.equals(value) && !STATUS_INACTIVE.equals(value)) {
            throw new ServiceException("状态只能为 active 或 inactive，实际：" + status);
        }
        return value;
    }

    /**
     * 校验生效期顺序。
     *
     * @param bo 入参
     */
    private void checkDates(RecruitStandardBo bo) {
        if (bo.getEffectiveDate() != null && bo.getExpiryDate() != null
            && bo.getExpiryDate().isBefore(bo.getEffectiveDate())) {
            throw new ServiceException("失效日期不能早于生效日期");
        }
    }

    /**
     * 回填派生字段（是否集团通用、状态标签）。
     *
     * @param vo 视图对象
     */
    private void fillDerived(RecruitStandardVo vo) {
        if (vo == null) {
            return;
        }
        vo.setGroupWide(vo.getCompanyDeptId() == null);
        vo.setStatusLabel(STATUS_ACTIVE.equals(vo.getStatus()) ? "生效" : "停用");
    }

}
