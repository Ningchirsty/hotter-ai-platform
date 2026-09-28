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
 * 文案与要点块 dp_copy_block（详情页的「文字」）。
 *
 * <p><b>解决的实际缺口</b>：R7 之前，详情页的文字只有两处——事实快照（≤500 字一行的规格类文本）
 * 和分镜单屏文案（这一屏这张图配什么字）。「详情页正文分段 / 卖点清单与其优先级 / 参数表」
 * 无处可放，只能被压扁成一行字符串或干脆不录。本表把它们变成可排序、可分段、可逐条引用的块。</p>
 *
 * <p><b>为什么 sort_no 是业务字段而不是展示细节</b>：卖点的先后顺序就是优先级，
 * 详情页从上到下的顺序就是阅读顺序。把顺序丢掉，等于把「先说什么」交给数据库默认排序。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_copy_block")
public class DpCopyBlock extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花ID）
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 块类型（SELLING_POINT/BODY_SECTION/SPEC_ROW/MUST_SHOW）
     */
    private String blockType;

    /**
     * 排序（详情页上从上到下的顺序，也是卖点优先级）
     */
    private Integer sortNo;

    /**
     * 卖点标题/段落小标题/参数名
     */
    private String title;

    /**
     * 卖点说明/段落正文/参数值
     */
    private String content;

    /**
     * 来源（MANUAL人工录入/MODEL模型起草/FACT由已确认事实派生）
     */
    private String source;

    /**
     * 来源引用（事实编码，或分镜屏号 S03），便于回看它从哪来
     */
    private String sourceRef;

    /**
     * 状态（DRAFT草稿/CONFIRMED已确认）
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
