package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 交付渲染结果视图（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * @author creative
 */
@Data
public class DeliveryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long taskId;

    /** 交付类型 */
    private String deliveryType;

    /** 渲染模式（dp_delivery_type.render_mode） */
    private String renderMode;

    /** 这次实际用的渲染器编码 */
    private String renderer;

    /** 这次实际用的渲染器中文名 */
    private String rendererName;

    /** 当前最新版本（没有交付过为 0） */
    private Integer currentVersion;

    /** 全部交付版本（倒序） */
    private List<DeliveryArtifactVo> artifacts;

    /**
     * 渲染器能力清单（哪些能跑、哪些还没实现）。
     *
     * <p>放这里是为了页面一次请求就能把"能选什么"显示出来，不用再打一个接口。</p>
     */
    private List<RendererCapabilityVo> renderers;

    /**
     * 渲染器能力（页面显示"已实现 / 规划中"）。
     */
    @Data
    public static class RendererCapabilityVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 编码 */
        private String code;

        /** 中文名 */
        private String name;

        /** 服务的场景步骤 */
        private String targetStep;

        /** 是否已实现（false = 只登记、不能执行） */
        private Boolean implemented;

        /** 说明 */
        private String note;

        /** 是否由当前交付类型的渲染模式选中 */
        private Boolean selected;
    }
}
