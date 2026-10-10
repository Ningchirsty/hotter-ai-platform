package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门户里的岗位分类（主文档线增量 2）。
 *
 * @author ai-gov
 */
@Data
public class AigPortalCategoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类编码
     */
    private String code;

    /**
     * 分类名称（员工看到的）
     */
    private String name;

    /**
     * 该分类下的卡片数（0 表示这一栏暂时是空的，而不是"这栏不存在"）
     */
    private Integer actionCount;

}
