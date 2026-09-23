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
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.DpStageEvent;
import org.dromara.creative.domain.bo.CreativeProjectBo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpStageEventVo;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpStageEventMapper;
import org.dromara.creative.service.ICreativeProjectService;
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
    private final ContentOssHelper contentOssHelper;
    private final CreativeTaskStageMapper stageMapper;
    private final DpStageEventMapper eventMapper;

    @Override
    public PageResult<CreativeProjectVo> queryPage(ContentTaskBo bo, PageQuery pageQuery) {
        // 视觉工厂只处理电商详情页：无论前端传什么，交付类型一律强制覆盖
        bo.setQueryDeliverableType(CreativeConstants.DELIVERABLE_ECOM_DETAIL);
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
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(CreativeProjectBo bo) {
        ContentTaskBo taskBo = new ContentTaskBo();
        taskBo.setTaskName(bo.getTaskName());
        taskBo.setDeliverableType(CreativeConstants.DELIVERABLE_ECOM_DETAIL);
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
        // 复用内容模块的附件上传（落对象存储 + 登记 cp_task_file，业务留痕在内容侧）
        Long fileId = contentTaskService.uploadFile(taskId, null, file);
        String stage = readStage(taskId);
        moveStage(taskId, DpVisualStageEnum.MATERIAL_READY, "REFERENCE_UPLOADED",
            "{\"fileId\":" + fileId + ",\"fromStage\":" + quote(stage) + "}");
        return fileId;
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

    private void requireEcomDetail(CpTaskVo task) {
        if (!CreativeConstants.DELIVERABLE_ECOM_DETAIL.equalsIgnoreCase(task.getDeliverableType())) {
            throw new ServiceException("该任务不是电商详情页项目，视觉工厂不处理：" + task.getDeliverableType());
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
