package org.dromara.talent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.talent.config.TalentProperties;
import org.dromara.talent.domain.TlParseField;
import org.dromara.talent.domain.TlParseTask;
import org.dromara.talent.domain.TlTalent;
import org.dromara.talent.domain.TlTalentAttachment;
import org.dromara.talent.domain.bo.TlParseFieldConfirmBo;
import org.dromara.talent.domain.vo.TlParseFieldVo;
import org.dromara.talent.domain.vo.TlParseTaskVo;
import org.dromara.talent.enums.ParseTaskStatusEnum;
import org.dromara.talent.helper.TalentScopeHelper;
import org.dromara.talent.mapper.TlParseFieldMapper;
import org.dromara.talent.mapper.TlParseTaskMapper;
import org.dromara.talent.mapper.TlTalentAttachmentMapper;
import org.dromara.talent.mapper.TlTalentMapper;
import org.dromara.talent.service.ITalentParseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历解析任务服务实现。
 * <p>
 * 只存元数据：{@code tl_parse_task.error_summary} 禁止写入简历正文；
 * 人工确认值只写 {@code tl_parse_field}，<b>不自动覆盖</b>人才档案主字段（避免"解析即覆盖"的越权写入通道）。
 *
 * @author talent
 */
@Service
@RequiredArgsConstructor
public class TalentParseServiceImpl implements ITalentParseService {

    /**
     * 字段确认状态：待确认 / 已确认 / 已忽略。
     */
    private static final List<String> CONFIRM_STATUS = List.of("0", "1", "2");

    /**
     * 重试次数上限。
     */
    private static final int MAX_RETRY = 3;

    /**
     * 解析任务 Mapper。
     */
    private final TlParseTaskMapper parseTaskMapper;

    /**
     * 解析字段 Mapper。
     */
    private final TlParseFieldMapper parseFieldMapper;

    /**
     * 附件 Mapper（任务未绑定 talentId 时用于反查人才）。
     */
    private final TlTalentAttachmentMapper attachmentMapper;

    /**
     * 人才主档 Mapper。
     */
    private final TlTalentMapper talentMapper;

    /**
     * 数据范围与单条授权。
     */
    private final TalentScopeHelper scopeHelper;

    /**
     * 人才库配置（解析开关）。
     */
    private final TalentProperties talentProperties;

    /**
     * 解析任务详情。
     *
     * @param taskId 任务ID
     * @return 任务详情
     */
    @Override
    public TlParseTaskVo getTask(Long taskId) {
        TlParseTask task = loadTaskChecked(taskId);
        TlParseTaskVo vo = BeanUtil.copyProperties(task, TlParseTaskVo.class);
        if (task.getAttachmentId() != null) {
            TlTalentAttachment attachment = attachmentMapper.selectById(task.getAttachmentId());
            if (attachment != null) {
                vo.setAttachmentName(attachment.getOriginalName());
                vo.setTalentId(task.getTalentId() == null ? attachment.getTalentId() : task.getTalentId());
            }
        }
        vo.setFields(listFieldsInternal(taskId));
        return vo;
    }

    /**
     * 解析字段列表。
     *
     * @param taskId 任务ID
     * @return 字段列表
     */
    @Override
    public List<TlParseFieldVo> listFields(Long taskId) {
        loadTaskChecked(taskId);
        return listFieldsInternal(taskId);
    }

    /**
     * 人工确认解析字段。
     *
     * @param taskId 任务ID
     * @param fields 字段确认列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmFields(Long taskId, List<TlParseFieldConfirmBo> fields) {
        loadTaskChecked(taskId);
        if (CollUtil.isEmpty(fields)) {
            return;
        }
        Long operatorId = LoginHelper.getUserId();
        LocalDateTime now = LocalDateTime.now();
        for (TlParseFieldConfirmBo bo : fields) {
            if (bo == null || bo.getFieldId() == null) {
                throw new ServiceException("解析字段ID不能为空");
            }
            if (StringUtils.isNotBlank(bo.getConfirmStatus()) && !CONFIRM_STATUS.contains(bo.getConfirmStatus())) {
                throw new ServiceException("非法的确认状态");
            }
            TlParseField field = parseFieldMapper.selectById(bo.getFieldId());
            if (field == null || !taskId.equals(field.getTaskId())) {
                throw new ServiceException("解析字段不存在或不属于该任务");
            }
            TlParseField entity = new TlParseField();
            entity.setFieldId(bo.getFieldId());
            entity.setConfirmedValue(StringUtils.substring(StringUtils.defaultString(bo.getConfirmedValue()), 0, 500));
            entity.setConfirmStatus(StringUtils.defaultIfBlank(bo.getConfirmStatus(), "1"));
            entity.setConfirmBy(operatorId);
            entity.setConfirmTime(now);
            parseFieldMapper.updateById(entity);
        }
    }

    /**
     * 重试解析任务。
     *
     * @param taskId 任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long taskId) {
        TlParseTask task = loadTaskChecked(taskId);
        if (!talentProperties.isParseEnabled()) {
            throw new ServiceException("简历解析功能未启用");
        }
        if (ParseTaskStatusEnum.SUCCESS.getCode().equals(task.getStatus())) {
            throw new ServiceException("解析已成功的任务无需重试");
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= MAX_RETRY) {
            throw new ServiceException("重试次数已达上限");
        }
        parseTaskMapper.update(null, new LambdaUpdateWrapper<TlParseTask>()
            .eq(TlParseTask::getTaskId, taskId)
            .set(TlParseTask::getStatus, ParseTaskStatusEnum.PENDING.getCode())
            .set(TlParseTask::getRetryCount, retryCount + 1)
            .set(TlParseTask::getErrorSummary, null)
            .set(TlParseTask::getStartTime, null)
            .set(TlParseTask::getFinishTime, null)
            .set(TlParseTask::getUpdateBy, LoginHelper.getUserId())
            .set(TlParseTask::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 创建解析任务。
     *
     * @param attachmentId 附件ID
     * @return 任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Long attachmentId) {
        TlTalentAttachment attachment = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (attachment == null || "1".equals(attachment.getDelFlag())) {
            throw new ServiceException("附件不存在");
        }
        TlTalent talent = talentMapper.selectById(attachment.getTalentId());
        scopeHelper.checkTalentVisible(talent);
        boolean enabled = talentProperties.isParseEnabled();
        TlParseTask task = new TlParseTask();
        task.setTalentId(talent.getTalentId());
        task.setAttachmentId(attachmentId);
        task.setStatus(enabled ? ParseTaskStatusEnum.PENDING.getCode() : ParseTaskStatusEnum.DISABLED.getCode());
        task.setRetryCount(0);
        parseTaskMapper.insert(task);
        return task.getTaskId();
    }

    /**
     * 加载解析任务并执行区域 / 单条授权校验。
     *
     * @param taskId 任务ID
     * @return 解析任务
     */
    private TlParseTask loadTaskChecked(Long taskId) {
        TlParseTask task = taskId == null ? null : parseTaskMapper.selectById(taskId);
        if (task == null || "1".equals(task.getDelFlag())) {
            throw new ServiceException("解析任务不存在");
        }
        Long talentId = task.getTalentId();
        if (talentId == null && task.getAttachmentId() != null) {
            TlTalentAttachment attachment = attachmentMapper.selectById(task.getAttachmentId());
            talentId = attachment == null ? null : attachment.getTalentId();
        }
        TlTalent talent = talentId == null ? null : talentMapper.selectById(talentId);
        scopeHelper.checkTalentVisible(talent);
        return task;
    }

    /**
     * 字段列表（调用方已完成授权）。
     *
     * @param taskId 任务ID
     * @return 字段列表
     */
    private List<TlParseFieldVo> listFieldsInternal(Long taskId) {
        return parseFieldMapper.selectVoList(new LambdaQueryWrapper<TlParseField>()
            .eq(TlParseField::getTaskId, taskId)
            .orderByAsc(TlParseField::getFieldName));
    }

}
