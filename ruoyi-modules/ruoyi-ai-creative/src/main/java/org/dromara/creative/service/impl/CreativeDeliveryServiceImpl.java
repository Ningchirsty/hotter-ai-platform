package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpDeliveryArtifact;
import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DeliveryArtifactVo;
import org.dromara.creative.domain.vo.DeliveryVo;
import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.CreativeDeliveryManifest;
import org.dromara.creative.helper.CreativeRenderer;
import org.dromara.creative.helper.CreativeRendererHub;
import org.dromara.creative.mapper.DpDeliveryArtifactMapper;
import org.dromara.creative.service.ICreativeDeliveryService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交付渲染服务实现（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * <p><b>这一层做的是"渲染器都一样的部分"</b>：解析出该用哪个渲染器、版本号递增、
 * 清单落库、事件与阶段留痕、把产物拼成下载体。渲染器只管"产出哪几张图"。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeDeliveryServiceImpl implements ICreativeDeliveryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CreativeRendererHub rendererHub;
    private final DpDeliveryArtifactMapper artifactMapper;
    private final ICreativeProjectService projectService;
    private final ICreativeScenarioConfigService scenarioConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryVo render(Long taskId) {
        return renderWith(taskId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryVo renderWith(Long taskId, String rendererCode) {
        CreativeProjectVo project = projectService.getProject(taskId);
        String deliveryType = project.getDeliverableType();
        DpDeliveryType type = scenarioConfigService.getDeliveryType(deliveryType);
        String renderMode = type == null ? null : type.getRenderMode();

        // 渲染器：显式指定优先（排障/验收用），否则按交付类型的渲染模式解析
        CreativeRenderer renderer = StringUtils.isBlank(rendererCode)
            ? rendererHub.resolveFor(renderMode)
            : rendererHub.require(rendererCode);

        CreativeRenderer.Context context = new CreativeRenderer.Context(
            taskId, deliveryType, renderMode, null, null, outputSpecOf(deliveryType), Map.of());
        CreativeRenderer.Outcome outcome = renderer.render(context);
        if (outcome == null || outcome.products() == null || outcome.products().isEmpty()) {
            throw new ServiceException("渲染器「" + renderer.displayName() + "」没有产出任何东西，"
                + "本次不生成交付版本（空交付包比失败更糟）。");
        }

        int nextVersion = nextVersion(taskId, renderer.code());
        String manifest = CreativeDeliveryManifest.build(renderer.code(), deliveryType,
            templateKeyOf(renderer), LocalDateTime.now().format(TIME), outcome.products());
        long totalBytes = outcome.products().stream()
            .mapToLong(p -> p.bytes() == null ? 0L : p.bytes()).sum();

        DpDeliveryArtifact row = new DpDeliveryArtifact();
        row.setTaskId(taskId);
        row.setDeliveryType(deliveryType);
        row.setRenderer(renderer.code());
        row.setVersion(nextVersion);
        row.setManifestJson(manifest);
        row.setImageCount(outcome.products().size());
        row.setTotalBytes(totalBytes);
        row.setChecksum(CreativeDeliveryManifest.checksumOf(outcome.products()));
        row.setRemark(outcome.remark());
        artifactMapper.insert(row);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("artifactId", row.getId());
        event.put("renderer", renderer.code());
        event.put("version", nextVersion);
        event.put("imageCount", row.getImageCount());
        event.put("totalBytes", totalBytes);
        event.put("checksum", row.getChecksum());
        event.put("renderMode", renderMode);
        projectService.appendEvent(taskId, renderer.targetStep(), "DELIVERY_RENDER",
            toJson(event));
        // 阶段推进：多图交付（FINAL 步）完成后进入终审；长图渲染器内部已经把阶段推到 V08_READY，
        // 这里只在还没到那一步时补推（moveStage 自己做合法性校验，重复推同一阶段是幂等的）。
        if (MultiImageRenderer.CODE.equals(renderer.code())) {
            projectService.moveStage(taskId, DpVisualStageEnum.FINAL_REVIEW, "DELIVERY_RENDER",
                toJson(Map.of("version", nextVersion, "imageCount", row.getImageCount())));
        }
        log.info("交付渲染完成 taskId={} renderer={} version={} 产物={} 合计={}B",
            taskId, renderer.code(), nextVersion, row.getImageCount(), totalBytes);
        return view(taskId, renderer.code());
    }

    @Override
    public DeliveryVo view(Long taskId) {
        return view(taskId, null);
    }

    @Override
    public Download download(Long taskId, Long versionId) {
        DpDeliveryArtifact row = artifactMapper.selectById(versionId);
        if (row == null || !taskId.equals(row.getTaskId())) {
            throw new ServiceException("交付版本不属于该项目：" + versionId);
        }
        List<Map<String, Object>> products = productsOf(row.getManifestJson());
        if (products.isEmpty()) {
            throw new ServiceException("这一版交付清单里没有产物（清单可能已损坏）：" + versionId);
        }
        String base = "delivery-" + row.getRenderer().toLowerCase(java.util.Locale.ROOT)
            + "-v" + row.getVersion();
        if (products.size() == 1 && CreativeDeliveryManifest.ROLE_LONG_PAGE
            .equals(String.valueOf(products.get(0).get("role")))) {
            // 长图：直接给图片，不套一层 ZIP（人拿到就是要那张图）
            byte[] bytes = readProduct(taskId, products.get(0));
            return new Download(bytes, "image/png", base + ".png");
        }
        List<CreativeRenderer.Product> list = new ArrayList<>();
        for (Map<String, Object> item : products) {
            list.add(toProduct(item));
        }
        byte[] zip;
        try {
            zip = CreativeDeliveryManifest.zip(list, row.getManifestJson(),
                fileId -> readProduct(taskId, Map.of("fileId", fileId)));
        } catch (IllegalStateException e) {
            throw new ServiceException(e.getMessage());
        }
        return new Download(zip, "application/zip", base + ".zip");
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private DeliveryVo view(Long taskId, String rendererCode) {
        CreativeProjectVo project = projectService.getProject(taskId);
        String deliveryType = project.getDeliverableType();
        DpDeliveryType type = scenarioConfigService.getDeliveryType(deliveryType);
        String renderMode = type == null ? null : type.getRenderMode();

        DeliveryVo vo = new DeliveryVo();
        vo.setTaskId(taskId);
        vo.setDeliveryType(deliveryType);
        vo.setRenderMode(renderMode);

        List<DpDeliveryArtifact> rows = artifactMapper.selectList(
            new LambdaQueryWrapper<DpDeliveryArtifact>()
                .eq(DpDeliveryArtifact::getTaskId, taskId)
                .orderByDesc(DpDeliveryArtifact::getVersion));
        List<DeliveryArtifactVo> artifacts = new ArrayList<>();
        for (DpDeliveryArtifact row : rows) {
            artifacts.add(toVo(row));
        }
        vo.setArtifacts(artifacts);
        vo.setCurrentVersion(artifacts.isEmpty() ? 0 : artifacts.get(0).getVersion());

        // "这次用的渲染器"：显式指定优先，否则按配置解析（解析不出来也不报错——只读接口不该 500）
        String selected = rendererCode;
        if (StringUtils.isBlank(selected)) {
            try {
                selected = rendererHub.resolveFor(renderMode).code();
            } catch (Exception e) {
                selected = null;
            }
        }
        vo.setRenderer(selected);
        vo.setRendererName(nameOf(selected));
        vo.setRenderers(capabilities(selected));
        return vo;
    }

    private List<DeliveryVo.RendererCapabilityVo> capabilities(String selected) {
        List<DeliveryVo.RendererCapabilityVo> list = new ArrayList<>();
        for (CreativeRenderer renderer : rendererHub.all()) {
            DeliveryVo.RendererCapabilityVo item = new DeliveryVo.RendererCapabilityVo();
            item.setCode(renderer.code());
            item.setName(renderer.displayName());
            item.setTargetStep(renderer.targetStep());
            item.setImplemented(renderer.implemented());
            item.setNote(renderer.note());
            item.setSelected(renderer.code().equals(selected));
            list.add(item);
        }
        return list;
    }

    private String nameOf(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        for (CreativeRenderer renderer : rendererHub.all()) {
            if (renderer.code().equals(code)) {
                return renderer.displayName();
            }
        }
        return code;
    }

    private DeliveryArtifactVo toVo(DpDeliveryArtifact row) {
        DeliveryArtifactVo vo = new DeliveryArtifactVo();
        vo.setId(row.getId());
        vo.setTaskId(row.getTaskId());
        vo.setDeliveryType(row.getDeliveryType());
        vo.setRenderer(row.getRenderer());
        vo.setRendererName(nameOf(row.getRenderer()));
        vo.setVersion(row.getVersion());
        vo.setImageCount(row.getImageCount());
        vo.setTotalBytes(row.getTotalBytes());
        vo.setChecksum(row.getChecksum());
        vo.setRemark(row.getRemark());
        vo.setProducts(productsOf(row.getManifestJson()));
        vo.setDownloadName("delivery-" + StringUtils.blankToDefault(row.getRenderer(), "x")
            .toLowerCase(java.util.Locale.ROOT) + "-v" + row.getVersion()
            + (Integer.valueOf(1).equals(row.getImageCount()) ? ".png" : ".zip"));
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    private int nextVersion(Long taskId, String renderer) {
        List<DpDeliveryArtifact> rows = artifactMapper.selectList(new LambdaQueryWrapper<DpDeliveryArtifact>()
            .eq(DpDeliveryArtifact::getTaskId, taskId)
            .eq(DpDeliveryArtifact::getRenderer, renderer)
            .orderByDesc(DpDeliveryArtifact::getVersion)
            .last("limit 1"));
        return rows.isEmpty() || rows.get(0).getVersion() == null ? 1 : rows.get(0).getVersion() + 1;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> productsOf(String manifestJson) {
        if (StringUtils.isBlank(manifestJson)) {
            return List.of();
        }
        try {
            Map<String, Object> root = MAPPER.readValue(manifestJson, new TypeReference<>() { });
            Object artifacts = root.get("artifacts");
            return artifacts instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        } catch (Exception e) {
            log.warn("解析交付清单失败：{}", e.getMessage());
            return List.of();
        }
    }

    private CreativeRenderer.Product toProduct(Map<String, Object> item) {
        return new CreativeRenderer.Product(
            str(item.get("fileName")),
            str(item.get("role")),
            str(item.get("screenNo")),
            str(item.get("screenType")),
            str(item.get("moduleCode")),
            longOf(item.get("fileId")),
            longOf(item.get("generationId")),
            intOf(item.get("width")),
            intOf(item.get("height")),
            longOf(item.get("bytes")),
            str(item.get("sha256")));
    }

    private byte[] readProduct(Long taskId, Map<String, Object> item) {
        Long fileId = longOf(item.get("fileId"));
        if (fileId == null) {
            throw new ServiceException("这一版交付清单里的产物没有附件ID，取不到内容（清单可能来自旧版本）");
        }
        return projectService.readFileContent(taskId, fileId).bytes();
    }

    private Map<String, Object> outputSpecOf(String deliveryType) {
        try {
            List<DpOutputSpec> specs = scenarioConfigService.listOutputSpecs(deliveryType);
            if (specs.isEmpty()) {
                return Map.of();
            }
            DpOutputSpec spec = specs.get(0);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("specCode", spec.getSpecCode());
            map.put("width", spec.getWidth());
            map.put("height", spec.getHeight());
            map.put("heightMode", spec.getHeightMode());
            map.put("ratio", spec.getRatio());
            return map;
        } catch (Exception e) {
            log.warn("读取输出规格失败（不阻断交付渲染）：{}", e.getMessage());
            return Map.of();
        }
    }

    private String templateKeyOf(CreativeRenderer renderer) {
        // 只有长图渲染器有模板；多图交付没有模板概念（每张图是独立产物）
        return LongPageRenderer.CODE.equals(renderer.code()) ? "longpage" : null;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOf(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
