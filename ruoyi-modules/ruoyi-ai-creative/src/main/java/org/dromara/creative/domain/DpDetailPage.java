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
 * 详情页 dp_detail_page（一项目一页，版本在 dp_detail_page_version）。
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_detail_page")
public class DpDetailPage extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 当前版本号（0＝尚无版本）
     */
    private Integer currentVersion;

    /**
     * 状态（DRAFT/V08_READY机排完成/REFINING人工精修/FINAL最终/ARCHIVED）
     */
    private String status;

    private String remark;

    @TableLogic
    private String delFlag;

}
