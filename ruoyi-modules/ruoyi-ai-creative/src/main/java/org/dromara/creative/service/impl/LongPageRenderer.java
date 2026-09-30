package org.dromara.creative.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.creative.domain.vo.DpDetailPageVo;
import org.dromara.creative.helper.CreativeDeliveryManifest;
import org.dromara.creative.helper.CreativeRenderer;
import org.dromara.creative.service.ICreativeLayoutService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 长图渲染器（V0.2 R30，文档 §26 的 LongPageRenderer）。
 *
 * <p><b>它不重新实现排版</b>：长图排版从 R3.3 起就是 {@code CreativeLayoutServiceImpl}
 * （模板 longpage + 独立渲染服务 + 版本表），本轮把它**接进 Renderer Hub**，
 * 而不是另写一份——否则同一个交付形态会有两条产出路径，出了问题不知道该看哪条。</p>
 *
 * <p>因此这个渲染器做的是"适配"：调一次既有排版，把产出的长图包装成 Hub 的
 * {@link Product} 清单（含 sha256），让交付包/交付清单对所有交付形态是同一套结构。</p>
 *
 * @author creative
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LongPageRenderer implements CreativeRenderer {

    /** 渲染器编码（文档 §26 的 LONG_PAGE） */
    public static final String CODE = "LONG_PAGE";

    private final ICreativeLayoutService layoutService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "长图排版渲染器";
    }

    @Override
    public String targetStep() {
        return "LAYOUT";
    }

    @Override
    public boolean implemented() {
        return true;
    }

    @Override
    public String note() {
        return "把已锁定的 N 屏渲染成一张 750×N 长图（模板 longpage，独立渲染服务）";
    }

    @Override
    public Outcome render(Context context) {
        Long taskId = context.taskId();
        if (taskId == null) {
            throw new ServiceException("taskId 不能为空。");
        }
        // 既有排版链路自带全部守卫（视觉门、有没有 LAYOUT 环节、模板是否已发布、有没有已选定产出），
        // 这里不重复判断——重复判断迟早会与那边不一致。
        DpDetailPageVo page = layoutService.render(taskId);
        DpDetailPageVo.DpDetailPageVersionVo version = currentVersionOf(page);
        if (version == null || version.getRenderedFileId() == null) {
            throw new ServiceException("排版没有产出长图（版本或附件缺失），无法生成交付清单。");
        }
        byte[] bytes = layoutService.preview(taskId, version.getId());
        Map<String, Object> spec = context.outputSpec() == null ? Map.of() : context.outputSpec();
        Product product = new Product(
            "longpage-" + (version.getVersion() == null ? 1 : version.getVersion()) + ".png",
            CreativeDeliveryManifest.ROLE_LONG_PAGE,
            null,
            null,
            null,
            version.getRenderedFileId(),
            null,
            version.getPageWidth(),
            version.getPageHeight(),
            (long) bytes.length,
            CreativeDeliveryManifest.sha256(bytes));
        List<Product> products = new ArrayList<>();
        products.add(product);
        log.info("长图渲染器完成 taskId={} 版本={} 尺寸={}×{} 字节={}", taskId, version.getVersion(),
            version.getPageWidth(), version.getPageHeight(), bytes.length);
        Map<String, Object> extra = new LinkedHashMap<>(spec);
        extra.put("detailPageVersionId", version.getId());
        return new Outcome(products, "长图排版 v" + version.getVersion() + "（"
            + version.getPageWidth() + "×" + version.getPageHeight() + "，"
            + (bytes.length / 1024) + "KB）");
    }

    /**
     * 取刚渲染出来的那一版（排版服务返回的 {@code currentVersion}）。
     *
     * @param page 详情页视图
     * @return 版本；找不到返回 null
     */
    private static DpDetailPageVo.DpDetailPageVersionVo currentVersionOf(DpDetailPageVo page) {
        if (page == null || page.getVersions() == null || page.getVersions().isEmpty()) {
            return null;
        }
        Integer current = page.getCurrentVersion();
        for (DpDetailPageVo.DpDetailPageVersionVo version : page.getVersions()) {
            if (current != null && current.equals(version.getVersion())) {
                return version;
            }
        }
        return page.getVersions().get(0);
    }
}
