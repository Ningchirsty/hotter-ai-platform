package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 分镜展示对象（含屏列表）。
 *
 * @author creative
 */
@Data
public class DpStoryboardVo implements Serializable {

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
    private String storyboardNo;

    /**
     * 版本
     */
    private Integer version;

    /**
     * 采用的方向ID
     */
    private Long visualDirectionId;

    /**
     * 采用的方向名称（页面直读）
     */
    private String visualDirectionName;

    /**
     * 采用的基因ID
     */
    private Long visualDnaId;

    /**
     * 采用的基因版本号
     */
    private Integer visualDnaVersion;

    /**
     * 屏数
     */
    private Integer screenCount;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 来源（TEMPLATE/AI/MANUAL）
     */
    private String source;

    /**
     * 来源描述
     */
    private String sourceDesc;

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
     * 屏列表
     */
    private List<DpStoryboardScreenVo> screens = new ArrayList<>();

}
