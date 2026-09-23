package org.dromara.content.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.CpAsyncJob;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.CpGateRule;
import org.dromara.content.domain.CpInteractionCard;
import org.dromara.content.domain.CpProduct;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.CpWorkPackage;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.domain.vo.CpAsyncJobVo;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.domain.vo.CpInteractionCardVo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.domain.vo.CpTaskVo;
import org.dromara.content.domain.vo.CpWorkPackageVo;
import org.dromara.content.domain.vo.ContentFactSyncVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.enums.ContentAsyncJobTypeEnum;
import org.dromara.content.enums.ContentCardStatusEnum;
import org.dromara.content.enums.ContentCardTypeEnum;
import org.dromara.content.enums.ContentDeliverableTypeEnum;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.enums.ContentFileKindEnum;
import org.dromara.content.enums.ContentGateLevelEnum;
import org.dromara.content.enums.ContentParseStatusEnum;
import org.dromara.content.enums.ContentTaskStatusEnum;
import org.dromara.content.helper.ContentAsyncExecutor;
import org.dromara.content.helper.ContentFieldAlias;
import org.dromara.content.helper.ContentGateEngine;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.mapper.CpAsyncJobMapper;
import org.dromara.content.mapper.CpFactSnapshotMapper;
import org.dromara.content.mapper.CpInteractionCardMapper;
import org.dromara.content.mapper.CpProductMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.mapper.CpWorkPackageMapper;
import org.dromara.content.service.IContentGateRuleService;
import org.dromara.content.service.IContentTaskGateService;
import org.dromara.content.service.IContentTaskService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 内容生产任务服务实现。
 *
 * <p>核心链路：上传资料 → 解析（经治理层 {@code document_parse}）→ 候选值以 {@code PENDING} 落库
 * → 预检（{@code brief_precheck}）生成互动卡 → 人工确认 → 闸门重算决定能否开工。</p>
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentTaskServiceImpl implements IContentTaskService {

    /**
     * 任务号日期格式
     */
    private static final DateTimeFormatter TASK_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 默认数据等级
     */
    private static final String DEFAULT_DATA_LEVEL = "INTERNAL";

    /**
     * 产品事实的「主数据」来源标注（写入 {@code source_locator}，让事实清单能显示出处）
     */
    private static final String PRODUCT_MASTER_SOURCE = "产品与SKU主数据";

    /**
     * 主数据来源事实的备注（说明可追溯性与效力边界）
     */
    private static final String PRODUCT_MASTER_REMARK =
        "由任务所选产品的「产品与SKU」主数据写入（人工维护来源）；如与该产品的资料不一致，以资料确认结果为准";

    /**
     * 可由产品主数据同步的字段 → 中文名。
     *
     * <p>刻意只放这两个：{@code cp_product} 只有名称与 SKU 两个字段能对上闸门要求，
     * 其余强制项（主体版本/颜色/数量/参数/包装版本）在该表里没有对应列。</p>
     */
    private static final Map<String, String> PRODUCT_MASTER_FIELD_LABELS = Map.of(
        "product_name", "产品名称",
        "sku_code", "SKU"
    );

    /**
     * 任务 Mapper
     */
    private final CpTaskMapper taskMapper;

    /**
     * 附件 Mapper
     */
    private final CpTaskFileMapper taskFileMapper;

    /**
     * 事实快照 Mapper
     */
    private final CpFactSnapshotMapper factSnapshotMapper;

    /**
     * 互动卡 Mapper
     */
    private final CpInteractionCardMapper cardMapper;

    /**
     * 作业 Mapper
     */
    private final CpAsyncJobMapper asyncJobMapper;

    /**
     * 产品 Mapper
     */
    private final CpProductMapper productMapper;

    /**
     * 开工包 Mapper
     */
    private final CpWorkPackageMapper workPackageMapper;

    /**
     * 对象存储助手
     */
    private final ContentOssHelper ossHelper;

    /**
     * 异步执行器
     */
    private final ContentAsyncExecutor asyncExecutor;

    /**
     * 闸门规则服务
     */
    private final IContentGateRuleService gateRuleService;

    /**
     * 闸门重算服务
     */
    private final IContentTaskGateService taskGateService;

    /**
     * 治理层调用编排（内容模块只调用能力编码，不直接调用模型地址）
     */
    private final IAigInvokeService aigInvokeService;

    @Override
    public PageResult<CpTaskVo> queryPage(ContentTaskBo bo, PageQuery pageQuery) {
        ContentTaskBo q = bo == null ? new ContentTaskBo() : bo;
        LambdaQueryWrapper<CpTask> wrapper = new LambdaQueryWrapper<CpTask>()
            .like(StringUtils.isNotBlank(q.getQueryTaskNo()), CpTask::getTaskNo, q.getQueryTaskNo())
            .like(StringUtils.isNotBlank(q.getQueryTaskName()), CpTask::getTaskName, q.getQueryTaskName())
            .eq(StringUtils.isNotBlank(q.getQueryDeliverableType()), CpTask::getDeliverableType, q.getQueryDeliverableType())
            .eq(StringUtils.isNotBlank(q.getQueryStatus()), CpTask::getStatus, q.getQueryStatus())
            .eq(q.getQueryOwnerId() != null, CpTask::getOwnerId, q.getQueryOwnerId())
            .eq(q.getProductId() != null, CpTask::getProductId, q.getProductId())
            .orderByDesc(CpTask::getCreateTime);
        var voPage = taskMapper.selectVoPage(pageQuery.build(), wrapper);
        List<CpTaskVo> rows = voPage.getRecords() == null ? List.of() : voPage.getRecords();
        fillProductName(rows);
        fillCardCount(rows);
        return PageResult.build(rows, voPage.getTotal());
    }

    @Override
    public ContentTaskDetailVo getDetail(Long taskId) {
        CpTask task = load(taskId);
        ContentTaskDetailVo vo = new ContentTaskDetailVo();
        vo.setTask(BeanUtil.copyProperties(task, CpTaskVo.class));

        vo.setFiles(taskFileMapper.selectVoList(new LambdaQueryWrapper<CpTaskFile>()
            .eq(CpTaskFile::getTaskId, taskId).orderByAsc(CpTaskFile::getCreateTime)));
        vo.setFacts(factSnapshotMapper.selectVoList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId).orderByAsc(CpFactSnapshot::getFieldCode)));
        vo.setCards(cardMapper.selectVoList(new LambdaQueryWrapper<CpInteractionCard>()
            .eq(CpInteractionCard::getTaskId, taskId)
            .orderByDesc(CpInteractionCard::getBlocking)
            .orderByAsc(CpInteractionCard::getCreateTime)));
        vo.setJobs(asyncJobMapper.selectVoList(new LambdaQueryWrapper<CpAsyncJob>()
            .eq(CpAsyncJob::getTaskId, taskId).orderByDesc(CpAsyncJob::getJobId)));
        // GET 不应产生写副作用，故只判定不落库
        vo.setGate(taskGateService.evaluate(taskId));
        List<CpWorkPackage> pkgs = workPackageMapper.selectList(new LambdaQueryWrapper<CpWorkPackage>()
            .eq(CpWorkPackage::getTaskId, taskId).orderByDesc(CpWorkPackage::getPackageId));
        if (CollUtil.isNotEmpty(pkgs)) {
            vo.setWorkPackage(BeanUtil.copyProperties(pkgs.get(0), CpWorkPackageVo.class));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ContentTaskBo bo) {
        if (ContentDeliverableTypeEnum.find(bo.getDeliverableType()) == null) {
            throw new ServiceException("交付类型非法：" + bo.getDeliverableType());
        }
        if (bo.getProductId() != null && productMapper.selectById(bo.getProductId()) == null) {
            throw new ServiceException("产品不存在：" + bo.getProductId());
        }
        CpTask entity = BeanUtil.copyProperties(bo, CpTask.class);
        entity.setTaskId(null);
        entity.setStatus(ContentTaskStatusEnum.DRAFT.getCode());
        entity.setDataLevel(StringUtils.isBlank(bo.getDataLevel()) ? DEFAULT_DATA_LEVEL : bo.getDataLevel());
        entity.setAllowExternal(ContentConstants.NO);
        if (StringUtils.isBlank(entity.getOwnerName()) && bo.getOwnerId() != null) {
            entity.setOwnerName(String.valueOf(bo.getOwnerId()));
        }
        // SKU 留空时回落到所选产品的 SKU：界面一直承诺「默认取产品的SKU」，但此前前后端都没实现，
        // 实测选中产品后 cp_task.sku_code 仍是空串。放在服务端兜底，前端与接口调用方行为才一致。
        if (StringUtils.isBlank(entity.getSkuCode()) && entity.getProductId() != null) {
            CpProduct skuSource = productMapper.selectById(entity.getProductId());
            if (skuSource != null && StringUtils.isNotBlank(skuSource.getSkuCode())) {
                entity.setSkuCode(skuSource.getSkuCode());
            }
        }
        insertTaskWithGeneratedNo(entity);
        // 把所选产品在「产品与SKU」里的名称/SKU 直接写成已确认事实：
        // 否则用户明明选了产品，闸门仍要求把这两项再录一遍（实测确认过该现象）。
        if (entity.getProductId() != null) {
            ContentFactSyncVo sync = writeProductFacts(entity);
            log.info("新建任务同步产品主数据事实, taskId={}, synced={}, skipped={}, conflicts={}",
                entity.getTaskId(), sync.getSynced(), sync.getSkipped(), sync.getConflicts());
        }
        log.info("新建内容任务, taskId={}, taskNo={}, deliverableType={}",
            entity.getTaskId(), entity.getTaskNo(), entity.getDeliverableType());
        return entity.getTaskId();
    }

    @Override
    public void update(ContentTaskBo bo) {
        CpTask exist = load(bo.getTaskId());
        if (StringUtils.isNotBlank(bo.getDeliverableType())
            && ContentDeliverableTypeEnum.find(bo.getDeliverableType()) == null) {
            throw new ServiceException("交付类型非法：" + bo.getDeliverableType());
        }
        CpTask entity = BeanUtil.copyProperties(bo, CpTask.class);
        entity.setTaskId(exist.getTaskId());
        // 任务号与状态不由编辑接口改写：状态只能经闸门重算流转
        entity.setTaskNo(null);
        entity.setStatus(null);
        taskMapper.updateById(entity);
        // 换了产品或改了 SKU 时把主数据事实同步过来（幂等：取值已一致就不重复写入）
        if (entity.getProductId() != null) {
            CpTask latest = taskMapper.selectById(exist.getTaskId());
            if (latest != null) {
                writeProductFacts(latest);
            }
        }
    }

    @Override
    public ContentFactSyncVo syncProductFacts(Long taskId) {
        CpTask task = load(taskId);
        ContentFactSyncVo result = writeProductFacts(task);
        if (task.getProductId() != null) {
            // 显式动作（人工点同步）：事实变了就重算闸门，与「确认/否决/手工录入」同口径。
            // 注意 create/update 走的是 writeProductFacts（不重算状态）——任务状态只由
            // 闸门重算这一条规则入口流转，创建时不该顺手把 DRAFT 改掉。
            taskGateService.recheckAndApply(taskId);
        }
        return result;
    }

    /**
     * 把产品与SKU模块的主数据写成产品事实（只写事实，不改任务状态）。
     *
     * <p><b>只同步主数据确实拥有的两个字段</b>：{@code product_name} 与 {@code sku_code}。
     * 闸门要的 main_version / color / quantity / spec_params / package_version 在
     * {@code cp_product} 里根本没有对应列，只能来自产品资料或人工录入。</p>
     *
     * <p><b>冲突保护</b>：同字段已有不同取值时，本次写入降级为「待确认」——
     * 让主数据与资料的分歧以冲突候选的形式交给人裁定，而不是自动把主数据值变成事实。</p>
     *
     * @param task 任务（需含 productId）
     * @return 同步结果
     */
    private ContentFactSyncVo writeProductFacts(CpTask task) {
        ContentFactSyncVo vo = new ContentFactSyncVo();
        if (task == null || task.getProductId() == null) {
            vo.getMessages().add("任务未关联产品，无可同步的产品与SKU信息");
            return vo;
        }
        CpProduct product = productMapper.selectById(task.getProductId());
        if (product == null) {
            vo.getMessages().add("任务关联的产品已不存在，请重新选择产品");
            return vo;
        }

        // SKU 以任务上填写的为准（任务可选具体 SKU），任务没填才回落到产品的默认 SKU
        Map<String, String> masterValues = new LinkedHashMap<>();
        masterValues.put("product_name", product.getProductName());
        masterValues.put("sku_code", StringUtils.isNotBlank(task.getSkuCode())
            ? task.getSkuCode() : product.getSkuCode());

        String sourceLocator = PRODUCT_MASTER_SOURCE + " · 产品编码 " + product.getProductCode();
        for (Map.Entry<String, String> entry : masterValues.entrySet()) {
            String code = entry.getKey();
            String value = entry.getValue();
            String label = PRODUCT_MASTER_FIELD_LABELS.getOrDefault(code, code);
            if (StringUtils.isBlank(value)) {
                vo.getMessages().add(label + "：产品与SKU主数据中该项为空，未写入");
                continue;
            }
            List<CpFactSnapshot> rows = factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
                .eq(CpFactSnapshot::getTaskId, task.getTaskId())
                .eq(CpFactSnapshot::getFieldCode, code));

            boolean sameConfirmed = false;
            boolean differentExists = false;
            int maxVersion = 1;
            String existingName = null;
            for (CpFactSnapshot row : rows) {
                if (StringUtils.isBlank(existingName)) {
                    existingName = row.getFieldName();
                }
                if (row.getSnapshotVersion() != null && row.getSnapshotVersion() > maxVersion) {
                    maxVersion = row.getSnapshotVersion();
                }
                if (value.equals(row.getFieldValue())) {
                    if (ContentFactConfirmStatusEnum.CONFIRMED.getCode().equals(row.getConfirmStatus())) {
                        sameConfirmed = true;
                    }
                } else {
                    differentExists = true;
                }
            }
            if (sameConfirmed) {
                vo.setSkipped(vo.getSkipped() + 1);
                vo.getMessages().add(label + "：已有取值相同的已确认事实，未重复写入");
                continue;
            }

            CpFactSnapshot entity = new CpFactSnapshot();
            entity.setTaskId(task.getTaskId());
            entity.setSnapshotVersion(maxVersion + 1);
            entity.setFieldCode(code);
            entity.setFieldName(StringUtils.blankToDefault(existingName, ContentFieldAlias.fieldName(code)));
            entity.setFieldValue(value);
            entity.setSourceLocator(sourceLocator);
            entity.setConfirmedBy(LoginHelper.getUserId());
            entity.setConfirmedAt(LocalDateTime.now());
            if (differentExists) {
                // 与既有取值冲突：不自动确认，留给人工裁定
                entity.setConfirmStatus(ContentFactConfirmStatusEnum.PENDING.getCode());
                entity.setRemark(PRODUCT_MASTER_REMARK + "；与任务中已有取值不一致，故仅作待确认候选，请人工裁定");
                factSnapshotMapper.insert(entity);
                vo.setConflicts(vo.getConflicts() + 1);
                vo.getMessages().add(label + "：与任务中已有取值不一致，已作为「待确认」候选写入，"
                    + "不会自动成为事实；请在事实清单或互动卡中裁定");
            } else {
                entity.setConfirmStatus(ContentFactConfirmStatusEnum.CONFIRMED.getCode());
                entity.setRemark(PRODUCT_MASTER_REMARK);
                factSnapshotMapper.insert(entity);
                vo.setSynced(vo.getSynced() + 1);
                vo.getMessages().add(label + "：已按产品与SKU主数据写入并确认为事实（" + value + "）");
            }
        }
        log.info("产品主数据事实同步, taskId={}, productCode={}, synced={}, skipped={}, conflicts={}",
            task.getTaskId(), product.getProductCode(), vo.getSynced(), vo.getSkipped(), vo.getConflicts());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long taskId) {
        load(taskId);
        taskMapper.deleteById(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadFile(Long taskId, String dataLevel, MultipartFile file) {
        CpTask task = load(taskId);
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        if (file.getSize() > ContentConstants.MAX_FILE_SIZE) {
            throw new ServiceException("文件大小超过 " + (ContentConstants.MAX_FILE_SIZE / 1024 / 1024) + "MB 限制");
        }
        String originalName = StringUtils.blankToDefault(file.getOriginalFilename(), "unnamed");
        String ext = extOf(originalName);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new ServiceException("文件读取失败");
        }

        CpTaskFile entity = new CpTaskFile();
        entity.setTaskId(taskId);
        entity.setFileName(originalName);
        entity.setFileExt(ext);
        entity.setFileSize((long) bytes.length);
        entity.setFileKind(ContentFileKindEnum.ofExt(ext).getCode());
        entity.setSourceType("UPLOAD");
        entity.setDataLevel(StringUtils.isBlank(dataLevel) ? task.getDataLevel() : dataLevel);
        entity.setParseStatus(ContentParseStatusEnum.PENDING.getCode());
        taskFileMapper.insert(entity);

        String key = ossHelper.buildFileKey(taskId, entity.getFileId(), ext);
        ossHelper.put(key, bytes);
        CpTaskFile update = new CpTaskFile();
        update.setFileId(entity.getFileId());
        update.setFileRef(key);
        taskFileMapper.updateById(update);

        log.info("上传资料附件, taskId={}, fileId={}, ext={}, size={}",
            taskId, entity.getFileId(), ext, bytes.length);
        return entity.getFileId();
    }

    @Override
    public List<CpTaskFileVo> listFiles(Long taskId) {
        load(taskId);
        return taskFileMapper.selectVoList(new LambdaQueryWrapper<CpTaskFile>()
            .eq(CpTaskFile::getTaskId, taskId).orderByAsc(CpTaskFile::getCreateTime));
    }

    @Override
    public Long triggerParse(Long taskId) {
        load(taskId);
        return asyncExecutor.submit(taskId, ContentAsyncJobTypeEnum.PARSE, () -> parseWorker(taskId));
    }

    @Override
    public Long triggerPrecheck(Long taskId) {
        load(taskId);
        return asyncExecutor.submit(taskId, ContentAsyncJobTypeEnum.PRECHECK, () -> precheckWorker(taskId));
    }

    @Override
    public ContentGateEngine.GateResult recheck(Long taskId) {
        return taskGateService.recheckAndApply(taskId);
    }

    // ------------------------------------------------------------------
    // 异步工作：解析
    // ------------------------------------------------------------------

    /**
     * 解析工作：逐份资料经治理层 {@code document_parse} 抽取候选字段。
     *
     * @param taskId 任务ID
     */
    private void parseWorker(Long taskId) {
        CpTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        markTaskStatus(taskId, ContentTaskStatusEnum.PARSING.getCode(), null);

        List<CpTaskFile> files = taskFileMapper.selectList(new LambdaQueryWrapper<CpTaskFile>()
            .eq(CpTaskFile::getTaskId, taskId).orderByAsc(CpTaskFile::getCreateTime));
        for (CpTaskFile file : files) {
            // 已解析成功的不重复解析
            if (ContentParseStatusEnum.DONE.getCode().equals(file.getParseStatus())) {
                continue;
            }
            parseOne(task, file);
        }

        CpTask done = new CpTask();
        done.setTaskId(taskId);
        done.setParseDoneAt(java.time.LocalDateTime.now());
        taskMapper.updateById(done);

        // 解析完立即重算闸门：此时所有候选仍是 PENDING，闸门会判为待确认
        taskGateService.recheckAndApply(taskId);
    }

    /**
     * 解析单个文件。
     *
     * @param task 任务
     * @param file 附件
     */
    private void parseOne(CpTask task, CpTaskFile file) {
        markFileStatus(file.getFileId(), ContentParseStatusEnum.PARSING, null);
        if (StringUtils.isBlank(file.getFileRef())) {
            markFileStatus(file.getFileId(), ContentParseStatusEnum.FAILED, "文件引用缺失，请重新上传");
            return;
        }
        byte[] bytes;
        try {
            bytes = ossHelper.getBytes(file.getFileRef());
        } catch (ServiceException e) {
            markFileStatus(file.getFileId(), ContentParseStatusEnum.FAILED, e.getMessage());
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileBytes", bytes);
        payload.put("fileExt", file.getFileExt());
        payload.put("fileName", file.getFileName());

        AigInvokeBo invokeBo = new AigInvokeBo();
        invokeBo.setCapabilityCode(ContentConstants.CAP_DOCUMENT_PARSE);
        invokeBo.setDataLevel(StringUtils.isBlank(file.getDataLevel()) ? task.getDataLevel() : file.getDataLevel());
        invokeBo.setPayload(payload);

        AigInvokeVo result;
        try {
            result = aigInvokeService.invoke(invokeBo);
        } catch (Exception e) {
            markFileStatus(file.getFileId(), ContentParseStatusEnum.FAILED, "解析调用异常，请联系管理员");
            log.error("资料解析调用异常, taskId={}, fileId={}, exception={}",
                task.getTaskId(), file.getFileId(), e.getClass().getSimpleName());
            return;
        }
        if (result == null || !"MODEL".equals(result.getDecision()) || StringUtils.isBlank(result.getOutput())) {
            String reason = result == null ? "解析未返回结果"
                : StringUtils.blankToDefault(result.getReason(), "解析被治理策略拒绝（" + result.getDecision() + "）");
            markFileStatus(file.getFileId(), ContentParseStatusEnum.FAILED, reason);
            return;
        }

        Map<String, Object> out;
        try {
            out = JsonUtils.parseMap(result.getOutput());
        } catch (Exception e) {
            markFileStatus(file.getFileId(), ContentParseStatusEnum.FAILED, "解析结果格式异常");
            return;
        }
        if (out == null) {
            markFileStatus(file.getFileId(), ContentParseStatusEnum.FAILED, "解析结果为空");
            return;
        }

        boolean extracted = !Boolean.FALSE.equals(out.get("extracted"));
        if (!extracted) {
            // 类型不支持或文件无法解析：明确记为已跳过并保留可读原因
            markFileStatus(file.getFileId(), ContentParseStatusEnum.SKIPPED,
                firstString(out.get("skipReason"), "该文件暂不支持本地解析"));
            return;
        }

        List<Map<String, Object>> candidates = asMapList(out.get("candidates"));
        int saved = 0;
        for (Map<String, Object> c : candidates) {
            String fieldCode = str(c.get("fieldCode"));
            String value = str(c.get("value"));
            if (StringUtils.isBlank(fieldCode) || StringUtils.isBlank(value)) {
                continue;
            }
            CpFactSnapshot snap = new CpFactSnapshot();
            snap.setTaskId(task.getTaskId());
            snap.setSnapshotVersion(1);
            snap.setFieldCode(fieldCode);
            snap.setFieldName(str(c.get("fieldName")));
            snap.setFieldValue(value);
            snap.setSourceFileId(file.getFileId());
            snap.setSourceLocator(str(c.get("locator")));
            snap.setSourceExcerpt(str(c.get("excerpt")));
            // 红线：解析结果一律待确认，绝不直接成为产品事实
            snap.setConfirmStatus(ContentFactConfirmStatusEnum.PENDING.getCode());
            factSnapshotMapper.insert(snap);
            saved++;
        }
        markFileStatus(file.getFileId(), ContentParseStatusEnum.DONE, null);
        log.info("资料解析入库, taskId={}, fileId={}, candidateCount={}", task.getTaskId(), file.getFileId(), saved);
    }

    // ------------------------------------------------------------------
    // 异步工作：预检
    // ------------------------------------------------------------------

    /**
     * 预检工作：冲突与缺失检测，并生成互动确认卡。
     *
     * @param taskId 任务ID
     */
    private void precheckWorker(Long taskId) {
        CpTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        List<CpFactSnapshot> snapshots = factSnapshotMapper.selectList(new LambdaQueryWrapper<CpFactSnapshot>()
            .eq(CpFactSnapshot::getTaskId, taskId));
        List<CpGateRule> rules = gateRuleService.listEnabledRules(task.getDeliverableType());

        // 组装 payload（只传字段与证据，不传文件本体）
        List<Map<String, Object>> candidates = new ArrayList<>();
        Map<Long, String> fileNameById = new LinkedHashMap<>();
        for (CpTaskFile f : taskFileMapper.selectList(new LambdaQueryWrapper<CpTaskFile>()
            .eq(CpTaskFile::getTaskId, taskId))) {
            fileNameById.put(f.getFileId(), f.getFileName());
        }
        for (CpFactSnapshot s : snapshots) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("snapshotId", s.getSnapshotId());
            m.put("fieldCode", s.getFieldCode());
            m.put("fieldName", s.getFieldName());
            m.put("value", s.getFieldValue());
            m.put("locator", s.getSourceLocator());
            m.put("excerpt", s.getSourceExcerpt());
            m.put("sourceFileName", s.getSourceFileId() == null ? null : fileNameById.get(s.getSourceFileId()));
            candidates.add(m);
        }
        List<Map<String, Object>> rulePayload = new ArrayList<>();
        for (CpGateRule r : rules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldCode", r.getFieldCode());
            m.put("fieldName", r.getFieldName());
            m.put("gateLevel", r.getGateLevel());
            m.put("requirePresent", r.getRequirePresent());
            rulePayload.add(m);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidates", candidates);
        payload.put("gateRules", rulePayload);

        AigInvokeBo invokeBo = new AigInvokeBo();
        invokeBo.setCapabilityCode(ContentConstants.CAP_BRIEF_PRECHECK);
        invokeBo.setDataLevel(task.getDataLevel());
        invokeBo.setPayload(payload);

        AigInvokeVo result = aigInvokeService.invoke(invokeBo);
        if (result == null || !"MODEL".equals(result.getDecision()) || StringUtils.isBlank(result.getOutput())) {
            log.warn("资料预检未获得结果, taskId={}, decision={}", taskId, result == null ? "null" : result.getDecision());
            taskGateService.recheckAndApply(taskId);
            return;
        }
        Map<String, Object> out = JsonUtils.parseMap(result.getOutput());
        if (out == null) {
            taskGateService.recheckAndApply(taskId);
            return;
        }

        Map<String, CpGateRule> ruleByField = new LinkedHashMap<>();
        for (CpGateRule r : rules) {
            ruleByField.put(r.getFieldCode(), r);
        }

        int created = 0;
        for (Map<String, Object> conflict : asMapList(out.get("conflicts"))) {
            created += createConflictCard(task, conflict, ruleByField);
        }
        for (Map<String, Object> missing : asMapList(out.get("missings"))) {
            created += createMissingCard(task, missing);
        }
        log.info("资料预检完成, taskId={}, conflictCount={}, missingCount={}, cardsCreated={}",
            taskId, asMapList(out.get("conflicts")).size(), asMapList(out.get("missings")).size(), created);

        taskGateService.recheckAndApply(taskId);
    }

    /**
     * 生成冲突卡。
     *
     * @param task        任务
     * @param conflict    冲突项
     * @param ruleByField 字段 → 规则
     * @return 生成条数
     */
    private int createConflictCard(CpTask task, Map<String, Object> conflict,
                                   Map<String, CpGateRule> ruleByField) {
        String fieldCode = str(conflict.get("fieldCode"));
        if (StringUtils.isBlank(fieldCode)) {
            return 0;
        }
        List<Map<String, Object>> values = asMapList(conflict.get("values"));
        if (values.size() < 2) {
            return 0;
        }
        ContentGateLevelEnum level = resolveLevel(ruleByField.get(fieldCode));

        StringBuilder q = new StringBuilder();
        q.append("同一字段存在多个不一致的取值，请确认以哪个为准：");
        for (Map<String, Object> v : values) {
            q.append("\n· ").append(str(v.get("value")));
            String from = str(v.get("sourceFileName"));
            String loc = str(v.get("locator"));
            if (StringUtils.isNotBlank(from) || StringUtils.isNotBlank(loc)) {
                q.append("（来源：").append(StringUtils.blankToDefault(from, "未知文件"));
                if (StringUtils.isNotBlank(loc)) {
                    q.append(" ").append(loc);
                }
                q.append("）");
            }
        }

        clearPendingCards(task.getTaskId(), ContentCardTypeEnum.CONFLICT, fieldCode);
        CpInteractionCard card = new CpInteractionCard();
        card.setTaskId(task.getTaskId());
        card.setCardType(ContentCardTypeEnum.CONFLICT.getCode());
        card.setFieldCode(fieldCode);
        card.setTitle("字段「" + str(conflict.get("fieldName")) + "」存在多个取值待确认");
        card.setQuestion(q.toString());
        card.setEvidenceJson(JsonUtils.toJsonString(values));
        card.setImpactJson(buildImpact(task, str(conflict.get("fieldName"))));
        card.setOptionsJson(JsonUtils.toJsonString(buildOptions(values)));
        card.setGateLevel(level.getCode());
        card.setBlocking(level.blocking() ? ContentConstants.YES : ContentConstants.NO);
        applyAssignee(card, task);
        card.setStatus(ContentCardStatusEnum.PENDING.getCode());
        cardMapper.insert(card);
        return 1;
    }

    /**
     * 生成缺料卡。
     *
     * @param task    任务
     * @param missing 缺失项
     * @return 生成条数
     */
    private int createMissingCard(CpTask task, Map<String, Object> missing) {
        String fieldCode = str(missing.get("fieldCode"));
        if (StringUtils.isBlank(fieldCode)) {
            return 0;
        }
        ContentGateLevelEnum level = ContentGateLevelEnum.find(str(missing.get("gateLevel")));
        if (level == null) {
            level = ContentGateLevelEnum.CONDITION;
        }
        String fieldName = StringUtils.blankToDefault(str(missing.get("fieldName")), fieldCode);

        clearPendingCards(task.getTaskId(), ContentCardTypeEnum.MISSING, fieldCode);
        CpInteractionCard card = new CpInteractionCard();
        card.setTaskId(task.getTaskId());
        card.setCardType(ContentCardTypeEnum.MISSING.getCode());
        card.setFieldCode(fieldCode);
        card.setTitle("缺少「" + fieldName + "」，无法开工前确认");
        card.setQuestion("已上传的资料中未找到「" + fieldName + "」。请补充资料，或由责任人确认后手工录入。");
        card.setEvidenceJson(JsonUtils.toJsonString(List.of()));
        card.setImpactJson(buildImpact(task, fieldName));
        card.setOptionsJson(JsonUtils.toJsonString(buildMissingOptions()));
        card.setGateLevel(level.getCode());
        card.setBlocking(level.blocking() ? ContentConstants.YES : ContentConstants.NO);
        applyAssignee(card, task);
        card.setStatus(ContentCardStatusEnum.PENDING.getCode());
        cardMapper.insert(card);
        return 1;
    }

    /**
     * 取字段对应的闸门等级；无规则时按非阻断提醒处理。
     *
     * @param rule 规则（可空）
     * @return 闸门等级
     */
    private ContentGateLevelEnum resolveLevel(CpGateRule rule) {
        if (rule == null) {
            return ContentGateLevelEnum.NOTICE;
        }
        ContentGateLevelEnum level = ContentGateLevelEnum.find(rule.getGateLevel());
        return level == null ? ContentGateLevelEnum.NOTICE : level;
    }

    /**
     * 构建影响对象 JSON。
     * <p>只描述「该字段未确认会影响交付物定稿」这一事实，不臆测具体页面清单——
     * 页面级影响面属阶段1B 的内容单元范围。</p>
     *
     * @param task      任务
     * @param fieldName 字段名
     * @return JSON
     */
    private String buildImpact(CpTask task, String fieldName) {
        ContentDeliverableTypeEnum type = ContentDeliverableTypeEnum.find(task.getDeliverableType());
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("deliverableType", task.getDeliverableType());
        impact.put("deliverableTypeName", type == null ? task.getDeliverableType() : type.getDesc());
        impact.put("fieldName", fieldName);
        impact.put("note", "该信息确认前，涉及它的交付物内容不得定稿");
        return JsonUtils.toJsonString(List.of(impact));
    }

    /**
     * 冲突卡选项：每个候选值一个选项，另加「填写其他值」与「暂不确认并阻断」。
     *
     * @param values 候选值
     * @return 选项列表
     */
    private List<Map<String, Object>> buildOptions(List<Map<String, Object>> values) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> v : values) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("option", "CONFIRM");
            o.put("label", "确认 " + str(v.get("value")));
            o.put("value", str(v.get("value")));
            o.put("snapshotId", v.get("snapshotId"));
            options.add(o);
        }
        options.add(option("OTHER", "填写其他值", null));
        options.add(option("BLOCK", "暂不确认并阻断相关任务", null));
        return options;
    }

    /**
     * 缺料卡选项。
     *
     * @return 选项列表
     */
    private List<Map<String, Object>> buildMissingOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        options.add(option("SUPPLEMENT", "补充资料后重新解析", null));
        options.add(option("OTHER", "由责任人手工录入", null));
        options.add(option("BLOCK", "暂不确认并阻断相关任务", null));
        return options;
    }

    /**
     * 构造一个选项。
     *
     * @param code  选项编码
     * @param label 展示文案
     * @param value 值
     * @return 选项 Map
     */
    private Map<String, Object> option(String code, String label, String value) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("option", code);
        o.put("label", label);
        o.put("value", value);
        return o;
    }

    /**
     * 按任务指定责任人填充卡片（基线 B6：不按角色自动推导）。
     *
     * @param card 卡片
     * @param task 任务
     */
    private void applyAssignee(CpInteractionCard card, CpTask task) {
        card.setAssigneeId(task.getOwnerId());
        card.setAssigneeName(task.getOwnerName());
        card.setDueAt(task.getDeadline());
    }

    /**
     * 清除同字段同类型的待处理卡，避免重复预检堆叠卡片。
     * <p>只清 {@code PENDING}：已处理、已阻断、已关闭的卡片是人的决策记录，不得动。</p>
     *
     * @param taskId    任务ID
     * @param type      卡片类型
     * @param fieldCode 字段编码
     */
    private void clearPendingCards(Long taskId, ContentCardTypeEnum type, String fieldCode) {
        cardMapper.delete(new LambdaQueryWrapper<CpInteractionCard>()
            .eq(CpInteractionCard::getTaskId, taskId)
            .eq(CpInteractionCard::getCardType, type.getCode())
            .eq(CpInteractionCard::getFieldCode, fieldCode)
            .eq(CpInteractionCard::getStatus, ContentCardStatusEnum.PENDING.getCode()));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 加载任务。
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    private CpTask load(Long taskId) {
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
     * 更新附件解析状态。
     * <p>用 {@code set()} 显式赋值：MyBatis-Plus 默认 NOT_NULL 策略下，
     * 实体里为 null 的 parseMessage 不会进 UPDATE，会导致「重试成功后仍显示上次的失败原因」。</p>
     *
     * @param fileId  附件ID
     * @param status  状态
     * @param message 可读原因（成功传 null）
     */
    private void markFileStatus(Long fileId, ContentParseStatusEnum status, String message) {
        taskFileMapper.update(null, new LambdaUpdateWrapper<CpTaskFile>()
            .eq(CpTaskFile::getFileId, fileId)
            .set(CpTaskFile::getParseStatus, status.getCode())
            .set(CpTaskFile::getParseMessage, message));
    }

    /**
     * 更新任务状态。
     * <p>同样用 {@code set()}：进入解析时若传 null 的阻断原因而不显式清空，
     * 会残留上一轮的阻断文本。</p>
     *
     * @param taskId      任务ID
     * @param status      状态
     * @param blockReason 阻断原因
     */
    private void markTaskStatus(Long taskId, String status, String blockReason) {
        taskMapper.update(null, new LambdaUpdateWrapper<CpTask>()
            .eq(CpTask::getTaskId, taskId)
            .set(CpTask::getStatus, status)
            .set(CpTask::getBlockReason, blockReason));
    }

    /**
     * 生成任务号：CT + yyyyMMdd + 4 位序号。
     *
     * <p><b>必须把已逻辑删除的行算进去</b>：{@code uk_cp_task_no} 唯一索引只建在
     * {@code task_no} 上，不含 {@code del_flag}。若按「可见行」取序号，删光当天任务后
     * 序号会从 0001 重来，插入直接撞唯一索引——现象是「当天把任务删完之后，当天再也建不了新任务」。
     * 所以这里走 {@link CpTaskMapper#selectMaxTaskNoIncludeDeleted(String)}。</p>
     *
     * @param offset 在最大已用序号基础上再往后顺延的位数（用于并发撞号后重试）
     * @return 任务号
     */
    private String nextTaskNo(int offset) {
        String prefix = "CT" + LocalDate.now().format(TASK_NO_DATE);
        String max = taskMapper.selectMaxTaskNoIncludeDeleted(prefix);
        int seq = 1;
        if (StringUtils.isNotBlank(max) && max.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq + offset);
    }

    /**
     * 带任务号生成地插入任务：撞唯一索引时顺延序号重试。
     *
     * <p>任务号是「当天最大序号 + 1」，两人同时建任务会算出同一个号。这里不引入分布式锁
     * （为一个演示级并发量上锁不划算），而是让唯一索引当仲裁者：撞了就换下一个号再试。
     * 重试上限取 20，远超正常并发量，超过就如实失败而不是无限重试。</p>
     *
     * @param task 任务（taskNo 由本方法填充）
     */
    private void insertTaskWithGeneratedNo(CpTask task) {
        for (int offset = 0; offset < 20; offset++) {
            task.setTaskNo(nextTaskNo(offset));
            try {
                taskMapper.insert(task);
                return;
            } catch (DuplicateKeyException e) {
                // 该任务号刚被并发请求占用（或历史软删除行占用），顺延一位重试
                log.warn("任务号被占用，顺延重试, taskNo={}, offset={}", task.getTaskNo(), offset);
            }
        }
        throw new ServiceException("生成任务号失败（当日序号冲突），请稍后重试");
    }

    /**
     * 取扩展名。
     *
     * @param fileName 文件名
     * @return 小写扩展名
     */
    private String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 安全取字符串。
     *
     * @param v 值
     * @return 字符串
     */
    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 取第一个非空字符串。
     *
     * @param v       值
     * @param fallback 兜底
     * @return 字符串
     */
    private String firstString(Object v, String fallback) {
        String s = str(v);
        return StringUtils.isBlank(s) ? fallback : s;
    }

    /**
     * 把对象转为 Map 列表。
     *
     * @param value 值
     * @return Map 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(value instanceof Iterable<?> it)) {
            return out;
        }
        for (Object o : it) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    /**
     * 补齐任务列表的产品名称。
     *
     * @param rows 任务行
     */
    private void fillProductName(List<CpTaskVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (CpTaskVo r : rows) {
            if (r.getProductId() != null && !ids.contains(r.getProductId())) {
                ids.add(r.getProductId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, CpProduct> map = new LinkedHashMap<>();
        for (CpProduct p : productMapper.selectByIds(ids)) {
            map.put(p.getProductId(), p);
        }
        for (CpTaskVo r : rows) {
            CpProduct p = r.getProductId() == null ? null : map.get(r.getProductId());
            if (p != null) {
                r.setProductName(p.getProductName());
                r.setProductCode(p.getProductCode());
            }
        }
    }

    /**
     * 补齐任务列表的卡片计数（列表页直接显示「还有几张卡要处理」）。
     *
     * @param rows 任务行
     */
    private void fillCardCount(List<CpTaskVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        for (CpTaskVo r : rows) {
            List<CpInteractionCard> cards = cardMapper.selectList(new LambdaQueryWrapper<CpInteractionCard>()
                .eq(CpInteractionCard::getTaskId, r.getTaskId())
                .eq(CpInteractionCard::getStatus, ContentCardStatusEnum.PENDING.getCode()));
            r.setPendingCardCount(cards.size());
            int blocking = 0;
            for (CpInteractionCard c : cards) {
                if (ContentConstants.YES.equals(c.getBlocking())) {
                    blocking++;
                }
            }
            r.setBlockingCardCount(blocking);
        }
    }

}
