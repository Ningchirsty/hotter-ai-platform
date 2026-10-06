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
 * 分镜单屏展示对象。
 *
 * @author creative
 */
@Data
public class DpStoryboardScreenVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 分镜ID
     */
    private Long storyboardId;

    /**
     * 项目ID
     */
    private Long taskId;

    /**
     * 屏号
     */
    private String screenNo;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 屏类型
     */
    private String screenType;

    /**
     * 屏类型描述
     */
    private String screenTypeDesc;

    /**
     * 标题
     */
    private String title;

    /**
     * 副标题
     */
    private String subtitle;

    /**
     * 正文
     */
    private String bodyText;

    /**
     * 画面独白
     */
    private String pictureSoloStatement;

    /**
     * 视觉规格（结构化）
     */
    private Map<String, Object> spec = new LinkedHashMap<>();

    /**
     * 视觉规格 json
     */
    private String specJson;

    /**
     * 出图能力编码
     */
    private String workflowCode;

    /**
     * 产品保真等级（STRICT/LOOSE）
     */
    private String productLockLevel;

    /**
     * 状态
     */
    private String status;

    /**
     * 逐屏锁定（DRAFT/LOCKED；v1 裁定 ④）。
     */
    private String lockStatus;

    /**
     * 逐屏锁定的中文口径（「未锁定，可改」/「已单独锁定，冻结这一屏」）——页面直接显示，不再由前端拼。
     */
    private String lockStatusDesc;

    /**
     * 这一屏能不能改（＝未锁定且整版未锁定）。页面据此决定按钮的可用与提示语，
     * 规则只在后端算一次（前端复算容易与守卫漂移）。
     */
    private Boolean editable;

    /**
     * 备注
     */
    private String remark;

}
