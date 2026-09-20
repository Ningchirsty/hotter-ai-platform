package org.dromara.aigov.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.bo.AigModelCreateBo;
import org.dromara.aigov.domain.bo.AigModelGovernanceBo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigLifecycleStatusEnum;
import org.dromara.aigov.helper.AigPermissionHelper;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.IAigModelGovernanceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 模型治理服务实现。
 *
 * <p><b>跨表读取</b>：{@link #list} 以 {@code sai_model_config} 为主表 LEFT JOIN
 * {@code aig_model_governance}（XML 写在 {@code mapper/aigov/AigModelViewMapper.xml}），
 * <b>只读</b>，不修改任何 {@code sai_*} 表。</p>
 *
 * <p><b>密钥口径</b>：{@code api_key} 任何语句都不查询、任何响应都不返回；
 * {@code api_endpoint} 与 {@code secretRef} 仅在当前用户具备 {@code aig:model:secret}
 * 权限时下发，其余情况统一置空。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigModelGovernanceServiceImpl implements IAigModelGovernanceService {

    /**
     * secretRef 明文特征：常见密钥前缀。
     */
    private static final String[] SECRET_PREFIXES = {"sk-", "Bearer ", "AKID", "ghp_"};

    /**
     * secretRef 最小可疑长度：过长且无 scheme 视为明文密钥。
     */
    private static final int SECRET_SUSPECT_LENGTH = 32;

    /**
     * 作用域缺省值：与 {@code sai_model_config.scope} 的库默认值保持一致。
     */
    private static final String DEFAULT_SCOPE = "GLOBAL";

    /**
     * 模型主数据只读视图 Mapper。
     */
    private final AigModelViewMapper modelViewMapper;

    /**
     * 模型主数据写入 Mapper（仅新增模型时使用）。
     */
    private final AigModelConfigMapper modelConfigMapper;

    /**
     * 模型治理属性 Mapper。
     */
    private final AigModelGovernanceMapper modelGovernanceMapper;

    /**
     * 权限工具（aig:model:secret）。
     */
    private final AigPermissionHelper permissionHelper;

    @Override
    public PageResult<AigModelVo> list(AigModelGovernanceBo bo, PageQuery pageQuery) {
        AigModelGovernanceBo query = bo == null ? new AigModelGovernanceBo() : bo;
        Page<AigModelVo> page = pageQuery.build();
        IPage<AigModelVo> voPage = modelViewMapper.selectModelPage(page, query);
        List<AigModelVo> rows = voPage.getRecords();
        maskSecrets(rows);
        return PageResult.build(rows, voPage.getTotal());
    }

    @Override
    public AigModelVo getDetail(Long modelId) {
        if (modelId == null) {
            throw new ServiceException("模型ID不能为空");
        }
        AigModelVo vo = modelViewMapper.selectModelById(modelId);
        if (vo == null) {
            throw new ServiceException("模型不存在：" + modelId);
        }
        maskSecrets(List.of(vo));
        return vo;
    }

    @Override
    public Long saveGovernance(AigModelGovernanceBo bo) {
        if (bo.getModelId() == null) {
            throw new ServiceException("模型ID不能为空");
        }
        if (modelViewMapper.selectModelById(bo.getModelId()) == null) {
            throw new ServiceException("模型不存在：" + bo.getModelId());
        }
        // 写侧授权校验：读得到才写得了。仅有 aig:model:edit 而无 aig:model:secret 的账号
        // 读不到 secretRef，就不得通过本接口改写密钥引用（否则可把引用指向自控 KMS 或他人凭据）。
        // 注意：前端「无权限时不下发 secretRef」不构成保障，直接 curl 必须同样被拒。
        if (StringUtils.isNotBlank(bo.getSecretRef()) && !permissionHelper.canViewModelSecret()) {
            throw new ServiceException("无权修改密钥引用（aig:model:secret）");
        }
        checkSecretRef(bo.getSecretRef());
        AigModelGovernance existing = findExisting(bo);
        AigModelGovernance entity = BeanUtil.copyProperties(bo, AigModelGovernance.class);
        if (existing == null) {
            entity.setGovernanceId(null);
            modelGovernanceMapper.insert(entity);
            return entity.getGovernanceId();
        }
        entity.setGovernanceId(existing.getGovernanceId());
        entity.setModelId(existing.getModelId());
        // secretRef 等字段为 null 时表示「不更新」：依赖 MyBatis-Plus 全局 updateStrategy=NOT_NULL，
        // 空字段不会进入 UPDATE 语句（因此无权限的前端不下发 secretRef 也不会清空已有引用）。
        // ⚠️ 若后续把全局 updateStrategy 改为 IGNORED/ALWAYS，这里必须改为显式忽略 null，否则会静默清空。
        modelGovernanceMapper.updateById(entity);
        return existing.getGovernanceId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModel(AigModelCreateBo bo) {
        if (bo == null) {
            throw new ServiceException("新增模型参数不能为空");
        }
        // 1. 枚举合法性。
        //    这一步不是形式主义：路由引擎用 find() 解析这些值，写错的取值不会报错，
        //    只会让模型在「步骤4 候选可用性」被静默排除，排查成本极高，故在建的时候就拦住。
        AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(bo.getDeploymentType());
        if (deployment == null) {
            throw new ServiceException("部署类型非法：" + bo.getDeploymentType());
        }
        AigDataLevelEnum dataLevel = AigDataLevelEnum.find(bo.getDataLevelMax());
        if (dataLevel == null) {
            throw new ServiceException("最高数据等级非法：" + bo.getDataLevelMax());
        }
        AigLifecycleStatusEnum lifecycle = AigLifecycleStatusEnum.find(bo.getLifecycleStatus());
        if (lifecycle == null) {
            throw new ServiceException("生命周期状态非法：" + bo.getLifecycleStatus());
        }
        // 2. 模型标识唯一（路由与审计按 model_key 引用模型，重名会让审计无法区分）
        if (modelConfigMapper.countByModelKey(bo.getModelKey()) > 0) {
            throw new ServiceException("模型标识已存在：" + bo.getModelKey());
        }
        // 3. 供应商必须存在（否则列表页会出现无供应商归属的孤儿记录）
        if (modelConfigMapper.countProvider(bo.getProviderId()) == 0) {
            throw new ServiceException("供应商不存在：" + bo.getProviderId());
        }
        // 4. 密钥引用与 saveGovernance 同一口径：有值需 aig:model:secret，且禁止明文
        if (StringUtils.isNotBlank(bo.getSecretRef())) {
            if (!permissionHelper.canViewModelSecret()) {
                throw new ServiceException("无权登记密钥引用（aig:model:secret）");
            }
            checkSecretRef(bo.getSecretRef());
        }
        // 5. 归一默认值。这几个列可空，显式传 null 会覆盖掉库里的默认值，故在此补全。
        bo.setScope(StringUtils.isBlank(bo.getScope()) ? DEFAULT_SCOPE : bo.getScope());
        bo.setIsDefault(Boolean.TRUE.equals(bo.getIsDefault()));
        bo.setIsEnabled(bo.getIsEnabled() == null || Boolean.TRUE.equals(bo.getIsEnabled()));

        // 6. 主数据入库（自增主键回填到 bo.id）
        modelConfigMapper.insertModel(bo);
        if (bo.getId() == null) {
            throw new ServiceException("模型新增失败：未取回主键");
        }

        // 7. 同一事务内写入首份治理属性，避免出现「有模型、无治理属性」的孤儿模型
        AigModelGovernance entity = new AigModelGovernance();
        entity.setModelId(bo.getId());
        entity.setDeploymentType(deployment.getCode());
        entity.setDataLevelMax(dataLevel.getCode());
        entity.setLifecycleStatus(lifecycle.getCode());
        entity.setSecretRef(bo.getSecretRef());
        entity.setCostLimit(bo.getCostLimit());
        entity.setOwnerTech(bo.getOwnerTech());
        entity.setOwnerBiz(bo.getOwnerBiz());
        entity.setOwnerSecurity(bo.getOwnerSecurity());
        entity.setValidFrom(bo.getValidFrom());
        entity.setValidTo(bo.getValidTo());
        entity.setRemark(bo.getRemark());
        entity.setStatus("0");
        modelGovernanceMapper.insert(entity);

        log.info("新增模型完成, modelId={}, modelKey={}, deploymentType={}, dataLevelMax={}, lifecycleStatus={}",
            bo.getId(), bo.getModelKey(), deployment.getCode(), dataLevel.getCode(), lifecycle.getCode());
        return bo.getId();
    }

    @Override
    public List<AigModelProviderVo> listProviders() {
        return modelConfigMapper.selectProviderOptions();
    }

    /**
     * 查找已存在的治理记录（优先 governanceId，其次 modelId）。
     *
     * @param bo 治理参数
     * @return 已存在记录，不存在返回 null
     */
    private AigModelGovernance findExisting(AigModelGovernanceBo bo) {
        if (bo.getGovernanceId() != null) {
            AigModelGovernance byId = modelGovernanceMapper.selectById(bo.getGovernanceId());
            if (byId != null) {
                return byId;
            }
        }
        List<AigModelGovernance> list = modelGovernanceMapper.selectList(new LambdaQueryWrapper<AigModelGovernance>()
            .eq(AigModelGovernance::getModelId, bo.getModelId()));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    /**
     * 密钥引用必须为「引用」，禁止明文密钥。
     *
     * @param secretRef 密钥引用
     */
    private void checkSecretRef(String secretRef) {
        if (StringUtils.isBlank(secretRef)) {
            return;
        }
        String value = secretRef.trim();
        for (String prefix : SECRET_PREFIXES) {
            if (value.startsWith(prefix)) {
                throw new ServiceException("secretRef 必须是密钥引用（如 kms://ai/qwen），禁止明文密钥");
            }
        }
        if (value.length() >= SECRET_SUSPECT_LENGTH && !value.contains("://")) {
            throw new ServiceException("secretRef 必须是密钥引用（如 kms://ai/qwen），禁止明文密钥");
        }
    }

    /**
     * 无 {@code aig:model:secret} 权限时清空端点与密钥引用。
     *
     * @param rows 模型视图列表
     */
    private void maskSecrets(List<AigModelVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        if (permissionHelper.canViewModelSecret()) {
            return;
        }
        for (AigModelVo row : rows) {
            row.setApiEndpoint(null);
            row.setSecretRef(null);
        }
    }

}
