package org.dromara.aigov.studio.helper;

import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.common.core.utils.StringUtils;

import java.util.Map;

/**
 * 把草稿的八个 Prompt 分节拼成模板文本。
 *
 * <p><b>为什么必须只有这一份实现</b>：提交（落 {@code aig_agent_version.prompt_template}）
 * 与沙箱测试（真正发出去的那段 prompt）都要用它。若两处各拼一遍，
 * 迟早会漂移成"测试跑的是 A 版本、提交上去的是 B 版本"——
 * 而测试的全部意义就是"证明这份内容可用"。</p>
 *
 * @author ai-gov
 */
public final class AigStudioPromptBuilder {

    private AigStudioPromptBuilder() {
    }

    /**
     * 按标准顺序拼装分节。
     *
     * @param content 草稿内容
     * @return 模板文本（分节名 + 正文；空内容返回空串）
     */
    public static String build(AigStudioDraftContent content) {
        if (content == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        Map<String, String> sections = content.getPromptSections() == null
            ? Map.of() : content.getPromptSections();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            text.append("## ").append(key).append('\n')
                .append(StringUtils.blankToDefault(sections.get(key), "")).append("\n\n");
        }
        return text.toString().trim();
    }

}
