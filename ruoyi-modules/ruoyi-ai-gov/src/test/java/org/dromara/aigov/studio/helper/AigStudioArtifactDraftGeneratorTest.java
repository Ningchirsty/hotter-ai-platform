package org.dromara.aigov.studio.helper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.studio.domain.AigStudioActionDraft;
import org.dromara.aigov.studio.domain.AigStudioChildAgentDraft;
import org.dromara.aigov.studio.domain.AigStudioDraft;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.AigStudioRevision;
import org.dromara.aigov.studio.domain.AigStudioSkillDraft;
import org.dromara.aigov.studio.domain.vo.AigStudioValidateVo;
import org.dromara.aigov.studio.mapper.AigStudioDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioRevisionMapper;
import org.dromara.aigov.studio.service.impl.AigStudioDraftServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 草案生成器测试（附件 07 增量 7／ST-005/006/007）。
 *
 * <p><b>这份测试要挡住的是"生成了一份看起来正常的草稿"</b>：少一个 Prompt 分节键、
 * 能力或类别没校验、把草案里没有槽位的字段静默丢掉——三种都不会报错，
 * 都要等到提交或上线才暴露。因此这里有两层断言：</p>
 * <ol>
 *     <li>逐类钉住"生成物里应该有什么、note 里应该说清什么"；</li>
 *     <li><b>用训练台真实的预检（{@code validateDraft}）验三类生成物</b>——
 *         "可提交内容"这句话必须能被真实的预检证明，而不是靠本测试自己定的一套规则。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioArtifactDraftGeneratorTest {

    private static final long DRAFT_ID = 9001L;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * 真实的草稿服务（只用来跑预检；它的 mappers 都给替身）
     */
    private AigStudioDraftServiceImpl draftService;

    /**
     * 内存里的"草稿行"（长度 1）
     */
    private AigStudioDraft[] row;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigStudioDraft.class);
        TableInfoHelper.initTableInfo(assistant, AigStudioRevision.class);
        TableInfoHelper.initTableInfo(assistant, AigAgent.class);
        TableInfoHelper.initTableInfo(assistant, AigAgentVersion.class);
    }

    @BeforeEach
    void setUp() {
        AigStudioDraftMapper draftMapper = mock(AigStudioDraftMapper.class);
        row = new AigStudioDraft[1];
        when(draftMapper.selectById(any())).thenAnswer(invocation -> row[0]);
        draftService = new AigStudioDraftServiceImpl(draftMapper, mock(AigStudioRevisionMapper.class),
            mock(AigAgentMapper.class), mock(AigAgentVersionMapper.class),
            mock(AigStudioCapabilityFactsResolver.class), JSON);
    }

    /**
     * ★ 用训练台真实的预检验生成物：把生成内容摆进一份草稿行，再调 {@code validateDraft}。
     *
     * @param content 生成物
     */
    private void assertPassesPreflight(AigStudioDraftContent content) {
        AigStudioDraft draft = new AigStudioDraft();
        draft.setDraftId(DRAFT_ID);
        try {
            draft.setContentJson(JSON.writeValueAsString(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        draft.setLatestRevision(1);
        row[0] = draft;

        AigStudioValidateVo vo = draftService.validateDraft(DRAFT_ID);

        assertTrue(vo.getPassed(), "生成物必须能过训练台预检，问题=" + vo.getProblems());
    }

    /* ------------------------------------------------------------------ 技能草案 */

    private static AigStudioSkillDraft skillDraft() {
        AigStudioSkillDraft draft = new AigStudioSkillDraft();
        draft.setDraftCode("SKILL-BRAND-BRIEF");
        draft.setDraftName("品牌简报技能助手");
        draft.setSkillCode("brand_brief");
        draft.setSkillName("品牌简报");
        draft.setCapabilities("text_generation, image_generation");
        draft.setDescription("把品牌资料整理成一份可复用的简报");
        draft.setUsageNotes("先取品牌资料，再产出简报；资料缺失时先问清楚");
        draft.setAgentCategory("planning");
        draft.setProviderCapability("text_generation");
        draft.setAllowedTools(" brand_brief , image_search ");
        return draft;
    }

    @Test
    @DisplayName("★ 技能草案：技能编码进 allowedSkills、类别规范化、能力进 providerCapability、八节齐全")
    void skillDraftSeedsContent() {
        AigStudioArtifactDraftGenerator.Result result =
            AigStudioArtifactDraftGenerator.forSkill(skillDraft());
        AigStudioDraftContent content = result.content();

        assertEquals("brand_brief", content.getAllowedSkills().get(0));
        assertEquals("PLANNING", content.getAgentCategory(), "类别要规范化成枚举 code（大小写不敏感）");
        assertEquals("text_generation", content.getProviderCapability());
        assertEquals(List.of("brand_brief", "image_search"), content.getAllowedTools());
        assertEquals(List.of("text_generation", "image_generation"), content.getCoreCapabilities());
        String toolsSection = content.getPromptSections().get("tools_and_skills");
        assertTrue(toolsSection.contains("品牌简报"), toolsSection);
        assertTrue(toolsSection.contains("brand_brief"), toolsSection);
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            assertTrue(content.getPromptSections().containsKey(key), "缺分节键 " + key);
        }
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("allowedSkills")));
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("不参与路由")),
            "要说清 capabilities 只是人读清单：" + result.notes());
    }

    @Test
    @DisplayName("★ 技能草案：缺技能编码 / 缺类别 / 缺能力 → 一律拒绝，且点名缺的是哪个字段")
    void skillDraftRejectsMissingRequired() {
        AigStudioSkillDraft noSkill = skillDraft();
        noSkill.setSkillCode("  ");
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forSkill(noSkill)).getMessage().contains("技能编码"));

        AigStudioSkillDraft noCategory = skillDraft();
        noCategory.setAgentCategory(null);
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forSkill(noCategory)).getMessage()
            .contains("agentCategory"));

        AigStudioSkillDraft badCategory = skillDraft();
        badCategory.setAgentCategory("NOPE");
        String message = assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forSkill(badCategory)).getMessage();
        assertTrue(message.contains("PLANNING"), "报错要列出可选类别：" + message);

        AigStudioSkillDraft noCapability = skillDraft();
        noCapability.setProviderCapability("");
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forSkill(noCapability)).getMessage()
            .contains("providerCapability"));

        assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forSkill(null));
    }

    /* ------------------------------------------------------------------ 子 Agent 草案 */

    private static AigStudioChildAgentDraft childAgentDraft() {
        AigStudioChildAgentDraft draft = new AigStudioChildAgentDraft();
        draft.setDraftCode("CHILD-SKU-ASSIST");
        draft.setDraftName("SKU 助手");
        draft.setAgentCode("SKU_ASSIST");
        draft.setAgentName("SKU 详情助手");
        draft.setAgentCategory("PLANNING");
        draft.setParentAgentCode("DETAIL_COPYWRITER");
        draft.setRoleDescription("电商 SKU 资料助手");
        draft.setObjective("把 SKU 事实整理成可用的卖点");
        draft.setProhibitions("不得编造参数");
        draft.setOutputSummary("结构化卖点清单");
        draft.setPromptSectionsJson("{\"workflow_rules\":\"先查事实再写\",\"inputs\":\"SKU 编号\"}");
        draft.setProviderCapability("text_generation");
        draft.setAllowedTools("brand_brief");
        draft.setKnowledgeScope("brand-kb, product-facts");
        return draft;
    }

    @Test
    @DisplayName("★ 子 Agent 草案：八节合并（草案分节优先）、允许清单拆成数组、缺省不允许外发")
    void childAgentDraftSeedsContent() {
        AigStudioArtifactDraftGenerator.Result result =
            AigStudioArtifactDraftGenerator.forChildAgent(childAgentDraft());
        AigStudioDraftContent content = result.content();

        assertEquals("SKU 详情助手", content.getAgentName());
        assertEquals("PLANNING", content.getAgentCategory());
        assertEquals("text_generation", content.getProviderCapability());
        assertEquals("电商 SKU 资料助手", content.getRoleDescription());
        assertEquals("把 SKU 事实整理成可用的卖点", content.getObjective());
        assertEquals("不得编造参数", content.getProhibitions());
        assertEquals("结构化卖点清单", content.getOutputSummary());
        assertEquals("先查事实再写", content.getPromptSections().get("workflow_rules"), "草案给的分节优先");
        assertEquals("SKU 编号", content.getPromptSections().get("inputs"));
        assertEquals("电商 SKU 资料助手", content.getPromptSections().get("role"), "角色定位要落到 role 分节");
        assertEquals(List.of("brand_brief"), content.getAllowedTools());
        assertEquals(List.of("brand-kb", "product-facts"), content.getKnowledgeScope());
        assertEquals("N", content.getAllowExternal(), "缺省不允许外发（更安全的那一侧）");
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("DETAIL_COPYWRITER")),
            "父 Agent 没进内容，必须说出来：" + result.notes());
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("SKU_ASSIST")),
            "agentCode 不在内容里，必须说出来：" + result.notes());
    }

    @Test
    @DisplayName("子 Agent 草案：allowExternal 给了就照用；八分节 JSON 非法或不是对象 → 拒绝")
    void childAgentDraftValidatesInputs() {
        AigStudioChildAgentDraft external = childAgentDraft();
        external.setAllowExternal("Y");
        assertEquals("Y", AigStudioArtifactDraftGenerator.forChildAgent(external).content().getAllowExternal());

        AigStudioChildAgentDraft broken = childAgentDraft();
        broken.setPromptSectionsJson("{not json");
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forChildAgent(broken)).getMessage()
            .contains("不是合法 JSON"));

        AigStudioChildAgentDraft notObject = childAgentDraft();
        notObject.setPromptSectionsJson("[1,2]");
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forChildAgent(notObject)).getMessage()
            .contains("JSON 对象"));
    }

    /* ------------------------------------------------------------------ 快捷指令草案 */

    private static AigStudioActionDraft actionDraft() {
        AigStudioActionDraft draft = new AigStudioActionDraft();
        draft.setDraftCode("ACTION-WRITE-SELLING-POINT");
        draft.setDraftName("写卖点");
        draft.setActionCode("WRITE_SELLING_POINT");
        draft.setActionName("写一句卖点");
        draft.setLaunchMode("QUICK");
        draft.setTargetType("QUICK_CAPABILITY");
        draft.setTargetRef("text_generation");
        draft.setAgentCategory("ANALYSIS");
        draft.setObjective("给商品写一句卖点");
        draft.setOutputContract("一句话，不超过 20 字");
        draft.setProviderCapability("text_generation");
        return draft;
    }

    @Test
    @DisplayName("★ 快捷指令草案：目标进 objective/输出要求进 output_contract；启动方式与目标必须出现在 note 里")
    void actionDraftSeedsContentAndNotesDroppedFields() {
        AigStudioArtifactDraftGenerator.Result result =
            AigStudioArtifactDraftGenerator.forAction(actionDraft());
        AigStudioDraftContent content = result.content();

        assertEquals("ANALYSIS", content.getAgentCategory());
        assertEquals("给商品写一句卖点", content.getObjective());
        assertEquals("一句话，不超过 20 字", content.getPromptSections().get("output_contract"));
        assertEquals("一句话，不超过 20 字", content.getOutputSummary());
        assertTrue(content.getPromptSections().get("role").contains("写一句卖点"), content.getPromptSections().toString());

        String joined = String.join("\n", result.notes());
        assertTrue(joined.contains("QUICK"), "启动方式要出现在提示里：" + joined);
        assertTrue(joined.contains("QUICK_CAPABILITY"), "启动目标类型要出现在提示里：" + joined);
        assertTrue(joined.contains("text_generation"), "启动目标引用要出现在提示里：" + joined);
        assertTrue(joined.contains("岗位卡片"), "要说清这些字段该去哪里配：" + joined);
    }

    @Test
    @DisplayName("★ 留空待补的分节要列出来（不替人编内容，但要说清还差什么）")
    void emptySectionsAreListed() {
        AigStudioArtifactDraftGenerator.Result result =
            AigStudioArtifactDraftGenerator.forAction(actionDraft());

        String note = result.notes().stream()
            .filter(item -> item.contains("留空待补充"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("必须列出留空分节：" + result.notes()));
        assertTrue(note.contains("inputs"), note);
        assertTrue(note.contains("workflow_rules"), note);
        assertTrue(note.contains("tools_and_skills"), note);
        assertTrue(note.contains("constraints"), note);
        assertTrue(note.contains("uncertainty_policy"), note);
        assertFalse(note.contains("objective"), "有内容的分节不该出现在待补清单里：" + note);
        assertFalse(note.contains("output_contract"), note);
    }

    @Test
    @DisplayName("快捷指令草案：缺类别/缺能力 → 拒绝；空草案 → 拒绝")
    void actionDraftRejectsMissingRequired() {
        AigStudioActionDraft noCategory = actionDraft();
        noCategory.setAgentCategory(" ");
        assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forAction(noCategory));

        AigStudioActionDraft noCapability = actionDraft();
        noCapability.setProviderCapability(null);
        assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forAction(noCapability));

        assertThrows(IllegalArgumentException.class,
            () -> AigStudioArtifactDraftGenerator.forAction(null));
    }

    /* ------------------------------------------------------------------ 可提交性 */

    @Test
    @DisplayName("★★ 三类生成物统统能过训练台真实的预检——「可提交内容」必须被真预检证明")
    void allThreeKindsPassTheRealPreflight() {
        assertPassesPreflight(AigStudioArtifactDraftGenerator.forSkill(skillDraft()).content());
        assertPassesPreflight(AigStudioArtifactDraftGenerator.forChildAgent(childAgentDraft()).content());
        assertPassesPreflight(AigStudioArtifactDraftGenerator.forAction(actionDraft()).content());
    }

}
