package org.dromara.aigov.workspace.recommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 自然语言推荐（`/intent/suggest`）配置（主文档线增量 7）。
 *
 * <h3>为什么默认关闭</h3>
 * <p>每一次推荐都是**一次真实的模型调用**（要花钱、要走配额）。这与训练台的测试调用是同一类东西，
 * 所以沿用同一条已经定过的口径：<b>默认关闭，需要的人显式打开</b>。
 * 关着的时候接口明确报错，而不是静默返回空列表——静默会让调用方以为"没有推荐"。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.recommend")
public class AigRecommendProperties {

    /**
     * 是否启用自然语言推荐（默认关闭：开着就在花真钱）
     */
    private boolean enabled = false;

    /**
     * 用户输入的最大字符数（拦住"把整篇文档贴进来"这种调用）
     */
    private int maxInputChars = 500;

    /**
     * 最多推荐几张卡片（模型返回更多也只取前 N 个）
     */
    private int maxCandidates = 3;

    /**
     * 提示词里最多列出多少张可见卡片（可见卡片很多的岗位不必全塞进提示词）
     */
    private int maxCatalogCards = 60;

    /**
     * 取输入上限（带下限保护）。
     *
     * @return 上限
     */
    public int effectiveMaxInputChars() {
        return maxInputChars <= 0 ? 500 : Math.min(maxInputChars, 4000);
    }

    /**
     * 取推荐条数上限（带下限保护）。
     *
     * @return 上限
     */
    public int effectiveMaxCandidates() {
        return maxCandidates <= 0 ? 3 : Math.min(maxCandidates, 10);
    }

    /**
     * 取提示词里卡片数上限（带下限保护）。
     *
     * @return 上限
     */
    public int effectiveMaxCatalogCards() {
        return maxCatalogCards <= 0 ? 60 : Math.min(maxCatalogCards, 200);
    }

}
