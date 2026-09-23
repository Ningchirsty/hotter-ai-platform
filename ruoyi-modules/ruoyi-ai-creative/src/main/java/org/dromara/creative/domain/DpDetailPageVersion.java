package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 详情页版本 dp_detail_page_version
 *
 * <p>{@code layout_json} 存**结构化排版数据**（每屏的文字与所用产出的ID），不存内联图片：
 * 这样版本可回溯（谁用了哪张图一目了然），也不会把几 MB 的 base64 灌进业务库。
 * 重新渲染时按其中的产出ID取字节再内联。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_detail_page_version")
public class DpDetailPageVersion extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 详情页ID
     */
    private Long detailPageId;

    /**
     * 视觉项目
     */
    private Long taskId;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 类型（V08机排版 / V10_FINAL最终版）
     */
    private String kind;

    /**
     * 结构化排版数据
     */
    private String layoutJson;

    /**
     * 渲染长图附件ID（cp_task_file.file_id）
     */
    private Long renderedFileId;

    /**
     * 页宽（px）
     */
    private Integer pageWidth;

    /**
     * 页高（px）
     */
    private Integer pageHeight;

    /**
     * 屏数
     */
    private Integer screenCount;

    /**
     * 整页质检ID（R2 的成品一致性检查，可为空）
     */
    private Long qaCheckId;

    /**
     * 状态（DRAFT/RENDERED/REVIEWING/APPROVED/REJECTED）
     */
    private String status;

    private Long reviewBy;

    private LocalDateTime reviewAt;

    private String reviewComment;

    private String remark;

    @TableLogic
    private String delFlag;

}
