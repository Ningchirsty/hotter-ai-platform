package org.dromara.content.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.service.invoker.ModelImagePayload;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.CpInteractionCard;
import org.dromara.content.domain.CpOutputCheck;
import org.dromara.content.domain.CpProduct;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.bo.ContentOutputCheckQueryBo;
import org.dromara.content.domain.vo.CpOutputCheckVo;
import org.dromara.content.enums.ContentAsyncJobTypeEnum;
import org.dromara.content.enums.ContentCardStatusEnum;
import org.dromara.content.enums.ContentCardTypeEnum;
import org.dromara.content.enums.ContentCheckStatusEnum;
import org.dromara.content.enums.ContentCheckVerdictEnum;
import org.dromara.content.enums.ContentFileKindEnum;
import org.dromara.content.enums.ContentFileSourceEnum;
import org.dromara.content.enums.ContentGateLevelEnum;
import org.dromara.content.helper.ContentAsyncExecutor;
import org.dromara.content.helper.ContentImageInspector;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.mapper.CpInteractionCardMapper;
import org.dromara.content.mapper.CpOutputCheckMapper;
import org.dromara.content.mapper.CpProductMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.service.IContentOutputCheckService;
import org.dromara.content.service.IContentTaskService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 成品一致性检查服务实现。
 *
 * <p><b>链路</b>：上传参考图 + 成品图 → 本地确定性度量（画布几何 + 网格结构）
 * → 经治理层能力 {@code deliverable_consistency} 比对（本地规则或视觉模型）
 * → 结论落 {@code cp_output_check}；未通过时生成一张 EXCEPTION 互动卡进入既有处理闭环。</p>
 *
 * <p><b>三条硬边界</b>：</p>
 * <ol>
 *     <li><b>不产生产品事实</b>：全程不写 {@code cp_fact_snapshot}。参考图与 AI 结论
 *     不得反向成为产品参数的依据（SPEC §0.1 红线第 2 条）。</li>
 *     <li><b>不静默外发</b>：任务未开启外部 AI（{@code cp_task.allow_external != 'Y'}）时，
 *     若路由指向外部部署模型，直接以可读原因失败，绝不"顺手"把未发布素材发出去。</li>
 *     <li><b>不编造结论</b>：模型没给出可用结论时状态是 {@code FAILED}，
 *     而不是伪装成「一致」。分值缺失时留空，不填 0 或 100。</li>
 * </ol>
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentOutputCheckServiceImpl implements IContentOutputCheckService {

    /**
     * 检查单号日期格式
     */
    private static final DateTimeFormatter CHECK_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 检查项在互动卡上的字段编码（同一任务重复检查时不堆叠卡片）
     */
    private static final String CARD_FIELD_CODE = "deliverable_consistency";

    /**
     * 系统提示类提示词：交给调用器与能力输出模板共同约束模型输出
     */
    private static final String CHECK_PROMPT = """
        你是内容生产协同的成品验收助手。请把「成品图」与「参考图」逐项对照，判断成品是否忠实还原参考图。
        重点核对：
        ① 产品主体的形态、结构与颜色是否一致；
        ② 数量、朝向与排布是否一致；
        ③ Logo、品牌名、包装文字是否被改写、错漏或凭空新增；
        ④ 画面的尺寸比例与版式是否匹配。
        要求：
        - 只依据图中确实可见的内容判断；看不清、图中没有的信息一律不要推测。
        - 若参考图或成品图是整版长图，请按版面区块逐项说明差异，不要只给整体印象。
        - 不得把任何推断当作产品事实：产品参数只能来自已确认的产品资料。
        - 证据不足以判断时，verdict 必须为 UNCERTAIN，不要勉强给出一致或不一致。
        """;

    /**
     * 检查 Mapper
     */
    private final CpOutputCheckMapper checkMapper;

    /**
     * 任务 Mapper
     */
    private final CpTaskMapper taskMapper;

    /**
     * 附件 Mapper
     */
    private final CpTaskFileMapper taskFileMapper;

    /**
     * 产品 Mapper
     */
    private final CpProductMapper productMapper;

    /**
     * 互动卡 Mapper
     */
    private final CpInteractionCardMapper cardMapper;

    /**
     * 对象存储助手
     */
    private final ContentOssHelper ossHelper;

    /**
     * 异步执行器
     */
    private final ContentAsyncExecutor asyncExecutor;

    /**
     * 任务服务（复用附件上传：对象键规则、大小上限、file_kind 推断口径统一）
     */
    private final IContentTaskService taskService;

    /**
     * 治理层调用编排（内容模块只调用能力编码，不直接调用模型）
     */
    private final IAigInvokeService aigInvokeService;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Override
    public PageResult<CpOutputCheckVo> queryPage(ContentOutputCheckQueryBo bo, PageQuery pageQuery) {
        ContentOutputCheckQueryBo q = bo == null ? new ContentOutputCheckQueryBo() : bo;

        // 任务号过滤：先按参数化查询取任务ID再 in，不做字符串拼接（与卡片列表同口径）
        List<Long> taskIds = null;
        if (StringUtils.isNotBlank(q.getTaskNo())) {
            taskIds = taskMapper.selectList(new LambdaQueryWrapper<CpTask>()
                    .like(CpTask::getTaskNo, q.getTaskNo()))
                .stream().map(CpTask::getTaskId).toList();
            if (taskIds.isEmpty()) {
                return PageResult.build(List.of(), 0L);
            }
        }

        LambdaQueryWrapper<CpOutputCheck> wrapper = new LambdaQueryWrapper<CpOutputCheck>()
            .eq(q.getTaskId() != null, CpOutputCheck::getTaskId, q.getTaskId())
            .in(taskIds != null, CpOutputCheck::getTaskId, taskIds)
            .eq(StringUtils.isNotBlank(q.getStatus()), CpOutputCheck::getStatus, q.getStatus())
            .eq(StringUtils.isNotBlank(q.getVerdict()), CpOutputCheck::getVerdict, q.getVerdict());
        if (Boolean.TRUE.equals(q.getOnlyFailed())) {
            // 「没通过」包含两类：拿到了结论但结论是不一致/无法判定，以及压根没拿到结论。
            // 只筛 verdict 会把 FAILED 的记录漏掉——而那恰恰是最需要人看的。
            wrapper.and(w -> w
                .in(CpOutputCheck::getVerdict,
                    ContentCheckVerdictEnum.INCONSISTENT.getCode(),
                    ContentCheckVerdictEnum.UNCERTAIN.getCode())
                .or()
                .eq(CpOutputCheck::getStatus, ContentCheckStatusEnum.FAILED.getCode()));
        }
        wrapper.orderByDesc(CpOutputCheck::getCreateTime);

        var voPage = checkMapper.selectVoPage(pageQuery.build(), wrapper);
        List<CpOutputCheckVo> rows = voPage.getRecords() == null ? List.of() : voPage.getRecords();
        fillContext(rows);
        return PageResult.build(rows, voPage.getTotal());
    }

    @Override
    public CpOutputCheckVo getDetail(Long checkId) {
        CpOutputCheck entity = load(checkId);
        CpOutputCheckVo vo = checkMapper.selectVoById(entity.getCheckId());
        if (vo != null) {
            fillContext(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<CpOutputCheckVo> listByTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        List<CpOutputCheckVo> rows = checkMapper.selectVoList(new LambdaQueryWrapper<CpOutputCheck>()
            .eq(CpOutputCheck::getTaskId, taskId)
            .orderByDesc(CpOutputCheck::getCreateTime));
        fillContext(rows);
        return rows;
    }

    // ------------------------------------------------------------------
    // 发起检查
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long run(Long taskId, Long referenceFileId, MultipartFile referenceFile,
                    MultipartFile resultFile, String remark) {
        CpTask task = loadTask(taskId);
        if (resultFile == null || resultFile.isEmpty()) {
            throw new ServiceException("请上传生成的结果图（成品图）");
        }
        if (referenceFileId == null && (referenceFile == null || referenceFile.isEmpty())) {
            throw new ServiceException("请提供原参考图：可引用任务中已有的图片附件，或直接上传");
        }
        // 类型校验放在写对象存储之前：先拒绝，不留下一堆用不上的附件与对象
        requireImage(extOf(resultFile.getOriginalFilename()), resultFile.getOriginalFilename());
        if (referenceFileId == null) {
            requireImage(extOf(referenceFile.getOriginalFilename()), referenceFile.getOriginalFilename());
        }

        // 参考图：优先用任务里已有的附件，避免同一张图重复入库
        Long referenceId;
        if (referenceFileId != null) {
            CpTaskFile existing = taskFileMapper.selectById(referenceFileId);
            if (existing == null || !taskId.equals(existing.getTaskId())) {
                throw new ServiceException("参考图附件不存在，或不属于该任务");
            }
            requireImage(existing.getFileExt(), existing.getFileName());
            referenceId = existing.getFileId();
        } else {
            referenceId = taskService.uploadFile(taskId, task.getDataLevel(), referenceFile);
            markAsReference(referenceId);
        }

        Long resultId = taskService.uploadFile(taskId, task.getDataLevel(), resultFile);
        // 角色标注：这张图是系统生成的结果，不是人工上传的资料
        markAsGenerated(resultId);

        return insertAndSubmit(taskId, referenceId, resultId, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long runWithFiles(Long taskId, Long referenceFileId, Long resultFileId, String remark) {
        loadTask(taskId);
        if (referenceFileId == null || resultFileId == null) {
            throw new ServiceException("参考图与成品图附件都不能为空");
        }
        CpTaskFile reference = taskFileMapper.selectById(referenceFileId);
        if (reference == null || !taskId.equals(reference.getTaskId())) {
            throw new ServiceException("参考图附件不存在，或不属于该任务");
        }
        CpTaskFile result = taskFileMapper.selectById(resultFileId);
        if (result == null || !taskId.equals(result.getTaskId())) {
            throw new ServiceException("成品图附件不存在，或不属于该任务");
        }
        requireImage(reference.getFileExt(), reference.getFileName());
        requireImage(result.getFileExt(), result.getFileName());
        return insertAndSubmit(taskId, referenceFileId, resultFileId, remark);
    }

    /**
     * 建检查记录并交给异步工作（{@link #run} 与 {@link #runWithFiles} 的公共尾部）。
     *
     * @param taskId          任务ID
     * @param referenceId     参考图附件ID
     * @param resultId        成品图附件ID
     * @param remark          备注
     * @return 检查ID
     */
    private Long insertAndSubmit(Long taskId, Long referenceId, Long resultId, String remark) {
        CpOutputCheck check = new CpOutputCheck();
        check.setTaskId(taskId);
        check.setReferenceFileId(referenceId);
        check.setResultFileId(resultId);
        check.setStatus(ContentCheckStatusEnum.PENDING.getCode());
        check.setCheckedBy(LoginHelper.getUserId());
        check.setRemark(remark);
        insertCheckWithGeneratedNo(check);

        final Long checkId = check.getCheckId();
        asyncExecutor.submit(taskId, ContentAsyncJobTypeEnum.OUTPUT_CHECK, () -> checkWorker(checkId));
        log.info("发起成品一致性检查, checkId={}, checkNo={}, taskId={}, referenceFileId={}, resultFileId={}",
            checkId, check.getCheckNo(), taskId, referenceId, resultId);
        return checkId;
    }

    @Override
    public void remove(Long checkId) {
        load(checkId);
        // 逻辑删除：检查结论是验收留痕，物理删除会让「当时到底查没查」变得无法追溯
        checkMapper.deleteById(checkId);
    }

    // ------------------------------------------------------------------
    // 异步工作：比对
    // ------------------------------------------------------------------

    /**
     * 比对工作：本地度量 → 治理层能力 → 结论落库 → 未通过时生成互动卡。
     *
     * @param checkId 检查ID
     */
    private void checkWorker(Long checkId) {
        CpOutputCheck check = checkMapper.selectById(checkId);
        if (check == null) {
            return;
        }
        try {
            markRunning(checkId);
            CpTask task = taskMapper.selectById(check.getTaskId());
            CpTaskFile reference = taskFileMapper.selectById(check.getReferenceFileId());
            CpTaskFile result = taskFileMapper.selectById(check.getResultFileId());
            if (reference == null || result == null) {
                throw new ServiceException("参考图或成品图附件已不存在，请重新发起检查");
            }
            byte[] referenceBytes = readBytes(reference);
            byte[] resultBytes = readBytes(result);

            // 1. 本地确定性度量：无论走本地还是走模型，这份度量都要留痕——
            //    它是唯一不依赖模型、可复算的证据。
            ContentImageInspector.Comparison comparison =
                ContentImageInspector.compare(referenceBytes, resultBytes);
            updateMetrics(checkId, JsonUtils.toJsonString(comparison.toMap()));

            // 2. 组装治理层调用载荷
            AigInvokeBo bo = new AigInvokeBo();
            bo.setCapabilityCode(ContentConstants.CAP_DELIVERABLE_CONSISTENCY);
            bo.setDataLevel(StringUtils.isBlank(task.getDataLevel()) ? "INTERNAL" : task.getDataLevel());
            bo.setPrompt(CHECK_PROMPT);
            bo.setPayload(buildPayload(task, reference, result, referenceBytes, resultBytes, comparison));

            // 3. 不静默外发：任务未开启外部 AI 时，若路由指向外部部署，直接失败
            guardExternal(bo, task);

            // 4. 调用
            AigInvokeVo result2 = aigInvokeService.invoke(bo);
            if (result2 == null || !"MODEL".equals(result2.getDecision()) || StringUtils.isBlank(result2.getOutput())) {
                String reason = result2 == null ? "治理层未返回结果"
                    : StringUtils.blankToDefault(result2.getReason(), "调用被治理策略拒绝（" + result2.getDecision() + "）");
                throw new ServiceException(reason);
            }

            // 5. 解析结论
            Map<String, Object> out = JsonUtils.parseMap(result2.getOutput());
            if (out == null) {
                throw new ServiceException("检查结果格式异常，无法解析");
            }
            ContentCheckVerdictEnum verdict = ContentCheckVerdictEnum.find(str(out.get("verdict")));
            if (verdict == null) {
                // 输出模板只约束字段存在，不约束取值；这里做业务侧兜底，不猜。
                throw new ServiceException("检查结果未给出可识别的结论（verdict）");
            }
            String summary = StringUtils.blankToDefault(str(out.get("summary")), verdict.getDesc());
            BigDecimal score = parseScore(out.get("score"));
            String findingsJson = JsonUtils.toJsonString(mapList(out.get("findings")));

            finish(checkId, ContentCheckStatusEnum.DONE, verdict, score, summary, findingsJson,
                result2, task);
            log.info("成品一致性检查完成, checkId={}, verdict={}, score={}, modelKey={}, invoker={}",
                checkId, verdict.getCode(), score, result2.getModelKey(), result2.getInvoker());

            if (ContentCheckVerdictEnum.INCONSISTENT == verdict) {
                createFailureCard(task, check, summary, findingsJson);
            }
        } catch (ServiceException e) {
            // 业务性失败：把可读原因写进检查记录，再抛出以便作业状态也是 FAILED
            fail(checkId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("成品一致性检查异常, checkId={}, exception={}", checkId, e.getClass().getSimpleName(), e);
            fail(checkId, "检查过程异常，请稍后重试或联系管理员");
            throw new ServiceException("检查过程异常，请稍后重试或联系管理员");
        }
    }

    /**
     * 未开启外部 AI 的任务不得把素材外发。
     *
     * <p>用 {@code dryRun} 先问一次路由：它与真正的 invoke 走同一套决策，
     * 因此「预告要走外部」与「实际会走外部」是一致的。命中外部且任务未授权时直接失败，
     * 给出可操作的原因，而不是默默降级或默默外发。</p>
     *
     * @param bo   调用入参
     * @param task 任务
     */
    private void guardExternal(AigInvokeBo bo, CpTask task) {
        AigInvokeVo preview = aigInvokeService.dryRun(bo);
        if (preview == null || !"MODEL".equals(preview.getDecision())) {
            return;
        }
        AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(preview.getDeploymentType());
        if (deployment == null || !deployment.isExternal()) {
            return;
        }
        if (ContentConstants.YES.equalsIgnoreCase(task.getAllowExternal())) {
            return;
        }
        throw new ServiceException("本次检查将调用外部模型（" + deployment.getDesc()
            + "，模型 " + preview.getModelKey() + "），但任务未开启「允许外部 AI」。"
            + "请在任务上开启该开关，或为该能力绑定本地/集团共享模型");
    }

    /**
     * 组装调用载荷。
     *
     * @param task          任务
     * @param reference     参考图附件
     * @param result        成品图附件
     * @param referenceBytes 参考图字节
     * @param resultBytes    成品图字节
     * @param comparison    本地度量
     * @return 载荷
     */
    private Map<String, Object> buildPayload(CpTask task, CpTaskFile reference, CpTaskFile result,
                                             byte[] referenceBytes, byte[] resultBytes,
                                             ContentImageInspector.Comparison comparison) {
        Map<String, Object> payload = new LinkedHashMap<>();
        // 顺序即契约：images[0]=参考图、images[1]=成品图（本地调用器会校验标签）
        List<Map<String, Object>> images = new ArrayList<>();
        images.add(imagePart(ContentConstants.IMAGE_LABEL_REFERENCE, reference, referenceBytes));
        images.add(imagePart(ContentConstants.IMAGE_LABEL_RESULT, result, resultBytes));
        payload.put(ModelImagePayload.KEY, images);

        payload.put("taskNo", task.getTaskNo());
        payload.put("taskName", task.getTaskName());
        payload.put("deliverableType", task.getDeliverableType());
        payload.put("referenceFileName", reference.getFileName());
        payload.put("resultFileName", result.getFileName());
        if (task.getProductId() != null) {
            CpProduct product = productMapper.selectById(task.getProductId());
            if (product != null) {
                payload.put("productCode", product.getProductCode());
                payload.put("productName", product.getProductName());
                // 只带「标识性」信息用于说明画面应出现什么；产品参数事实不参与比对，
                // 也不因比对结果被改写（红线第 2 条）。
                payload.put("category", product.getCategory());
            }
        }
        payload.put("localMetrics", comparison.toMap());
        return payload;
    }

    /**
     * 构造一张图片分片。
     *
     * @param label 标签（参考图/成品图）
     * @param file  附件
     * @param bytes 字节
     * @return Map
     */
    private Map<String, Object> imagePart(String label, CpTaskFile file, byte[] bytes) {
        if (bytes.length > ContentConstants.MAX_CHECK_IMAGE_SIZE) {
            throw new ServiceException("图片「" + file.getFileName() + "」超过 "
                + (ContentConstants.MAX_CHECK_IMAGE_SIZE / 1024 / 1024) + "MB，无法用于在线比对");
        }
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("label", label);
        part.put("mimeType", ModelImagePayload.mimeOfExt(file.getFileExt()));
        part.put("base64", Base64.getEncoder().encodeToString(bytes));
        return part;
    }

    /**
     * 读取附件字节。
     *
     * @param file 附件
     * @return 字节
     */
    private byte[] readBytes(CpTaskFile file) {
        if (StringUtils.isBlank(file.getFileRef())) {
            throw new ServiceException("附件「" + file.getFileName() + "」的文件引用缺失，请重新上传");
        }
        return ossHelper.getBytes(file.getFileRef());
    }

    /**
     * 结论为「不一致」时生成一张例外卡，进入既有的处理闭环。
     *
     * <p><b>为什么只给两个选项</b>：互动卡的处理选项在
     * {@code ContentCardServiceImpl.resolve} 里是固定的四个（CONFIRM/OTHER/SUPPLEMENT/BLOCK）。
     * 其中 CONFIRM 与 OTHER 都会<b>写入事实快照</b>——用它们来处理「成品画错了」会直接违反
     * 「参考图与 AI 结论不得成为产品事实」的红线。因此这里只用两个不写事实的选项：
     * 返工后关闭（SUPPLEMENT 语义：已处理，重新提交检查）、或先阻断流转（BLOCK）。
     * 卡片等级取 CONDITION 而非 BLOCK：是否因此阻断交付由人决定（闸门强度是表驱动的），
     * 系统不替人下这个结论。</p>
     *
     * @param task        任务
     * @param check       检查记录
     * @param summary     结论摘要
     * @param findingsJson 差异清单
     */
    private void createFailureCard(CpTask task, CpOutputCheck check, String summary, String findingsJson) {
        try {
            // 同一任务只保留一张待处理的检查卡：重复检查应更新结论，而不是堆一屏卡片
            cardMapper.delete(new LambdaQueryWrapper<CpInteractionCard>()
                .eq(CpInteractionCard::getTaskId, task.getTaskId())
                .eq(CpInteractionCard::getCardType, ContentCardTypeEnum.EXCEPTION.getCode())
                .eq(CpInteractionCard::getFieldCode, CARD_FIELD_CODE)
                .eq(CpInteractionCard::getStatus, ContentCardStatusEnum.PENDING.getCode()));

            List<Map<String, Object>> options = new ArrayList<>();
            options.add(option("SUPPLEMENT", "已返工，重新提交检查", null));
            options.add(option("BLOCK", "成品不一致，先阻断流转", null));

            List<Map<String, Object>> impact = new ArrayList<>();
            Map<String, Object> impactItem = new LinkedHashMap<>();
            impactItem.put("deliverableType", task.getDeliverableType());
            impactItem.put("checkNo", check.getCheckNo());
            impactItem.put("note", "该成品与参考图存在差异，返工确认前不得据此定稿交付");
            impact.add(impactItem);

            CpInteractionCard card = new CpInteractionCard();
            card.setTaskId(task.getTaskId());
            card.setCardType(ContentCardTypeEnum.EXCEPTION.getCode());
            card.setFieldCode(CARD_FIELD_CODE);
            card.setTitle("成品与原参考图一致性检查未通过（" + check.getCheckNo() + "）");
            card.setQuestion(StringUtils.blankToDefault(summary, "成品图与原参考图存在差异，请核对后决定返工或阻断。")
                + "\n差异明细见「成品一致性检查」页面。");
            card.setEvidenceJson(findingsJson);
            card.setImpactJson(JsonUtils.toJsonString(impact));
            card.setOptionsJson(JsonUtils.toJsonString(options));
            ContentGateLevelEnum level = ContentGateLevelEnum.CONDITION;
            card.setGateLevel(level.getCode());
            card.setBlocking(level.blocking() ? ContentConstants.YES : ContentConstants.NO);
            card.setAssigneeId(task.getOwnerId());
            card.setAssigneeName(task.getOwnerName());
            card.setDueAt(task.getDeadline());
            card.setStatus(ContentCardStatusEnum.PENDING.getCode());
            cardMapper.insert(card);
            log.info("成品一致性检查未通过，已生成例外卡, taskId={}, checkNo={}", task.getTaskId(), check.getCheckNo());
        } catch (Exception e) {
            // 卡片只是提醒通道；生成失败不能把已经拿到的检查结论一起判死
            log.warn("生成成品检查例外卡失败, taskId={}, checkNo={}, exception={}",
                task.getTaskId(), check.getCheckNo(), e.getClass().getSimpleName());
        }
    }

    /**
     * 构造一个卡片选项。
     *
     * @param code  选项编码
     * @param label 展示文案
     * @param value 值
     * @return Map
     */
    private Map<String, Object> option(String code, String label, String value) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("option", code);
        o.put("label", label);
        o.put("value", value);
        return o;
    }

    // ------------------------------------------------------------------
    // 落库助手
    // ------------------------------------------------------------------

    /**
     * 标记为检查中。
     *
     * @param checkId 检查ID
     */
    private void markRunning(Long checkId) {
        checkMapper.update(null, new LambdaUpdateWrapper<CpOutputCheck>()
            .eq(CpOutputCheck::getCheckId, checkId)
            .set(CpOutputCheck::getStatus, ContentCheckStatusEnum.RUNNING.getCode())
            .set(CpOutputCheck::getFailureReason, null));
    }

    /**
     * 写入本地度量。
     *
     * @param checkId     检查ID
     * @param metricsJson 度量 JSON
     */
    private void updateMetrics(Long checkId, String metricsJson) {
        checkMapper.update(null, new LambdaUpdateWrapper<CpOutputCheck>()
            .eq(CpOutputCheck::getCheckId, checkId)
            .set(CpOutputCheck::getMetricsJson, metricsJson));
    }

    /**
     * 写入结论。
     *
     * @param checkId      检查ID
     * @param status       状态
     * @param verdict      结论
     * @param score        分值（可空）
     * @param summary      摘要
     * @param findingsJson 差异清单
     * @param invoke       治理层返回（取模型与调用器信息）
     * @param task         任务
     */
    private void finish(Long checkId, ContentCheckStatusEnum status, ContentCheckVerdictEnum verdict,
                        BigDecimal score, String summary, String findingsJson,
                        AigInvokeVo invoke, CpTask task) {
        // 显式 set：MyBatis-Plus 默认 NOT_NULL 策略下，实体里的 null 不会进 UPDATE，
        // 会残留上一轮的值（本项目已在解析状态上踩过一次，这里用同样的写法）。
        checkMapper.update(null, new LambdaUpdateWrapper<CpOutputCheck>()
            .eq(CpOutputCheck::getCheckId, checkId)
            .set(CpOutputCheck::getStatus, status.getCode())
            .set(CpOutputCheck::getVerdict, verdict.getCode())
            .set(CpOutputCheck::getScore, score)
            .set(CpOutputCheck::getSummary, summary)
            .set(CpOutputCheck::getFindingsJson, findingsJson)
            .set(CpOutputCheck::getFailureReason, null)
            .set(CpOutputCheck::getModelId, invoke == null ? null : invoke.getModelId())
            .set(CpOutputCheck::getModelKey, invoke == null ? null : invoke.getModelKey())
            .set(CpOutputCheck::getDeploymentType, invoke == null ? null : invoke.getDeploymentType())
            .set(CpOutputCheck::getInvokerName, invoke == null ? null : invoke.getInvoker())
            .set(CpOutputCheck::getTraceId, invoke == null ? null : invoke.getTraceId())
            .set(CpOutputCheck::getCheckedAt, LocalDateTime.now()));
    }

    /**
     * 写入失败。
     *
     * @param checkId 检查ID
     * @param reason  可读原因
     */
    private void fail(Long checkId, String reason) {
        String message = StringUtils.blankToDefault(reason, "检查失败");
        checkMapper.update(null, new LambdaUpdateWrapper<CpOutputCheck>()
            .eq(CpOutputCheck::getCheckId, checkId)
            .set(CpOutputCheck::getStatus, ContentCheckStatusEnum.FAILED.getCode())
            .set(CpOutputCheck::getVerdict, null)
            .set(CpOutputCheck::getScore, null)
            .set(CpOutputCheck::getSummary, message)
            .set(CpOutputCheck::getFailureReason, message)
            .set(CpOutputCheck::getCheckedAt, LocalDateTime.now()));
    }

    /**
     * 把参考图附件的来源纠正为 REFERENCE。
     *
     * <p>复用附件上传会把来源记成 UPLOAD；检查场景下参考图是"被引用"的素材，
     * 与成品图角色不同，按 SPEC §2.3 的 source_type 口径如实标注。</p>
     *
     * @param fileId 附件ID
     */
    private void markAsReference(Long fileId) {
        taskFileMapper.update(null, new LambdaUpdateWrapper<CpTaskFile>()
            .eq(CpTaskFile::getFileId, fileId)
            .set(CpTaskFile::getSourceType, ContentFileSourceEnum.REFERENCE.getCode()));
    }

    /**
     * 把成品图附件标成系统生成（R4 起统一角色口径）。
     *
     * @param fileId 附件ID
     */
    private void markAsGenerated(Long fileId) {
        taskFileMapper.update(null, new LambdaUpdateWrapper<CpTaskFile>()
            .eq(CpTaskFile::getFileId, fileId)
            .set(CpTaskFile::getSourceType, ContentFileSourceEnum.GENERATED.getCode()));
    }

    /**
     * 校验是图片类型。
     *
     * @param ext      扩展名
     * @param fileName 文件名（用于提示）
     */
    private void requireImage(String ext, String fileName) {
        ContentFileKindEnum kind = ContentFileKindEnum.ofExt(ext);
        if (kind != ContentFileKindEnum.IMAGE) {
            throw new ServiceException("「" + StringUtils.blankToDefault(fileName, "未命名文件")
                + "」不是图片，成品一致性检查只接受图片文件（PNG/JPEG/GIF/BMP/WebP 等）");
        }
    }

    // ------------------------------------------------------------------
    // 预览
    // ------------------------------------------------------------------

    @Override
    public CheckImage loadImage(Long checkId, String side) {
        CpOutputCheck check = load(checkId);
        boolean isReference = "reference".equalsIgnoreCase(side);
        Long fileId = isReference ? check.getReferenceFileId() : check.getResultFileId();
        if (fileId == null) {
            throw new ServiceException("该检查未关联对应的图片");
        }
        CpTaskFile file = taskFileMapper.selectById(fileId);
        if (file == null) {
            throw new ServiceException("图片附件已不存在");
        }
        return new CheckImage(ossHelper.getBytes(file.getFileRef()), file.getFileName(),
            ModelImagePayload.mimeOfExt(file.getFileExt()));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 加载检查记录。
     *
     * @param checkId 检查ID
     * @return 实体
     */
    private CpOutputCheck load(Long checkId) {
        if (checkId == null) {
            throw new ServiceException("检查ID不能为空");
        }
        CpOutputCheck check = checkMapper.selectById(checkId);
        if (check == null) {
            throw new ServiceException("检查记录不存在");
        }
        return check;
    }

    /**
     * 加载任务。
     *
     * @param taskId 任务ID
     * @return 实体
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
     * 补齐列表/详情所需的上下文（任务、产品、附件文件名）。
     *
     * @param rows 检查行
     */
    private void fillContext(List<CpOutputCheckVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        List<Long> taskIds = new ArrayList<>();
        List<Long> fileIds = new ArrayList<>();
        for (CpOutputCheckVo r : rows) {
            if (r.getTaskId() != null && !taskIds.contains(r.getTaskId())) {
                taskIds.add(r.getTaskId());
            }
            if (r.getReferenceFileId() != null && !fileIds.contains(r.getReferenceFileId())) {
                fileIds.add(r.getReferenceFileId());
            }
            if (r.getResultFileId() != null && !fileIds.contains(r.getResultFileId())) {
                fileIds.add(r.getResultFileId());
            }
        }

        Map<Long, CpTask> taskMap = new LinkedHashMap<>();
        List<Long> productIds = new ArrayList<>();
        if (!taskIds.isEmpty()) {
            for (CpTask t : taskMapper.selectByIds(taskIds)) {
                taskMap.put(t.getTaskId(), t);
                if (t.getProductId() != null && !productIds.contains(t.getProductId())) {
                    productIds.add(t.getProductId());
                }
            }
        }
        Map<Long, CpProduct> productMap = new LinkedHashMap<>();
        if (!productIds.isEmpty()) {
            for (CpProduct p : productMapper.selectByIds(productIds)) {
                productMap.put(p.getProductId(), p);
            }
        }
        Map<Long, CpTaskFile> fileMap = new LinkedHashMap<>();
        if (!fileIds.isEmpty()) {
            for (CpTaskFile f : taskFileMapper.selectByIds(fileIds)) {
                fileMap.put(f.getFileId(), f);
            }
        }

        for (CpOutputCheckVo r : rows) {
            CpTask t = r.getTaskId() == null ? null : taskMap.get(r.getTaskId());
            if (t != null) {
                r.setTaskNo(t.getTaskNo());
                r.setTaskName(t.getTaskName());
                r.setDeliverableType(t.getDeliverableType());
                r.setProductId(t.getProductId());
                if (t.getProductId() != null) {
                    CpProduct p = productMap.get(t.getProductId());
                    if (p != null) {
                        r.setProductName(p.getProductName());
                    }
                }
            }
            CpTaskFile ref = r.getReferenceFileId() == null ? null : fileMap.get(r.getReferenceFileId());
            if (ref != null) {
                r.setReferenceFileName(ref.getFileName());
                r.setReferenceFileRef(ref.getFileRef());
            }
            CpTaskFile res = r.getResultFileId() == null ? null : fileMap.get(r.getResultFileId());
            if (res != null) {
                r.setResultFileName(res.getFileName());
                r.setResultFileRef(res.getFileRef());
            }
        }
    }

    /**
     * 生成检查单号：CK + yyyyMMdd + 4 位序号。
     *
     * <p><b>必须把已逻辑删除的行算进去</b>：{@code uk_cp_output_check_no} 唯一索引只建在
     * {@code check_no} 上，不含 {@code del_flag}。若按「可见行」取序号，删光当天记录后
     * 序号会从 0001 重来，插入直接撞唯一索引——现象是「删过一次检查后，再也发不起新检查」。
     * 所以这里走 {@link CpOutputCheckMapper#selectMaxCheckNoIncludeDeleted(String)}。</p>
     *
     * @param offset 在最大已用序号基础上再往后顺延的位数（用于并发撞号后重试）
     * @return 检查单号
     */
    private String nextCheckNo(int offset) {
        String prefix = "CK" + LocalDate.now().format(CHECK_NO_DATE);
        String max = checkMapper.selectMaxCheckNoIncludeDeleted(prefix);
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
     * 带单号生成地插入检查记录：撞唯一索引时顺延序号重试。
     *
     * <p>单号是「当天最大序号 + 1」，两人同时发起检查会算出同一个号。这里不做全局锁
     * （为一个演示级并发量上分布式锁不划算），而是让唯一索引当仲裁者：撞了就换下一个号再试。
     * 重试上限取 20，远超正常并发量，超过就如实失败而不是无限重试。</p>
     *
     * @param check 检查记录（checkNo 由本方法填充）
     */
    private void insertCheckWithGeneratedNo(CpOutputCheck check) {
        for (int offset = 0; offset < 20; offset++) {
            check.setCheckNo(nextCheckNo(offset));
            try {
                checkMapper.insert(check);
                return;
            } catch (DuplicateKeyException e) {
                // 该单号刚被并发请求占用（或历史软删除行占用），顺延一位重试
                log.warn("检查单号被占用，顺延重试, checkNo={}, offset={}", check.getCheckNo(), offset);
            }
        }
        throw new ServiceException("生成检查单号失败（当日序号冲突），请稍后重试");
    }

    /**
     * 解析分值：只接受 0–100 的数值。
     *
     * <p>越界或非数值一律返回 null——宁可界面上空着，也不显示一个被截断或被猜出来的分数。</p>
     *
     * @param value 原值
     * @return 分值或 null
     */
    private BigDecimal parseScore(Object value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal score = new BigDecimal(String.valueOf(value).trim());
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) {
                return null;
            }
            return score.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 把对象转为 Map 列表。
     *
     * @param value 值
     * @return Map 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
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
     * 取扩展名。
     *
     * @param fileName 文件名
     * @return 小写扩展名
     */
    private String extOf(String fileName) {
        String name = StringUtils.blankToDefault(fileName, "");
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
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

}
