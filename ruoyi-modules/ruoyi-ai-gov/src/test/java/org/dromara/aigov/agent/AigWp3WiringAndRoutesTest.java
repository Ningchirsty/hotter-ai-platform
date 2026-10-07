package org.dromara.aigov.agent;

import org.dromara.aigov.agent.domain.bo.AigPackageQueryBo;
import org.dromara.aigov.agent.domain.vo.AigPackageVo;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationSubjectRegistry;
import org.dromara.aigov.agent.evaluation.AigExpectedRuleChecker;
import org.dromara.aigov.agent.manifest.AigPackageManifestValidator;
import org.dromara.aigov.agent.mapper.AigAgentBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigEvaluationCaseMapper;
import org.dromara.aigov.agent.mapper.AigEvaluationRunMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigReleaseEventMapper;
import org.dromara.aigov.agent.mapper.AigSkillMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.aigov.agent.service.IAigAgentRegistryQueryService;
import org.dromara.aigov.agent.service.IAigAgentRegistryService;
import org.dromara.aigov.agent.service.IAigEvaluationService;
import org.dromara.aigov.agent.service.impl.AigAgentRegistryQueryServiceImpl;
import org.dromara.aigov.agent.service.impl.AigAgentRegistryServiceImpl;
import org.dromara.aigov.agent.service.impl.AigEvaluationServiceImpl;
import org.dromara.aigov.controller.AigAgentRegistryController;
import org.dromara.aigov.controller.AigEvaluationController;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WP3 的布线烟测（设计 §5、§6、§13.2 的落地形状）。
 *
 * <p><b>为什么需要这类测试</b>：WP3 其余测试都是纯 Mockito——它们<b>不加载 Spring</b>，
 * 因此「这些 Bean 能不能装起来」从来没被验证过。而装不起来的症状是<b>整个应用启动失败</b>：
 * 一个漏了 {@code @Service} 的查询服务、一个构造器参数没写上，都会在启动那一刻炸掉，
 * 而不是等到某个接口被调用。</p>
 *
 * <p>两件事分开验：</p>
 * <ol>
 *     <li>上下文能装配（用 {@link ApplicationContextRunner}，不需要数据库与 Redis；
 *         注意 {@code run()} 不会把启动失败抛出来，必须显式断言 {@code hasNotFailed}，
 *         否则「失败的上下文」会被当成「检查通过」）；</li>
 *     <li>路由表能解析（standalone MockMvc）：确认路径命中，并确认真实存在的歧义被正确裁决——
 *         {@code /package/list} 必须命中清单方法，而不是被 {@code /package/{packageId}} 抢走。</li>
 * </ol>
 *
 * <p>这不能替代整机启动（真 Spring Boot + MyBatis 扫描 + Redis 仍然要跑一次），
 * 但它把「WP3 的类装不起来」这类问题挡在构建期。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigWp3WiringAndRoutesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(Wp3Beans.class);

    private IAigAgentRegistryQueryService queryService;
    private IAigAgentRegistryService registryService;
    private IAigEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        queryService = mock(IAigAgentRegistryQueryService.class);
        registryService = mock(IAigAgentRegistryService.class);
        evaluationService = mock(IAigEvaluationService.class);
    }

    @Test
    @DisplayName("上下文能装配：WP3 的服务/组件/控制器全部可用（缺 Bean 会是启动失败，不是运行失败）")
    void wp3BeansWire() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IAigAgentRegistryService.class);
            assertThat(context).hasSingleBean(IAigAgentRegistryQueryService.class);
            assertThat(context).hasSingleBean(IAigEvaluationService.class);
            assertThat(context).hasSingleBean(AigPackageManifestValidator.class);
            assertThat(context).hasSingleBean(AigExpectedRuleChecker.class);
            assertThat(context).hasSingleBean(AigEvaluationSubjectRegistry.class);
            assertThat(context).hasSingleBean(AigAgentRegistryController.class);
            assertThat(context).hasSingleBean(AigEvaluationController.class);

            // 注册表在「一个执行器都没注册」时也必须存在：业务模块按需注册，
            // 缺实现不能拖垮启动（这也是当初用 setter + required=false 的原因）
            AigEvaluationSubjectRegistry registry = context.getBean(AigEvaluationSubjectRegistry.class);
            assertThat(registry.size()).isZero();
            assertThat(registry.describeAll()).contains("还没有任何评测执行器");

            // 装起来不算数：真调用一次，确认拿到的是可用服务（而不是空壳）
            IAigAgentRegistryService service = context.getBean(IAigAgentRegistryService.class);
            ServiceException error = assertThrows(ServiceException.class,
                () -> service.advanceRelease(null));
            assertTrue(error.getMessage().contains("不能为空"), error.getMessage());
        });
    }

    @Test
    @DisplayName("路由表：路径都能命中原方法，且真实存在的歧义被正确裁决")
    void routesResolveToControllers() throws Exception {
        when(queryService.queryPackagePage(any(), any())).thenReturn(PageResult.build(List.of(), 0L));
        when(queryService.queryAgentPage(any(), any())).thenReturn(PageResult.build(List.of(), 0L));
        AigPackageVo packageVo = new AigPackageVo();
        packageVo.setPackageId(7L);
        when(queryService.getPackage(7L)).thenReturn(packageVo);
        when(registryService.advanceRelease(any())).thenReturn(AigReleaseStatusEnum.VALIDATED);
        when(evaluationService.listCaseVos(any(), any())).thenReturn(List.of());

        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AigAgentRegistryController(registryService, queryService),
                new AigEvaluationController(evaluationService))
            .build();

        // 字面路径优先：/package/list 不能被 /package/{packageId} 抢走
        mockMvc.perform(get("/aigov/agent/package/list"))
            .andExpect(status().isOk());
        verify(queryService).queryPackagePage(any(AigPackageQueryBo.class), any());

        // 模板路径仍然命中详情方法
        mockMvc.perform(get("/aigov/agent/package/7"))
            .andExpect(status().isOk());
        verify(queryService).getPackage(7L);

        // 其余清单路径存在
        mockMvc.perform(get("/aigov/agent/list")).andExpect(status().isOk());
        mockMvc.perform(get("/aigov/agent/version/list")).andExpect(status().isOk());
        mockMvc.perform(get("/aigov/agent/skill/list")).andExpect(status().isOk());
        mockMvc.perform(get("/aigov/agent/binding/list")).andExpect(status().isOk());
        mockMvc.perform(get("/aigov/agent/package/version/list")).andExpect(status().isOk());
        mockMvc.perform(get("/aigov/evaluation/case/list")).andExpect(status().isOk());

        // 写接口的请求体绑定也走通（响应里是服务给的枚举名）
        mockMvc.perform(post("/aigov/agent/release/advance")
                .contentType("application/json")
                .content("{\"targetType\":\"AGENT_VERSION\",\"targetVersionId\":1,"
                    + "\"expectedStatus\":\"DRAFT\",\"toStatus\":\"VALIDATED\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("VALIDATED")));
        verify(registryService).advanceRelease(any());

        mockMvc.perform(post("/aigov/evaluation/review")
                .contentType("application/json")
                .content("{\"runId\":1,\"reviewResult\":\"PASS\"}"))
            .andExpect(status().isOk());
        verify(evaluationService).reviewRun(any());

        // 反面对照：没映射的路径仍然 404 —— 证明上面的 200 不是「什么都返回 200」
        mockMvc.perform(get("/aigov/agent/definitely-not-mapped"))
            .andExpect(status().isNotFound());
    }

    /**
     * WP3 的 Bean 装配。
     *
     * <p>生产靠组件扫描；{@link ApplicationContextRunner} 不做组件扫描，所以这里显式注册
     * （与 {@code ImageModuleWiringTest} 同一手法）。Mapper 用 mock：这一步只验装配，
     * 不碰数据库。</p>
     */
    @Configuration
    static class Wp3Beans {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        AigAgentMapper agentMapper() {
            return mock(AigAgentMapper.class);
        }

        @Bean
        AigAgentVersionMapper agentVersionMapper() {
            return mock(AigAgentVersionMapper.class);
        }

        @Bean
        AigSkillMapper skillMapper() {
            return mock(AigSkillMapper.class);
        }

        @Bean
        AigSkillVersionMapper skillVersionMapper() {
            return mock(AigSkillVersionMapper.class);
        }

        @Bean
        AigPackageMapper packageMapper() {
            return mock(AigPackageMapper.class);
        }

        @Bean
        AigPackageVersionMapper packageVersionMapper() {
            return mock(AigPackageVersionMapper.class);
        }

        @Bean
        AigAgentBindingMapper bindingMapper() {
            return mock(AigAgentBindingMapper.class);
        }

        @Bean
        AigEvaluationCaseMapper evaluationCaseMapper() {
            return mock(AigEvaluationCaseMapper.class);
        }

        @Bean
        AigEvaluationRunMapper evaluationRunMapper() {
            return mock(AigEvaluationRunMapper.class);
        }

        @Bean
        AigReleaseEventMapper releaseEventMapper() {
            return mock(AigReleaseEventMapper.class);
        }

        @Bean
        AigPackageManifestValidator manifestValidator(JsonMapper jsonMapper) {
            return new AigPackageManifestValidator(jsonMapper);
        }

        @Bean
        AigExpectedRuleChecker ruleChecker(JsonMapper jsonMapper) {
            return new AigExpectedRuleChecker(jsonMapper);
        }

        @Bean
        AigEvaluationSubjectRegistry subjectRegistry() {
            return new AigEvaluationSubjectRegistry();
        }

        @Bean
        IAigEvaluationService evaluationService(AigEvaluationCaseMapper caseMapper,
                                               AigEvaluationRunMapper runMapper,
                                               AigAgentMapper agentMapper,
                                               AigSkillMapper skillMapper,
                                               AigPackageMapper packageMapper,
                                               AigAgentVersionMapper agentVersionMapper,
                                               AigSkillVersionMapper skillVersionMapper,
                                               AigPackageVersionMapper packageVersionMapper,
                                               AigEvaluationSubjectRegistry subjectRegistry,
                                               AigExpectedRuleChecker ruleChecker,
                                               JsonMapper jsonMapper) {
            return new AigEvaluationServiceImpl(caseMapper, runMapper, agentMapper, skillMapper,
                packageMapper, agentVersionMapper, skillVersionMapper, packageVersionMapper,
                subjectRegistry, ruleChecker, jsonMapper);
        }

        @Bean
        IAigAgentRegistryService registryService(AigAgentVersionMapper agentVersionMapper,
                                                 AigSkillVersionMapper skillVersionMapper,
                                                 AigPackageVersionMapper packageVersionMapper,
                                                 AigReleaseEventMapper releaseEventMapper,
                                                 AigAgentBindingMapper bindingMapper,
                                                 AigPackageMapper packageMapper,
                                                 AigPackageManifestValidator manifestValidator,
                                                 IAigEvaluationService evaluationService) {
            return new AigAgentRegistryServiceImpl(agentVersionMapper, skillVersionMapper,
                packageVersionMapper, releaseEventMapper, bindingMapper, packageMapper,
                manifestValidator, evaluationService);
        }

        @Bean
        IAigAgentRegistryQueryService registryQueryService(AigAgentMapper agentMapper,
                                                           AigAgentVersionMapper agentVersionMapper,
                                                           AigSkillMapper skillMapper,
                                                           AigSkillVersionMapper skillVersionMapper,
                                                           AigPackageMapper packageMapper,
                                                           AigPackageVersionMapper packageVersionMapper,
                                                           AigAgentBindingMapper bindingMapper) {
            return new AigAgentRegistryQueryServiceImpl(agentMapper, agentVersionMapper, skillMapper,
                skillVersionMapper, packageMapper, packageVersionMapper, bindingMapper);
        }

        @Bean
        AigAgentRegistryController agentRegistryController(IAigAgentRegistryService registryService,
                                                          IAigAgentRegistryQueryService queryService) {
            return new AigAgentRegistryController(registryService, queryService);
        }

        @Bean
        AigEvaluationController evaluationController(IAigEvaluationService evaluationService) {
            return new AigEvaluationController(evaluationService);
        }
    }

}
