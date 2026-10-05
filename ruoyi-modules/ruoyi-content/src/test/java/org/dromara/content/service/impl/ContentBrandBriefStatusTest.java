package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.domain.CpBrandBrief;
import org.dromara.content.domain.bo.BrandBriefBo;
import org.dromara.content.enums.ContentBriefStatusEnum;
import org.dromara.content.mapper.CpBrandBriefMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.service.IContentTaskService;
import org.dromara.system.api.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 品牌 Brief「确认态」语义的单元测试（内测 S9 / S16 的回归钉）。
 *
 * <p>这三件事都曾被真机实测证明会<b>静默</b>出错——不报错、但结果是错的：</p>
 * <ol>
 *   <li><b>改了内容要打回草稿</b>：视觉门的 {@code BRAND_BRIEF_CONFIRMED} 判的就是
 *       {@code status=CONFIRMED}；内容改了不重置，等于拿着「旧内容的确认」一路放行出图（S9）。</li>
 *   <li><b>没改内容不能被误伤</b>：重复点保存不该把已确认作废。所以比较前两侧都要归一化——
 *       库里 {@code ""} 与 {@code null} 混用时，不归一化会变成「每次都算改了」。</li>
 *   <li><b>空 Brief 不能确认</b>：有记录 ≠ 填过内容。前端本来就有这条校验，服务端以前没有，
 *       于是绕过页面直接调接口就能造出一个「已确认但什么都没说」的假信号（S16）。</li>
 * </ol>
 *
 * @author content
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentBrandBriefStatusTest {

    private static final Long TASK_ID = 9001L;

    private static final Long CONFIRMED_BY = 7001L;

    @Mock
    private CpBrandBriefMapper briefMapper;
    @Mock
    private IContentTaskService taskService;
    @Mock
    private CpTaskFileMapper taskFileMapper;
    @Mock
    private UserService userService;

    @InjectMocks
    private ContentBrandBriefServiceImpl service;

    /**
     * 初始化 MyBatis-Plus 的实体元信息缓存。
     *
     * <p><b>为什么必须显式初始化</b>：{@code save()} 里用
     * {@code new LambdaUpdateWrapper<CpBrandBrief>().set(CpBrandBrief::getXxx, ...)} 写库
     * ——这是为了绕开 MP 的 NOT_NULL 策略、让"清空某个字段"真的能存进去。
     * 但 {@code LambdaUpdateWrapper} 需要把方法引用解析成列名，靠的是 MP 启动时建立的
     * lambda 缓存；纯单元测试没有 Spring/MP 引导，于是报
     * {@code MybatisPlus can not find lambda cache for this entity}。</p>
     *
     * <p>这里手工建一次 {@code TableInfo} 把缓存填上——只有走真实 wrapper 的测试需要它。</p>
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant =
            new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CpBrandBrief.class);
    }

    /**
     * 造一条「已确认且有内容」的 Brief。
     *
     * @return 实体
     */
    private static CpBrandBrief confirmedBrief() {
        CpBrandBrief entity = new CpBrandBrief();
        entity.setId(5001L);
        entity.setTaskId(TASK_ID);
        entity.setStatus(ContentBriefStatusEnum.CONFIRMED.getCode());
        entity.setBrandTone("清新、治愈");
        entity.setMustShow("品牌名「趣往」");
        entity.setForbiddenWords("最\n第一");
        entity.setMainPush("1 单枝直发");
        entity.setTargetAudience("25-35 岁都市女性");
        entity.setSizeSpecReq("详情页宽 750px");
        entity.setStyleRef("参考图 2 的自然光");
        entity.setRemark(null);
        entity.setConfirmedBy(CONFIRMED_BY);
        entity.setConfirmedAt(LocalDateTime.now());
        return entity;
    }

    /**
     * 把库里的行挂上。
     *
     * <p>{@code find()} 走的是 {@code selectList}，而 service 会<b>原地改写</b>返回的实体，
     * 所以两次调用返回同一个实例即可让「保存后的回读」看见改动。</p>
     *
     * @param stored 库里的行
     */
    private void givenStored(CpBrandBrief stored) {
        when(briefMapper.selectList(any())).thenReturn(List.of(stored));
    }

    /**
     * 造一份提交表单，只有「必显信息」可变——用来构造"改了"与"没改"两种情形。
     *
     * @param mustShow 必显信息
     * @return 表单
     */
    private static BrandBriefBo form(String mustShow) {
        BrandBriefBo bo = new BrandBriefBo();
        bo.setBrandTone("清新、治愈");
        bo.setMustShow(mustShow);
        bo.setForbiddenWords("最\n第一");
        bo.setMainPush("1 单枝直发");
        bo.setTargetAudience("25-35 岁都市女性");
        bo.setSizeSpecReq("详情页宽 750px");
        bo.setStyleRef("参考图 2 的自然光");
        return bo;
    }

    @Test
    @DisplayName("S9：已确认的 Brief 改了内容 → 打回草稿，并清掉确认人与确认时间")
    void saveResetsConfirmedToDraftWhenContentChanged() {
        CpBrandBrief stored = confirmedBrief();
        givenStored(stored);

        var vo = service.save(TASK_ID, form("品牌名「趣往」\n新加的口号"));

        assertEquals(ContentBriefStatusEnum.DRAFT.getCode(), vo.getStatus(),
            "内容改了，状态必须回到草稿——否则视觉门会拿旧内容的确认放行");
        assertNull(vo.getConfirmedBy(), "确认人要清掉");
        assertNull(stored.getConfirmedAt(), "确认时间要清掉");
        assertTrue(vo.getMustShow().contains("新加的口号"), "新内容要真的存下去");
    }

    @Test
    @DisplayName("S9 反例：内容没变的重复保存 → 不能把已确认误伤成草稿")
    void saveKeepsConfirmedWhenContentUnchanged() {
        CpBrandBrief stored = confirmedBrief();
        givenStored(stored);

        var vo = service.save(TASK_ID, form("品牌名「趣往」"));

        assertEquals(ContentBriefStatusEnum.CONFIRMED.getCode(), vo.getStatus(),
            "内容没变就作废确认，会让品牌部每点一次保存都掉一次确认");
        assertEquals(CONFIRMED_BY, vo.getConfirmedBy(), "确认人不该被动过");
    }

    @Test
    @DisplayName("S16：8 项全空且无参考风格图片 → 服务端拒绝确认")
    void confirmRejectsBlankBrief() {
        CpBrandBrief blank = new CpBrandBrief();
        blank.setId(5002L);
        blank.setTaskId(TASK_ID);
        blank.setStatus(ContentBriefStatusEnum.DRAFT.getCode());
        givenStored(blank);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(TASK_ID));
        assertTrue(ex.getMessage().contains("至少填一项"),
            "报错要说清为什么不给确认，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("S16 反例：只要填了一项，确认就放行（避免修成过度拦截）")
    void confirmAllowsBriefWithAnyContent() {
        CpBrandBrief onlyTone = new CpBrandBrief();
        onlyTone.setId(5003L);
        onlyTone.setTaskId(TASK_ID);
        onlyTone.setStatus(ContentBriefStatusEnum.DRAFT.getCode());
        onlyTone.setBrandTone("克制不喧哗");
        givenStored(onlyTone);

        // confirm 成功路径会取当前登录人，单元测试里没有登录上下文，故把静态方法钉住
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(CONFIRMED_BY);

            var vo = service.confirm(TASK_ID);

            assertEquals(ContentBriefStatusEnum.CONFIRMED.getCode(), vo.getStatus());
            assertEquals(CONFIRMED_BY, vo.getConfirmedBy());
        }
    }
}
