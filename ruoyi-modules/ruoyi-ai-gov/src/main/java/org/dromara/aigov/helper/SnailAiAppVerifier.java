package org.dromara.aigov.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigGovProperties;
import org.dromara.aigov.domain.vo.AigSnailAppVo;
import org.dromara.aigov.mapper.AigSnailAppMapper;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * snail-ai 客户端身份的核对：把「配置里的 app-id / token 与 {@code sai_app} 对不对得上」
 * 从<b>人肉比对</b>变成一次查询（阶段2 C2）。
 *
 * <h3>为什么值得专门做</h3>
 * <p>配置与库不一致时，调用会「发出去了但没人应答」或直接被服务端拒绝，而报错往往出现在别处
 * （超时、鉴权失败），排查的人第一反应不会去比对两处 app-id 与 token。而且这两处
 * <b>天然会漂移</b>：应用在 snail-ai 里重建一次，app-id 与 token 都变了，配置还在原处。</p>
 *
 * <p>更关键的是：C1 的「模型 → Agent」作用域过滤要用到「我们是谁（app-id）」。
 * 这个身份如果本身是错的，过滤会安静地筛掉所有候选 Agent，表现为「模型明明有 Agent，
 * 却报没有可用的 Agent」。把身份核对与作用域取值<strong>放在同一个类里</strong>，
 * 就不会出现「过滤用 A、校验用 B」。</p>
 *
 * <h3>配置来源与优先级</h3>
 * <ul>
 *     <li>应用标识：优先 {@code aigov.snail-ai.app-id}（治理层显式指定），
 *         否则回落到平台客户端的 {@code snail-ai.app-id}（真正参与握手的就是它）；</li>
 *     <li>令牌：{@code snail-ai.token}——与平台客户端用<b>同一个键</b>，不另起一份，
 *         否则「校验的那个」与「实际发出去的那个」会不一致；</li>
 *     <li>表：{@code sai_app}，与 {@code sai_model_config} 同库（平台基线 {@code ry_ai.sql}）。</li>
 * </ul>
 *
 * <h3>刻意不做的事</h3>
 * <ul>
 *     <li><b>不在启动期失败</b>：snail-ai 通道默认关闭（{@code aigov.snail-ai.enabled=false}），
 *         而 {@code sai_*} 表属平台基线、未必每个环境都导入。因为一条可选通道的配置问题就让
 *         整个应用起不来，是把治理层变成单点。核对发生在<b>真正要用这条通道时</b>；</li>
 *     <li><b>不缓存结论</b>：配置与应用都可能在运行期被改，缓存会把「刚修好了」挡在外面。
 *         代价是一次唯一键上的单行查询，相对一次聊天调用可以忽略；</li>
 *     <li><b>不回显 token</b>：只报「一致 / 不一致」。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnailAiAppVerifier {

    /**
     * 平台客户端的应用标识键（真正参与握手的就是它）。
     */
    private static final String PLATFORM_APP_ID_KEY = "snail-ai.app-id";

    /**
     * 平台客户端的令牌键（与平台客户端共用，不另起一份）。
     */
    private static final String PLATFORM_TOKEN_KEY = "snail-ai.token";

    /**
     * {@code sai_app.status} 的启用值（1=启用 0=停用）。
     */
    private static final int ENABLED_STATUS = 1;

    /**
     * 治理层配置（可显式指定 app-id）。
     */
    private final AigGovProperties properties;

    /**
     * {@code sai_app} 只读 Mapper。
     */
    private final AigSnailAppMapper snailAppMapper;

    /**
     * 配置来源（读平台客户端的同名键；用 Environment 而不是 @Value 字段，
     * 是为了让本类的判定在单测里可直接构造，不需要 Spring 容器）。
     */
    private final Environment environment;

    /**
     * 有效应用标识：治理层显式配置优先，否则用平台客户端的。
     *
     * <p>C1 的 Agent 作用域过滤也用这个值——「我们是谁」只有一个答案。</p>
     *
     * @return 应用标识；都没配时返回空串
     */
    public String effectiveAppId() {
        if (StringUtils.isNotBlank(properties.getAppId())) {
            return properties.getAppId().trim();
        }
        return StringUtils.trimToEmpty(environment.getProperty(PLATFORM_APP_ID_KEY));
    }

    /**
     * 核对配置与 {@code sai_app} 是否一致。
     *
     * <p><b>不抛异常</b>：调用方（模型调用器）的契约是「失败返回结果，不向外抛」，
     * 而这里连数据库都可能读不到（表未导入）。任何异常都被翻译成一条可读的问题。</p>
     *
     * @return 问题清单；<b>空 = 一致</b>
     */
    public List<String> verify() {
        List<String> problems = new ArrayList<>();
        String appId = effectiveAppId();
        if (StringUtils.isBlank(appId)) {
            problems.add("未配置 snail-ai.app-id（也没有 aigov.snail-ai.app-id）："
                + "无法确定本客户端在 snail-ai 里的身份");
            return problems;
        }
        String token = StringUtils.trimToNull(environment.getProperty(PLATFORM_TOKEN_KEY));
        if (token == null) {
            problems.add("未配置 snail-ai.token：服务端会用令牌校验客户端，缺了会被拒绝");
        }

        AigSnailAppVo app;
        try {
            app = snailAppMapper.selectByAppId(appId, token);
        } catch (Exception e) {
            // 表未导入 / 库不可达都归这里：如实报告，不假装通过
            log.error("sai_app 核对失败, appId={}, exception={}", appId, e.getClass().getSimpleName());
            problems.add("无法核对 sai_app（表是否已导入？）：" + e.getClass().getSimpleName()
                + "；请确认平台基线建表脚本 ry_ai.sql 已执行");
            return problems;
        }
        if (app == null) {
            problems.add("sai_app 里没有应用 app_id=" + appId
                + "：调用会被服务端拒绝。请在 snail-ai 的应用管理里创建，"
                + "或用 aigov.snail-ai.app-id 指定正确的那个");
            return problems;
        }
        if (app.getStatus() == null || app.getStatus() != ENABLED_STATUS) {
            problems.add("应用 app_id=" + appId + "（" + app.getAppName() + "）在 sai_app 里不是启用状态："
                + "请先在 snail-ai 里启用它");
        }
        if (token != null && !Boolean.TRUE.equals(app.getTokenMatched())) {
            problems.add("snail-ai.token 与 sai_app 中该应用的令牌不一致："
                + "请从 snail-ai 的应用管理取回当前令牌并更新配置"
                + "（应用重建会换令牌，配置最容易在这里漂移）");
        }
        return problems;
    }

    /**
     * 把问题清单拼成一句话（用于失败信息与体检报告）。
     *
     * @param problems 问题清单
     * @return 文本；空清单返回空串
     */
    public static String describe(List<String> problems) {
        if (problems == null || problems.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String problem : problems) {
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append(problem);
        }
        return sb.toString();
    }

}
