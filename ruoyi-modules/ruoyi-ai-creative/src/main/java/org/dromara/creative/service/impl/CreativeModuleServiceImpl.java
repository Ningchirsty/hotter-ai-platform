package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.domain.DpScenarioProfile;
import org.dromara.creative.helper.CreativeScreenSkeleton;
import org.dromara.creative.mapper.DpModuleDefinitionMapper;
import org.dromara.creative.mapper.DpProjectModuleMapper;
import org.dromara.creative.service.ICreativeModuleService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块引擎实现（V0.2 R21，文档 §18/§20/§21）。
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeModuleServiceImpl implements ICreativeModuleService {

    /** 屏展示名多屏时的中文序号（契约要求展示名唯一） */
    private static final String[] CN_NUM = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    /** 启用的取值（本项目约定：0=启用 1=停用） */
    private static final String ENABLED = "0";

    private final DpModuleDefinitionMapper definitionMapper;
    private final DpProjectModuleMapper projectModuleMapper;
    private final ICreativeScenarioConfigService scenarioConfigService;

    @Override
    public List<DpModuleDefinition> listDefinitions(String deliveryType) {
        String type = canonical(deliveryType);
        if (type == null) {
            return List.of();
        }
        return definitionMapper.selectList(new LambdaQueryWrapper<DpModuleDefinition>()
            .eq(DpModuleDefinition::getDeliveryType, type)
            .eq(DpModuleDefinition::getEnabled, ENABLED)
            // 默认骨架按 default_sort_no 在前，可选模块库按 default_sort_no（多为 0）再按 id，稳定可复现
            .orderByDesc(DpModuleDefinition::getDefaultSelected)
            .orderByAsc(DpModuleDefinition::getDefaultSortNo)
            .orderByAsc(DpModuleDefinition::getId));
    }

    @Override
    public List<DpProjectModule> listProjectModules(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return projectModuleMapper.selectList(new LambdaQueryWrapper<DpProjectModule>()
            .eq(DpProjectModule::getTaskId, taskId)
            .orderByAsc(DpProjectModule::getSortNo)
            .orderByAsc(DpProjectModule::getId));
    }

    @Override
    public List<DpProjectModule> ensureProjectModules(Long taskId, String deliveryType) {
        List<DpProjectModule> existing = listProjectModules(taskId);
        if (!existing.isEmpty()) {
            return existing;
        }
        String type = canonical(deliveryType);
        if (type == null) {
            return existing;
        }
        List<DpModuleDefinition> defaults = definitionMapper.selectList(new LambdaQueryWrapper<DpModuleDefinition>()
            .eq(DpModuleDefinition::getDeliveryType, type)
            .eq(DpModuleDefinition::getEnabled, ENABLED)
            .eq(DpModuleDefinition::getDefaultSelected, "1")
            .orderByAsc(DpModuleDefinition::getDefaultSortNo)
            .orderByAsc(DpModuleDefinition::getId));
        if (defaults.isEmpty()) {
            log.warn("交付类型 {} 没有默认模块骨架，项目 {} 的模块计划无法初始化（分镜将回落契约文件）", type, taskId);
            return existing;
        }
        for (DpModuleDefinition definition : defaults) {
            DpProjectModule row = new DpProjectModule();
            row.setTaskId(taskId);
            row.setModuleCode(definition.getModuleCode());
            row.setModuleName(definition.getModuleName());
            row.setScreenType(definition.getScreenType());
            // 默认屏数取 min_screens：种子把"卖点"写成 min=2/max=2，于是默认就是今天的"卖点一/卖点二"
            row.setScreenCount(definition.getMinScreens() == null ? 1 : definition.getMinScreens());
            row.setSortNo(definition.getDefaultSortNo());
            row.setStatus("PLANNED");
            row.setSource("DEFAULT");
            row.setRemark("按交付类型 " + type + " 的默认骨架初始化（R21）");
            projectModuleMapper.insert(row);
        }
        List<DpProjectModule> created = listProjectModules(taskId);
        log.info("项目 {} 按交付类型 {} 初始化模块计划：{} 个模块 / {} 屏",
            taskId, type, created.size(), countScreens(created));
        return created;
    }

    @Override
    public CreativeScreenSkeleton skeletonOf(Long taskId, String deliveryType) {
        String type = canonical(deliveryType);
        List<DpProjectModule> modules = ensureProjectModules(taskId, deliveryType);
        if (modules.isEmpty()) {
            return null;
        }
        // 一次把该交付类型的定义读进来做成 map：保真等级与取景以**定义**为准（计划只是快照）。
        // 别在循环里逐个查库（N+1），也避免"定义缺失时只能猜"。
        Map<String, DpModuleDefinition> definitions = new LinkedHashMap<>();
        for (DpModuleDefinition definition : listDefinitions(type)) {
            definitions.putIfAbsent(definition.getModuleCode(), definition);
        }
        List<CreativeScreenSkeleton.ScreenSpec> specs = new ArrayList<>();
        Map<String, String> typeDesc = new LinkedHashMap<>();
        for (DpProjectModule module : modules) {
            DpModuleDefinition definition = definitions.get(module.getModuleCode());
            int count = module.getScreenCount() == null ? 1 : Math.max(1, module.getScreenCount());
            for (int i = 0; i < count; i++) {
                String label = count == 1 ? module.getModuleName() : module.getModuleName() + cn(i);
                specs.add(new CreativeScreenSkeleton.ScreenSpec(
                    module.getScreenType(), label, lockLevelOf(definition), shotOf(module, definition)));
            }
            typeDesc.putIfAbsent(module.getScreenType(), module.getModuleName());
        }
        try {
            return CreativeScreenSkeleton.of(specs, typeDesc);
        } catch (Exception e) {
            // 模块计划本身坏了（展示名重复/缺取景等）：不静默改成空骨架，交给调用方回落契约文件
            log.warn("项目 {} 的模块计划无法构成屏骨架（{}），将回落到契约文件", taskId, e.getMessage());
            return null;
        }
    }

    /**
     * 取模块的保真等级：以模块定义为准（项目模块只是快照）。
     *
     * @param definition 模块定义（可空：人工加的模块可能没有定义）
     * @return STRICT/LOOSE；定义缺失或写错时给 LOOSE（宽松，不误伤）
     */
    private String lockLevelOf(DpModuleDefinition definition) {
        String level = definition == null ? null : definition.getProductLockLevel();
        return CreativeScreenSkeleton.LEVEL_STRICT.equals(level)
            ? CreativeScreenSkeleton.LEVEL_STRICT : CreativeScreenSkeleton.LEVEL_LOOSE;
    }

    /**
     * 取模块的取景描述；定义缺失时用模块名兜底（契约要求 shot 非空）。
     *
     * @param module     项目模块
     * @param definition 模块定义（可空）
     * @return 取景描述
     */
    private String shotOf(DpProjectModule module, DpModuleDefinition definition) {
        String shot = definition == null ? null : definition.getShot();
        return StringUtils.isBlank(shot) ? module.getModuleName() + "（取景未配置）" : shot;
    }

    /**
     * 第几屏的中文序号（0 → 一）。
     *
     * @param index 下标
     * @return 中文序号；超出预置范围时用数字
     */
    private String cn(int index) {
        return index < CN_NUM.length ? CN_NUM[index] : String.valueOf(index + 1);
    }

    /**
     * 统计屏数（日志与验收用）。
     *
     * @param modules 模块计划
     * @return 屏数
     */
    private int countScreens(List<DpProjectModule> modules) {
        int n = 0;
        for (DpProjectModule module : modules) {
            n += module.getScreenCount() == null ? 1 : Math.max(1, module.getScreenCount());
        }
        return n;
    }

    /**
     * 解析交付类型的规范编码（支持别名，例如 ECOM_DETAIL_PAGE → ECOM_DETAIL）。
     *
     * @param deliveryType 交付类型编码或别名（可空）
     * @return 规范编码；解析不到时返回原值（保持"能查到就查"的宽松），全空返回 null
     */
    private String canonical(String deliveryType) {
        String value = StringUtils.trimToNull(deliveryType);
        if (value == null) {
            return null;
        }
        DpScenarioProfile profile = scenarioConfigService.getScenario(value);
        if (profile != null && StringUtils.isNotBlank(profile.getDeliveryType())) {
            return profile.getDeliveryType();
        }
        return value;
    }
}
