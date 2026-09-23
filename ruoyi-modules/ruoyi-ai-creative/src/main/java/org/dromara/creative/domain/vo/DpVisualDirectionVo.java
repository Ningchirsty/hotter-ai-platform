package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉方向展示对象。
 *
 * @author creative
 */
@Data
public class DpVisualDirectionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long taskId;

    /**
     * 方向代号（A/B/C）
     */
    private String directionCode;

    /**
     * 方向名称
     */
    private String directionName;

    /**
     * 一句话概念
     */
    private String concept;

    /**
     * 差异点（结构化，页面直接展示）
     */
    private Map<String, Object> strategy = new LinkedHashMap<>();

    /**
     * 原始策略 json
     */
    private String strategyJson;

    /**
     * 差异点标签（哪几项与其它方向不同）
     */
    private List<String> differences = new ArrayList<>();

    /**
     * 预览附件ID
     */
    private List<String> previewFileIds = new ArrayList<>();

    /**
     * 状态（GENERATED/SELECTED/REJECTED）
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 来源
     */
    private String source;

    /**
     * 选定人
     */
    private Long selectedBy;

    /**
     * 选定时间
     */
    private LocalDateTime selectedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
