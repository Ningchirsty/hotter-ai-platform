package org.dromara.creative.helper;

import org.dromara.common.core.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交付渲染器（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * <p><b>为什么要有这层抽象</b>：R30 之前"渲染"这件事只有一个实现——
 * {@code CreativeLayoutServiceImpl} 里写死的长图排版（模板 {@code longpage}）。
 * 交付类型一多，"渲染"就不再是同一件事：长图是**把 N 屏拼成一张**，
 * 主图是**把 N 屏打包成一组**（文档 §9.2 的 MULTI_IMAGE_EXPORT），
 * 海报/印刷/视频将来还会是别的形态。把差异放在"渲染器"里、
 * 把共同点（选了哪几屏、产出登记、版本留痕、可复现校验和）留在服务层，
 * 就是文档 §26 说的 Renderer Hub。</p>
 *
 * <p><b>统一入口</b>（文档 §26 的请求形状）：
 * {@code { renderer, templateCode, templateVersion, outputSpec, layout }} → 本接口的 {@link Context}。</p>
 *
 * <p><b>没实现的渲染器必须说出来</b>：文档列出的 ArticleRenderer / PrintRenderer /
 * VideoRenderer 现在都还没有实现（PosterRenderer 已在 V0.2 R52 落地）。它们在 {@link #implemented()}
 * 上如实返回 false，被调用时由 Hub 直接拒绝并说明——绝不允许"跑了个空壳还报成功"。</p>
 *
 * @author creative
 */
public interface CreativeRenderer {

    /**
     * 渲染器编码（文档 §26 的 {@code renderer} 字段，形如 {@code LONG_PAGE}）。
     *
     * @return 编码
     */
    String code();

    /**
     * 中文名（页面直接显示）。
     *
     * @return 名称
     */
    String displayName();

    /**
     * 它服务的是场景流程里的哪一步（{@code dp_scenario_step.step_code}）。
     *
     * <p>用它把"渲染器"和"配置的流程"对上：配了 LAYOUT 的交付类型走长图，
     * 只有 FINAL 的走打包——判据来自配置，不写死交付类型。</p>
     *
     * @return 步骤码
     */
    String targetStep();

    /**
     * 是否已经实现。未实现的渲染器只能出现在能力清单里，不能被执行。
     *
     * @return true 表示可以执行
     */
    boolean implemented();

    /**
     * 说明（给页面看：为什么还没实现、什么时候会上）。
     *
     * @return 说明
     */
    String note();

    /**
     * 执行渲染。
     *
     * @param context 渲染上下文
     * @return 渲染结果（产物描述清单 + 备注）
     */
    Outcome render(Context context);

    /**
     * 渲染上下文（统一请求形状）。
     *
     * @param taskId          项目ID
     * @param deliveryType    交付类型
     * @param renderMode      交付类型的渲染模式（{@code dp_delivery_type.render_mode}，如 LONGPAGE / MULTI_IMAGE）
     * @param templateCode    模板码（可为空＝由渲染器自定）
     * @param templateVersion 模板版本（可为空）
     * @param outputSpec      输出规格（可为空）
     * @param layout          版面参数（可为空）
     */
    record Context(Long taskId, String deliveryType, String renderMode, String templateCode,
                   String templateVersion, Map<String, Object> outputSpec, Map<String, Object> layout) {
    }

    /**
     * 一个产物（长图是 1 个；多图打包是 N 个）。
     *
     * <p><b>产物只记"引用"，不重复存字节</b>：主图打包的产物就是各屏已选定的交付图
     * （它们已经是任务附件），长图的产物就是那一张排版 PNG。交付包在下载时按这份清单
     * 现拼 ZIP，因此**不会为了"打包"再复制一份存储**。</p>
     *
     * @param fileName     包内文件名（ASCII 安全，形如 {@code 01-S01-MAIN_WHITE_BG.png}）
     * @param role         角色（LONG_PAGE / SCREEN_DELIVERY）
     * @param screenNo     屏号（长图无屏号时为 null）
     * @param screenType   屏类型（可空）
     * @param moduleCode   模块编码（可空）
     * @param fileId       附件ID（可空＝产物直接来自生成记录）
     * @param generationId 出图候选ID（可空）
     * @param width        宽（px）
     * @param height       高（px）
     * @param bytes        字节数
     * @param sha256       内容 sha256（可复现性核对）
     */
    record Product(String fileName, String role, String screenNo, String screenType, String moduleCode,
                   Long fileId, Long generationId, Integer width, Integer height, Long bytes, String sha256) {
    }

    /**
     * 渲染结果。
     *
     * @param products 产物清单（至少 1 个；空清单意味着"什么都没产出"，服务层会拒）
     * @param remark   人可读的结果说明（进版本备注与事件）
     */
    record Outcome(List<Product> products, String remark) {
    }

    /**
     * 交付包内文件名：序 + 屏号 + 模块编码，**只保留 ASCII 安全字符**。
     *
     * <p>为什么强制 ASCII：ZIP 条目名虽然支持 UTF-8，但不同解压工具对编码标记的处理不一致，
     * 用中文名会出现"解出来是乱码文件名"这种看起来像数据损坏的现象。中文信息（模块名）留在
     * 清单里，不放进文件名。</p>
     *
     * @param index      序号（从 1 开始）
     * @param screenNo   屏号（可空）
     * @param moduleCode 模块编码（可空）
     * @param ext        扩展名（不含点，可空＝png）
     * @return 文件名
     */
    static String fileNameOf(int index, String screenNo, String moduleCode, String ext) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%02d", index)).append('-');
        String no = sanitize(screenNo);
        if (StringUtils.isNotBlank(no)) {
            sb.append(no).append('-');
        }
        String code = sanitize(moduleCode);
        sb.append(StringUtils.isBlank(code) ? "SCREEN" : code);
        sb.append('.').append(StringUtils.isBlank(ext) ? "png" : ext);
        return sb.toString();
    }

    /**
     * 把任意文本收敛成 ASCII 安全片段（非 [A-Za-z0-9_-] 一律换成下划线，空则返回空串）。
     *
     * @param raw 原文（可空）
     * @return 安全片段
     */
    static String sanitize(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : raw.trim().toCharArray()) {
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_';
            sb.append(ok ? c : '_');
        }
        return sb.toString().replaceAll("_{2,}", "_");
    }

    /**
     * 把产物清单转成页面/清单用的 Map 列表（纯数据，便于单测断言）。
     *
     * @param products 产物
     * @return Map 列表
     */
    static List<Map<String, Object>> toRows(List<Product> products) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Product product : products) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fileName", product.fileName());
            row.put("role", product.role());
            row.put("screenNo", product.screenNo());
            row.put("screenType", product.screenType());
            row.put("moduleCode", product.moduleCode());
            row.put("fileId", product.fileId());
            row.put("generationId", product.generationId());
            row.put("width", product.width());
            row.put("height", product.height());
            row.put("bytes", product.bytes());
            row.put("sha256", product.sha256());
            rows.add(row);
        }
        return rows;
    }
}
