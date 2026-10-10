package org.dromara.aigov.workspace.recommend.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自然语言推荐的入参（主文档线增量 7）。
 *
 * <p>刻意只有"一句话需求"：推荐不接受"你想让我从哪些卡片里挑"这种参数——
 * 候选清单**只能**来自服务端算出的当前用户可见卡片，否则调用方就能拿推荐接口去
 * 探别人可见的岗位编码。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRecommendSuggestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 员工用自然语言描述的需求（如"我要给新品做一套详情页"）
     */
    private String input;

}
