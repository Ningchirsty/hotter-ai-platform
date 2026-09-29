package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 屏结构骨架（V0.2 C′：把写死的「7 屏」收敛成一份可配置契约）。
 *
 * <p><b>为什么要有这个类</b>：改造前屏骨架在代码里有<b>两份</b>互不相干的写死清单——
 * {@code CreativeStoryboardServiceImpl.TEMPLATES}（建屏用）与 {@code CreativeDraftFactory.screens()}（文案草稿用），
 * 两处靠"数量都是 7"的约定对齐，改一处就会在下标访问时越界；另外还有两处 {@code screenTypeDesc} 的
 * switch、一处 {@code shot} 的 switch 与提示词里写死的「7 屏」字样。屏数属于场景配置，不该写死在 Java 里。</p>
 *
 * <p><b>权威顺序</b>：这份契约是屏骨架的唯一权威。数据库里的 {@code dp_storyboard_screen} 是<b>已生成的历史</b>，
 * 不回写骨架；改了契约只影响之后生成的分镜，老项目已生成的屏不动（所以 {@link #descOf} 对
 * 契约里已经没有的历史屏类型仍要给出可读名字，取不到才回落原样输出）。</p>
 *
 * <p><b>不可配置的边界</b>（与 V0.2 对照文档 D3 一致）：这里只允许改"有哪些屏、顺序、展示名、保真等级、取景"，
 * 不允许改阶段合法性、门禁、模板校验和这类质量红线。</p>
 */
@Slf4j
public final class CreativeScreenSkeleton {

    /**
     * 内置契约位置（classpath）。
     */
    public static final String RESOURCE = "creative/screen-skeleton.json";

    /**
     * 产品保真等级：严格保真（产品图必须一致）。
     */
    public static final String LEVEL_STRICT = "STRICT";

    /**
     * 产品保真等级：宽松（允许场景化演绎）。
     */
    public static final String LEVEL_LOOSE = "LOOSE";

    /**
     * 取景描述里的产品占比占位符（由锁定基因代入）。
     */
    private static final String RATIO_PLACEHOLDER = "{ratio}";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ScreenSpec> screens;
    private final Map<String, String> typeDesc;

    /**
     * 一屏的骨架定义。
     *
     * @param type            屏类型（同类型可出现多次，业务侧按出现顺序区分）
     * @param label           展示名（同一份契约内必须唯一）
     * @param productLockLevel 产品保真等级（STRICT/LOOSE）
     * @param shot            取景描述（可含 {ratio} 占位符）
     */
    public record ScreenSpec(String type, String label, String productLockLevel, String shot) {
    }

    private CreativeScreenSkeleton(List<ScreenSpec> screens, Map<String, String> typeDesc) {
        this.screens = List.copyOf(screens);
        this.typeDesc = Map.copyOf(typeDesc);
    }

    /**
     * 加载骨架：优先外部契约文件，取不到再回落 classpath 内置契约。
     *
     * <p>为什么外部文件不存在时只告警不抛：部署是"补丁式镜像 + 环境变量"，运营配错一个路径不该让服务起不来；
     * 而骨架又是生成分镜的必需项，所以内置契约必须始终可用。</p>
     *
     * @param externalPath 外部契约路径（可空）
     * @return 骨架
     */
    public static CreativeScreenSkeleton load(String externalPath) {
        String json = null;
        String from = null;
        if (StringUtils.isNotBlank(externalPath)) {
            Path path = Path.of(externalPath.trim());
            if (Files.isRegularFile(path)) {
                try {
                    json = Files.readString(path, StandardCharsets.UTF_8);
                    from = path.toString();
                } catch (Exception e) {
                    log.warn("读取外部屏骨架契约失败 path={}，回退内置契约：{}", externalPath, e.getMessage());
                }
            } else {
                log.warn("配置了外部屏骨架契约但文件不存在 path={}，回退内置契约", externalPath);
            }
        }
        if (json == null) {
            try (InputStream in = CreativeScreenSkeleton.class.getClassLoader().getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("屏骨架契约缺失（classpath:" + RESOURCE + "）");
                }
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                from = "classpath:" + RESOURCE;
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("读取内置屏骨架契约失败：" + e.getMessage(), e);
            }
        }
        CreativeScreenSkeleton skeleton = parse(json);
        log.info("屏骨架契约加载完成：来源={}，共 {} 屏（{}）", from, skeleton.size(), skeleton.brief());
        return skeleton;
    }

    /**
     * 解析契约文本（只做解析与校验，不读文件——便于测试直接喂字符串）。
     *
     * @param json 契约 JSON
     * @return 骨架
     */
    public static CreativeScreenSkeleton parse(String json) {
        if (StringUtils.isBlank(json)) {
            throw new IllegalArgumentException("屏骨架契约内容为空");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("屏骨架契约不是合法 JSON：" + e.getMessage(), e);
        }
        JsonNode screensNode = root.path("screens");
        if (!screensNode.isArray() || screensNode.isEmpty()) {
            throw new IllegalArgumentException("屏骨架契约缺少 screens 数组或为空");
        }
        List<ScreenSpec> specs = new ArrayList<>();
        for (JsonNode node : screensNode) {
            specs.add(new ScreenSpec(
                node.path("type").asText(null),
                node.path("label").asText(null),
                node.path("productLockLevel").asText(null),
                node.path("shot").asText(null)));
        }
        Map<String, String> desc = new LinkedHashMap<>();
        JsonNode descNode = root.path("typeDesc");
        if (descNode.isObject()) {
            descNode.properties().forEach(e -> desc.put(e.getKey(), e.getValue().asText("")));
        }
        return of(specs, desc);
    }

    /**
     * 构造并校验骨架（校验失败直接抛出：坏骨架比没有骨架更危险，会让分镜少屏或错位）。
     *
     * @param screens  屏列表（顺序即叙事顺序）
     * @param typeDesc 屏类型 → 展示短名（供历史数据与列表展示用）
     * @return 骨架
     */
    public static CreativeScreenSkeleton of(List<ScreenSpec> screens, Map<String, String> typeDesc) {
        if (screens == null || screens.isEmpty()) {
            throw new IllegalArgumentException("屏骨架不能为空");
        }
        Set<String> labels = new LinkedHashSet<>();
        for (int i = 0; i < screens.size(); i++) {
            ScreenSpec spec = screens.get(i);
            String where = "screens[" + i + "]";
            if (spec == null || StringUtils.isBlank(spec.type())) {
                throw new IllegalArgumentException(where + ".type 不能为空");
            }
            if (StringUtils.isBlank(spec.label())) {
                throw new IllegalArgumentException(where + ".label 不能为空");
            }
            if (!LEVEL_STRICT.equals(spec.productLockLevel()) && !LEVEL_LOOSE.equals(spec.productLockLevel())) {
                throw new IllegalArgumentException(where + ".productLockLevel 只能是 " + LEVEL_STRICT + " 或 "
                    + LEVEL_LOOSE + "，实际=" + spec.productLockLevel());
            }
            if (StringUtils.isBlank(spec.shot())) {
                throw new IllegalArgumentException(where + ".shot 不能为空");
            }
            if (!labels.add(spec.label())) {
                throw new IllegalArgumentException("屏展示名重复：" + spec.label()
                    + "（同类型多屏要用「卖点一/卖点二」这样可区分的展示名）");
            }
        }
        return new CreativeScreenSkeleton(screens, typeDesc == null ? Map.of() : typeDesc);
    }

    /**
     * @return 屏列表（顺序即叙事顺序，不可变）
     */
    public List<ScreenSpec> screens() {
        return screens;
    }

    /**
     * @return 屏数
     */
    public int size() {
        return screens.size();
    }

    /**
     * 统计某类型出现几次（例如卖点屏有几个，决定取几条卖点块）。
     *
     * @param type 屏类型
     * @return 次数；类型不存在返回 0
     */
    public int countOf(String type) {
        int n = 0;
        for (ScreenSpec spec : screens) {
            if (spec.type().equals(type)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 屏类型的展示短名（列表/生产视角用）。
     *
     * <p>顺序：契约的 typeDesc → 骨架里同类型的展示名 → 原样返回类型。
     * 之所以要后两级兜底：历史分镜里可能存在当前契约已删掉的屏类型，展示时不能变成空白。</p>
     *
     * @param type 屏类型
     * @return 展示短名
     */
    public String descOf(String type) {
        String key = StringUtils.blankToDefault(type, "");
        String desc = typeDesc.get(key);
        if (StringUtils.isNotBlank(desc)) {
            return desc;
        }
        for (ScreenSpec spec : screens) {
            if (spec.type().equals(key)) {
                return spec.label();
            }
        }
        return type;
    }

    /**
     * 取景描述（把 {ratio} 换成实际产品占比区间）。
     *
     * @param spec  屏定义
     * @param ratio 产品占比区间（如 45%~65%）
     * @return 取景描述
     */
    public String shotOf(ScreenSpec spec, String ratio) {
        return StringUtils.blankToDefault(spec.shot(), "")
            .replace(RATIO_PLACEHOLDER, StringUtils.blankToDefault(ratio, ""));
    }

    /**
     * @return 出现过的屏类型（按首次出现顺序，去重）
     */
    public Set<String> types() {
        Set<String> types = new LinkedHashSet<>();
        for (ScreenSpec spec : screens) {
            types.add(spec.type());
        }
        return types;
    }

    /**
     * 一句话摘要（启动日志用）。
     *
     * @return 形如 HERO/SELLING_POINT×2/SCENE/DETAIL/SIZE/BRAND
     */
    public String brief() {
        StringBuilder sb = new StringBuilder();
        for (String type : types()) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            int n = countOf(type);
            sb.append(type);
            if (n > 1) {
                sb.append('×').append(n);
            }
        }
        return sb.toString();
    }
}
