package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitSensitiveAuditQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitSensitiveAudit;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitSensitiveAuditVo;
import org.dromara.hrtalent.mapper.RecruitSensitiveAuditMapper;
import org.dromara.hrtalent.service.recruitment.ISensitiveAuditService;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感操作审计查询服务实现（SPEC-P3 §3.6 / 设计文档 §15.2、§21.9）。
 *
 * <p><b>安全要点</b>：</p>
 * <ul>
 *     <li>审计表只查不改不删，本实现不暴露任何写接口（写入走 {@code SensitiveAuditRecorder}）；</li>
 *     <li>导出按 {@code hrtalent.export-max-rows} 限制最大条数，避免无界导出；</li>
 *     <li>导出动作本身写入一条 {@code export} 审计，明细只记录筛选条件与导出行数，
 *     <b>不写</b>电话明文、简历正文、背调明细与签名地址；</li>
 *     <li>日志不输出审计明细内容与敏感字段。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveAuditServiceImpl implements ISensitiveAuditService {

    /**
     * 导出用途（写入审计的 purpose）。
     */
    private static final String EXPORT_PURPOSE = "敏感操作审计导出";

    /**
     * 导出文件工作表名与文件前缀。
     */
    private static final String EXPORT_SHEET_NAME = "敏感操作审计";

    /**
     * 事件时间文本格式（导出用，避免导出格式随环境变化）。
     */
    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 敏感操作审计 Mapper（只读使用）。
     */
    private final RecruitSensitiveAuditMapper recruitSensitiveAuditMapper;

    /**
     * 招聘与人才管理业务配置（导出最大条数）。
     */
    private final HrTalentProperties hrTalentProperties;

    /**
     * 敏感操作审计统一入口（导出动作本身也要留痕）。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    @Override
    public PageResult<RecruitSensitiveAuditVo> queryPage(RecruitSensitiveAuditQueryBo bo, PageQuery pageQuery) {
        Page<RecruitSensitiveAuditVo> page = recruitSensitiveAuditMapper.selectVoPage(
            pageQuery.build(), orderByEventTimeDesc(buildWrapper(bo)));
        if (page.getRecords() != null) {
            page.getRecords().forEach(this::fillEventTimeText);
        }
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public void export(RecruitSensitiveAuditQueryBo bo, HttpServletResponse response) {
        int maxRows = hrTalentProperties.getExportMaxRows() <= 0 ? Integer.MAX_VALUE
            : hrTalentProperties.getExportMaxRows();
        List<RecruitSensitiveAuditVo> records = recruitSensitiveAuditMapper.selectVoList(
            orderByEventTimeDesc(buildWrapper(bo)));
        boolean truncated = records.size() > maxRows;
        if (truncated) {
            records = records.subList(0, maxRows);
        }
        if (records.isEmpty()) {
            throw new ServiceException("当前筛选条件下没有可导出的审计记录");
        }
        records.forEach(this::fillEventTimeText);

        // 先在内存中生成 Excel，生成成功后再写审计与响应，保证审计结果与实际导出一致
        byte[] data;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelBuilder.of(records, RecruitSensitiveAuditVo.class)
                .sheetName(EXPORT_SHEET_NAME)
                .toStream(out);
            data = out.toByteArray();
        } catch (IOException e) {
            log.error("敏感操作审计导出内容生成失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("导出失败，请稍后重试");
        }

        sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_EXPORT, SensitiveAuditRecorder.BIZ_AUDIT, null,
            EXPORT_PURPOSE, SensitiveAuditRecorder.RESULT_SUCCESS,
            exportDetail(bo, records.size(), truncated));

        FileUtils.setAttachmentResponseHeader(response, EXPORT_SHEET_NAME + ".xlsx");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
        try {
            response.getOutputStream().write(data);
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("敏感操作审计导出响应写出失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("导出失败，请稍后重试");
        }
        log.info("敏感操作审计导出完成, rows={}, truncated={}", records.size(), truncated);
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 构造查询条件（追加型审计表的只读检索）。
     *
     * @param bo 查询条件，可为空
     * @return 查询包装器
     */
    private LambdaQueryWrapper<RecruitSensitiveAudit> buildWrapper(RecruitSensitiveAuditQueryBo bo) {
        RecruitSensitiveAuditQueryBo query = bo == null ? new RecruitSensitiveAuditQueryBo() : bo;
        return new LambdaQueryWrapper<RecruitSensitiveAudit>()
            .eq(StringUtils.isNotBlank(query.getEventType()), RecruitSensitiveAudit::getEventType, query.getEventType())
            .eq(StringUtils.isNotBlank(query.getBizType()), RecruitSensitiveAudit::getBizType, query.getBizType())
            .eq(query.getBizId() != null, RecruitSensitiveAudit::getBizId, query.getBizId())
            .eq(query.getOperatorId() != null, RecruitSensitiveAudit::getOperatorId, query.getOperatorId())
            .eq(StringUtils.isNotBlank(query.getResult()), RecruitSensitiveAudit::getResult, query.getResult())
            .ge(query.getEventTimeBegin() != null, RecruitSensitiveAudit::getEventTime, query.getEventTimeBegin())
            .le(query.getEventTimeEnd() != null, RecruitSensitiveAudit::getEventTime, query.getEventTimeEnd());
    }

    /**
     * 固定按事件时间倒序、审计ID倒序输出。
     *
     * @param wrapper 查询包装器
     * @return 追加排序后的查询包装器
     */
    private LambdaQueryWrapper<RecruitSensitiveAudit> orderByEventTimeDesc(
        LambdaQueryWrapper<RecruitSensitiveAudit> wrapper) {
        return wrapper.orderByDesc(RecruitSensitiveAudit::getEventTime)
            .orderByDesc(RecruitSensitiveAudit::getAuditId);
    }

    /**
     * 回填事件时间文本（导出列，避免导出格式随环境变化）。
     *
     * @param vo 审计视图对象
     */
    private void fillEventTimeText(RecruitSensitiveAuditVo vo) {
        if (vo != null && vo.getEventTime() != null) {
            vo.setEventTimeText(EVENT_TIME_FORMATTER.format(vo.getEventTime()));
        }
    }

    /**
     * 构造导出动作的审计明细：只记录筛选条件与行数，不含任何敏感明文。
     *
     * @param bo        查询条件
     * @param rows      实际导出行数
     * @param truncated 是否因超过上限被截断
     * @return 脱敏 JSON 明细
     */
    private String exportDetail(RecruitSensitiveAuditQueryBo bo, int rows, boolean truncated) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (bo != null) {
            detail.put("eventType", bo.getEventType());
            detail.put("bizType", bo.getBizType());
            detail.put("bizId", bo.getBizId());
            detail.put("operatorId", bo.getOperatorId());
            detail.put("result", bo.getResult());
            detail.put("eventTimeBegin", bo.getEventTimeBegin() == null ? null
                : EVENT_TIME_FORMATTER.format(bo.getEventTimeBegin()));
            detail.put("eventTimeEnd", bo.getEventTimeEnd() == null ? null
                : EVENT_TIME_FORMATTER.format(bo.getEventTimeEnd()));
        }
        detail.put("rows", rows);
        detail.put("truncated", truncated);
        return toJson(detail);
    }

    /**
     * 构造脱敏 JSON 明细。
     * <p>不依赖 Spring 容器内的 JsonMapper，保证审计明细构造在任意上下文中都可用。</p>
     *
     * @param detail 键值对（值为 null 的键不输出）
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> detail) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append(jsonString(String.valueOf(value)));
            }
        }
        return json.append('}').toString();
    }

    /**
     * 转义 JSON 字符串值。
     *
     * @param value 原值
     * @return 带引号的转义字符串
     */
    private String jsonString(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

}
