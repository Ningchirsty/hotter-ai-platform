package org.dromara.aigov.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.bo.AigModelBaseBo;
import org.dromara.aigov.domain.bo.AigModelCreateBo;
import org.dromara.aigov.domain.bo.AigModelGovernanceBo;
import org.dromara.aigov.domain.bo.AigModelProviderBo;
import org.dromara.aigov.domain.bo.AigModelSecretBo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigLifecycleStatusEnum;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.aigov.helper.AigPermissionHelper;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.IAigModelGovernanceService;
import org.dromara.aigov.service.invoker.ModelConnectionTester;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI 模型治理服务实现。
 *
 * <p><b>跨表读取</b>：{@link #list} 以 {@code sai_model_config} 为主表 LEFT JOIN
 * {@code aig_model_governance}（XML 写在 {@code mapper/aigov/AigModelViewMapper.xml}）。</p>
 *
 * <p><b>密钥口径</b>：{@code api_key} 的<b>写</b>只发生在 {@link #createModel} 与
 * {@link #updateModelSecret}，且写入前一律经 {@link AigModelSecretCipher} 加密成
 * snail-ai 可解的 SM4 密文；<b>读</b>只暴露布尔位 {@code keyConfigured}，
 * 任何响应都不返回密钥原值。{@code api_endpoint} 与 {@code secretRef} 仅在当前用户
 * 具备 {@code aig:model:secret} 权限时下发，其余情况统一置空。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigModelGovernanceServiceImpl implements IAigModelGovernanceService {

    /**
     * secretRef 明文特征：常见密钥前缀（命中时给出更贴切的提示）。
     */
    private static final String[] SECRET_PREFIXES = {"sk-", "Bearer ", "AKID", "ghp_"};

    /**
     * secretRef 允许的引用 scheme 白名单。
     *
     * <p>此处由「反向启发式」改为「正向白名单」：原实现是「长度 ≥32 且不含 {@code ://} 才判为明文」，
     * 挡不住 {@code mysecret123} 这类短明文。现在认不出来的一律拒绝。</p>
     *
     * <p><b>大小写不敏感</b>：RFC 3986 规定 scheme 大小写不敏感，且既有数据里存在
     * {@code REF://fixture/local-rule-v1}（本地夹具）。若这里做成大小写敏感，用户编辑该行
     * 治理属性时前端原样回传就会被拒——那是个自造的回归。</p>
     *
     * <p>{@code ref} 一并放行，用于兼容已经登记的 {@code REF://} 值。</p>
     */
    private static final Pattern SECRET_REF_PATTERN =
        Pattern.compile("(?i)^(kms|vault|env|sm|secret|ref)://\\S+$");

    /**
     * 密钥引用非法时的统一提示。
     * <p>冒烟脚本断言文案中必须含「密钥引用」字样，改动时请同步。</p>
     */
    private static final String SECRET_REF_INVALID =
        "secretRef 必须是密钥引用（受支持的 scheme：kms:// vault:// env:// sm:// secret:// ref://，"
            + "如 kms://ai/qwen），禁止填明文密钥";

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

    /**
     * 模型连通性探测器（本地执行者 / snail-ai / OpenAI 兼容端点）。
     */
    private final ModelConnectionTester connectionTester;

    /**
     * 模型密钥加解密工具（SM4/CBC/PKCS5，与 snail-ai 口径一致）。
     */
    private final AigModelSecretCipher secretCipher;

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
        // 4b. 明文密钥：与密钥引用同为凭据，写它同样需要 aig:model:secret。
        //     就地加密——明文只在本方法栈内存活，后续入库/Mapper/日志拿到的都是密文。
        //     encrypt() 在「未启用」或「未配置密钥」时会抛错，不会静默跳过。
        String encryptedApiKey = null;
        if (StringUtils.isNotBlank(bo.getApiKey())) {
            if (!permissionHelper.canViewModelSecret()) {
                throw new ServiceException("无权登记模型密钥（aig:model:secret）");
            }
            encryptedApiKey = secretCipher.encrypt(bo.getApiKey());
        }
        // 覆盖为密文（或 null）：避免明文随 bo 继续流转到拷贝/序列化/异常堆栈里
        bo.setApiKey(encryptedApiKey);
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

        log.info("新增模型完成, modelId={}, modelKey={}, deploymentType={}, dataLevelMax={}, lifecycleStatus={}, keyConfigured={}",
            bo.getId(), bo.getModelKey(), deployment.getCode(), dataLevel.getCode(), lifecycle.getCode(),
            encryptedApiKey != null);
        return bo.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateModelBase(AigModelBaseBo bo) {
        if (bo == null || bo.getModelId() == null) {
            throw new ServiceException("模型ID不能为空");
        }
        if (modelViewMapper.selectModelById(bo.getModelId()) == null) {
            throw new ServiceException("模型不存在：" + bo.getModelId());
        }
        // 供应商可以改，但必须指向真实存在的供应商，否则列表里会出现无归属的孤儿模型
        if (bo.getProviderId() == null || modelConfigMapper.countProvider(bo.getProviderId()) == 0) {
            throw new ServiceException("供应商不存在：" + bo.getProviderId());
        }
        // 标识是路由与审计的引用键，必须全局唯一。
        // 这里必须排除自身：否则「一个字段都不改、直接保存」会被自己判成重复。
        if (modelConfigMapper.countByModelKeyExcluding(bo.getModelKey(), bo.getModelId()) > 0) {
            throw new ServiceException("模型标识已存在：" + bo.getModelKey());
        }
        // api_endpoint 有三态，必须分开处理（XML 里 null 表示「本次不改」）：
        //   · 无 aig:model:secret → 该列对这类账号是脱敏的（读不到），一律忽略，避免误清空；
        //   · 有权限但请求里没带该字段（null）→ 视为「本次不改」，不能顺手清掉；
        //   · 有权限且带了值 → 按给的值写；空串视为「显式清空」。
        // 注意不能用 isBlank 判空：那会把「未提供」也当成「清空」。
        if (!permissionHelper.canViewModelSecret()) {
            bo.setApiEndpoint(null);
        } else if (bo.getApiEndpoint() != null) {
            bo.setApiEndpoint(bo.getApiEndpoint().trim());
        }
        bo.setScope(StringUtils.isBlank(bo.getScope()) ? DEFAULT_SCOPE : bo.getScope());
        bo.setIsDefault(Boolean.TRUE.equals(bo.getIsDefault()));
        bo.setIsEnabled(bo.getIsEnabled() == null || Boolean.TRUE.equals(bo.getIsEnabled()));
        int rows = modelConfigMapper.updateModelBase(bo);
        log.info("编辑模型主数据完成, modelId={}, modelKey={}, providerId={}, modelType={}, enabled={}, rows={}",
            bo.getModelId(), bo.getModelKey(), bo.getProviderId(), bo.getModelType(), bo.getIsEnabled(), rows);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateModelSecret(AigModelSecretBo bo) {
        if (bo == null || bo.getModelId() == null) {
            throw new ServiceException("模型ID不能为空");
        }
        if (modelViewMapper.selectModelById(bo.getModelId()) == null) {
            throw new ServiceException("模型不存在：" + bo.getModelId());
        }
        // 服务层再校验一次：控制器上的 @SaCheckPermission 只覆盖 HTTP 入口，
        // 而本方法是接口公开能力，内部调用同样不得绕过。
        if (!permissionHelper.canViewModelSecret()) {
            throw new ServiceException("无权修改模型密钥（aig:model:secret）");
        }
        if (Boolean.TRUE.equals(bo.getClearKey())) {
            int rows = modelConfigMapper.updateModelApiKey(bo.getModelId(), null);
            log.info("清除模型密钥, modelId={}, rows={}", bo.getModelId(), rows);
            return rows;
        }
        if (StringUtils.isBlank(bo.getApiKey())) {
            throw new ServiceException("密钥不能为空；如需清除已有密钥，请显式选择「清除密钥」");
        }
        // 明文只在此处存活一个局部变量的生存期；加密后立刻丢弃。
        String cipherText = secretCipher.encrypt(bo.getApiKey());
        int rows = modelConfigMapper.updateModelApiKey(bo.getModelId(), cipherText);
        // 日志只记长度，绝不记密钥本身（连掩码形式都不要）
        log.info("写入模型密钥, modelId={}, plainLen={}, cipherLen={}, rows={}",
            bo.getModelId(), bo.getApiKey().length(), cipherText == null ? 0 : cipherText.length(), rows);
        return rows;
    }

    @Override
    public List<AigModelProviderVo> listProviders() {
        return modelConfigMapper.selectProviderOptions();
    }

    @Override
    public List<AigModelProviderVo> listAllProviders() {
        return modelConfigMapper.selectAllProviders();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProvider(AigModelProviderBo bo) {
        if (bo == null) {
            throw new ServiceException("供应商参数不能为空");
        }
        String name = StringUtils.trim(bo.getProviderName());
        String key = StringUtils.trim(bo.getProviderKey()).toLowerCase();
        if (StringUtils.isBlank(name) || StringUtils.isBlank(key)) {
            throw new ServiceException("供应商名称与标识都不能为空");
        }
        // 标识与名称都要求唯一：标识用于机器识别，名称用于人工在下拉里辨认，重名会让人选错。
        if (modelConfigMapper.countByProviderKey(key, null) > 0) {
            throw new ServiceException("供应商标识已存在：" + key);
        }
        if (modelConfigMapper.countByProviderName(name, null) > 0) {
            throw new ServiceException("供应商名称已存在：" + name);
        }
        bo.setId(null);
        bo.setProviderName(name);
        bo.setProviderKey(key);
        bo.setIsEnabled(bo.getIsEnabled() == null || Boolean.TRUE.equals(bo.getIsEnabled()));
        modelConfigMapper.insertProvider(bo);
        if (bo.getId() == null) {
            throw new ServiceException("供应商新增失败：未取回主键");
        }
        log.info("新增模型供应商完成, providerId={}, providerKey={}, enabled={}", bo.getId(), key, bo.getIsEnabled());
        return bo.getId();
    }

    @Override
    public int updateProvider(AigModelProviderBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("供应商ID不能为空");
        }
        if (bo.getId() != null && modelConfigMapper.countProvider(bo.getId()) == 0) {
            throw new ServiceException("供应商不存在：" + bo.getId());
        }
        if (StringUtils.isNotBlank(bo.getProviderName())
            && modelConfigMapper.countByProviderName(StringUtils.trim(bo.getProviderName()), bo.getId()) > 0) {
            throw new ServiceException("供应商名称已存在：" + bo.getProviderName());
        }
        // 标识是模型的归属键，创建后不再允许修改（改标识会让既有模型指向不明）。
        bo.setProviderKey(null);
        if (StringUtils.isNotBlank(bo.getProviderName())) {
            bo.setProviderName(StringUtils.trim(bo.getProviderName()));
        }
        int rows = modelConfigMapper.updateProvider(bo);
        log.info("修改模型供应商完成, providerId={}, rows={}, enabled={}", bo.getId(), rows, bo.getIsEnabled());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigModelTestVo testConnection(Long modelId) {
        if (modelId == null) {
            throw new ServiceException("模型ID不能为空");
        }
        AigModelTestTargetVo target = modelConfigMapper.selectTestTarget(modelId);
        if (target == null) {
            throw new ServiceException("模型不存在：" + modelId);
        }
        AigModelTestVo result = connectionTester.test(target);
        // 把健康状态落回治理表（health_status / health_time 一直存在但此前从未被写入）。
        // 治理记录缺失时不补建：那属于治理属性登记的职责，测试不该顺手造一条治理记录。
        AigModelGovernance governance = modelGovernanceMapper.selectOne(
            new LambdaQueryWrapper<AigModelGovernance>()
                .eq(AigModelGovernance::getModelId, modelId)
                .last("limit 1"));
        if (governance != null) {
            AigModelGovernance update = new AigModelGovernance();
            update.setGovernanceId(governance.getGovernanceId());
            update.setHealthStatus(result.getHealthStatus());
            update.setHealthTime(LocalDateTime.now());
            modelGovernanceMapper.updateById(update);
            result.setCheckedAt(update.getHealthTime());
        } else {
            log.info("模型连通性测试：无治理记录，跳过健康状态写入, modelId={}", modelId);
        }
        return result;
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
     * <p>两道校验：先按常见明文前缀给出明确提示，再用 scheme 白名单收口。
     * 只做前缀黑名单是不够的——{@code mysecret123} 这类短明文既无前缀、长度也不足，
     * 会被原实现放过。</p>
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
                throw new ServiceException(SECRET_REF_INVALID);
            }
        }
        if (!SECRET_REF_PATTERN.matcher(value).matches()) {
            throw new ServiceException(SECRET_REF_INVALID);
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
