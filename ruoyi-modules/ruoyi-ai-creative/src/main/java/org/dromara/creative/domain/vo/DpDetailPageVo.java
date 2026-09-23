package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 详情页展示对象（含版本列表）。
 *
 * @author creative
 */
@Data
public class DpDetailPageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private Integer currentVersion;
    private String status;
    private String statusDesc;
    private String remark;

    /**
     * 版本列表（倒序）
     */
    private List<DpDetailPageVersionVo> versions = new ArrayList<>();

    /**
     * 还没有已选定产出的屏号（渲染时会明确画出「这一屏还没有产出」）
     */
    private List<String> screensWithoutSelection = new ArrayList<>();

    /**
     * 渲染服务是否可用
     */
    private Boolean rendererAvailable;

    /**
     * 本次渲染用的模板（编码@版本）
     */
    private String templateKey;

    /**
     * 版本条目。
     */
    @Data
    public static class DpDetailPageVersionVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;
        private Integer version;
        private String kind;
        private String kindDesc;
        private Long renderedFileId;
        private Integer pageWidth;
        private Integer pageHeight;
        private Integer screenCount;
        private String status;
        private String statusDesc;
        private Long reviewBy;
        private LocalDateTime reviewAt;
        private String reviewComment;
        private String remark;
        private LocalDateTime createTime;
        private Boolean previewable;

    }

}
