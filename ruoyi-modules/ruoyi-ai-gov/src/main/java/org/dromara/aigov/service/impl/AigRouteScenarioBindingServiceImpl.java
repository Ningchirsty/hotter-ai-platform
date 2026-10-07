package org.dromara.aigov.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.aigov.domain.bo.AigRouteScenarioBindingBo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;
import org.dromara.aigov.domain.vo.AigRouteScenarioBindingVo;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.aigov.mapper.AigRouteScenarioBindingMapper;
import org.dromara.aigov.service.IAigRouteScenarioBindingService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场景强制绑定服务实现。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigRouteScenarioBindingServiceImpl implements IAigRouteScenarioBindingService {

    /**
     * 状态：正常。
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 场景强制绑定 Mapper。
     */
    private final AigRouteScenarioBindingMapper bindingMapper;

    /**
     * 能力目录 Mapper（回填 capabilityName，并校验能力存在）。
     */
    private final AigCapabilityMapper capabilityMapper;

    /**
     * 模型/供应商 Mapper（回填供应商名称，并校验供应商存在）。
     */
    private final AigModelConfigMapper modelConfigMapper;

    @Override
    public PageResult<AigRouteScenarioBindingVo> queryPage(AigRouteScenarioBindingBo bo, PageQuery pageQuery) {
        AigRouteScenarioBindingBo query = bo == null ? new AigRouteScenarioBindingBo() : bo;
        LambdaQueryWrapper<AigRouteScenarioBinding> wrapper = new LambdaQueryWrapper<AigRouteScenarioBinding>()
            .eq(StringUtils.isNotBlank(query.getScenarioCode()), AigRouteScenarioBinding::getScenarioCode,
                query.getScenarioCode())
            .like(StringUtils.isNotBlank(query.getScenarioCodeLike()), AigRouteScenarioBinding::getScenarioCode,
                query.getScenarioCodeLike())
            .eq(StringUtils.isNotBlank(query.getCapabilityCode()), AigRouteScenarioBinding::getCapabilityCode,
                query.getCapabilityCode())
            .eq(query.getProviderId() != null, AigRouteScenarioBinding::getProviderId, query.getProviderId())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigRouteScenarioBinding::getStatus, query.getStatus())
            .orderByAsc(AigRouteScenarioBinding::getScenarioCode)
            .orderByAsc(AigRouteScenarioBinding::getCapabilityCode)
            .orderByAsc(AigRouteScenarioBinding::getPriority);
        Page<AigRouteScenarioBindingVo> voPage = bindingMapper.selectVoPage(pageQuery.build(), wrapper);
        List<AigRouteScenarioBindingVo> rows = voPage.getRecords();
        fillCapabilityName(rows);
        fillProviderName(rows);
        return PageResult.build(rows, voPage.getTotal());
    }

    @Override
    public AigRouteScenarioBindingVo getDetail(Long bindId) {
        if (bindId == null) {
            throw new ServiceException("绑定ID不能为空");
        }
        AigRouteScenarioBindingVo vo = bindingMapper.selectVoById(bindId);
        if (vo == null) {
            throw new ServiceException("场景强制绑定不存在");
        }
        fillCapabilityName(List.of(vo));
        fillProviderName(List.of(vo));
        return vo;
    }

    @Override
    public Long create(AigRouteScenarioBindingBo bo) {
        String scenarioCode = normalizeScenario(bo.getScenarioCode());
        String capabilityCode = normalizeCapability(bo.getCapabilityCode());
        checkCapabilityExists(capabilityCode);
        checkProviderExists(bo.getProviderId());
        if (existsBinding(scenarioCode, capabilityCode, bo.getProviderId(), null)) {
            throw new ServiceException("该场景在能力 " + capabilityCode + " 下已绑定供应商 " + bo.getProviderId());
        }
        AigRouteScenarioBinding entity = BeanUtil.copyProperties(bo, AigRouteScenarioBinding.class);
        // 主键由自增分配：外部传入的 bindId 必须丢弃，否则会覆盖既有行
        entity.setBindId(null);
        entity.setScenarioCode(scenarioCode);
        entity.setCapabilityCode(capabilityCode);
        entity.setStatus(StringUtils.isBlank(bo.getStatus()) ? STATUS_NORMAL : bo.getStatus());
        entity.setPriority(bo.getPriority() == null ? 0 : bo.getPriority());
        bindingMapper.insert(entity);
        log.info("新增场景强制绑定, bindId={}, scenarioCode={}, capabilityCode={}, providerId={}",
            entity.getBindId(), scenarioCode, capabilityCode, bo.getProviderId());
        return entity.getBindId();
    }

    @Override
    public void update(AigRouteScenarioBindingBo bo) {
        AigRouteScenarioBinding exist = loadBinding(bo.getBindId());
        String scenarioCode = StringUtils.isBlank(bo.getScenarioCode())
            ? exist.getScenarioCode() : normalizeScenario(bo.getScenarioCode());
        String capabilityCode = StringUtils.isBlank(bo.getCapabilityCode())
            ? exist.getCapabilityCode() : normalizeCapability(bo.getCapabilityCode());
        Long providerId = bo.getProviderId() == null ? exist.getProviderId() : bo.getProviderId();
        checkCapabilityExists(capabilityCode);
        checkProviderExists(providerId);
        if (existsBinding(scenarioCode, capabilityCode, providerId, exist.getBindId())) {
            throw new ServiceException("该场景在能力 " + capabilityCode + " 下已绑定供应商 " + providerId);
        }
        AigRouteScenarioBinding entity = BeanUtil.copyProperties(bo, AigRouteScenarioBinding.class);
        entity.setBindId(exist.getBindId());
        entity.setScenarioCode(scenarioCode);
        entity.setCapabilityCode(capabilityCode);
        entity.setProviderId(providerId);
        bindingMapper.updateById(entity);
        log.info("修改场景强制绑定, bindId={}, scenarioCode={}, capabilityCode={}, providerId={}, status={}",
            entity.getBindId(), scenarioCode, capabilityCode, providerId, entity.getStatus());
    }

    @Override
    public void remove(Long bindId) {
        AigRouteScenarioBinding exist = loadBinding(bindId);
        bindingMapper.deleteById(bindId);
        // 删除是「放开这个场景的钉死」，不是「禁止该场景」——说清楚，避免被当成安全事件
        log.info("删除场景强制绑定, bindId={}, scenarioCode={}, capabilityCode={}, providerId={}；"
                + "该场景将恢复为按策略与优先级自由选取供应商",
            bindId, exist.getScenarioCode(), exist.getCapabilityCode(), exist.getProviderId());
    }

    /**
     * 回填能力名称。
     *
     * @param rows 绑定视图列表
     */
    private void fillCapabilityName(List<AigRouteScenarioBindingVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (AigRouteScenarioBindingVo row : rows) {
            if (StringUtils.isNotBlank(row.getCapabilityCode())) {
                codes.add(row.getCapabilityCode());
            }
        }
        if (codes.isEmpty()) {
            return;
        }
        List<AigCapability> capabilities = capabilityMapper.selectList(new LambdaQueryWrapper<AigCapability>()
            .in(AigCapability::getCapabilityCode, new ArrayList<>(codes)));
        Map<String, String> nameMap = new HashMap<>();
        if (capabilities != null) {
            for (AigCapability capability : capabilities) {
                if (capability != null && StringUtils.isNotBlank(capability.getCapabilityCode())) {
                    nameMap.put(capability.getCapabilityCode(), capability.getCapabilityName());
                }
            }
        }
        for (AigRouteScenarioBindingVo row : rows) {
            row.setCapabilityName(nameMap.get(row.getCapabilityCode()));
        }
    }

    /**
     * 回填供应商名称。
     * <p>供应商表很小（内置 7 家 + 自建），整体取回再映射比按 ID 逐条查更省事且无 N+1。</p>
     *
     * @param rows 绑定视图列表
     */
    private void fillProviderName(List<AigRouteScenarioBindingVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        List<AigModelProviderVo> providers = modelConfigMapper.selectAllProviders();
        if (CollUtil.isEmpty(providers)) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>();
        for (AigModelProviderVo provider : providers) {
            if (provider != null && provider.getProviderId() != null) {
                nameMap.put(provider.getProviderId(), provider.getProviderName());
            }
        }
        for (AigRouteScenarioBindingVo row : rows) {
            row.setProviderName(nameMap.get(row.getProviderId()));
        }
    }

    /**
     * 加载绑定，不存在时抛异常。
     *
     * @param bindId 绑定ID
     * @return 绑定实体
     */
    private AigRouteScenarioBinding loadBinding(Long bindId) {
        if (bindId == null) {
            throw new ServiceException("绑定ID不能为空");
        }
        AigRouteScenarioBinding entity = bindingMapper.selectById(bindId);
        if (entity == null) {
            throw new ServiceException("场景强制绑定不存在");
        }
        return entity;
    }

    /**
     * 绑定是否已存在（场景 × 能力 × 供应商唯一）。
     *
     * @param scenarioCode   场景编码
     * @param capabilityCode 能力编码
     * @param providerId     供应商ID
     * @param excludeId      需要排除的绑定ID
     * @return 是否已存在
     */
    private boolean existsBinding(String scenarioCode, String capabilityCode, Long providerId, Long excludeId) {
        if (StringUtils.isBlank(scenarioCode) || StringUtils.isBlank(capabilityCode) || providerId == null) {
            return false;
        }
        LambdaQueryWrapper<AigRouteScenarioBinding> wrapper = new LambdaQueryWrapper<AigRouteScenarioBinding>()
            .eq(AigRouteScenarioBinding::getScenarioCode, scenarioCode)
            .eq(AigRouteScenarioBinding::getCapabilityCode, capabilityCode)
            .eq(AigRouteScenarioBinding::getProviderId, providerId)
            .ne(excludeId != null, AigRouteScenarioBinding::getBindId, excludeId);
        return bindingMapper.selectCount(wrapper) > 0;
    }

    /**
     * 校验能力存在。
     * <p>不校验的话，一个拼错的能力编码会安静地躺在表里永不生效——而它的语义是
     * 「钉死供应商」，不生效就意味着路由会自由选，与配置意图相反且无人知晓。</p>
     *
     * @param capabilityCode 能力编码
     */
    private void checkCapabilityExists(String capabilityCode) {
        Long count = capabilityMapper.selectCount(new LambdaQueryWrapper<AigCapability>()
            .eq(AigCapability::getCapabilityCode, capabilityCode));
        if (count == null || count <= 0) {
            throw new ServiceException("能力不存在：" + capabilityCode + "；请先在能力模板中登记该能力");
        }
    }

    /**
     * 校验供应商存在。
     *
     * @param providerId 供应商ID
     */
    private void checkProviderExists(Long providerId) {
        if (providerId == null) {
            throw new ServiceException("供应商不能为空");
        }
        if (modelConfigMapper.countProvider(providerId) <= 0) {
            throw new ServiceException("供应商不存在：" + providerId);
        }
    }

    /**
     * 规范化场景编码：去空白 + 转大写。
     * <p>统一大写是为了让「路由请求里的场景」与「配置里的场景」比对不依赖书写习惯；
     * 比对本身也是大小写不敏感的（见路由引擎），这里再存一份大写是为了让列表可读。</p>
     *
     * @param raw 原始场景编码
     * @return 规范化后的场景编码
     */
    private String normalizeScenario(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new ServiceException("场景编码不能为空");
        }
        return raw.trim().toUpperCase();
    }

    /**
     * 规范化能力编码（去空白；能力编码在库里已是小写规范，不强制大小写转换）。
     *
     * @param raw 原始能力编码
     * @return 规范化后的能力编码
     */
    private String normalizeCapability(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new ServiceException("能力编码不能为空");
        }
        return raw.trim();
    }

}
