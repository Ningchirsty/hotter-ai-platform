package org.dromara.creative.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.domain.vo.CpTaskVo;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.service.IContentProductService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.DpStageEvent;
import org.dromara.creative.domain.bo.CreativeProjectBo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpStageEventVo;
import org.dromara.creative.helper.CreativeStepStateWriter;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpStageEventMapper;
import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 视觉项目服务实现。
 *
 * <p><b>不做的事</b>：不碰 cp_task 的其它列、不自己实现闸门与事实确认、不自己存文件。
 * 三项都直接复用内容协同——这样「任务在视觉工厂里」与「任务在内容协同里」永远是同一行数据，
 * 不存在两份状态要对账的问题。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeProjectServiceImpl implements ICreativeProjectService {

    /**
     * 单张参考图上限（字节）。与内容模块 MAX_FILE_SIZE（50MB）取齐的是「上传通道」，
     * 但出图内核把图 base64 进请求体，故这里收紧到 20MB（与图像模块上传上限一致）。
     */
    private static final long MAX_REFERENCE_BYTES = 20L * 1024 * 1024;

    private final IContentTaskService contentTaskService;
    private final IContentProductService productService;
    private final ContentOssHelper contentOssHelper;
    private final CreativeTaskStageMapper stageMapper;
    /**
     * 项目步骤状态写入者（V0.2 D2）：只在本类的 moveStage 里调用一次
     */
    private final CreativeStepStateWriter stepStateWriter;

    /**
     * 场景配置（R21）：判断"哪些交付类型视觉工厂能处理"（dp_delivery_type 里启用且有场景档案的那些）。
     */
    private final ICreativeScenarioConfigService scenarioConfigService;
    private final DpStageEventMapper eventMapper;

    @Override
    public PageResult<CreativeProjectVo> queryPage(ContentTaskBo bo, PageQuery pageQuery) {
        // R21：视觉工厂处理的交付类型改为"配置里登记且启用的那些"，不再硬写 ECOM_DETAIL。
        // 前端不传交付类型时，按配置里启用的类型集合过滤（而不是"全放进来"——那样列表会混入
        // 视频、说明书等根本不走这套流程的任务）。
        if (StringUtils.isBlank(bo.getQueryDeliverableType())) {
            List<String> supported = supportedDeliverableTypes();
            if (supported.isEmpty()) {
                return PageResult.build(List.of(), 0L);
            }
            bo.setQueryDeliverableTypes(supported);
        }
        PageResult<CpTaskVo> page = contentTaskService.queryPage(bo, pageQuery);
        List<CreativeProjectVo> rows = new ArrayList<>();
        for (CpTaskVo task : page.getRows()) {
            CreativeProjectVo vo = toProjectVo(task);
            String stage = readStage(task.getTaskId());
            vo.setVisualStage(stage);
            vo.setVisualStageDesc(DpVisualStageEnum.descOf(stage));
            rows.add(vo);
        }
        return PageResult.build(rows, page.getTotal());
    }

    @Override
    public CreativeProjectVo getProject(Long taskId) {
        ContentTaskDetailVo detail = contentTaskService.getDetail(taskId);
        if (detail == null || detail.getTask() == null) {
            throw new ServiceException("视觉项目不存在：" + taskId);
        }
        requireEcomDetail(detail.getTask());
        CreativeProjectVo vo = toProjectVo(detail.getTask());
        String stage = readStage(taskId);
        vo.setVisualStage(stage);
        vo.setVisualStageDesc(DpVisualStageEnum.descOf(stage));
        long imageCount = detail.getFiles() == null ? 0
            : detail.getFiles().stream().filter(f -> "IMAGE".equalsIgnoreCase(f.getFileKind())).count();
        vo.setImageFileCount((int) imageCount);
        fillProductImage(vo);
        return vo;
    }

    /**
     * 填充项目的产品图信息（未配置时明确置 false，不猜）。
     *
     * @param vo 项目视图
     */
    private void fillProductImage(CreativeProjectVo vo) {
        vo.setProductImageConfigured(false);
        if (vo.getProductId() == null) {
            return;
        }
        try {
            IContentProductService.ProductImage image = productService.imageOf(vo.getProductId());
            vo.setProductImageConfigured(image.configured());
            vo.setProductImageFileName(image.fileName());
            vo.setProductImageSourceTaskId(image.sourceTaskId());
        } catch (Exception e) {
            // 产品被删/数据异常不应把整个项目页带崩；页面按「产品图未配置」展示并给出补齐入口
            log.warn("读取产品图失败 taskId={} productId={} error={}",
                vo.getTaskId(), vo.getProductId(), e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(CreativeProjectBo bo) {
        // R21：交付类型不再硬写 ECOM_DETAIL（不传时仍按详情页，保持老行为）；
        // 传了就在这里校验，避免创建出"进不了流程"的任务（场景档案缺失会在 getProject 时才炸）。
        String deliverableType = StringUtils.blankToDefault(
            StringUtils.trimToNull(bo.getDeliverableType()), CreativeConstants.DELIVERABLE_ECOM_DETAIL);
        DpDeliveryType type = scenarioConfigService.getDeliveryType(deliverableType);
        if (type == null || "1".equals(type.getEnabled())) {
            throw new ServiceException("交付类型不可用（未登记或已停用）：" + deliverableType);
        }
        if (scenarioConfigService.getScenario(deliverableType) == null) {
            throw new ServiceException("交付类型 " + deliverableType + " 还没有已发布的场景档案，无法创建项目");
        }
        ContentTaskBo taskBo = new ContentTaskBo();
        taskBo.setTaskName(bo.getTaskName());
        taskBo.setDeliverableType(deliverableType);
        taskBo.setProductId(bo.getProductId());
        taskBo.setSkuCode(bo.getSkuCode());
        taskBo.setOwnerId(bo.getOwnerId());
        taskBo.setOwnerName(bo.getOwnerName());
        taskBo.setDeadline(bo.getDeadline());
        taskBo.setRemark(bo.getRemark());
        Long taskId = contentTaskService.create(taskBo);
        // 新项目进入视觉工厂的起点：资料就绪（等参考图与事实确认）
        moveStage(taskId, DpVisualStageEnum.MATERIAL_READY, "PROJECT_CREATED",
            "{\"source\":\"creative\"}");
        return taskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadReference(Long taskId, MultipartFile file) {
        return uploadReference(taskId, file, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadReference(Long taskId, MultipartFile file, boolean asProductImage) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_REFERENCE_BYTES) {
            throw new ServiceException("参考图不能超过 20MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new ServiceException("参考图必须是图片（png/jpg/webp）");
        }
        // 「同时设为产品图」要在落对象存储之前先判断可行性：没有关联产品的项目不可能设产品图，
        // 与其传完再报错留一张孤儿图，不如先拒绝
        Long productId = null;
        if (asProductImage) {
            CreativeProjectVo project = getProject(taskId);
            if (project.getProductId() == null) {
                throw new ServiceException("该项目没有关联产品，无法设为产品图；请先在项目里选择产品");
            }
            productId = project.getProductId();
        }
        // 复用内容模块的附件上传（落对象存储 + 登记 cp_task_file，业务留痕在内容侧）
        Long fileId = contentTaskService.uploadFile(taskId, null, file);
        if (asProductImage) {
            productService.bindImageFromFile(productId, fileId);
        }
        // 【R7 修复】补一张参考图**绝不改视觉阶段**，只留一条时间线事件。
        //
        // 为什么必须这样改（实测证据）：
        //   1) 原先这里调 moveStage(MATERIAL_READY)。项目一旦走到「已完成(COMPLETED)」，
        //      DpVisualStageEnum#canMoveTo 只允许退回「排版中/机排版完成/人工精修」，
        //      MATERIAL_READY 被拒 → 本方法整体事务回滚（附件也一起回滚）→ 用户看到
        //      「项目已处于「已完成」，不能再变为「资料就绪」」，表现就是「设置产品图后
        //      无法上传产品参考图」。已在生产 2102948730396520450 上复现（HTTP 200 + code 500）。
        //   2) 在「出图中(PRODUCING)」的项目上上传虽然成功，却把阶段倒退回「资料就绪」，
        //      把已经跑完的出图进度标记抹掉（生产 2104416314929696770 时间线上可见）。
        //
        // 语义上：上传附图是「补充材料」，不是「回退流程」。阶段推进只由流程动作（生成基因、
        // 锁定分镜、提交视觉门…）驱动，人的补充材料不该替流程做决定。因此这里改用 appendEvent，
        // 保留「谁在什么时候补了哪张图」的可追溯性，同时把当前阶段原样写进事件明细备查。
        String stage = readStage(taskId);
        appendEvent(taskId, "REFERENCE",
            asProductImage ? "REFERENCE_UPLOADED_AS_PRODUCT_IMAGE" : "REFERENCE_UPLOADED",
            "{\"fileId\":" + fileId + ",\"asProductImage\":" + asProductImage
                + ",\"stage\":" + quote(stage) + "}");
        return fileId;
    }

    @Override
    public ProductImageView productImage(Long taskId) {
        CreativeProjectVo project = getProject(taskId);
        if (project.getProductId() == null) {
            return new ProductImageView(null, project.getProductName(), false, null, null, null, null, null,
                null, "该项目没有关联产品：请先在项目里选择产品，才能把上传的产品照片登记为产品图");
        }
        IContentProductService.ProductImage image = productService.imageOf(project.getProductId());
        if (!image.configured()) {
            return new ProductImageView(project.getProductId(), project.getProductName(), false, null, null, null,
                null, null, null,
                "该产品还没有产品图：在项目里上传产品照片时勾选「同时设为产品图」，"
                    + "或在附件上点「设为产品图」");
        }
        // 本项目是否有产品图角色的附件（有则前端可直接用它做并排对比）
        Long localFileId = productService.ensureProductAttachment(taskId);
        return new ProductImageView(project.getProductId(), project.getProductName(), true, localFileId,
            image.fileName(), image.sourceTaskId(), image.setAt(), image.setBy(),
            "/creative/projects/" + taskId + "/product-image/content",
            taskId.equals(image.sourceTaskId()) ? "产品图来自本项目"
                : "产品图来自其它项目（taskId=" + image.sourceTaskId() + "），本项目已登记同一张图作为基准");
    }

    @Override
    public FileContent productImageContent(Long taskId) {
        CreativeProjectVo project = getProject(taskId);
        if (project.getProductId() == null) {
            throw new ServiceException("该项目没有关联产品，无法读取产品图");
        }
        IContentProductService.ProductImage image = productService.imageOf(project.getProductId());
        if (!image.configured()) {
            throw new ServiceException("该产品还没有产品图");
        }
        byte[] bytes = productService.imageBytes(project.getProductId());
        return new FileContent(bytes, contentTypeOf(image.fileExt()), image.fileName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductImageView bindProductImage(Long taskId, Long fileId) {
        CreativeProjectVo project = getProject(taskId);
        if (project.getProductId() == null) {
            throw new ServiceException("该项目没有关联产品，无法设为产品图；请先在项目里选择产品");
        }
        productService.bindImageFromFile(project.getProductId(), fileId);
        appendEvent(taskId, "PRODUCT_IMAGE", "PRODUCT_IMAGE_SET",
            "{\"fileId\":" + fileId + ",\"productId\":" + project.getProductId() + "}");
        return productImage(taskId);
    }

    @Override
    public List<CpTaskFileVo> listFiles(Long taskId) {
        return contentTaskService.listFiles(taskId);
    }

    @Override
    public FileContent readFileContent(Long taskId, Long fileId) {
        CpTaskFileVo file = contentTaskService.listFiles(taskId).stream()
            .filter(f -> fileId != null && fileId.equals(f.getFileId()))
            .findFirst()
            .orElseThrow(() -> new ServiceException("附件不在该项目中：" + fileId));
        if (StringUtils.isBlank(file.getFileRef())) {
            throw new ServiceException("附件没有存储引用，无法读取：" + fileId);
        }
        byte[] bytes = contentOssHelper.getBytes(file.getFileRef());
        return new FileContent(bytes, contentTypeOf(file.getFileExt()), file.getFileName());
    }

    @Override
    public List<DpStageEventVo> timeline(Long taskId) {
        return eventMapper.selectVoList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DpStageEvent>()
            .eq(DpStageEvent::getTaskId, taskId)
            .orderByAsc(DpStageEvent::getCreateTime)
            .orderByAsc(DpStageEvent::getId));
    }

    @Override
    public String stageOf(Long taskId) {
        return readStage(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveStage(Long taskId, DpVisualStageEnum target, String action, String detailJson) {
        if (target == null) {
            throw new ServiceException("目标阶段不能为空");
        }
        String current = readStage(taskId);
        DpVisualStageEnum from = DpVisualStageEnum.find(current);
        if (from == null) {
            from = DpVisualStageEnum.MATERIAL_READY;
        }
        if (!from.canMoveTo(target)) {
            throw new ServiceException("项目已处于「" + from.getDesc() + "」，不能再变为「"
                + target.getDesc() + "」");
        }
        if (!target.getCode().equals(current)) {
            stageMapper.updateStage(taskId, target.getCode());
        }
        insertEvent(taskId, "STAGE", current, target.getCode(), action, detailJson);
        // V0.2 D2：同一次调用里同步"项目步骤状态"（dp_project_step_state）。
        //   · 单一写入点：本表只在这里写（经 CreativeStepStateWriter，查询路径永不写库）；
        //   · visual_stage 仍是阶段与合法性的权威，本表是它的派生视图；
        //   · 事件已在上一行写掉——本表行上的 stage_code 记录是哪次变更把它推到该状态，便于对账。
        // 派生表写失败**不让阶段变更失败**：阶段与事件才是权威，本表可由两者重建；
        // 但也绝不静默——失败打 ERROR，便于发现"页面步骤状态不动"的根因。
        try {
            stepStateWriter.sync(taskId, target.getCode(), stageMapper.selectDeliverableType(taskId));
        } catch (Exception e) {
            log.error("步骤状态同步失败（阶段变更本身已成功）taskId={} stage={}：{}",
                taskId, target.getCode(), e.getMessage());
        }
    }

    @Override
    public void appendEvent(Long taskId, String eventType, String action, String detailJson) {
        String current = readStage(taskId);
        insertEvent(taskId, eventType, current, current, action, detailJson);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 读取视觉阶段（列可能为 NULL：老任务第一次进入视觉工厂）。
     *
     * @param taskId 项目ID
     * @return 阶段编码（NULL 视为资料就绪）
     */
    private String readStage(Long taskId) {
        Map<String, Object> row = stageMapper.selectStage(taskId);
        if (row == null) {
            throw new ServiceException("视觉项目不存在：" + taskId);
        }
        Object stage = row.get("visualStage");
        if (stage == null || StringUtils.isBlank(String.valueOf(stage))) {
            return DpVisualStageEnum.MATERIAL_READY.getCode();
        }
        return String.valueOf(stage);
    }

    private void insertEvent(Long taskId, String eventType, String fromStage, String toStage,
                             String action, String detailJson) {
        try {
            DpStageEvent event = new DpStageEvent();
            event.setTaskId(taskId);
            event.setEventType(eventType);
            event.setFromStage(fromStage);
            event.setToStage(toStage);
            event.setAction(action);
            event.setDetailJson(detailJson);
            event.setActorId(LoginHelper.getUserId());
            event.setActorName(nicknameOrNull());
            eventMapper.insert(event);
        } catch (Exception e) {
            // 事件是辅助证据，不能因为它写失败就把业务动作回滚掉
            log.warn("写入视觉阶段事件失败 taskId={} action={} error={}", taskId, action,
                e.getClass().getSimpleName());
        }
    }

    private String nicknameOrNull() {
        try {
            return LoginHelper.getLoginUser() == null ? null : LoginHelper.getLoginUser().getNickname();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 校验"这个项目属于视觉工厂能处理的交付类型"（R21 起不再是"只处理电商详情页"）。
     *
     * <p><b>为什么放开</b>：文档 §9 明确"不同业务必须允许不同流程"，§9.2 的商品主图是 P1 场景；
     * 硬写 {@code ECOM_DETAIL} 会让第二个交付类型连项目页都进不去，配置层的价值就只剩一半。</p>
     *
     * <p><b>放开的边界（fail-closed）</b>：交付类型必须在 {@code dp_delivery_type} 里**存在且启用**，
     * 并且要有已发布的场景档案（否则前端拿不到步骤/规格/工作台，页面会空）；
     * 查不到就明确报错——不是"什么都放进来"。</p>
     *
     * @param task 内容任务
     */
    /**
     * 视觉工厂支持的交付类型（R21）：配置里启用、且有已发布场景档案的那些。
     *
     * <p>列表页按它过滤，而不是"什么任务都显示"——视频/说明书这类不走这套流程的任务
     * 混进视觉项目列表会让人以为它们也能做。</p>
     *
     * @return 交付类型编码列表；一个都没有时返回空列表（调用方返回空页）
     */
    private List<String> supportedDeliverableTypes() {
        List<String> codes = new ArrayList<>();
        for (DpDeliveryType type : scenarioConfigService.listDeliveryTypes()) {
            String code = type.getDeliveryType();
            if (StringUtils.isBlank(code) || "1".equals(type.getEnabled())) {
                continue;
            }
            if (scenarioConfigService.getScenario(code) != null) {
                codes.add(code);
            }
        }
        return codes;
    }

    private void requireEcomDetail(CpTaskVo task) {
        String type = StringUtils.trimToNull(task.getDeliverableType());
        if (type == null) {
            throw new ServiceException("该任务没有交付类型，视觉工厂无法处理：" + task.getTaskName());
        }
        DpDeliveryType deliveryType = scenarioConfigService.getDeliveryType(type);
        if (deliveryType == null) {
            throw new ServiceException("交付类型不在视觉工厂的配置里（dp_delivery_type）：" + type
                + "——请先在场景配置里登记该交付类型");
        }
        boolean enabled = !"1".equals(deliveryType.getEnabled());
        if (!enabled) {
            throw new ServiceException("交付类型已停用：" + type);
        }
        if (scenarioConfigService.getScenario(type) == null) {
            throw new ServiceException("交付类型 " + type + " 还没有已发布的场景档案（dp_scenario_profile），"
                + "视觉工厂无法确定流程与步骤");
        }
    }

    private CreativeProjectVo toProjectVo(CpTaskVo task) {
        CreativeProjectVo vo = new CreativeProjectVo();
        vo.setTaskId(task.getTaskId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskName(task.getTaskName());
        vo.setDeliverableType(task.getDeliverableType());
        vo.setProductId(task.getProductId());
        vo.setProductName(task.getProductName());
        vo.setProductCode(task.getProductCode());
        vo.setSkuCode(task.getSkuCode());
        vo.setOwnerId(task.getOwnerId());
        vo.setOwnerName(task.getOwnerName());
        vo.setStatus(task.getStatus());
        vo.setDataLevel(task.getDataLevel());
        vo.setAllowExternal(task.getAllowExternal());
        vo.setBlockReason(task.getBlockReason());
        vo.setPendingCardCount(task.getPendingCardCount());
        vo.setBlockingCardCount(task.getBlockingCardCount());
        vo.setDeadline(task.getDeadline());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /**
     * 由扩展名推 MIME（附件表只存扩展名）。
     *
     * @param ext 扩展名
     * @return MIME
     */
    private static String contentTypeOf(String ext) {
        String e = ext == null ? "" : ext.trim().toLowerCase();
        return switch (e) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

}
