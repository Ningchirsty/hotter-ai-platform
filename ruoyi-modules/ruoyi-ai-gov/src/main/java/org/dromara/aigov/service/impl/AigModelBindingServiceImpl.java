package org.dromara.aigov.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.bo.AigCapabilityModelBo;
import org.dromara.aigov.domain.vo.AigCapabilityModelVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.enums.AigUsageTypeEnum;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.IAigModelBindingService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 能力-模型绑定服务实现。
 * <p>绑定列表只返回模型展示字段（modelKey/modelName/dataLevelMax 等），
 * <b>不含</b> {@code api_endpoint}/{@code secret_ref}。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigModelBindingServiceImpl implements IAigModelBindingService {

    /**
     * 状态：正常。
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 默认用途：主选。
     */
    private static final String DEFAULT_USAGE = "PRIMARY";

    /**
     * 默认优先级。
     */
    private static final int DEFAULT_PRIORITY = 100;

    /**
     * 绑定 Mapper。
     */
    private final AigCapabilityModelMapper capabilityModelMapper;

    /**
     * 模型主数据只读视图 Mapper（回填 modelKey/modelName 等展示字段）。
     */
    private final AigModelViewMapper modelViewMapper;

    @Override
    public PageResult<AigCapabilityModelVo> queryPage(AigCapabilityModelBo bo, PageQuery pageQuery) {
        AigCapabilityModelBo query = bo == null ? new AigCapabilityModelBo() : bo;
        LambdaQueryWrapper<AigCapabilityModel> wrapper = new LambdaQueryWrapper<AigCapabilityModel>()
            .eq(StringUtils.isNotBlank(query.getCapabilityCode()), AigCapabilityModel::getCapabilityCode, query.getCapabilityCode())
            .eq(query.getModelId() != null, AigCapabilityModel::getModelId, query.getModelId())
            .eq(StringUtils.isNotBlank(query.getUsageType()), AigCapabilityModel::getUsageType, query.getUsageType())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigCapabilityModel::getStatus, query.getStatus())
            .orderByAsc(AigCapabilityModel::getCapabilityCode)
            .orderByAsc(AigCapabilityModel::getPriority);
        Page<AigCapabilityModelVo> voPage = capabilityModelMapper.selectVoPage(pageQuery.build(), wrapper);
        List<AigCapabilityModelVo> rows = voPage.getRecords();
        fillModelInfo(rows);
        return PageResult.build(rows, voPage.getTotal());
    }

    @Override
    public List<AigCapabilityModelVo> listByCapability(String capabilityCode) {
        if (StringUtils.isBlank(capabilityCode)) {
            return List.of();
        }
        List<AigCapabilityModelVo> rows = capabilityModelMapper.selectVoList(new LambdaQueryWrapper<AigCapabilityModel>()
            .eq(AigCapabilityModel::getCapabilityCode, capabilityCode)
            .eq(AigCapabilityModel::getStatus, STATUS_NORMAL)
            .orderByAsc(AigCapabilityModel::getPriority));
        fillModelInfo(rows);
        return rows;
    }

    @Override
    public Long create(AigCapabilityModelBo bo) {
        String capabilityCode = bo.getCapabilityCode() == null ? null : bo.getCapabilityCode().trim();
        if (bo.getModelId() == null) {
            throw new ServiceException("模型ID不能为空");
        }
        if (bo.getModelId() != null && modelViewMapper.selectModelById(bo.getModelId()) == null) {
            throw new ServiceException("模型不存在：" + bo.getModelId());
        }
        Long existCount = capabilityModelMapper.selectCount(new LambdaQueryWrapper<AigCapabilityModel>()
            .eq(AigCapabilityModel::getCapabilityCode, capabilityCode)
            .eq(AigCapabilityModel::getModelId, bo.getModelId()));
        if (existCount != null && existCount > 0) {
            throw new ServiceException("该能力已绑定该模型");
        }
        AigCapabilityModel entity = BeanUtil.copyProperties(bo, AigCapabilityModel.class);
        entity.setBindId(null);
        entity.setCapabilityCode(capabilityCode);
        String usage = normalizeUsage(bo.getUsageType());
        entity.setUsageType(usage == null ? DEFAULT_USAGE : usage);
        entity.setPriority(bo.getPriority() == null ? DEFAULT_PRIORITY : bo.getPriority());
        entity.setStatus(StringUtils.isBlank(bo.getStatus()) ? STATUS_NORMAL : bo.getStatus());
        capabilityModelMapper.insert(entity);
        return entity.getBindId();
    }

    @Override
    public void update(Long bindId, AigCapabilityModelBo bo) {
        if (bindId == null) {
            throw new ServiceException("绑定ID不能为空");
        }
        AigCapabilityModel exist = capabilityModelMapper.selectById(bindId);
        if (exist == null) {
            throw new ServiceException("绑定关系不存在：" + bindId);
        }
        // 先归一化再校验：界面上复制粘贴常带空格/小写，为此报"非法用途"是没必要的摩擦；
        // 而且库里存进 "fallback" 会让路由排序把这条候选掉到最低权重（见 normalizeUsage）
        String usageType = normalizeUsage(bo == null ? null : bo.getUsageType());
        Integer priority = bo == null ? null : bo.getPriority();
        if (priority != null && (priority <= 0 || priority > 999)) {
            throw new ServiceException("优先级必须在 1~999 之间：" + priority);
        }
        AigCapabilityModel update = new AigCapabilityModel();
        update.setBindId(bindId);
        // 只更新这三个可改字段 + 备注；能力编码与模型ID刻意不接受修改（换模型请新增候选）
        update.setUsageType(usageType == null ? exist.getUsageType() : usageType);
        update.setPriority(priority == null ? exist.getPriority() : priority);
        if (bo != null && StringUtils.isNotBlank(bo.getStatus())) {
            update.setStatus(bo.getStatus().trim());
        }
        if (bo != null) {
            update.setRemark(bo.getRemark());
        }
        int rows = capabilityModelMapper.updateById(update);
        if (rows == 0) {
            throw new ServiceException("候选已被其它操作改变，请刷新后重试：" + bindId);
        }
        log.info("候选已更新, bindId={}, capabilityCode={}, usageType={}, priority={}",
            bindId, exist.getCapabilityCode(), update.getUsageType(), update.getPriority());
    }

    @Override
    public void remove(Long bindId) {
        if (bindId == null) {
            throw new ServiceException("绑定ID不能为空");
        }
        if (capabilityModelMapper.selectById(bindId) == null) {
            throw new ServiceException("绑定关系不存在");
        }
        capabilityModelMapper.deleteById(bindId);
    }

    /**
     * 归一化用途：去两端空白 + 转成枚举的规范大写，非法值直接拒绝。
     *
     * <p><b>为什么必须归一化</b>：路由排序用 {@code AigUsageTypeEnum.find(usageType)} 判用途权重，
     * 而它是<b>大小写敏感</b>的（{@code code.equals(usageType)}）。库里写进 {@code "fallback"}
     * 不会报任何错，只会让这条候选的权重掉到最低（PRIMARY 之后）——属于"配了不生效"的典型。
     * 因此在入口就归一成规范值。</p>
     *
     * @param usageType 原始用途（可空/可带空白）
     * @return 规范用途；入参为空返回 null（由调用方决定用默认值还是保持原值）
     */
    private String normalizeUsage(String usageType) {
        if (StringUtils.isBlank(usageType)) {
            return null;
        }
        AigUsageTypeEnum usage = AigUsageTypeEnum.find(usageType.trim().toUpperCase(Locale.ROOT));
        if (usage == null) {
            throw new ServiceException("非法的用途：" + usageType + "（可选 PRIMARY / FALLBACK / GRAY）");
        }
        return usage.getCode();
    }

    /**
     * 回填模型主数据展示字段（不包含端点与密钥引用）。
     *
     * @param rows 绑定视图列表
     */
    private void fillModelInfo(List<AigCapabilityModelVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        List<Long> modelIds = new ArrayList<>();
        for (AigCapabilityModelVo row : rows) {
            if (row.getModelId() != null && !modelIds.contains(row.getModelId())) {
                modelIds.add(row.getModelId());
            }
        }
        if (modelIds.isEmpty()) {
            return;
        }
        List<AigModelVo> models = modelViewMapper.selectModelListByIds(modelIds);
        Map<Long, AigModelVo> modelMap = new LinkedHashMap<>();
        if (models != null) {
            for (AigModelVo model : models) {
                if (model != null && model.getModelId() != null) {
                    modelMap.put(model.getModelId(), model);
                }
            }
        }
        for (AigCapabilityModelVo row : rows) {
            AigModelVo model = modelMap.get(row.getModelId());
            if (model == null) {
                continue;
            }
            row.setModelKey(model.getModelKey());
            row.setModelName(model.getModelName());
            row.setModelType(model.getModelType());
            row.setModelEnabled(model.getIsEnabled());
            row.setDeploymentType(model.getDeploymentType());
        }
    }

    /**
     * 用途排序权重（供列表排序参考，PRIMARY 优先）。
     *
     * @param usageType 用途
     * @return 权重
     */
    public static int usageOrder(String usageType) {
        AigUsageTypeEnum usage = AigUsageTypeEnum.find(usageType);
        if (usage == null) {
            return 3;
        }
        return switch (usage) {
            case PRIMARY -> 0;
            case GRAY -> 1;
            case FALLBACK -> 2;
        };
    }

}
