package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分镜单屏 dp_storyboard_screen
 *
 * <p>一整屏的「要讲什么」与「画面单独要说什么」都在这里。{@code picture_solo_statement}
 * 是刻意加的字段：详情页的图常常被当成装饰，结果出一堆好看但没信息量的图——
 * 强制写清「这张图不讲文案时自己要说清什么」，是把「好看」拉回「有用」的最便宜手段。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_storyboard_screen")
public class DpStoryboardScreen extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 分镜（dp_storyboard.id）
     */
    private Long storyboardId;

    /**
     * 视觉项目（冗余，便于按项目直查）
     */
    private Long taskId;

    /**
     * 屏号 S01/S02…
     */
    private String screenNo;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 屏类型（HERO/SELLING_POINT/SCENE/DETAIL/SIZE/PACKAGE/BRAND）
     */
    private String screenType;

    /**
     * 标题文案
     */
    private String title;

    /**
     * 副标题文案
     */
    private String subtitle;

    /**
     * 正文文案
     */
    private String bodyText;

    /**
     * 画面独白（这张图不讲文案时自己要说清什么）
     */
    private String pictureSoloStatement;

    /**
     * 视觉规格（镜头/构图/光线/背景）
     */
    private String specJson;

    /**
     * 指定出图能力编码
     */
    private String workflowCode;

    /**
     * 产品保真等级（STRICT严格一致/LOOSE允许艺术化）
     */
    private String productLockLevel;

    /**
     * 状态（DRAFT/READY/GENERATING/GENERATED/APPROVED/REJECTED）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
