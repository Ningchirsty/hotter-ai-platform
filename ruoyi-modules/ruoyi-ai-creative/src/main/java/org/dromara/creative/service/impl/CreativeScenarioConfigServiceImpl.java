package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.domain.DpProjectStepState;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.domain.DpScenarioProfile;
import org.dromara.creative.domain.DpScenarioStep;
import org.dromara.creative.domain.DpWorkspaceSchema;
import org.dromara.creative.domain.vo.ProjectStepStateVo;
import org.dromara.creative.helper.CreativeStepProjection;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpDeliveryTypeMapper;
import org.dromara.creative.mapper.DpProjectStepStateMapper;
import org.dromara.creative.mapper.DpOutputSpecMapper;
import org.dromara.creative.mapper.DpScenarioProfileMapper;
import org.dromara.creative.mapper.DpScenarioStepMapper;
import org.dromara.creative.mapper.DpWorkspaceSchemaMapper;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/**
 * 场景配置层·只读实现（V0.2 B1）。
 *
 * <p>本轮**只读不写、不改既有行为**：没有把阶段机/闸门/排版接到这些配置上（那是 B2）。
 * 唯一的"逻辑"是两件事：</p>
 * <ol>
 *   <li><b>别名解析</b>：文档 §40 写的交付类型是 `ECOM_DETAIL_PAGE`，而生产库与内容域枚举用的是
 *       `ECOM_DETAIL`。为了不出现第三套词表，库里以 `ECOM_DETAIL` 为准，`ECOM_DETAIL_PAGE`
 *       放在 `alias_codes` 里；这里按编码查时两者都能命中。</li>
 *   <li><b>默认规格排序</b>：把 `is_default='1'` 的规格排到最前，方便调用方直接取第一条。</li>
 * </ol>
 *
 * @author creative
 */
@Service
@RequiredArgsConstructor
public class CreativeScenarioConfigServiceImpl implements ICreativeScenarioConfigService {

    /**
     * 启用状态（与 dp_layout_template.enabled 同口径：0 启用）
     */
    private static final String ENABLED = "0";

    /**
     * 发布状态
     */
    private static final String PUBLISHED = "PUBLISHED";

    private final DpDeliveryTypeMapper deliveryTypeMapper;
    private final DpScenarioProfileMapper profileMapper;
    private final DpScenarioStepMapper stepMapper;
    private final DpOutputSpecMapper outputSpecMapper;
    private final DpWorkspaceSchemaMapper workspaceSchemaMapper;
    /**
     * 读项目阶段与交付类型（V0.2 D2 的只读投影要用；刻意用最小专表 Mapper，
     * 避免依赖 ICreativeProjectService 造成"项目服务 → 步骤写入者 → 本服务 → 项目服务"的循环依赖）
     */
    private final CreativeTaskStageMapper taskStageMapper;
    /**
     * 项目步骤状态（只读；写入只发生在 moveStage）
     */
    private final DpProjectStepStateMapper stepStateMapper;

    @Override
    public List<DpDeliveryType> listDeliveryTypes() {
        return deliveryTypeMapper.selectList(new LambdaQueryWrapper<DpDeliveryType>()
            .eq(DpDeliveryType::getEnabled, ENABLED)
            .orderByAsc(DpDeliveryType::getSortNo)
            .orderByAsc(DpDeliveryType::getId));
    }

    @Override
    public DpDeliveryType getDeliveryType(String code) {
        return resolve(code);
    }

    @Override
    public DpScenarioProfile getScenario(String deliveryType) {
        DpDeliveryType type = resolve(deliveryType);
        String value = type == null ? blankToNull(deliveryType) : type.getDeliveryType();
        if (value == null) {
            return null;
        }
        // 已发布优先；没有再退到该交付类型下最新的一条（配置层允许草稿存在，调用方自行判断 status）
        List<DpScenarioProfile> published = profileMapper.selectList(new LambdaQueryWrapper<DpScenarioProfile>()
            .eq(DpScenarioProfile::getDeliveryType, value)
            .eq(DpScenarioProfile::getStatus, PUBLISHED)
            .orderByDesc(DpScenarioProfile::getVersion)
            .orderByDesc(DpScenarioProfile::getId));
        if (!published.isEmpty()) {
            return published.get(0);
        }
        List<DpScenarioProfile> any = profileMapper.selectList(new LambdaQueryWrapper<DpScenarioProfile>()
            .eq(DpScenarioProfile::getDeliveryType, value)
            .orderByDesc(DpScenarioProfile::getId));
        return any.isEmpty() ? null : any.get(0);
    }

    @Override
    public List<DpScenarioStep> listSteps(String deliveryType) {
        DpScenarioProfile profile = getScenario(deliveryType);
        if (profile == null) {
            return List.of();
        }
        return stepMapper.selectList(new LambdaQueryWrapper<DpScenarioStep>()
            .eq(DpScenarioStep::getProfileId, profile.getId())
            .orderByAsc(DpScenarioStep::getSortNo)
            .orderByAsc(DpScenarioStep::getId));
    }

    @Override
    public DpWorkspaceSchema getWorkspace(String deliveryType) {
        DpDeliveryType type = resolve(deliveryType);
        String value = type == null ? blankToNull(deliveryType) : type.getDeliveryType();
        if (value == null) {
            return null;
        }
        List<DpWorkspaceSchema> published = workspaceSchemaMapper.selectList(
            new LambdaQueryWrapper<DpWorkspaceSchema>()
                .eq(DpWorkspaceSchema::getDeliveryType, value)
                .eq(DpWorkspaceSchema::getStatus, PUBLISHED)
                .orderByDesc(DpWorkspaceSchema::getVersion)
                .orderByDesc(DpWorkspaceSchema::getId));
        if (!published.isEmpty()) {
            return published.get(0);
        }
        List<DpWorkspaceSchema> any = workspaceSchemaMapper.selectList(
            new LambdaQueryWrapper<DpWorkspaceSchema>()
                .eq(DpWorkspaceSchema::getDeliveryType, value)
                .orderByDesc(DpWorkspaceSchema::getId));
        return any.isEmpty() ? null : any.get(0);
    }

    @Override
    public List<DpOutputSpec> listOutputSpecs(String deliveryType) {
        DpDeliveryType type = resolve(deliveryType);
        String value = type == null ? blankToNull(deliveryType) : type.getDeliveryType();
        if (value == null) {
            return List.of();
        }
        List<DpOutputSpec> specs = outputSpecMapper.selectList(new LambdaQueryWrapper<DpOutputSpec>()
            .eq(DpOutputSpec::getDeliveryType, value)
            .orderByAsc(DpOutputSpec::getSortNo)
            .orderByAsc(DpOutputSpec::getId));
        // 默认规格排最前（其余保持 sort_no 顺序）：调用方取第一条即"默认尺寸"
        return specs.stream()
            .sorted((a, b) -> Boolean.compare("1".equals(b.getIsDefault()), "1".equals(a.getIsDefault())))
            .toList();
    }

    @Override
    public List<ProjectStepStateVo> listProjectSteps(Long taskId) {
        // 只读投影：不写库（写库只发生在 CreativeProjectServiceImpl#moveStage）
        Map<String, Object> stageRow = taskStageMapper.selectStage(taskId);
        String stage = stageRow == null ? null : String.valueOf(stageRow.get("visualStage"));
        String deliveryType = stageRow == null ? null : taskStageMapper.selectDeliverableType(taskId);
        List<DpScenarioStep> steps = listSteps(deliveryType);
        if (steps.isEmpty()) {
            return List.of();
        }
        Map<String, DpProjectStepState> persisted = new HashMap<>();
        for (DpProjectStepState row : stepStateMapper.selectList(new LambdaQueryWrapper<DpProjectStepState>()
            .eq(DpProjectStepState::getTaskId, taskId))) {
            persisted.put(row.getStepCode(), row);
        }
        List<CreativeStepProjection.ConfiguredStep> configured = steps.stream()
            .map(s -> new CreativeStepProjection.ConfiguredStep(
                s.getStepCode(), s.getStepName(), s.getStageCodes(), s.getSortNo()))
            .toList();
        List<ProjectStepStateVo> out = new ArrayList<>();
        for (CreativeStepProjection.StepState state : CreativeStepProjection.project(configured, stage)) {
            DpScenarioStep config = steps.stream()
                .filter(s -> state.stepCode().equals(s.getStepCode()))
                .findFirst().orElse(null);
            DpProjectStepState row = persisted.get(state.stepCode());
            if (row == null) {
                // 没有持久化行：按当前阶段推导，并在 source 里标明这是推导值而不是落库值
                out.add(vo(state, stage, null, null, "DERIVED", config, null, null));
            } else {
                // 名称与顺序一律取**当前配置**（改名/改顺序要立刻在界面上生效），
                // 只有"状态 + 时间戳 + 是哪次变更推的"取自落库行。
                // 否则同一份配置下，"有落库行的项目"显示旧名字、"没落库行的项目"显示新名字。
                // R36：跳过行的 remark 就是跳过原因，如实带到视图里。
                out.add(vo(state, row.getStageCode(), row.getStartedAt(), row.getCompletedAt(),
                    "PERSISTED", config, row.getStatus(), row));
            }
        }
        return out;
    }

    /**
     * 组装一行步骤状态（R36 起带上"能不能跳过 / 为什么跳过"）。
     *
     * @param state       投影结果（编码/名称/顺序）
     * @param stageCode   导致该状态的阶段
     * @param startedAt   开始时间（可空）
     * @param completedAt 完成时间（可空）
     * @param source      状态来源（PERSISTED/DERIVED）
     * @param config      配置步骤（可空＝配置查不到，按"不允许跳过"处理）
     * @param persistedStatus 落库状态（可空＝用投影状态）
     * @param row         落库行（可空）
     * @return 视图
     */
    private ProjectStepStateVo vo(CreativeStepProjection.StepState state, String stageCode,
                                  LocalDateTime startedAt, LocalDateTime completedAt, String source,
                                  DpScenarioStep config, String persistedStatus, DpProjectStepState row) {
        String status = StringUtils.blankToDefault(persistedStatus, state.status());
        String required = config == null ? null : config.getRequired();
        String gateType = config == null ? null : config.getGateType();
        boolean skippable = CreativeStepProjection.skippable(required, gateType, status);
        String skipReason = CreativeStepProjection.SKIPPED.equals(status) && row != null ? row.getRemark() : null;
        return new ProjectStepStateVo(state.stepCode(), state.stepName(), state.sortNo(), status,
            StringUtils.blankToDefault(stageCode, stageCode), startedAt, completedAt, source,
            required, StringUtils.isNotBlank(gateType), skippable, skipReason,
            row == null ? null : row.getUpdateTime());
    }

    /**
     * 按编码或别名解析交付类型。
     *
     * @param code 编码或别名（可空）
     * @return 交付类型；解析不到返回 null
     */
    private DpDeliveryType resolve(String code) {
        String value = blankToNull(code);
        if (value == null) {
            return null;
        }
        DpDeliveryType exact = deliveryTypeMapper.selectOne(new LambdaQueryWrapper<DpDeliveryType>()
            .eq(DpDeliveryType::getDeliveryType, value)
            .last("limit 1"));
        if (exact != null) {
            return exact;
        }
        // 别名：alias_codes 是逗号分隔的小字段，配置层只有个位数行，直接全量比对（不做 LIKE 以免误命中前缀）
        List<DpDeliveryType> all = deliveryTypeMapper.selectList(new LambdaQueryWrapper<>());
        return all.stream()
            .filter(item -> StringUtils.isNotBlank(item.getAliasCodes()))
            .filter(item -> Arrays.stream(item.getAliasCodes().split(","))
                .map(String::trim)
                .anyMatch(alias -> alias.equalsIgnoreCase(value)))
            .findFirst()
            .orElse(null);
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
