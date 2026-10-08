package org.dromara.aigov.mapper;

import io.github.linpeilie.BaseMapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 扫描本模块所有 {@code BaseMapperPlus<T, V>} 的 Mapper，逐个确认 {@code T -> V} 的转换器**真的被生成**。
 *
 * <h3>为什么需要这条测试（它不是"理论风险"）</h3>
 * <p>MapStruct-Plus 只为标了 {@code @AutoMapper} 的类生成转换器。少标一个注解，
 * {@code BaseMapperPlus.selectVoPage/selectVoList} 里的 {@code MapstructUtils.convert(...)}
 * 就会在**运行期**抛
 * {@code ConvertException: cannot find converter from AigUserQuota to AigUserQuotaVo}，
 * 对外表现为列表接口**恒 500**（空表也一样）。</p>
 *
 * <p>而这件事编译期、单测都拦不住：服务层单测把 Mapper mock 掉，压根不会走到转换器。
 * 2026-10-07 生产上就真出了这一次（{@code GET /aigov/quota/list} 500），
 * 排查绕了一圈才落到 {@code AigUserQuotaVo} 少一个 {@code @AutoMapper}。</p>
 *
 * <h3>为什么断言"生成物存在"而不是读注解</h3>
 * <p>{@code @AutoMapper} 的保留级别**不是 RUNTIME**，运行期 {@code getAnnotation} 读不到
 * （实测连已知可用的 {@code AigCapabilityVo} 也读不到）。所以这里断言的是运行期真正会被查找的
 * 那个类：MapStruct-Plus 按约定把 {@code <源类>To<目标类>Mapper} 生成在**源类所在包**里
 * （例：{@code org.dromara.aigov.domain.AigUserQuotaToAigUserQuotaVoMapper}，
 * 与仓库里既有的 {@code CpWorkPackageToCpWorkPackageVoMapper} 同一套约定）。</p>
 *
 * <p>扫描不到任何 Mapper 时会**失败**而不是静默通过——否则打包方式一变，这条测试就成了摆设。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigMapperVoConverterCoverageTest {

    /**
     * 至少要扫到多少个 Mapper 才算"扫描有效"。当前模块有 23 个（含 T == V 的那种）；
     * 取一个明显低于现状的数量，是为了让包结构调整时先失败、提醒修正扫描方式，
     * 而不是让测试悄悄变成空转。
     */
    private static final int MIN_EXPECTED_MAPPERS = 15;

    @Test
    @DisplayName("★ 本模块每个 BaseMapperPlus<T,V> 的 T->V 转换器都必须已生成（少 @AutoMapper 就会 500）")
    void everyMapperHasItsVoConverter() throws Exception {
        List<Class<?>> mappers = scanMapperInterfaces();
        assertTrue(mappers.size() >= MIN_EXPECTED_MAPPERS,
            "只扫到 " + mappers.size() + " 个 Mapper（期望 >= " + MIN_EXPECTED_MAPPERS
                + "）：扫描方式已失效，请修正本测试而不是放宽断言");

        List<String> problems = new ArrayList<>();
        int checkedPairs = 0;
        for (Class<?> mapper : mappers) {
            Class<?>[] pair = resolveBaseMapperPair(mapper);
            if (pair == null) {
                continue;
            }
            Class<?> entity = pair[0];
            Class<?> vo = pair[1];
            if (entity.equals(vo)) {
                // T == V：不经过 MapStruct 转换，没有生成物是正常的
                continue;
            }
            checkedPairs++;
            String converter = entity.getPackageName() + "." + entity.getSimpleName()
                + "To" + vo.getSimpleName() + "Mapper";
            try {
                Class<?> generated = Class.forName(converter);
                if (!BaseMapper.class.isAssignableFrom(generated)) {
                    problems.add(mapper.getSimpleName() + " -> " + converter
                        + " 存在但没有实现 io.github.linpeilie.BaseMapper");
                }
            } catch (ClassNotFoundException e) {
                problems.add(mapper.getSimpleName() + " 的 " + vo.getSimpleName()
                    + " 很可能少了 @AutoMapper(target = " + entity.getSimpleName() + ".class)："
                    + "找不到生成的 " + converter
                    + "（缺了它，" + mapper.getSimpleName() + " 的 selectVo* 会在运行期抛 "
                    + "ConvertException 并返回 500）");
            }
        }

        assertTrue(checkedPairs >= MIN_EXPECTED_MAPPERS - 1,
            "只解析出 " + checkedPairs + " 组 T/Target 对：泛型解析大概率失效了");
        assertTrue(problems.isEmpty(), "发现 " + problems.size() + " 处转换器缺失：\n  - "
            + String.join("\n  - ", problems));
    }

    /**
     * 扫描本模块 class 输出目录下的 {@code *Mapper.class}。
     *
     * @return Mapper 接口清单
     */
    private static List<Class<?>> scanMapperInterfaces() throws Exception {
        // 必须取**本模块**的类来定位输出目录：BaseMapperPlus 来自依赖 jar，
        // 它的 CodeSource 指向 ~/.m2 里的 ruoyi-common-mybatis-x.y.z.jar，不是 target/classes。
        URL location = AigUserQuotaMapper.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            fail("拿不到 class 输出目录，无法扫描 Mapper");
        }
        Path root = Paths.get(location.toURI()).resolve("org/dromara/aigov");
        if (!Files.isDirectory(root)) {
            fail("找不到 " + root + "：请确认测试是从模块的 target/classes 运行的");
        }
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.getFileName().toString().endsWith("Mapper.class"))
                .filter(p -> !p.getFileName().toString().contains("$"))
                .forEach(p -> {
                    String rel = root.relativize(p).toString()
                        .replace('\\', '.').replace('/', '.');
                    String name = "org.dromara.aigov." + rel.substring(0, rel.length() - ".class".length());
                    try {
                        Class<?> c = Class.forName(name);
                        if (c.isInterface() && BaseMapperPlus.class.isAssignableFrom(c)) {
                            found.add(c);
                        }
                    } catch (Throwable ignored) {
                        // 加载不了就跳过：这不属于本测试要管的事
                    }
                });
        }
        return found;
    }

    /**
     * 解析 Mapper 接口上的 {@code BaseMapperPlus<T, V>} 实参。
     *
     * @param mapper Mapper 接口
     * @return {@code [实体类, VO类]}；解析不出返回 null
     */
    private static Class<?>[] resolveBaseMapperPair(Class<?> mapper) {
        for (Type type : mapper.getGenericInterfaces()) {
            Class<?>[] resolved = resolveFromType(type);
            if (resolved != null) {
                return resolved;
            }
            if (type instanceof Class<?> raw && raw.isInterface()) {
                Class<?>[] nested = resolveBaseMapperPair(raw);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /**
     * 从单个泛型类型上取 {@code BaseMapperPlus} 的实参。
     *
     * @param type 泛型类型
     * @return {@code [实体类, VO类]}；不是 BaseMapperPlus 时返回 null
     */
    private static Class<?>[] resolveFromType(Type type) {
        if (!(type instanceof ParameterizedType pt)) {
            return null;
        }
        if (!(pt.getRawType() instanceof Class<?> raw)
            || !BaseMapperPlus.class.isAssignableFrom(raw)) {
            return null;
        }
        Type[] args = pt.getActualTypeArguments();
        if (args.length != 2 || !(args[0] instanceof Class<?> entity)
            || !(args[1] instanceof Class<?> vo)) {
            return null;
        }
        return new Class<?>[]{entity, vo};
    }

}
