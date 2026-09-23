package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpVisualDna;
import org.dromara.creative.domain.bo.CreativeDnaBo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpVisualDnaVo;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.DnaPromptBuilder;
import org.dromara.creative.helper.DnaSeedBuilder;
import org.dromara.creative.helper.VisualBrainAdapter;
import org.dromara.creative.helper.VisualDnaSchema;
import org.dromara.creative.mapper.DpVisualDnaMapper;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visual DNA 服务实现。
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeDnaServiceImpl implements ICreativeDnaService {

    /**
     * 来源：视觉模型生成
     */
    private static final String SOURCE_AI = "AI";

    /**
     * 来源：人工编辑
     */
    private static final String SOURCE_MANUAL = "MANUAL";

    /**
     * 状态
     */
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_REVIEW = "REVIEW";
    private static final String STATUS_LOCKED = "LOCKED";

    private final DpVisualDnaMapper dnaMapper;
    private final DnaSeedBuilder seedBuilder;
    private final DnaPromptBuilder promptBuilder;
    private final VisualBrainAdapter visualBrain;
    private final ContentOssHelper contentOssHelper;
    private final ICreativeProjectService projectService;
    private final IContentTaskService contentTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpVisualDnaVo generate(Long taskId) {
        CreativeProjectVo project = projectService.getProject(taskId);
        ContentTaskDetailVo detail = contentTaskService.getDetail(taskId);

        List<DnaSeedBuilder.FactRow> facts = confirmedFacts(detail);
        List<String> referenceNames = imageFileNames(detail);
        DnaSeedBuilder.SeedResult seed = seedBuilder.build(new DnaSeedBuilder.SeedInput(
            taskId, project.getProductName(), project.getSkuCode(), facts, referenceNames));

        // 视觉模型补全：治理层路由得到模型（且任务允许外部 AI）才调用；否则如实保留「事实推导」来源。
        // 这里不做任何「模型不可用就用默认值假装 AI」的事——来源字段会如实写进基因并展示给用户。
        List<String> notes = new ArrayList<>(seed.notes());
        String source = DnaSeedBuilder.SOURCE_FACTS;
        String modelKey = null;
        String traceId = null;
        VisualBrainAdapter.Analysis analysis = analyzeWithBrain(taskId, project, detail, seed.dna());
        if (analysis.applied()) {
            applyPatch(seed.dna(), analysis.patch());
            source = SOURCE_AI;
            modelKey = analysis.modelKey();
            traceId = analysis.traceId();
            VisualDnaSchema.addEvidence(seed.dna(), "MODEL", "视觉模型采纳字段",
                String.join("、", analysis.fields()),
                "治理层能力 " + analysis.modelKey() + "（traceId=" + analysis.traceId() + "）");
            notes.add("视觉模型采纳字段：" + String.join("、", analysis.fields())
                + "；其余字段仍来自已确认事实与默认规范");
        } else {
            notes.add("本版基因未经视觉模型分析：" + analysis.reason());
        }
        if (analysis.reason() != null && analysis.applied()) {
            notes.add(analysis.reason());
        }

        int version = nextVersion(taskId);
        DpVisualDna entity = new DpVisualDna();
        entity.setTaskId(taskId);
        entity.setDnaNo(dnaNo(version));
        entity.setVersion(version);
        entity.setStatus(STATUS_REVIEW);
        entity.setDnaJson(VisualDnaSchema.toJson(seed.dna()));
        entity.setSource(source);
        entity.setModelKey(modelKey);
        entity.setTraceId(traceId);
        entity.setRemark(notes.isEmpty() ? null : String.join("；", notes));
        applyColumns(entity, seed.dna());
        dnaMapper.insert(entity);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("dnaId", entity.getId());
        event.put("version", version);
        event.put("source", source);
        event.put("modelKey", modelKey);
        event.put("factCount", facts.size());
        event.put("referenceCount", referenceNames.size());
        event.put("modelApplied", analysis.applied());
        event.put("modelReason", analysis.reason());
        event.put("issues", VisualDnaSchema.validate(seed.dna()));
        event.put("missing", seed.missing());
        projectService.moveStage(taskId, DpVisualStageEnum.DNA_REVIEW, "DNA_GENERATE",
            JsonUtils.toJsonString(event));
        return toVo(entity);
    }

    /**
     * 让视觉模型分析参考图；任何一步不满足就返回「未采纳」并附可读原因。
     *
     * @param taskId  项目ID
     * @param project 项目
     * @param detail  任务详情（取参考图）
     * @param seed    基因种子
     * @return 分析结果
     */
    private VisualBrainAdapter.Analysis analyzeWithBrain(Long taskId, CreativeProjectVo project,
                                                         ContentTaskDetailVo detail, ObjectNode seed) {
        byte[] referenceBytes = null;
        String mimeType = null;
        if (detail != null && detail.getFiles() != null) {
            for (CpTaskFileVo file : detail.getFiles()) {
                if (!"IMAGE".equalsIgnoreCase(file.getFileKind()) || StringUtils.isBlank(file.getFileRef())) {
                    continue;
                }
                try {
                    referenceBytes = contentOssHelper.getBytes(file.getFileRef());
                    mimeType = "image/png";
                } catch (Exception e) {
                    log.warn("读取参考图失败 taskId={} fileId={} error={}", taskId, file.getFileId(), e.getMessage());
                }
                break;
            }
        }
        boolean allowExternal = "Y".equalsIgnoreCase(project.getAllowExternal());
        return visualBrain.analyze(project.getDataLevel(), seed, referenceBytes, mimeType,
            project.getProductName(), allowExternal);
    }

    /**
     * 把模型采纳的字段合并进基因（只覆盖 patch 里确实存在的项）。
     *
     * @param target 目标基因树
     * @param patch  模型采纳字段
     */
    private void applyPatch(ObjectNode target, ObjectNode patch) {
        for (String field : List.of("saturation", "contrastLevel", "whitespaceLevel",
            "sceneType", "typographyStyle")) {
            if (patch.hasNonNull(field)) {
                target.put(field, patch.get(field).asText());
            }
        }
        for (String field : List.of("styleKeywords", "avoidKeywords")) {
            JsonNode value = patch.path(field);
            if (value.isArray() && !value.isEmpty()) {
                ArrayNode array = target.putArray(field);
                value.forEach(item -> array.add(item.asText()));
            }
        }
        JsonNode colors = patch.path("colors");
        if (colors.isObject() && !colors.isEmpty()) {
            ObjectNode targetColors = target.withObject("/colors");
            colors.properties().forEach(entry -> targetColors.put(entry.getKey(), entry.getValue().asText()));
        }
        JsonNode lighting = patch.path("lighting");
        if (lighting.isObject() && !lighting.isEmpty()) {
            ObjectNode targetLighting = target.withObject("/lighting");
            lighting.properties().forEach(entry -> targetLighting.put(entry.getKey(), entry.getValue().asText()));
        }
        JsonNode ratio = patch.path("productRatio");
        if (ratio.isObject() && !ratio.isEmpty()) {
            ObjectNode targetRatio = target.withObject("/productRatio");
            ratio.properties().forEach(entry -> targetRatio.put(entry.getKey(), entry.getValue().asInt()));
        }
    }

    @Override
    public DpVisualDnaVo latest(Long taskId) {
        DpVisualDna entity = latestEntity(taskId);
        return entity == null ? null : toVo(entity);
    }

    @Override
    public DpVisualDnaVo locked(Long taskId) {
        DpVisualDna entity = lockedEntity(taskId);
        return entity == null ? null : toVo(entity);
    }

    @Override
    public List<DpVisualDnaVo> versions(Long taskId) {
        List<DpVisualDna> rows = dnaMapper.selectList(new LambdaQueryWrapper<DpVisualDna>()
            .eq(DpVisualDna::getTaskId, taskId)
            .orderByDesc(DpVisualDna::getVersion));
        List<DpVisualDnaVo> list = new ArrayList<>();
        for (DpVisualDna row : rows) {
            list.add(toVo(row));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpVisualDnaVo save(Long taskId, CreativeDnaBo bo) {
        projectService.getProject(taskId);
        DpVisualDna current = bo != null && bo.getId() != null
            ? requireOwned(taskId, bo.getId())
            : latestEntity(taskId);

        ObjectNode node;
        DpVisualDna target;
        if (current == null || STATUS_LOCKED.equals(current.getStatus())) {
            // 没有版本，或改动已锁定版本 → 新建版本（锁定版永不修改）
            ObjectNode base = current == null ? VisualDnaSchema.empty()
                : VisualDnaSchema.parse(current.getDnaJson());
            node = base.deepCopy();
            VisualDnaSchema.addEvidence(node, "MANUAL", "人工修订",
                current == null ? "新建" : ("基于 v" + current.getVersion()),
                "由 " + displayName() + " 编辑");
            target = new DpVisualDna();
            target.setTaskId(taskId);
            target.setVersion(nextVersion(taskId));
            target.setDnaNo(dnaNo(target.getVersion()));
            target.setStatus(STATUS_REVIEW);
            if (current != null && StringUtils.isNotBlank(current.getTraceId())) {
                target.setTraceId(current.getTraceId());
            }
            if (current != null) {
                target.setModelKey(current.getModelKey());
            }
            target.setSource(SOURCE_MANUAL);
        } else {
            node = VisualDnaSchema.parse(current.getDnaJson());
            VisualDnaSchema.addEvidence(node, "MANUAL", "人工编辑", "v" + current.getVersion(),
                "由 " + displayName() + " 编辑");
            target = current;
            target.setSource(SOURCE_MANUAL);
        }

        applyForm(node, bo);
        target.setDnaJson(VisualDnaSchema.toJson(node));
        applyColumns(target, node);
        if (bo != null && StringUtils.isNotBlank(bo.getRemark())) {
            target.setRemark(bo.getRemark());
        }

        if (target.getId() == null) {
            dnaMapper.insert(target);
        } else {
            dnaMapper.updateById(target);
        }
        projectService.moveStage(taskId, DpVisualStageEnum.DNA_REVIEW, "DNA_EDIT",
            JsonUtils.toJsonString(Map.of(
                "dnaId", target.getId(),
                "version", target.getVersion(),
                "newVersion", current != null && STATUS_LOCKED.equals(current.getStatus()))));
        return toVo(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DpVisualDnaVo lock(Long taskId, Long dnaId) {
        DpVisualDna entity = dnaId != null ? requireOwned(taskId, dnaId) : latestEntity(taskId);
        if (entity == null) {
            throw new ServiceException("还没有视觉基因可锁定，请先生成");
        }
        if (STATUS_LOCKED.equals(entity.getStatus())) {
            // 幂等：重复锁定直接返回，不报错
            return toVo(entity);
        }
        ObjectNode node = VisualDnaSchema.parse(entity.getDnaJson());
        List<String> issues = VisualDnaSchema.validate(node);
        if (!issues.isEmpty()) {
            throw new ServiceException("视觉基因未通过校验，无法锁定：" + String.join("；", issues));
        }
        entity.setStatus(STATUS_LOCKED);
        entity.setApprovedBy(LoginHelper.getUserId());
        entity.setApprovedAt(LocalDateTime.now());
        dnaMapper.updateById(entity);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("dnaId", entity.getId());
        event.put("version", entity.getVersion());
        event.put("source", entity.getSource());
        projectService.moveStage(taskId, DpVisualStageEnum.DNA_LOCKED, "DNA_LOCK",
            JsonUtils.toJsonString(event));
        return toVo(entity);
    }

    @Override
    public ObjectNode activeDna(Long taskId) {
        DpVisualDna locked = lockedEntity(taskId);
        DpVisualDna entity = locked != null ? locked : latestEntity(taskId);
        return entity == null ? null : VisualDnaSchema.parse(entity.getDnaJson());
    }

    @Override
    public Long activeDnaId(Long taskId) {
        DpVisualDna locked = lockedEntity(taskId);
        DpVisualDna entity = locked != null ? locked : latestEntity(taskId);
        return entity == null ? null : entity.getId();
    }

    @Override
    public DnaPromptBuilder.Prompt promptPreview(Long taskId, String screenHint) {
        CreativeProjectVo project = projectService.getProject(taskId);
        ObjectNode node = activeDna(taskId);
        String subject = StringUtils.isNotBlank(project.getProductName())
            ? project.getProductName()
            : node == null ? project.getTaskName() : node.path("subject").asText(project.getTaskName());
        return promptBuilder.build(node, subject, screenHint);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private List<DnaSeedBuilder.FactRow> confirmedFacts(ContentTaskDetailVo detail) {
        List<DnaSeedBuilder.FactRow> facts = new ArrayList<>();
        if (detail == null || detail.getFacts() == null) {
            return facts;
        }
        for (CpFactSnapshotVo fact : detail.getFacts()) {
            if (!ContentFactConfirmStatusEnum.CONFIRMED.getCode().equals(fact.getConfirmStatus())) {
                continue;
            }
            String source = StringUtils.isNotBlank(fact.getSourceLocator())
                ? fact.getSourceLocator()
                : StringUtils.blankToDefault(fact.getSourceFileName(), "人工录入");
            facts.add(new DnaSeedBuilder.FactRow(
                fact.getFieldCode(), fact.getFieldName(), fact.getFieldValue(), source));
        }
        return facts;
    }

    private List<String> imageFileNames(ContentTaskDetailVo detail) {
        List<String> names = new ArrayList<>();
        if (detail == null || detail.getFiles() == null) {
            return names;
        }
        for (CpTaskFileVo file : detail.getFiles()) {
            if ("IMAGE".equalsIgnoreCase(file.getFileKind())) {
                names.add(file.getFileName());
            }
        }
        return names;
    }

    private DpVisualDna latestEntity(Long taskId) {
        List<DpVisualDna> rows = dnaMapper.selectList(new LambdaQueryWrapper<DpVisualDna>()
            .eq(DpVisualDna::getTaskId, taskId)
            .orderByDesc(DpVisualDna::getVersion)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DpVisualDna lockedEntity(Long taskId) {
        List<DpVisualDna> rows = dnaMapper.selectList(new LambdaQueryWrapper<DpVisualDna>()
            .eq(DpVisualDna::getTaskId, taskId)
            .eq(DpVisualDna::getStatus, STATUS_LOCKED)
            .orderByDesc(DpVisualDna::getVersion)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DpVisualDna requireOwned(Long taskId, Long dnaId) {
        DpVisualDna entity = dnaMapper.selectById(dnaId);
        if (entity == null || !taskId.equals(entity.getTaskId())) {
            throw new ServiceException("视觉基因不属于该项目：" + dnaId);
        }
        return entity;
    }

    private int nextVersion(Long taskId) {
        DpVisualDna latest = latestEntity(taskId);
        return latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
    }

    private static String dnaNo(int version) {
        return "DNA-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + String.format("%04d", version);
    }

    /**
     * 把表单内容合并进 DNA 树。
     *
     * <p>约定：字段为 {@code null} 表示「本次不改」；传空串/空列表表示「清掉这一项」。</p>
     *
     * @param node DNA 树
     * @param bo   表单
     */
    private void applyForm(ObjectNode node, CreativeDnaBo bo) {
        if (bo == null) {
            return;
        }
        if (bo.getStyleKeywords() != null) {
            putArray(node, "styleKeywords", bo.getStyleKeywords());
        }
        if (bo.getAvoidKeywords() != null) {
            putArray(node, "avoidKeywords", bo.getAvoidKeywords());
        }
        ObjectNode colors = node.withObject("/colors");
        putOrRemove(colors, "primary", bo.getColorPrimary());
        putOrRemove(colors, "secondary", bo.getColorSecondary());
        putOrRemove(colors, "accent", bo.getColorAccent());
        putOrRemove(colors, "background", bo.getColorBg());
        putOrRemove(node, "saturation", bo.getSaturation());
        putOrRemove(node, "contrastLevel", bo.getContrastLevel());
        putOrRemove(node, "whitespaceLevel", bo.getWhitespaceLevel());
        putOrRemove(node, "typographyStyle", bo.getTypographyStyle());
        putOrRemove(node, "sceneType", bo.getSceneType());
        ObjectNode lighting = node.withObject("/lighting");
        putOrRemove(lighting, "type", bo.getLightingType());
        putOrRemove(lighting, "direction", bo.getLightingDir());
        if (bo.getProductRatioMin() != null || bo.getProductRatioMax() != null) {
            ObjectNode ratio = node.withObject("/productRatio");
            if (bo.getProductRatioMin() != null) {
                ratio.put("min", bo.getProductRatioMin());
            }
            if (bo.getProductRatioMax() != null) {
                ratio.put("max", bo.getProductRatioMax());
            }
        }
    }

    private static void putArray(ObjectNode node, String field, List<String> values) {
        ArrayNode array = node.putArray(field);
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                array.add(value.trim());
            }
        }
    }

    private static void putOrRemove(ObjectNode node, String field, String value) {
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            node.remove(field);
        } else {
            node.put(field, value.trim());
        }
    }

    private void applyColumns(DpVisualDna entity, ObjectNode node) {
        Map<String, Object> columns = VisualDnaSchema.toColumns(node);
        entity.setStyleKeywords((String) columns.get("styleKeywords"));
        entity.setAvoidKeywords((String) columns.get("avoidKeywords"));
        entity.setColorPrimary((String) columns.get("colorPrimary"));
        entity.setColorSecondary((String) columns.get("colorSecondary"));
        entity.setColorAccent((String) columns.get("colorAccent"));
        entity.setColorBg((String) columns.get("colorBg"));
        entity.setSaturation((String) columns.get("saturation"));
        entity.setContrastLevel((String) columns.get("contrastLevel"));
        entity.setLightingType((String) columns.get("lightingType"));
        entity.setLightingDir((String) columns.get("lightingDir"));
        entity.setProductRatioMin((java.math.BigDecimal) columns.get("productRatioMin"));
        entity.setProductRatioMax((java.math.BigDecimal) columns.get("productRatioMax"));
        entity.setWhitespaceLevel((String) columns.get("whitespaceLevel"));
        entity.setTypographyStyle((String) columns.get("typographyStyle"));
        entity.setSceneType((String) columns.get("sceneType"));
    }

    private DpVisualDnaVo toVo(DpVisualDna entity) {
        ObjectNode node = VisualDnaSchema.parse(entity.getDnaJson());
        DpVisualDnaVo vo = new DpVisualDnaVo();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setDnaNo(entity.getDnaNo());
        vo.setVersion(entity.getVersion());
        vo.setStatus(entity.getStatus());
        vo.setStatusDesc(statusDesc(entity.getStatus()));
        vo.setSubject(node.path("subject").asText(null));
        vo.setStyleKeywords(stringList(node.path("styleKeywords")));
        vo.setAvoidKeywords(stringList(node.path("avoidKeywords")));
        vo.setColors(colorMap(node.path("colors")));
        vo.setLighting(lightingMap(node.path("lighting")));
        vo.setProductRatio(ratioMap(node.path("productRatio")));
        vo.setSaturation(node.path("saturation").asText(null));
        vo.setContrastLevel(node.path("contrastLevel").asText(null));
        vo.setWhitespaceLevel(node.path("whitespaceLevel").asText(null));
        vo.setTypographyStyle(node.path("typographyStyle").asText(null));
        vo.setSceneType(node.path("sceneType").asText(null));
        vo.setDnaJson(entity.getDnaJson());
        vo.setSource(entity.getSource());
        vo.setSourceDesc(sourceDesc(entity.getSource(), entity.getModelKey()));
        vo.setModelKey(entity.getModelKey());
        vo.setTraceId(entity.getTraceId());
        vo.setEvidence(VisualDnaSchema.evidenceOf(node));
        vo.setIssues(VisualDnaSchema.validate(node));
        vo.setApprovedBy(entity.getApprovedBy());
        vo.setApprovedAt(entity.getApprovedAt());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private static String statusDesc(String status) {
        return switch (StringUtils.blankToDefault(status, "")) {
            case STATUS_DRAFT -> "草稿";
            case STATUS_REVIEW -> "待确认";
            case STATUS_LOCKED -> "已锁定";
            default -> status;
        };
    }

    private static String sourceDesc(String source, String modelKey) {
        return switch (StringUtils.blankToDefault(source, "")) {
            case SOURCE_AI -> "视觉模型分析生成" + (StringUtils.isBlank(modelKey) ? "" : "（模型：" + modelKey + "）");
            case SOURCE_MANUAL -> "人工编辑版本";
            case DnaSeedBuilder.SOURCE_FACTS -> "由已确认的产品事实与默认规范推导（未经视觉模型分析）";
            default -> source;
        };
    }

    private static List<String> stringList(JsonNode array) {
        List<String> items = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode item : array) {
                String text = item.asText("");
                if (!text.isBlank()) {
                    items.add(text);
                }
            }
        }
        return items;
    }

    private static Map<String, String> colorMap(JsonNode colors) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : List.of("primary", "secondary", "accent", "background")) {
            String value = colors.path(key).asText(null);
            if (StringUtils.isNotBlank(value)) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static Map<String, String> lightingMap(JsonNode lighting) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : List.of("type", "direction")) {
            String value = lighting.path(key).asText(null);
            if (StringUtils.isNotBlank(value)) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static Map<String, Integer> ratioMap(JsonNode ratio) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (ratio.path("min").isNumber()) {
            map.put("min", ratio.path("min").asInt());
        }
        if (ratio.path("max").isNumber()) {
            map.put("max", ratio.path("max").asInt());
        }
        return map;
    }

    private static String displayName() {
        try {
            return LoginHelper.getLoginUser() == null ? "系统"
                : StringUtils.blankToDefault(LoginHelper.getLoginUser().getNickname(), "用户");
        } catch (Exception e) {
            return "系统";
        }
    }

}
