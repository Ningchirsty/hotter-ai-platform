package org.dromara.aigov.workspace.recommend.domain.vo;

import lombok.Data;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalActionVo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条推荐结果（主文档线增量 7）。
 *
 * <p>它由**两部分拼出来**：模型给出的卡片编码，和服务端从可见清单里查到的卡片本体。
 * 编码来自模型，<b>卡片本体只可能来自服务端</b>——所以即使模型编了一个不存在的编码，
 * 也拿不到任何卡片，只会被丢掉（见 {@code AigRecommendServiceImpl} 的交集过滤）。</p>
 *
 * <p>复用门户的 {@link AigPortalActionVo} 而不是另造一个卡片视图：推荐出去的卡片**就是**
 * 门户里那张卡片，字段一旦分叉，就会出现"推荐里能启动、门户里不能"这种只有用户能发现的差异。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRecommendSuggestionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 卡片所在岗位编码
     */
    private String roleCode;

    /**
     * 卡片所在岗位名称（展示用）
     */
    private String roleName;

    /**
     * 卡片本体（服务端可见清单里的那一张，非模型编造）
     */
    private AigPortalActionVo action;

}
