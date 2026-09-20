package org.dromara.content.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.CpGateRule;
import org.dromara.content.domain.CpProduct;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.CpWorkPackage;
import org.dromara.content.domain.vo.CpWorkPackageVo;
import org.dromara.content.enums.ContentDeliverableTypeEnum;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.helper.ContentGateEngine;
import org.dromara.content.mapper.CpFactSnapshotMapper;
import org.dromara.content.mapper.CpProductMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.mapper.CpWorkPackageMapper;
import org.dromara.content.service.IContentTaskGateService;
import org.dromara.content.service.IContentWorkPackageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设计开工包服务实现。
 *
 * <p><b>只冻结已确认事实</b>：{@code confirmedFacts} 只取 {@code CONFIRMED} 的行，
 * 候选值（PENDING）绝不进入开工包——这是设计文档 §3.4「产品事实与视觉创作分离」的落点。</p>
 *
 * <p><b>如实标注本阶段未覆盖的部分</b>：素材分层与谱系（§11）、知识候选（§12）、
 * 文案与禁用表达、页面级内容单元均属后续阶段，开工包里以空数组加 {@code note} 说明，
 * 不编造内容充数。</p>
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentWorkPackageServiceImpl implements IContentWorkPackageService {

    /**
     * 状态：草稿
     */
    private static final String STATUS_DRAFT = "DRAFT";

    /**
     * 状态：已签发
     */
    private static final String STATUS_ISSUED = "ISSUED";

    /**
     * 开工包 Mapper
     */
    private final CpWorkPackageMapper workPackageMapper;

    /**
     * 任务 Mapper
     */
    private final CpTaskMapper taskMapper;

    /**
     * 事实快照 Mapper
     */
    private final CpFactSnapshotMapper factSnapshotMapper;

    /**
     * 附件 Mapper
     */
    private final CpTaskFileMapper taskFileMapper;

    /**
     * 产品 Mapper
     */
    private final CpProductMapper productMapper;

    /**
     * 闸门重算服务（只读判定）
     */
    private final IContentTaskGateService taskGateService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generate(Long taskId) {
        CpTask task = loadTask(taskId);
        ContentGateEngine.GateResult gate = taskGateService.evaluate(taskId);
        if (!gate.readyForPackage()) {
            // 说明「差什么」，而不是笼统报错——设计文档 §3.2「问题找人，不让人找问题」
            throw new ServiceException("当前不满足开工条件：" + StringUtils.blankToDefault(gate.getBlockReason(), "存在未确认的强制项"));
        }

        List<CpFactSnapshot> confirmed = factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId)
            .eq(CpFactSnapshot::getConfirmStatus, ContentFactConfirmStatusEnum.CONFIRMED.getCode())
            .orderByAsc(CpFactSnapshot::getFieldCode));
        int snapshotVersion = 1;
        for (CpFactSnapshot s : confirmed) {
            if (s.getSnapshotVersion() != null && s.getSnapshotVersion() > snapshotVersion) {
                snapshotVersion = s.getSnapshotVersion();
            }
        }

        // 重新生成时清掉旧草稿；已签发的历史版本保留（签发件是交付凭证，不应被覆盖）
        workPackageMapper.delete(new LambdaQueryWrapper<CpWorkPackage>()
            .eq(CpWorkPackage::getTaskId, taskId)
            .eq(CpWorkPackage::getStatus, STATUS_DRAFT));

        CpWorkPackage entity = new CpWorkPackage();
        entity.setTaskId(taskId);
        entity.setSnapshotVersion(snapshotVersion);
        entity.setStatus(STATUS_DRAFT);
        entity.setGeneratedBy(LoginHelper.getUserId());
        entity.setGeneratedAt(LocalDateTime.now());
        entity.setContentJson(JsonUtils.toJsonString(buildContent(task, confirmed, gate, snapshotVersion)));
        workPackageMapper.insert(entity);

        log.info("生成开工包, taskId={}, packageId={}, snapshotVersion={}, confirmedFacts={}, conditionGaps={}",
            taskId, entity.getPackageId(), snapshotVersion, confirmed.size(), gate.getConditionUnsatisfied().size());
        return entity.getPackageId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long packageId) {
        CpWorkPackage pkg = loadPackage(packageId);
        if (!STATUS_DRAFT.equals(pkg.getStatus())) {
            throw new ServiceException("该开工包已签发，不能重复签发");
        }
        CpWorkPackage update = new CpWorkPackage();
        update.setPackageId(packageId);
        update.setStatus(STATUS_ISSUED);
        update.setIssuedBy(LoginHelper.getUserId());
        update.setIssuedAt(LocalDateTime.now());
        workPackageMapper.updateById(update);
        log.info("签发开工包, taskId={}, packageId={}", pkg.getTaskId(), packageId);
    }

    @Override
    public CpWorkPackageVo getByTask(Long taskId) {
        loadTask(taskId);
        List<CpWorkPackage> list = workPackageMapper.selectList(new LambdaQueryWrapper<CpWorkPackage>()
            .eq(CpWorkPackage::getTaskId, taskId)
            .orderByDesc(CpWorkPackage::getPackageId));
        if (CollUtil.isEmpty(list)) {
            return null;
        }
        CpWorkPackageVo vo = new CpWorkPackageVo();
        CpWorkPackage latest = list.get(0);
        vo.setPackageId(latest.getPackageId());
        vo.setTaskId(latest.getTaskId());
        vo.setSnapshotVersion(latest.getSnapshotVersion());
        vo.setContentJson(latest.getContentJson());
        vo.setStatus(latest.getStatus());
        vo.setGeneratedBy(latest.getGeneratedBy());
        vo.setGeneratedAt(latest.getGeneratedAt());
        vo.setIssuedBy(latest.getIssuedBy());
        vo.setIssuedAt(latest.getIssuedAt());
        vo.setCreateBy(latest.getCreateBy());
        vo.setCreateTime(latest.getCreateTime());
        return vo;
    }

    /**
     * 组装开工包内容（结构见 SPEC §4.5）。
     *
     * @param task            任务
     * @param confirmed       已确认事实
     * @param gate            闸门结论
     * @param snapshotVersion 冻结的版本
     * @return 内容 Map
     */
    private Map<String, Object> buildContent(CpTask task, List<CpFactSnapshot> confirmed,
                                            ContentGateEngine.GateResult gate, int snapshotVersion) {
        ContentDeliverableTypeEnum type = ContentDeliverableTypeEnum.find(task.getDeliverableType());
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("taskNo", task.getTaskNo());
        content.put("taskName", task.getTaskName());
        content.put("deliverableType", task.getDeliverableType());
        content.put("deliverableTypeName", type == null ? task.getDeliverableType() : type.getDesc());
        content.put("snapshotVersion", snapshotVersion);

        // 产品
        Map<String, Object> product = new LinkedHashMap<>();
        if (task.getProductId() != null) {
            CpProduct p = productMapper.selectById(task.getProductId());
            if (p != null) {
                product.put("productCode", p.getProductCode());
                product.put("productName", p.getProductName());
                product.put("skuCode", p.getSkuCode());
                product.put("skuName", p.getSkuName());
                product.put("version", p.getVersion());
            }
        }
        product.put("skuCodeFromTask", task.getSkuCode());
        content.put("product", product);

        // 已确认事实（含来源证据）
        List<Map<String, Object>> facts = new ArrayList<>();
        for (CpFactSnapshot s : confirmed) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("fieldCode", s.getFieldCode());
            f.put("fieldName", s.getFieldName());
            f.put("value", s.getFieldValue());
            f.put("sourceLocator", s.getSourceLocator());
            f.put("sourceExcerpt", s.getSourceExcerpt());
            f.put("confirmedAt", s.getConfirmedAt() == null ? null : s.getConfirmedAt().toString());
            facts.add(f);
        }
        content.put("confirmedFacts", facts);

        // 素材：本阶段只提供当前任务附件
        content.put("assets", buildAssets(task.getTaskId()));

        // 文案：本阶段没有文案确认模型
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("confirmed", List.of());
        copy.put("forbidden", List.of());
        copy.put("note", "文案确认与禁用表达属品牌环节，阶段1A 未建内容模型，此处不代替品牌判断");
        content.put("copy", copy);

        // 缺口：未满足的条件项（可按替代方案开工，但必须记录）
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (CpGateRule rule : gate.getConditionUnsatisfied()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("fieldCode", rule.getFieldCode());
            g.put("fieldName", rule.getFieldName());
            g.put("note", "可按替代方案开工，但需确定替代方案或补齐");
            gaps.add(g);
        }
        content.put("gaps", gaps);

        // 允许的 AI 动作：本阶段仅本地解析与预检
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(aiAction("document_parse", "资料解析", "本地规则抽取资料字段"));
        actions.add(aiAction("brief_precheck", "资料预检", "本地规则比对冲突与缺失"));
        content.put("allowedAiActions", actions);

        // 不可修改项：设计文档 §10 明确 AI 生产 Agent 禁止自由重绘这些内容
        content.put("immutableItems", List.of(
            "产品主体结构与外观",
            "品牌 Logo",
            "包装文字与参数标识"));

        // 输出规格
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("outputSize", factValue(confirmed, "output_size"));
        spec.put("resolution", factValue(confirmed, "resolution"));
        spec.put("acceptance", "以闸门规则与已确认事实为准");
        content.put("spec", spec);

        // 责任人
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("ownerId", task.getOwnerId());
        owner.put("ownerName", task.getOwnerName());
        content.put("owner", owner);
        content.put("deadline", task.getDeadline() == null ? null : task.getDeadline().toString());

        // 内容单元：阶段1B 落地
        Map<String, Object> units = new LinkedHashMap<>();
        units.put("list", List.of());
        units.put("note", "页面/版面清单（内容单元）属阶段1B");
        content.put("contentUnits", units);
        return content;
    }

    /**
     * 组装素材段。
     *
     * @param taskId 任务ID
     * @return 素材 Map
     */
    private Map<String, Object> buildAssets(Long taskId) {
        List<CpTaskFile> files = taskFileMapper.selectList(new LambdaQueryWrapper<CpTaskFile>()
            .eq(CpTaskFile::getTaskId, taskId).orderByAsc(CpTaskFile::getCreateTime));
        List<Map<String, Object>> product = new ArrayList<>();
        List<Map<String, Object>> other = new ArrayList<>();
        for (CpTaskFile f : files) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fileId", f.getFileId());
            m.put("fileName", f.getFileName());
            m.put("fileKind", f.getFileKind());
            m.put("dataLevel", f.getDataLevel());
            // Excel/Word/PDF 是资料依据，其余（图片/设计稿等）归为待分类素材
            if ("EXCEL".equals(f.getFileKind()) || "WORD".equals(f.getFileKind()) || "PDF".equals(f.getFileKind())) {
                product.add(m);
            } else {
                other.add(m);
            }
        }
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put("productBasis", product);
        assets.put("unclassified", other);
        assets.put("brand", List.of());
        assets.put("reference", List.of());
        assets.put("note", "素材分层、已批准素材与谱系属阶段3；本阶段只列出当前任务附件，不作素材复用承诺");
        return assets;
    }

    /**
     * 构造一条 AI 动作。
     *
     * @param capability 能力编码
     * @param name       名称
     * @param desc       说明
     * @return Map
     */
    private Map<String, Object> aiAction(String capability, String name, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("capability", capability);
        m.put("name", name);
        m.put("desc", desc);
        m.put("scope", "LOCAL_ONLY");
        return m;
    }

    /**
     * 取某字段的已确认值。
     *
     * @param confirmed 已确认事实
     * @param fieldCode 字段编码
     * @return 值，无则 null
     */
    private String factValue(List<CpFactSnapshot> confirmed, String fieldCode) {
        for (CpFactSnapshot s : confirmed) {
            if (fieldCode.equals(s.getFieldCode())) {
                return s.getFieldValue();
            }
        }
        return null;
    }

    /**
     * 加载任务。
     *
     * @param taskId 任务ID
     * @return 任务
     */
    private CpTask loadTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        CpTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在");
        }
        return task;
    }

    /**
     * 加载开工包。
     *
     * @param packageId 开工包ID
     * @return 开工包
     */
    private CpWorkPackage loadPackage(Long packageId) {
        if (packageId == null) {
            throw new ServiceException("开工包ID不能为空");
        }
        CpWorkPackage pkg = workPackageMapper.selectById(packageId);
        if (pkg == null) {
            throw new ServiceException("开工包不存在");
        }
        return pkg;
    }

}
