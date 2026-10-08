package org.dromara.aigov.domain.vo;

import io.github.linpeilie.BaseMapper;
import org.dromara.aigov.domain.AigUserQuota;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 钉住「{@code AigUserQuota -> AigUserQuotaVo} 的转换器真的被生成」这件事。
 *
 * <p><b>为什么值得单独一条测试</b>：MapStruct-Plus 只为标了 {@code @AutoMapper} 的类生成转换器，
 * 少了它，{@code BaseMapperPlus.selectVoPage} 里的 {@code MapstructUtils.convert(list, voClass)}
 * 就会抛 {@code ConvertException: cannot find converter from AigUserQuota to AigUserQuotaVo} ——
 * 生产上（R43）表现为 {@code GET /aigov/quota/list} 恒 500，空表也一样。</p>
 *
 * <p>而 {@link org.dromara.aigov.service.impl.AigUserQuotaServiceImplTest} 用 mock 替换了 mapper，
 * 压根走不到 {@code MapstructUtils.convert}，<b>抓不到它</b>。这条测试补的就是那个盲区。</p>
 *
 * <p><b>为什么不用 {@code getAnnotation(AutoMapper.class)} 断言</b>：{@code @AutoMapper}
 * 的保留级别不是 RUNTIME，运行期读不到（实测 {@code AigCapabilityVo} 这种已知可用的 VO
 * 同样读不到）。所以改为断言<b>生成物存在</b>——那才是运行期真正被查找的东西，
 * 也是缺陷发生时唯一缺席的东西。生成物名字由 MapStruct-Plus 的约定决定
 * （{@code <源类>To<目标类>Mapper}，放在源类的包里），与仓库里既有的
 * {@code CpWorkPackageToCpWorkPackageVoMapper} 同一套约定。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigUserQuotaVoAutoMapperTest {

    private static final String CONVERTER = "org.dromara.aigov.domain.AigUserQuotaToAigUserQuotaVoMapper";

    @Test
    @DisplayName("★ 转换器 AigUserQuotaToAigUserQuotaVoMapper 必须存在（缺 @AutoMapper 就不会生成）")
    void converterIsGenerated() {
        Class<?> mapper;
        try {
            mapper = Class.forName(CONVERTER);
        } catch (ClassNotFoundException e) {
            fail("没有生成 " + CONVERTER + "：说明 AigUserQuotaVo 少了 "
                + "@AutoMapper(target = AigUserQuota.class)。缺了它，"
                + "GET /aigov/quota/list 会在运行期抛 ConvertException 并返回 500");
            return;
        }
        assertTrue(BaseMapper.class.isAssignableFrom(mapper),
            CONVERTER + " 应实现 io.github.linpeilie.BaseMapper（MapStruct-Plus 的转换器契约）");
    }

    @Test
    @DisplayName("反向转换器（VO -> 实体）同样存在：两个方向由同一个 @AutoMapper 一次生成")
    void reverseConverterIsGenerated() {
        try {
            Class.forName("org.dromara.aigov.domain.vo.AigUserQuotaVoToAigUserQuotaMapper");
        } catch (ClassNotFoundException e) {
            fail("没有生成 AigUserQuotaVoToAigUserQuotaMapper：@AutoMapper 会同时生成两个方向，"
                + "只缺一个通常意味着注解没生效");
        }
    }

    @Test
    @DisplayName("对照：AigCapability 的转换器确实存在（证明这个断言方式本身是有效的）")
    void controlConverterExists() {
        try {
            Class.forName("org.dromara.aigov.domain.AigCapabilityToAigCapabilityVoMapper");
        } catch (ClassNotFoundException e) {
            fail("连已知可用的 AigCapability 都没有转换器，说明这条断言的类名约定已经失效，"
                + "本测试需要按 MapStruct-Plus 的新约定修正");
        }
    }

}
