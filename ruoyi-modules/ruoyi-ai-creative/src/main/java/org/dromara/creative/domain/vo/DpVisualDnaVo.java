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
 * Visual DNA 展示对象。
 *
 * <p>既给结构化字段（页面直接编辑），也给原始 json 与证据链（可追溯「这一项是从哪来的」），
 * 还给校验结论（{@code issues}）——页面上「为什么不能锁定」不需要前端自己猜。</p>
 *
 * <p><b>刻意不加 {@code @AutoMapper}</b>：实体里 styleKeywords/avoidKeywords 是逗号串（索引列），
 * 这里要的是数组，两者不同形；而 colors/lighting/productRatio/evidence 这些结构在实体里根本不存在。
 * 由服务层 {@code toVo} 从权威 json 手工组装，比让 MapStruct 硬凑一份「能编译但语义错」的映射更可靠。</p>
 *
 * @author creative
 */
@Data
public class DpVisualDnaVo implements Serializable {

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
     * 编号
     */
    private String dnaNo;

    /**
     * 版本
     */
    private Integer version;

    /**
     * 状态（DRAFT/REVIEW/LOCKED）
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 主体（产品名）
     */
    private String subject;

    /**
     * 风格关键词
     */
    private List<String> styleKeywords = new ArrayList<>();

    /**
     * 禁忌关键词
     */
    private List<String> avoidKeywords = new ArrayList<>();

    /**
     * 配色
     */
    private Map<String, String> colors = new LinkedHashMap<>();

    /**
     * 光线
     */
    private Map<String, String> lighting = new LinkedHashMap<>();

    /**
     * 产品占比区间
     */
    private Map<String, Integer> productRatio = new LinkedHashMap<>();

    /**
     * 饱和度档
     */
    private String saturation;

    /**
     * 对比度档
     */
    private String contrastLevel;

    /**
     * 留白档
     */
    private String whitespaceLevel;

    /**
     * 字体风格
     */
    private String typographyStyle;

    /**
     * 场景类型
     */
    private String sceneType;

    /**
     * 原始 json（权威内容）
     */
    private String dnaJson;

    /**
     * 来源（AI/MANUAL/FACTS）
     */
    private String source;

    /**
     * 来源描述（页面直接展示，说明这是不是模型结论）
     */
    private String sourceDesc;

    /**
     * 模型标识
     */
    private String modelKey;

    /**
     * 治理层调用链ID
     */
    private String traceId;

    /**
     * 证据链
     */
    private List<Map<String, Object>> evidence = new ArrayList<>();

    /**
     * 校验问题（非空表示不可锁定）
     */
    private List<String> issues = new ArrayList<>();

    /**
     * 锁定人
     */
    private Long approvedBy;

    /**
     * 锁定时间
     */
    private LocalDateTime approvedAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否已锁定
     *
     * @return 是否锁定
     */
    public boolean isLocked() {
        return "LOCKED".equals(status);
    }

}
