package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpDetailPageVersion;
import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.domain.DpScenarioProfile;
import org.dromara.creative.domain.DpStoryboard;
import org.dromara.creative.domain.DpStoryboardScreen;
import org.dromara.creative.domain.bo.ModuleDefinitionBo;
import org.dromara.creative.domain.bo.ProjectModulePlanBo;
import org.dromara.creative.domain.vo.ProjectModulePlanVo;
import org.dromara.creative.helper.CreativeFacts;
import org.dromara.creative.helper.CreativeScreenSkeleton;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpDetailPageVersionMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.mapper.DpModuleDefinitionMapper;
import org.dromara.creative.mapper.DpProjectModuleMapper;
import org.dromara.creative.mapper.DpStoryboardMapper;
import org.dromara.creative.mapper.DpStoryboardScreenMapper;
import org.dromara.creative.service.ICreativeModuleService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块引擎实现（V0.2 R21/R22，文档 §18/§20/§21/§24）。
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

    /** 停用 */
    private static final String DISABLED = "1";

    /** 单模块屏数上限（护栏：防止一次拖成 99 屏把分镜生成拖垮；模块定义更严时以定义为准） */
    private static final int MAX_SCREENS_HARD_LIMIT = 10;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpModuleDefinition createDefinition(ModuleDefinitionBo bo) {
        String type = StringUtils.trimToNull(bo == null ? null : bo.getDeliveryType());
        if (type == null) {
            throw new ServiceException("交付类型不能为空。");
        }
        if (scenarioConfigService.getScenario(type) == null) {
            throw new ServiceException("交付类型 " + type + " 还没有已发布的场景档案，不能给它加模块。");
        }
        String code = StringUtils.trimToNull(bo.getModuleCode());
        if (code == null) {
            throw new ServiceException("模块编码不能为空。");
        }
        Long exists = definitionMapper.selectCount(new LambdaQueryWrapper<DpModuleDefinition>()
            .eq(DpModuleDefinition::getDeliveryType, type)
            .eq(DpModuleDefinition::getModuleCode, code));
        if (exists != null && exists > 0) {
            throw new ServiceException("交付类型 " + type + " 里已经有模块编码 " + code
                + " 了；同一个类型内模块编码必须唯一（它同时是项目计划里的关联键）。");
        }
        DpModuleDefinition row = new DpModuleDefinition();
        row.setDeliveryType(type);
        row.setModuleCode(code);
        applyDefinition(row, bo, true);
        definitionMapper.insert(row);
        log.info("新建模块定义 {}/{}（{}，屏类型 {}）", type, code, row.getModuleName(), row.getScreenType());
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpModuleDefinition updateDefinition(Long id, ModuleDefinitionBo bo) {
        DpModuleDefinition row = requireDefinition(id);
        if (StringUtils.isNotBlank(bo.getDeliveryType())
            && !bo.getDeliveryType().trim().equals(row.getDeliveryType())) {
            throw new ServiceException("不允许改交付类型（改类型等于换一套模块库）：当前="
                + row.getDeliveryType());
        }
        // 改模块编码要保证同类型内仍然唯一（项目计划按编码关联）
        String newCode = StringUtils.trimToNull(bo.getModuleCode());
        if (newCode != null && !newCode.equals(row.getModuleCode())) {
            Long exists = definitionMapper.selectCount(new LambdaQueryWrapper<DpModuleDefinition>()
                .eq(DpModuleDefinition::getDeliveryType, row.getDeliveryType())
                .eq(DpModuleDefinition::getModuleCode, newCode)
                .ne(DpModuleDefinition::getId, row.getId()));
            if (exists != null && exists > 0) {
                throw new ServiceException("交付类型 " + row.getDeliveryType() + " 里已经有模块编码 "
                    + newCode + " 了。");
            }
            row.setModuleCode(newCode);
        }
        applyDefinition(row, bo, false);
        definitionMapper.updateById(row);
        log.info("编辑模块定义 {}（{}/{}）", id, row.getDeliveryType(), row.getModuleCode());
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpModuleDefinition setDefinitionEnabled(Long id, String enabled) {
        DpModuleDefinition row = requireDefinition(id);
        String value = DISABLED.equals(StringUtils.trimToNull(enabled)) ? DISABLED : ENABLED;
        row.setEnabled(value);
        definitionMapper.updateById(row);
        log.info("模块定义 {}（{}/{}）已{}", id, row.getDeliveryType(), row.getModuleCode(),
            ENABLED.equals(value) ? "启用" : "停用");
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteDefinition(Long id) {
        DpModuleDefinition row = requireDefinition(id);
        Long used = projectModuleMapper.selectCount(new LambdaQueryWrapper<DpProjectModule>()
            .eq(DpProjectModule::getModuleCode, row.getModuleCode()));
        if (used != null && used > 0) {
            throw new ServiceException("还有 " + used + " 个项目计划在用模块「" + row.getModuleName()
                + "」（" + row.getModuleCode() + "）：删掉定义会让那些计划查不到取景与保真等级。"
                + "要下线请先「停用」，或先把项目计划里的它删掉。");
        }
        definitionMapper.deleteById(id);
        log.info("删除模块定义 {}（{}/{}）", id, row.getDeliveryType(), row.getModuleCode());
        return "模块「" + row.getModuleName() + "」已删除（同交付类型内不再可选）";
    }

    /**
     * 把请求里的可编辑字段写到定义上（新增与编辑共用，避免两处校验不一致）。
     *
     * @param row    目标行
     * @param bo     请求
     * @param isNew  是否新增（新增时必填项缺失要报错；编辑时只改传了的）
     */
    private void applyDefinition(DpModuleDefinition row, ModuleDefinitionBo bo, boolean isNew) {
        String name = StringUtils.trimToNull(bo.getModuleName());
        if (isNew && name == null) {
            throw new ServiceException("模块名不能为空。");
        }
        if (name != null) {
            checkLength("模块名", name, LEN_NAME);
            row.setModuleName(name);
        }
        String screenType = StringUtils.trimToNull(bo.getScreenType());
        if (isNew && screenType == null) {
            throw new ServiceException("屏类型不能为空（它决定草稿工厂用哪套文案策略）。");
        }
        if (screenType != null) {
            checkLength("屏类型", screenType, LEN_TYPE);
            row.setScreenType(screenType);
        }
        if (bo.getObjective() != null) {
            checkLength("模块目标", bo.getObjective(), LEN_OBJECTIVE);
            row.setObjective(StringUtils.trimToNull(bo.getObjective()));
        }
        if (bo.getShot() != null) {
            checkLength("取景", bo.getShot(), LEN_OBJECTIVE);
            row.setShot(StringUtils.trimToNull(bo.getShot()));
        }
        if (bo.getProductLockLevel() != null) {
            String level = StringUtils.trimToNull(bo.getProductLockLevel());
            if (level != null && !CreativeScreenSkeleton.LEVEL_STRICT.equals(level)
                && !CreativeScreenSkeleton.LEVEL_LOOSE.equals(level)) {
                throw new ServiceException("产品保真等级只能是 " + CreativeScreenSkeleton.LEVEL_STRICT
                    + " 或 " + CreativeScreenSkeleton.LEVEL_LOOSE + "。");
            }
            row.setProductLockLevel(level == null ? CreativeScreenSkeleton.LEVEL_LOOSE : level);
        }
        if (bo.getRequired() != null) {
            row.setRequired(DISABLED.equals(StringUtils.trimToNull(bo.getRequired())) ? DISABLED : ENABLED);
        }
        if (bo.getDefaultSelected() != null) {
            row.setDefaultSelected(DISABLED.equals(StringUtils.trimToNull(bo.getDefaultSelected()))
                ? DISABLED : ENABLED);
        }
        if (bo.getDefaultSortNo() != null) {
            row.setDefaultSortNo(bo.getDefaultSortNo());
        }
        int min = bo.getMinScreens() == null ? (row.getMinScreens() == null ? 1 : row.getMinScreens())
            : bo.getMinScreens();
        int max = bo.getMaxScreens() == null ? (row.getMaxScreens() == null ? 1 : row.getMaxScreens())
            : bo.getMaxScreens();
        if (min < 1 || max < 1) {
            throw new ServiceException("屏数至少为 1。");
        }
        if (min > max) {
            throw new ServiceException("最少屏数（" + min + "）不能大于最多屏数（" + max + "）。");
        }
        if (max > MAX_SCREENS_HARD_LIMIT) {
            throw new ServiceException("最多屏数不能超过 " + MAX_SCREENS_HARD_LIMIT + "（护栏：一次拖成几十屏会把分镜生成拖垮）。");
        }
        row.setMinScreens(min);
        row.setMaxScreens(max);
        for (String[] pair : new String[][] {
            {"模板", bo.getAllowedTemplates()}, {"工作流", bo.getAllowedWorkflows()},
            {"所需事实", bo.getRequiredFacts()}}) {
            if (pair[1] != null) {
                checkLength(pair[0], pair[1], LEN_CODES);
            }
        }
        if (bo.getAllowedTemplates() != null) {
            row.setAllowedTemplates(normalizeCodes(bo.getAllowedTemplates()));
        }
        if (bo.getAllowedWorkflows() != null) {
            row.setAllowedWorkflows(normalizeCodes(bo.getAllowedWorkflows()));
        }
        if (bo.getRequiredFacts() != null) {
            row.setRequiredFacts(normalizeCodes(bo.getRequiredFacts()));
        }
        if (bo.getVisualRulesJson() != null) {
            row.setVisualRulesJson(StringUtils.trimToNull(bo.getVisualRulesJson()));
        }
        if (bo.getQaRulesJson() != null) {
            row.setQaRulesJson(StringUtils.trimToNull(bo.getQaRulesJson()));
        }
        if (bo.getEnabled() != null) {
            row.setEnabled(DISABLED.equals(StringUtils.trimToNull(bo.getEnabled())) ? DISABLED : ENABLED);
        } else if (isNew) {
            row.setEnabled(ENABLED);
        }
        if (bo.getRemark() != null) {
            checkLength("备注", bo.getRemark(), LEN_REMARK);
            row.setRemark(StringUtils.trimToNull(bo.getRemark()));
        }
    }

    /**
     * 取模块定义（不存在直接报错）。
     *
     * @param id 定义ID
     * @return 定义
     */
    private DpModuleDefinition requireDefinition(Long id) {
        if (id == null) {
            throw new ServiceException("模块定义ID不能为空。");
        }
        DpModuleDefinition row = definitionMapper.selectById(id);
        if (row == null) {
            throw new ServiceException("模块定义不存在：" + id);
        }
        return row;
    }

    /** 各文本字段的长度上限（与建表长度一致；超了直接拒绝，不静默截断） */
    private static final int LEN_NAME = 128;
    private static final int LEN_CODE = 64;
    private static final int LEN_TYPE = 32;
    private static final int LEN_OBJECTIVE = 500;
    private static final int LEN_CODES = 500;
    private static final int LEN_COPY = 1000;
    private static final int LEN_REMARK = 500;

    private final DpModuleDefinitionMapper definitionMapper;
    private final DpProjectModuleMapper projectModuleMapper;
    private final ICreativeScenarioConfigService scenarioConfigService;
    private final CreativeTaskStageMapper stageMapper;
    private final IContentTaskService contentTaskService;
    private final DpStoryboardMapper storyboardMapper;
    private final DpStoryboardScreenMapper screenMapper;
    private final DpGenerationMapper generationMapper;
    private final DpDetailPageVersionMapper detailPageVersionMapper;

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
        List<DpModuleDefinition> defaults = defaultDefinitions(type);
        if (defaults.isEmpty()) {
            log.warn("交付类型 {} 没有默认模块骨架，项目 {} 的模块计划无法初始化（分镜将回落契约文件）", type, taskId);
            return existing;
        }
        int sortNo = 10;
        for (DpModuleDefinition definition : defaults) {
            DpProjectModule row = new DpProjectModule();
            row.setTaskId(taskId);
            row.setModuleCode(definition.getModuleCode());
            row.setModuleName(definition.getModuleName());
            row.setScreenType(definition.getScreenType());
            // 默认屏数取 min_screens：种子把"卖点"写成 min=2/max=2，于是默认就是今天的"卖点一/卖点二"
            row.setScreenCount(definition.getMinScreens() == null ? 1 : definition.getMinScreens());
            row.setSortNo(sortNo);
            sortNo += 10;
            row.setStatus("PLANNED");
            row.setSource("DEFAULT");
            row.setEnabled(ENABLED);
            // 右栏字段从模块定义带一份初值：规划页一打开就能看到"这个模块原本要什么"
            row.setObjective(definition.getObjective());
            row.setRequiredFactCodes(definition.getRequiredFacts());
            row.setWorkflowCodes(definition.getAllowedWorkflows());
            row.setTemplateCodes(definition.getAllowedTemplates());
            row.setVisualRulesJson(definition.getVisualRulesJson());
            row.setRemark("按交付类型 " + type + " 的默认骨架初始化（R21）");
            projectModuleMapper.insert(row);
        }
        List<DpProjectModule> created = listProjectModules(taskId);
        log.info("项目 {} 按交付类型 {} 初始化模块计划：{} 个模块 / {} 屏",
            taskId, type, created.size(), countScreens(enabledOnly(created)));
        return created;
    }

    @Override
    public ActiveScreens activeScreens(Long taskId, String deliveryType) {
        String type = canonical(deliveryType);
        List<DpProjectModule> modules = ensureProjectModules(taskId, deliveryType);
        if (modules.isEmpty()) {
            return new ActiveScreens(null, List.of());
        }
        List<DpProjectModule> usable = enabledOnly(modules);
        if (usable.isEmpty()) {
            // 有计划但一个模块都没启用：**不能回落契约文件**——那会凭空出现 7 屏，
            // 用户看到的分镜与自己刚保存的计划完全无关。这里明确报错。
            throw new ServiceException("模块计划里没有启用的模块，无法出屏；请在「模块规划」里至少启用一个模块。");
        }
        Map<String, DpModuleDefinition> definitions = definitionMap(type);
        Expansion expansion = expand(usable, definitions);
        Map<String, String> typeDesc = new LinkedHashMap<>();
        for (DpProjectModule module : usable) {
            typeDesc.putIfAbsent(module.getScreenType(), module.getModuleName());
        }
        // 展开后仍可能坏（例如两个不同模块名撞成同一个展示名）：让它抛出去。
        // 以前这里 catch 后返回 null，调用方会静默回落契约文件——对"人工编辑过的计划"来说那是假象。
        CreativeScreenSkeleton skeleton = CreativeScreenSkeleton.of(expansion.specs(), typeDesc);
        return new ActiveScreens(skeleton, expansion.owners());
    }

    @Override
    public CreativeScreenSkeleton skeletonOf(Long taskId, String deliveryType) {
        return activeScreens(taskId, deliveryType).skeleton();
    }

    @Override
    public ProjectModulePlanVo planOf(Long taskId) {
        String deliveryType = requireTask(taskId);
        ProjectModulePlanVo vo = new ProjectModulePlanVo();
        vo.setTaskId(taskId);
        vo.setDeliveryType(deliveryType);
        DpDeliveryType type = scenarioConfigService.getDeliveryType(deliveryType);
        vo.setDeliveryName(type == null || StringUtils.isBlank(type.getDeliveryName())
            ? deliveryType : type.getDeliveryName());
        List<DpProjectModule> modules = listProjectModules(taskId);
        vo.setModules(modules);
        vo.setLibrary(listDefinitions(deliveryType));
        Map<String, DpModuleDefinition> definitions = definitionMap(deliveryType);
        Map<String, String> facts = confirmedFacts(taskId);
        // 还没有计划时，预览给的是"按交付类型默认骨架初始化后会长成什么样"，并**明确标出来源**
        // （previewSource=DEFAULT_SKELETON）。不标的话用户会以为这个项目已经有计划了——
        // 这是 R22 真机验收抓到的一处"页面说的是默认骨架、接口却给了空预览"。
        boolean fromDefault = modules.isEmpty();
        List<DpProjectModule> previewRows = fromDefault
            ? defaultRows(taskId, deliveryType) : enabledOnly(modules);
        List<ProjectModulePlanVo.ScreenPreview> preview = preview(previewRows, definitions, facts);
        vo.setScreens(preview);
        vo.setScreenCount(preview.size());
        vo.setPreviewSource(fromDefault ? "DEFAULT_SKELETON" : "PLAN");
        vo.setPreviewNote(fromDefault
            ? "这个项目还没有模块计划：下面是按交付类型「" + deliveryType
                + "」的默认骨架算出来的预览，保存后才成为本项目的计划。"
            : "预览来自本项目的模块计划（保存前的实时预览）。");

        String blockReason = editBlockReason(taskId);
        vo.setEditable(blockReason == null);
        vo.setEditBlockReason(blockReason);
        vo.setStoryboard(storyboardRef(taskId, preview));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectModulePlanVo savePlan(Long taskId, ProjectModulePlanBo bo) {
        String deliveryType = requireTask(taskId);
        String blockReason = editBlockReason(taskId);
        if (blockReason != null) {
            throw new ServiceException(blockReason);
        }
        if (bo == null || bo.getModules() == null || bo.getModules().isEmpty()) {
            throw new ServiceException("模块计划不能为空：至少保留一个启用的模块。");
        }
        Map<String, DpModuleDefinition> definitions = definitionMap(deliveryType);
        List<DpProjectModule> rows = new ArrayList<>();
        int sortNo = 10;
        int enabledCount = 0;
        for (ProjectModulePlanBo.Item item : bo.getModules()) {
            DpProjectModule row = toRow(taskId, item, definitions);
            row.setSortNo(sortNo);
            sortNo += 10;
            if (ENABLED.equals(row.getEnabled())) {
                enabledCount++;
            }
            rows.add(row);
        }
        if (enabledCount == 0) {
            throw new ServiceException("至少要启用一个模块，否则分镜没有任何屏可出。");
        }
        // 先用同一段展开逻辑校验一遍：坏计划不该写进库（写进去以后分镜才报错，定位成本高得多）。
        // 展开/校验抛的是 IllegalArgumentException（骨架契约那层的语言），这里翻译成用户能看懂的业务错误。
        try {
            CreativeScreenSkeleton.of(expand(rows.stream()
                .filter(r -> ENABLED.equals(r.getEnabled())).toList(), definitions).specs(), Map.of());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("模块计划无法构成屏骨架：" + e.getMessage());
        }

        List<DpProjectModule> before = listProjectModules(taskId);
        // 留痕：软删旧行（del_flag=1，@TableLogic 自动过滤），再按新顺序写入。
        // 不做物理删除：模块计划的每次修改都该留下一行可追溯的历史。
        for (DpProjectModule old : before) {
            projectModuleMapper.deleteById(old.getId());
        }
        for (DpProjectModule row : rows) {
            projectModuleMapper.insert(row);
        }
        log.info("项目 {} 保存模块计划：{} 行（启用 {}）→ {} 屏；旧计划 {} 行已软删",
            taskId, rows.size(), enabledCount, countScreens(rows.stream()
                .filter(r -> ENABLED.equals(r.getEnabled())).toList()), before.size());
        return planOf(taskId);
    }

    // ------------------------------------------------------------------
    // 计划 → 屏（Module Plan → Screen Plan，文档 §21）
    // ------------------------------------------------------------------

    /**
     * 展开结果：屏定义 + "第几屏来自哪一行"（预览要用模块名，且必须与骨架逐屏对齐）。
     */
    private record Expansion(List<CreativeScreenSkeleton.ScreenSpec> specs, List<DpProjectModule> owners) {
    }

    /**
     * 把模块计划展开成屏（**分镜生成与规划页预览共用这一份逻辑**，避免"预览说 5 屏、生成出 7 屏"）。
     *
     * <p>展示名规则：同一个模块编码在计划里只出现一次、且只占一屏 → 直接用模块名；
     * 否则按该模块编码下的屏顺序加中文序号（卖点一/卖点二；复制两份主图时是主图一/主图二）。
     * 这条规则保证展示名**唯一**——契约校验要求唯一，重名会直接抛错。</p>
     *
     * @param modules     启用的模块行（已按 sortNo 排好）
     * @param definitions 模块库（编码 → 定义，保真等级与取景以定义为准）
     * @return 展开结果
     */
    private Expansion expand(List<DpProjectModule> modules, Map<String, DpModuleDefinition> definitions) {
        Map<String, Integer> groupSize = new LinkedHashMap<>();
        for (DpProjectModule module : modules) {
            groupSize.merge(module.getModuleCode(), 1, Integer::sum);
        }
        Map<String, Integer> cursor = new LinkedHashMap<>();
        List<CreativeScreenSkeleton.ScreenSpec> specs = new ArrayList<>();
        List<DpProjectModule> owners = new ArrayList<>();
        for (DpProjectModule module : modules) {
            DpModuleDefinition definition = definitions.get(module.getModuleCode());
            int count = module.getScreenCount() == null ? 1 : Math.max(1, module.getScreenCount());
            int size = groupSize.getOrDefault(module.getModuleCode(), 1);
            boolean multi = size > 1 || count > 1;
            for (int i = 0; i < count; i++) {
                int seq = cursor.merge(module.getModuleCode(), 1, Integer::sum);
                String name = StringUtils.blankToDefault(module.getModuleName(),
                    definition == null ? module.getModuleCode() : definition.getModuleName());
                String label = multi ? name + cn(seq - 1) : name;
                specs.add(new CreativeScreenSkeleton.ScreenSpec(
                    module.getScreenType(), label, lockLevelOf(module, definition), shotOf(module, definition)));
                owners.add(module);
            }
        }
        return new Expansion(specs, owners);
    }

    /**
     * 规划页的屏预览（与 {@link #expand} 同一份展开逻辑）。
     *
     * @param modules     启用的模块行
     * @param definitions 模块库
     * @param facts       已确认事实（用于标出"所需事实"里缺哪些）
     * @return 预览列表
     */
    private List<ProjectModulePlanVo.ScreenPreview> preview(List<DpProjectModule> modules,
                                                            Map<String, DpModuleDefinition> definitions,
                                                            Map<String, String> facts) {
        Expansion expansion = expand(modules, definitions);
        List<ProjectModulePlanVo.ScreenPreview> list = new ArrayList<>();
        for (int i = 0; i < expansion.specs().size(); i++) {
            CreativeScreenSkeleton.ScreenSpec spec = expansion.specs().get(i);
            DpProjectModule owner = expansion.owners().get(i);
            list.add(new ProjectModulePlanVo.ScreenPreview(
                String.format("S%02d", i + 1), owner.getModuleCode(), owner.getModuleName(),
                spec.type(), spec.label(), spec.productLockLevel(), spec.shot(), true,
                missingFacts(owner, facts)));
        }
        return list;
    }

    /**
     * 某模块"所需事实"里还没有确认值的那些码。
     *
     * @param module 模块行
     * @param facts  已确认事实
     * @return 缺失的字段码（空列表 = 齐了）
     */
    private List<String> missingFacts(DpProjectModule module, Map<String, String> facts) {
        List<String> missing = new ArrayList<>();
        for (String code : splitCodes(module.getRequiredFactCodes())) {
            if (!facts.containsKey(code) || StringUtils.isBlank(facts.get(code))) {
                missing.add(code);
            }
        }
        return missing;
    }

    // ------------------------------------------------------------------
    // 不可逆状态（fail-closed）
    // ------------------------------------------------------------------

    /**
     * 现在能不能改模块计划；不能改时返回**可读原因**。
     *
     * <p>三类状态一改就不可逆，因此直接拒绝（而不是"改了再提示"）：
     * 分镜已锁定（屏集合是锁定时的约定）、已经出过图（候选与屏对不上）、已经渲染过详情页版本。</p>
     *
     * @param taskId 项目ID
     * @return null = 可改；否则是不能改的原因
     */
    private String editBlockReason(Long taskId) {
        Long locked = storyboardMapper.selectCount(new LambdaQueryWrapper<DpStoryboard>()
            .eq(DpStoryboard::getTaskId, taskId)
            .eq(DpStoryboard::getStatus, "LOCKED"));
        if (locked != null && locked > 0) {
            return "分镜已锁定，模块计划不能再改：屏集合是锁定那一刻的约定。"
                + "如需调整，请回到分镜环节重新拆分（会生成新版本的分镜）。";
        }
        Long generations = generationMapper.selectCount(new LambdaQueryWrapper<DpGeneration>()
            .eq(DpGeneration::getTaskId, taskId));
        if (generations != null && generations > 0) {
            return "项目已经出过图（" + generations + " 条出图记录），改模块计划会让已有候选与屏对不上。"
                + "如需调整，请先按新计划重新走一遍分镜与出图。";
        }
        Long versions = detailPageVersionMapper.selectCount(new LambdaQueryWrapper<DpDetailPageVersion>()
            .eq(DpDetailPageVersion::getTaskId, taskId));
        if (versions != null && versions > 0) {
            return "项目已经渲染过详情页版本（" + versions + " 个），改模块计划与已渲染的排版不再对应。";
        }
        return null;
    }

    /**
     * 最近一次分镜与当前计划的对照（只读时算，不写库）。
     *
     * @param taskId  项目ID
     * @param preview 当前计划的屏预览
     * @return 对照；没有分镜时返回 null
     */
    private ProjectModulePlanVo.StoryboardRef storyboardRef(
        Long taskId, List<ProjectModulePlanVo.ScreenPreview> preview) {
        List<DpStoryboard> rows = storyboardMapper.selectList(new LambdaQueryWrapper<DpStoryboard>()
            .eq(DpStoryboard::getTaskId, taskId)
            .orderByDesc(DpStoryboard::getVersion)
            .last("limit 1"));
        if (rows.isEmpty()) {
            return new ProjectModulePlanVo.StoryboardRef(
                null, null, null, 0, List.of(), !preview.isEmpty(),
                "还没有分镜：保存计划后去分镜环节拆分镜，出的就是上面这些屏。");
        }
        DpStoryboard storyboard = rows.get(0);
        List<DpStoryboardScreen> screens = screenMapper.selectList(new LambdaQueryWrapper<DpStoryboardScreen>()
            .eq(DpStoryboardScreen::getStoryboardId, storyboard.getId())
            .orderByAsc(DpStoryboardScreen::getSortNo));
        List<String> storyboardTypes = new ArrayList<>();
        for (DpStoryboardScreen screen : screens) {
            storyboardTypes.add(screen.getScreenType());
        }
        List<String> planTypes = preview.stream()
            .map(ProjectModulePlanVo.ScreenPreview::screenType).toList();
        boolean stale = !storyboardTypes.equals(planTypes);
        String note = stale
            ? "最近的分镜（v" + storyboard.getVersion() + "）与当前计划不是同一套屏"
                + "（分镜 " + storyboardTypes.size() + " 屏 / 计划 " + planTypes.size() + " 屏）："
                + "要重新拆分镜才会用上新计划。"
            : "最近的分镜与当前计划一致（" + planTypes.size() + " 屏）。";
        return new ProjectModulePlanVo.StoryboardRef(storyboard.getId(), storyboard.getVersion(),
            storyboard.getStatus(), storyboard.getScreenCount(), storyboardTypes, stale, note);
    }

    // ------------------------------------------------------------------
    // 其它
    // ------------------------------------------------------------------

    /**
     * 交付类型的**默认骨架**（{@code default_selected=1}），顺序与模块库一致。
     *
     * <p>刻意在 Java 里从 {@link #listDefinitions(String)} 过滤，而不是再发一条带
     * {@code default_selected='1'} 的 SQL：两条几乎一样的查询会让"库里那份"和"接口那份"
     * 慢慢长歪（R22 单测第一版就因为 Mockito 不区分 wrapper 而多算出一条可选模块，
     * 7 屏变 8 屏）。一处查询、一处过滤，顺序与展示也天然一致。</p>
     *
     * @param type 规范交付类型
     * @return 默认骨架的定义列表（可能为空）
     */
    private List<DpModuleDefinition> defaultDefinitions(String type) {
        List<DpModuleDefinition> defaults = new ArrayList<>();
        for (DpModuleDefinition definition : listDefinitions(type)) {
            if ("1".equals(definition.getDefaultSelected())) {
                defaults.add(definition);
            }
        }
        return defaults;
    }

    /**
     * 按交付类型的默认骨架造一份"还没保存的预览行"（只为预览，**不写库**）。
     *
     * <p>和 {@code ensureProjectModules} 的初始化规则保持一致（屏数取 minScreens、顺序取 defaultSortNo），
     * 否则会出现"预览 7 屏、保存/生成后却是 6 屏"这种对不上的情况。</p>
     *
     * @param taskId       项目ID
     * @param deliveryType 交付类型
     * @return 预览用的模块行（未落库）
     */
    private List<DpProjectModule> defaultRows(Long taskId, String deliveryType) {
        String type = canonical(deliveryType);
        if (type == null) {
            return List.of();
        }
        List<DpProjectModule> rows = new ArrayList<>();
        int sortNo = 10;
        for (DpModuleDefinition definition : defaultDefinitions(type)) {
            DpProjectModule row = new DpProjectModule();
            row.setTaskId(taskId);
            row.setModuleCode(definition.getModuleCode());
            row.setModuleName(definition.getModuleName());
            row.setScreenType(definition.getScreenType());
            row.setScreenCount(definition.getMinScreens() == null ? 1 : definition.getMinScreens());
            row.setSortNo(sortNo);
            sortNo += 10;
            row.setEnabled(ENABLED);
            row.setRequiredFactCodes(definition.getRequiredFacts());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 交付类型的模块库（编码 → 定义）。
     */
    private Map<String, DpModuleDefinition> definitionMap(String type) {
        Map<String, DpModuleDefinition> map = new LinkedHashMap<>();
        for (DpModuleDefinition definition : listDefinitions(type)) {
            map.putIfAbsent(definition.getModuleCode(), definition);
        }
        return map;
    }

    private static List<DpProjectModule> enabledOnly(List<DpProjectModule> modules) {
        List<DpProjectModule> list = new ArrayList<>();
        for (DpProjectModule module : modules) {
            if (!DISABLED.equals(module.getEnabled())) {
                list.add(module);
            }
        }
        return list;
    }

    private DpProjectModule toRow(Long taskId, ProjectModulePlanBo.Item item,
                                 Map<String, DpModuleDefinition> definitions) {
        if (item == null) {
            throw new ServiceException("模块行不能为空。");
        }
        String code = StringUtils.trimToNull(item.getModuleCode());
        DpModuleDefinition definition = code == null ? null : definitions.get(code);
        if (definition == null) {
            throw new ServiceException("模块编码不在该交付类型的模块库里：" + (code == null ? "(空)" : code)
                + "。请从左侧模块库添加。");
        }
        String name = StringUtils.blankToDefault(StringUtils.trimToNull(item.getModuleName()),
            definition.getModuleName());
        String screenType = StringUtils.blankToDefault(StringUtils.trimToNull(item.getScreenType()),
            definition.getScreenType());
        checkLength("模块名", name, LEN_NAME);
        checkLength("模块编码", code, LEN_CODE);
        checkLength("屏类型", screenType, LEN_TYPE);
        checkLength("模块目标", item.getObjective(), LEN_OBJECTIVE);
        checkLength("对应卖点", item.getSellingPointCodes(), LEN_CODES);
        checkLength("文案", item.getCopyText(), LEN_COPY);
        checkLength("所需事实", item.getRequiredFactCodes(), LEN_CODES);
        checkLength("参考图", item.getReferenceCodes(), LEN_CODES);
        checkLength("Workflow", item.getWorkflowCodes(), LEN_CODES);
        checkLength("模板", item.getTemplateCodes(), LEN_CODES);
        checkLength("备注", item.getRemark(), LEN_REMARK);

        int count = item.getScreenCount() == null ? 1 : item.getScreenCount();
        if (count < 1) {
            throw new ServiceException("模块「" + name + "」的屏数至少为 1。");
        }
        int max = definition.getMaxScreens() == null ? MAX_SCREENS_HARD_LIMIT
            : Math.min(MAX_SCREENS_HARD_LIMIT, definition.getMaxScreens());
        if (count > max) {
            throw new ServiceException("模块「" + name + "」最多 " + max + " 屏（模块库定义），当前填了 " + count + "。");
        }
        String enabled = DISABLED.equals(StringUtils.trimToNull(item.getEnabled())) ? DISABLED : ENABLED;

        DpProjectModule row = new DpProjectModule();
        row.setTaskId(taskId);
        row.setModuleCode(code);
        row.setModuleName(name);
        row.setScreenType(screenType);
        row.setScreenCount(count);
        row.setStatus("PLANNED");
        row.setSource("MANUAL");
        row.setEnabled(enabled);
        row.setObjective(StringUtils.trimToNull(item.getObjective()));
        row.setSellingPointCodes(normalizeCodes(item.getSellingPointCodes()));
        row.setCopyText(StringUtils.trimToNull(item.getCopyText()));
        row.setRequiredFactCodes(normalizeCodes(item.getRequiredFactCodes()));
        row.setVisualRulesJson(StringUtils.trimToNull(item.getVisualRulesJson()));
        row.setReferenceCodes(normalizeCodes(item.getReferenceCodes()));
        row.setWorkflowCodes(normalizeCodes(item.getWorkflowCodes()));
        row.setTemplateCodes(normalizeCodes(item.getTemplateCodes()));
        row.setRemark(StringUtils.trimToNull(item.getRemark()));
        return row;
    }

    /**
     * 逗号分隔的编码串：去空白、去空项、去重（保留顺序），再拼回去。
     *
     * <p>为什么在这里规整：右栏这些字段是人手填的，"a, b"、"a,b"、"a,,b" 都该等价；
     * 落库前统一，后面的解析（取第一个 workflow、比事实码）才不会各写一套容错。</p>
     *
     * @param raw 原始文本
     * @return 规整后的编码串；空返回 null
     */
    private static String normalizeCodes(String raw) {
        List<String> codes = splitCodes(raw);
        return codes.isEmpty() ? null : String.join(",", codes);
    }

    /**
     * 解析逗号分隔的编码串（去空白/空项/重复，保留出现顺序）。
     *
     * @param raw 原始文本
     * @return 编码列表（可能为空）
     */
    public static List<String> splitCodes(String raw) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return list;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String part : Arrays.asList(raw.split(","))) {
            String code = StringUtils.trimToNull(part);
            if (code != null && seen.add(code)) {
                list.add(code);
            }
        }
        return list;
    }

    private static void checkLength(String label, String value, int max) {
        if (value != null && value.length() > max) {
            throw new ServiceException(label + "太长（最多 " + max + " 字，当前 " + value.length() + " 字）。");
        }
    }

    private Map<String, String> confirmedFacts(Long taskId) {
        ContentTaskDetailVo detail = contentTaskService.getDetail(taskId);
        return CreativeFacts.confirmed(detail);
    }

    /**
     * 取项目的交付类型（不存在/已删除直接报错——模块规划必须挂在一个真项目上）。
     *
     * <p>刻意用两个**标量**查询，而不是一次查回 Map：R22 第一版用 Map 取列，
     * {@code get("deliveryType")} 拿到 null 后被 {@code String.valueOf} 变成字符串 "null"，
     * 于是模块库按 {@code delivery_type='null'} 查，**静默 0 条**。这类"看起来没坏、其实查空了"
     * 的写法在这一轮已经吃过一次亏（真机验收抓到），所以这里连"字面量 null"也一并挡掉。</p>
     *
     * @param taskId 项目ID
     * @return 交付类型
     */
    private String requireTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("taskId 不能为空。");
        }
        String delFlag = stageMapper.selectDelFlag(taskId);
        if (delFlag == null) {
            throw new ServiceException("项目不存在：" + taskId);
        }
        if (!ENABLED.equals(delFlag)) {
            throw new ServiceException("项目已删除，不能再规划模块：" + taskId);
        }
        String type = usableType(stageMapper.selectDeliverableType(taskId));
        if (type == null) {
            throw new ServiceException("项目没有交付类型，无法规划模块：" + taskId);
        }
        return type;
    }

    /**
     * 把可能"脏"的交付类型字符串规整成可用值。
     *
     * @param raw 原值（可空；也可能是字符串 "null"）
     * @return 可用编码；不可用返回 null
     */
    private static String usableType(String raw) {
        String value = StringUtils.trimToNull(raw);
        if (value == null || "null".equalsIgnoreCase(value) || "undefined".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    /**
     * 取模块的保真等级：**以模块定义为准**（计划行只是快照；定义缺失时给 LOOSE，不误伤）。
     *
     * @param module     项目模块
     * @param definition 模块定义（可空）
     * @return STRICT/LOOSE
     */
    private String lockLevelOf(DpProjectModule module, DpModuleDefinition definition) {
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
