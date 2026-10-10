package org.dromara.aigov.workspace.recommend.helper;

import org.dromara.aigov.workspace.portal.domain.vo.AigPortalActionVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐提示词的构造（主文档线增量 7）。
 *
 * <h3>三条不可让步</h3>
 * <ol>
 *     <li><b>只把"这个用户看得见"的卡片写进提示词</b>：提示词本身就是一次外发，
 *         把别人可见的岗位塞进去等于借推荐链路泄漏配置。所以入参就是门户那条可见性路径的产物
 *         （`IAigPortalService#listMyRoles` 的同源结果），这里不再接受"候选卡片"这种自由入参。</li>
 *     <li><b>要求严格 JSON 输出</b>（`{"actionCodes":[...]}`）：自然语言回答没法可靠解析，
 *         而"解析失败就返回空"会让功能看起来像没推荐——所以格式要在提示词里说死，
 *         解析端仍按 fail-closed 处理（见 {@link AigRecommendParser}）。</li>
 *     <li><b>明确"只推荐、不启动"</b>：模型不该以为自己在执行什么。
 *         提示词里写清它只回答"哪几张卡片相关"，执行由用户点确认（启动凭证在增量 3 已定）。</li>
 * </ol>
 *
 * @author ai-gov
 */
public final class AigRecommendPromptBuilder {

    private AigRecommendPromptBuilder() {
    }

    /**
     * 构造提示词。
     *
     * @param roles          当前用户可见的岗位首页（含过滤后的卡片）
     * @param maxCatalogCards 最多列出多少张卡片（超出截断）
     * @return 提示词
     */
    public static String build(List<AigPortalRoleHomeVo> roles, int maxCatalogCards) {
        StringBuilder catalog = new StringBuilder();
        int listed = 0;
        if (roles != null) {
            for (AigPortalRoleHomeVo role : roles) {
                if (role == null) {
                    continue;
                }
                List<String> lines = roleLines(role, maxCatalogCards - listed);
                if (lines.isEmpty()) {
                    continue;
                }
                catalog.append("【岗位】").append(role.getRoleName() == null ? role.getRoleCode() : role.getRoleName())
                    .append("（").append(role.getRoleCode()).append("）").append('\n');
                for (String line : lines) {
                    catalog.append(line).append('\n');
                    listed++;
                }
                if (listed >= maxCatalogCards) {
                    break;
                }
            }
        }
        return "你在企业 AI 工作台里根据员工的自然语言需求，从下面这份【可选卡片清单】里挑出最相关的卡片。\n"
            + "\n"
            + "【可选卡片清单】（只能从这里挑，不得编造；每张卡片的编码在方括号里）\n"
            + catalog
            + "\n"
            + "【输出要求】\n"
            + "1. 只输出 JSON，不要解释、不要 markdown 代码块：{\"actionCodes\":[\"卡片编码\", ...]}\n"
            + "2. 最多 " + Math.max(1, Math.min(maxCatalogCards, 10)) + " 个卡片编码，按相关性从高到低；\n"
            + "3. 一个都不相关时输出 {\"actionCodes\":[]}，不要硬凑；\n"
            + "4. actionCodes 里只能出现上面清单里出现过的编码。\n"
            + "\n"
            + "【边界】你只做推荐，不执行任何操作、不假设员工已经确认；最终由员工自己选择并确认。";
    }

    /**
     * 单个岗位的卡片行（提示词里的每行就是一张卡片）。
     *
     * @param role  岗位
     * @param limit 最多几行
     * @return 行
     */
    private static List<String> roleLines(AigPortalRoleHomeVo role, int limit) {
        List<String> lines = new ArrayList<>();
        if (limit <= 0 || role.getActions() == null) {
            return lines;
        }
        for (AigPortalActionVo action : role.getActions()) {
            if (action == null || StringUtils.isBlank(action.getActionCode())) {
                continue;
            }
            if (lines.size() >= limit) {
                break;
            }
            StringBuilder line = new StringBuilder();
            line.append("- [").append(action.getActionCode()).append("] ")
                .append(StringUtils.isBlank(action.getTitle()) ? action.getActionCode() : action.getTitle());
            if (StringUtils.isNotBlank(action.getDescription())) {
                line.append("：").append(action.getDescription());
            }
            lines.add(line.toString());
        }
        return lines;
    }

}
