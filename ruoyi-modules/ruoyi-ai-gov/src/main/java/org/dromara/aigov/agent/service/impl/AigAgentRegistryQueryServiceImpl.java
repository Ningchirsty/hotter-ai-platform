package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentBinding;
import org.dromara.aigov.agent.domain.AigAgentResourceBinding;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigPackage;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.AigSkill;
import org.dromara.aigov.agent.domain.AigSkillVersion;
import org.dromara.aigov.agent.domain.bo.AigAgentBindingQueryBo;
import org.dromara.aigov.agent.domain.bo.AigAgentQueryBo;
import org.dromara.aigov.agent.domain.bo.AigAgentResourceBindingQueryBo;
import org.dromara.aigov.agent.domain.bo.AigAgentVersionQueryBo;
import org.dromara.aigov.agent.domain.bo.AigPackageQueryBo;
import org.dromara.aigov.agent.domain.bo.AigPackageVersionQueryBo;
import org.dromara.aigov.agent.domain.bo.AigSkillQueryBo;
import org.dromara.aigov.agent.domain.bo.AigSkillVersionQueryBo;
import org.dromara.aigov.agent.domain.vo.AigAgentBindingVo;
import org.dromara.aigov.agent.domain.vo.AigAgentDeclaredResourceVo;
import org.dromara.aigov.agent.domain.vo.AigAgentResourceBindingVo;
import org.dromara.aigov.agent.domain.vo.AigAgentResourceReferenceVo;
import org.dromara.aigov.agent.domain.vo.AigAgentVersionVo;
import org.dromara.aigov.agent.domain.vo.AigAgentVo;
import org.dromara.aigov.agent.domain.vo.AigPackageVersionVo;
import org.dromara.aigov.agent.domain.vo.AigPackageVo;
import org.dromara.aigov.agent.domain.vo.AigSkillVersionVo;
import org.dromara.aigov.agent.domain.vo.AigSkillVo;
import org.dromara.aigov.agent.enums.AigAgentResourceTypeEnum;
import org.dromara.aigov.agent.mapper.AigAgentBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentResourceBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSkillMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.aigov.agent.service.IAigAgentRegistryQueryService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 注册中心只读查询实现（设计 §5、§6、§10.2）。
 *
 * <p>只做查询构造与返回；所有写操作在 {@code AigAgentRegistryServiceImpl} 里，
 * 且只有 {@code advanceRelease} 一个状态写入口。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigAgentRegistryQueryServiceImpl implements IAigAgentRegistryQueryService {

    private final AigAgentMapper agentMapper;

    private final AigAgentVersionMapper agentVersionMapper;

    private final AigSkillMapper skillMapper;

    private final AigSkillVersionMapper skillVersionMapper;

    private final AigPackageMapper packageMapper;

    private final AigPackageVersionMapper packageVersionMapper;

    private final AigAgentBindingMapper bindingMapper;

    private final AigAgentResourceBindingMapper resourceBindingMapper;

    @Override
    public PageResult<AigAgentVo> queryAgentPage(AigAgentQueryBo bo, PageQuery pageQuery) {
        AigAgentQueryBo query = bo == null ? new AigAgentQueryBo() : bo;
        LambdaQueryWrapper<AigAgent> wrapper = new LambdaQueryWrapper<AigAgent>()
            .eq(StringUtils.isNotBlank(query.getAgentCode()), AigAgent::getAgentCode, query.getAgentCode())
            .like(StringUtils.isNotBlank(query.getAgentName()), AigAgent::getAgentName, query.getAgentName())
            .eq(StringUtils.isNotBlank(query.getCategory()), AigAgent::getCategory, query.getCategory())
            .eq(StringUtils.isNotBlank(query.getBuiltin()), AigAgent::getBuiltin, query.getBuiltin())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigAgent::getStatus, query.getStatus())
            .orderByDesc(AigAgent::getCreateTime);
        Page<AigAgentVo> voPage = agentMapper.selectVoPage(pageOf(pageQuery), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public AigAgentVo getAgent(Long agentId) {
        if (agentId == null) {
            throw new ServiceException("Agent ID不能为空");
        }
        AigAgentVo vo = agentMapper.selectVoById(agentId);
        if (vo == null) {
            throw new ServiceException("Agent 不存在：" + agentId);
        }
        return vo;
    }

    @Override
    public PageResult<AigAgentVersionVo> queryAgentVersionPage(AigAgentVersionQueryBo bo,
                                                               PageQuery pageQuery) {
        AigAgentVersionQueryBo query = bo == null ? new AigAgentVersionQueryBo() : bo;
        LambdaQueryWrapper<AigAgentVersion> wrapper = new LambdaQueryWrapper<AigAgentVersion>()
            .eq(query.getAgentId() != null, AigAgentVersion::getAgentId, query.getAgentId())
            .eq(StringUtils.isNotBlank(query.getReleaseStatus()), AigAgentVersion::getReleaseStatus,
                query.getReleaseStatus())
            .eq(StringUtils.isNotBlank(query.getReleaseChannel()), AigAgentVersion::getReleaseChannel,
                query.getReleaseChannel())
            .eq(StringUtils.isNotBlank(query.getProviderCapability()),
                AigAgentVersion::getProviderCapability, query.getProviderCapability())
            .eq(StringUtils.isNotBlank(query.getScenarioCode()), AigAgentVersion::getScenarioCode,
                query.getScenarioCode())
            .like(StringUtils.isNotBlank(query.getVersion()), AigAgentVersion::getVersion,
                query.getVersion())
            .orderByDesc(AigAgentVersion::getCreateTime);
        Page<AigAgentVersionVo> voPage = agentVersionMapper.selectVoPage(pageOf(pageQuery),
            wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public AigAgentVersionVo getAgentVersion(Long agentVersionId) {
        if (agentVersionId == null) {
            throw new ServiceException("Agent 版本ID不能为空");
        }
        AigAgentVersionVo vo = agentVersionMapper.selectVoById(agentVersionId);
        if (vo == null) {
            throw new ServiceException("Agent 版本不存在：" + agentVersionId);
        }
        return vo;
    }

    @Override
    public PageResult<AigSkillVo> querySkillPage(AigSkillQueryBo bo, PageQuery pageQuery) {
        AigSkillQueryBo query = bo == null ? new AigSkillQueryBo() : bo;
        LambdaQueryWrapper<AigSkill> wrapper = new LambdaQueryWrapper<AigSkill>()
            .eq(StringUtils.isNotBlank(query.getSkillCode()), AigSkill::getSkillCode, query.getSkillCode())
            .like(StringUtils.isNotBlank(query.getSkillName()), AigSkill::getSkillName, query.getSkillName())
            .eq(StringUtils.isNotBlank(query.getBuiltin()), AigSkill::getBuiltin, query.getBuiltin())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigSkill::getStatus, query.getStatus())
            .like(StringUtils.isNotBlank(query.getCapabilities()), AigSkill::getCapabilities,
                query.getCapabilities())
            .orderByDesc(AigSkill::getCreateTime);
        Page<AigSkillVo> voPage = skillMapper.selectVoPage(pageOf(pageQuery), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public PageResult<AigSkillVersionVo> querySkillVersionPage(AigSkillVersionQueryBo bo,
                                                               PageQuery pageQuery) {
        AigSkillVersionQueryBo query = bo == null ? new AigSkillVersionQueryBo() : bo;
        LambdaQueryWrapper<AigSkillVersion> wrapper = new LambdaQueryWrapper<AigSkillVersion>()
            .eq(query.getSkillId() != null, AigSkillVersion::getSkillId, query.getSkillId())
            .eq(StringUtils.isNotBlank(query.getReleaseStatus()), AigSkillVersion::getReleaseStatus,
                query.getReleaseStatus())
            .eq(StringUtils.isNotBlank(query.getReleaseChannel()), AigSkillVersion::getReleaseChannel,
                query.getReleaseChannel())
            .eq(StringUtils.isNotBlank(query.getProviderCapability()),
                AigSkillVersion::getProviderCapability, query.getProviderCapability())
            .like(StringUtils.isNotBlank(query.getVersion()), AigSkillVersion::getVersion,
                query.getVersion())
            .orderByDesc(AigSkillVersion::getCreateTime);
        Page<AigSkillVersionVo> voPage = skillVersionMapper.selectVoPage(pageOf(pageQuery),
            wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public PageResult<AigPackageVo> queryPackagePage(AigPackageQueryBo bo, PageQuery pageQuery) {
        AigPackageQueryBo query = bo == null ? new AigPackageQueryBo() : bo;
        LambdaQueryWrapper<AigPackage> wrapper = new LambdaQueryWrapper<AigPackage>()
            .eq(StringUtils.isNotBlank(query.getPackageCode()), AigPackage::getPackageCode,
                query.getPackageCode())
            .like(StringUtils.isNotBlank(query.getPackageName()), AigPackage::getPackageName,
                query.getPackageName())
            .eq(StringUtils.isNotBlank(query.getPackageType()), AigPackage::getPackageType,
                query.getPackageType())
            .like(StringUtils.isNotBlank(query.getPublisher()), AigPackage::getPublisher,
                query.getPublisher())
            .eq(StringUtils.isNotBlank(query.getSourceType()), AigPackage::getSourceType,
                query.getSourceType())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigPackage::getStatus, query.getStatus())
            .orderByDesc(AigPackage::getCreateTime);
        Page<AigPackageVo> voPage = packageMapper.selectVoPage(pageOf(pageQuery), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public AigPackageVo getPackage(Long packageId) {
        if (packageId == null) {
            throw new ServiceException("Package ID不能为空");
        }
        AigPackageVo vo = packageMapper.selectVoById(packageId);
        if (vo == null) {
            throw new ServiceException("Package 不存在：" + packageId);
        }
        return vo;
    }

    @Override
    public PageResult<AigPackageVersionVo> queryPackageVersionPage(AigPackageVersionQueryBo bo,
                                                                   PageQuery pageQuery) {
        AigPackageVersionQueryBo query = bo == null ? new AigPackageVersionQueryBo() : bo;
        LambdaQueryWrapper<AigPackageVersion> wrapper = new LambdaQueryWrapper<AigPackageVersion>()
            .eq(query.getPackageId() != null, AigPackageVersion::getPackageId, query.getPackageId())
            .eq(StringUtils.isNotBlank(query.getReleaseStatus()), AigPackageVersion::getReleaseStatus,
                query.getReleaseStatus())
            .eq(StringUtils.isNotBlank(query.getReleaseChannel()),
                AigPackageVersion::getReleaseChannel, query.getReleaseChannel())
            .eq(StringUtils.isNotBlank(query.getScanResult()), AigPackageVersion::getScanResult,
                query.getScanResult())
            .like(StringUtils.isNotBlank(query.getVersion()), AigPackageVersion::getVersion,
                query.getVersion())
            .orderByDesc(AigPackageVersion::getCreateTime);
        Page<AigPackageVersionVo> voPage = packageVersionMapper.selectVoPage(
            pageOf(pageQuery), wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public AigPackageVersionVo getPackageVersion(Long packageVersionId) {
        if (packageVersionId == null) {
            throw new ServiceException("Package 版本ID不能为空");
        }
        AigPackageVersionVo vo = packageVersionMapper.selectVoById(packageVersionId);
        if (vo == null) {
            throw new ServiceException("Package 版本不存在：" + packageVersionId);
        }
        return vo;
    }

    @Override
    public PageResult<AigAgentBindingVo> queryBindingPage(AigAgentBindingQueryBo bo,
                                                          PageQuery pageQuery) {
        AigAgentBindingQueryBo query = bo == null ? new AigAgentBindingQueryBo() : bo;
        LambdaQueryWrapper<AigAgentBinding> wrapper = new LambdaQueryWrapper<AigAgentBinding>()
            .eq(query.getAgentVersionId() != null, AigAgentBinding::getAgentVersionId,
                query.getAgentVersionId())
            .eq(query.getCompanyId() != null, AigAgentBinding::getCompanyId, query.getCompanyId())
            .eq(query.getBrandId() != null, AigAgentBinding::getBrandId, query.getBrandId())
            .eq(StringUtils.isNotBlank(query.getScenarioCode()), AigAgentBinding::getScenarioCode,
                query.getScenarioCode())
            .eq(StringUtils.isNotBlank(query.getReleaseChannel()),
                AigAgentBinding::getReleaseChannel, query.getReleaseChannel())
            .eq(StringUtils.isNotBlank(query.getEnabled()), AigAgentBinding::getEnabled,
                query.getEnabled())
            .orderByDesc(AigAgentBinding::getCreateTime);
        Page<AigAgentBindingVo> voPage = bindingMapper.selectVoPage(pageOf(pageQuery),
            wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    /**
     * 分页参数兜底（调用方漏传时分页不能用 null）。
     *
     * @param pageQuery 分页参数
     * @return 分页参数（非空）
     */
    private static <T> Page<T> pageOf(PageQuery pageQuery) {
        return (pageQuery == null ? new PageQuery() : pageQuery).build();
    }

    @Override
    public PageResult<AigAgentResourceBindingVo> queryResourceBindingPage(
        AigAgentResourceBindingQueryBo bo, PageQuery pageQuery) {
        AigAgentResourceBindingQueryBo query =
            bo == null ? new AigAgentResourceBindingQueryBo() : bo;
        LambdaQueryWrapper<AigAgentResourceBinding> wrapper =
            new LambdaQueryWrapper<AigAgentResourceBinding>()
                .eq(query.getAgentVersionId() != null, AigAgentResourceBinding::getAgentVersionId,
                    query.getAgentVersionId())
                .eq(StringUtils.isNotBlank(query.getResourceType()),
                    AigAgentResourceBinding::getResourceType, query.getResourceType())
                .eq(StringUtils.isNotBlank(query.getResourceCode()),
                    AigAgentResourceBinding::getResourceCode, query.getResourceCode())
                .eq(query.getCompanyId() != null, AigAgentResourceBinding::getCompanyId,
                    query.getCompanyId())
                .eq(query.getBrandId() != null, AigAgentResourceBinding::getBrandId, query.getBrandId())
                .eq(StringUtils.isNotBlank(query.getEnabled()), AigAgentResourceBinding::getEnabled,
                    query.getEnabled())
                .orderByDesc(AigAgentResourceBinding::getCreateTime);
        Page<AigAgentResourceBindingVo> voPage = resourceBindingMapper.selectVoPage(pageOf(pageQuery),
            wrapper);
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public List<AigAgentResourceReferenceVo> listResourceReferences(String resourceType,
                                                                   String resourceCode) {
        if (StringUtils.isBlank(resourceType) || StringUtils.isBlank(resourceCode)) {
            // 不按空条件查出"全库绑定"——那既慢又会被当成"这份资源被所有人引用"
            return List.of();
        }
        List<AigAgentResourceBinding> bindings = resourceBindingMapper.selectList(
            new LambdaQueryWrapper<AigAgentResourceBinding>()
                .eq(AigAgentResourceBinding::getResourceType, resourceType.trim())
                .eq(AigAgentResourceBinding::getResourceCode, resourceCode.trim())
                .orderByDesc(AigAgentResourceBinding::getCreateTime));
        if (bindings.isEmpty()) {
            return List.of();
        }
        // 批量取版本与 Agent：逐个查版本会变成 N+1，而这一屏本来就是要"看清楚影响面"的
        Map<Long, AigAgentVersion> versionById = loadVersions(bindings);
        Map<Long, AigAgent> agentById = loadAgents(versionById.values());

        List<AigAgentResourceReferenceVo> result = new ArrayList<>();
        for (AigAgentResourceBinding binding : bindings) {
            AigAgentVersion version = versionById.get(binding.getAgentVersionId());
            AigAgent agent = version == null ? null : agentById.get(version.getAgentId());
            AigAgentResourceReferenceVo vo = new AigAgentResourceReferenceVo();
            vo.setBindingId(binding.getBindingId());
            vo.setAgentVersionId(binding.getAgentVersionId());
            vo.setAgentId(version == null ? null : version.getAgentId());
            // 版本/Agent 已被删除时留空而不是编一个名字：留空才能看出"这条引用指着的东西不在了"
            vo.setAgentCode(agent == null ? null : agent.getAgentCode());
            vo.setAgentName(agent == null ? null : agent.getAgentName());
            vo.setAgentVersion(version == null ? null : version.getVersion());
            vo.setReleaseStatus(version == null ? null : version.getReleaseStatus());
            vo.setEnabled(binding.getEnabled());
            vo.setCompanyId(binding.getCompanyId());
            vo.setBrandId(binding.getBrandId());
            vo.setScenarioCode(binding.getScenarioCode());
            vo.setRoleScope(binding.getRoleScope());
            vo.setCreateTime(binding.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    /**
     * 批量取绑定指向的 Agent 版本。
     *
     * @param bindings 绑定行
     * @return 版本ID → 版本
     */
    private Map<Long, AigAgentVersion> loadVersions(List<AigAgentResourceBinding> bindings) {
        Set<Long> versionIds = new LinkedHashSet<>();
        for (AigAgentResourceBinding binding : bindings) {
            if (binding.getAgentVersionId() != null) {
                versionIds.add(binding.getAgentVersionId());
            }
        }
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AigAgentVersion> byId = new LinkedHashMap<>();
        for (AigAgentVersion version : agentVersionMapper.selectByIds(versionIds)) {
            byId.put(version.getAgentVersionId(), version);
        }
        return byId;
    }

    /**
     * 批量取 Agent。
     *
     * @param versions 版本集合
     * @return AgentID → Agent
     */
    private Map<Long, AigAgent> loadAgents(Collection<AigAgentVersion> versions) {
        Set<Long> agentIds = new LinkedHashSet<>();
        for (AigAgentVersion version : versions) {
            if (version.getAgentId() != null) {
                agentIds.add(version.getAgentId());
            }
        }
        if (agentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AigAgent> byId = new LinkedHashMap<>();
        for (AigAgent agent : agentMapper.selectByIds(agentIds)) {
            byId.put(agent.getAgentId(), agent);
        }
        return byId;
    }

    @Override
    public AigAgentDeclaredResourceVo declaredResources(Long agentVersionId) {
        if (agentVersionId == null) {
            throw new ServiceException("Agent 版本ID不能为空");
        }
        AigAgentVersion version = agentVersionMapper.selectById(agentVersionId);
        if (version == null) {
            throw new ServiceException("Agent 版本不存在：" + agentVersionId);
        }
        // 已启用的绑定编码：只算 enabled=Y 的（停用的绑定不该把声明项标成"已绑"，
        // 否则页面会劝人不要重建一条其实已经不生效的绑定）
        Set<String> boundTools = boundCodes(agentVersionId, AigAgentResourceTypeEnum.TOOL);
        Set<String> boundKnowledges = boundCodes(agentVersionId, AigAgentResourceTypeEnum.KNOWLEDGE);

        AigAgentDeclaredResourceVo vo = new AigAgentDeclaredResourceVo();
        vo.setAgentVersionId(agentVersionId);
        vo.setVersion(version.getVersion());
        vo.setAllowedTools(items(splitCodes(version.getAllowedTools()), boundTools));
        vo.setForbiddenTools(splitCodes(version.getForbiddenTools()));
        vo.setKnowledgeScopes(items(splitCodes(version.getKnowledgeScopeJson()), boundKnowledges));
        return vo;
    }

    /**
     * 取某版本某类型「启用中」的绑定编码集合。
     *
     * @param agentVersionId 版本ID
     * @param type           资源类型
     * @return 编码集合
     */
    private Set<String> boundCodes(Long agentVersionId, AigAgentResourceTypeEnum type) {
        List<AigAgentResourceBinding> rows = resourceBindingMapper.selectList(
            new LambdaQueryWrapper<AigAgentResourceBinding>()
                .eq(AigAgentResourceBinding::getAgentVersionId, agentVersionId)
                .eq(AigAgentResourceBinding::getResourceType, type.getCode())
                .eq(AigAgentResourceBinding::getEnabled, "Y"));
        Set<String> codes = new LinkedHashSet<>();
        for (AigAgentResourceBinding row : rows) {
            codes.add(row.getResourceCode());
        }
        return codes;
    }

    /**
     * 声明编码 → 带"是否已绑定"标记的项。
     *
     * @param codes 声明编码
     * @param bound 已绑定编码
     * @return 项清单
     */
    private static List<AigAgentDeclaredResourceVo.Item> items(List<String> codes, Set<String> bound) {
        List<AigAgentDeclaredResourceVo.Item> items = new ArrayList<>(codes.size());
        for (String code : codes) {
            items.add(new AigAgentDeclaredResourceVo.Item(code, bound.contains(code)));
        }
        return items;
    }

    /**
     * 把逗号分隔的编码串拆成清单（去空白、丢空项）。
     *
     * @param csv 逗号分隔串
     * @return 编码清单
     */
    private static List<String> splitCodes(String csv) {
        if (StringUtils.isBlank(csv)) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }
        return codes;
    }

}