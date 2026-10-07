package org.dromara.ai.image.cloud;

import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageAssetProbe;
import org.dromara.ai.image.service.ImageTaskRepository;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Images API 请求快照。浏览器只提交本人素材 ID，不提交路径、密钥或外部 URL。 */
public record CloudImageRequest(String model, String prompt, String capability,
                                List<Long> referenceAssetIds, Long maskAssetId, CloudImageOutput output) {
    public CloudImageRequest(String model, String prompt, String capability, List<Long> referenceAssetIds, Long maskAssetId) {
        this(model,prompt,capability,referenceAssetIds,maskAssetId,CloudImageOutput.DEFAULT);
    }
    public CloudImageRequest {
        capability = capability == null ? "T2I" : capability;
        output = output == null ? CloudImageOutput.DEFAULT : output;
        referenceAssetIds = referenceAssetIds == null ? List.of() : List.copyOf(referenceAssetIds);
    }
    public static final List<String> CAPABILITIES = List.of("T2I", "EDIT", "MULTI", "MASK", "OUTPAINT", "TRANSPARENT");
    public static boolean testedModel(String model) {
        return model != null && List.of("gpt-image-2.5-flare", "gpt-image-2.5-sunburst").contains(model);
    }
    public static boolean verified(String model, String capability) { return CloudImageValidation.verified(model, capability); }
    public static List<Map<String, Object>> profiles() {
        return BluOctoImageClient.MODELS.stream().map(model -> Map.<String, Object>of(
            "model", model, "testedAt", CloudImageValidation.TESTED_AT,
            "outputProfiles", CloudImageOutputValidation.profiles(model),
            "maxReferenceImages", CloudImageValidation.maxReferences(model), "maxReferenceBytes", CloudImageValidation.maxReferenceBytes(model),
            "capabilities", CloudImageValidation.capabilities(model).stream().map(cap -> Map.of(
                "code", cap, "verified", verified(model, cap), "historicallyVerified", testedModel(model) && "T2I".equals(cap),
                "status", CloudImageValidation.status(model, cap)
            )).toList())).toList();
    }
    public void validateShape() {
        output.validate(model,capability);
        if (model == null || !BluOctoImageClient.MODELS.contains(model) || !CAPABILITIES.contains(capability)) throw invalid("模型或创作能力无效");
        if (!CloudImageValidation.capabilities(model).contains(capability)) throw invalid("该型号没有此项已对接的创作能力");
        int count = referenceAssetIds.size();
        if (referenceAssetIds.stream().anyMatch(id -> id <= 0) || referenceAssetIds.stream().distinct().count() != count) throw invalid("参考素材 ID 无效或重复");
        if (List.of("T2I", "TRANSPARENT").contains(capability) && (count != 0 || maskAssetId != null)) throw invalid("该能力不接受参考图或蒙版");
        if (List.of("EDIT", "MASK", "OUTPAINT").contains(capability) && count != 1) throw invalid("请提供一张原图");
        if ("MULTI".equals(capability) && (count < 2 || count > CloudImageValidation.maxReferences(model))) throw invalid("多图参考须提供 2–" + CloudImageValidation.maxReferences(model) + " 张图片");
        if (List.of("MASK", "OUTPAINT").contains(capability) != (maskAssetId != null)) throw invalid("蒙版只用于局部编辑或扩图，并且必须提供");
    }
    public void requireVerified() {
        validateShape();
        if (!verified(model, capability)) throw new ImageTaskException("CLOUD_CAPABILITY_UNVERIFIED", "该模型的此项能力尚未通过供应商真实调用，暂不可提交");
    }
    public List<BluOctoImageClient.InputImage> inputs(ImageTaskRepository repository, ImageAssetStore store, String tenant, long user) {
        validateShape();
        List<BluOctoImageClient.InputImage> result = new ArrayList<>();
        long total = 0;
        int width = 0, height = 0;
        for (Long id : referenceAssetIds) {
            var asset = repository.requireOwnedAsset(id, tenant, user);
            byte[] content = store.read(asset.storageKey());
            if (content.length > CloudImageValidation.maxReferenceBytes(model)) throw invalid("参考图超过此型号的大小上限");
            var probe = probe(content);
            if (result.isEmpty()) { width = probe.width(); height = probe.height(); }
            total += content.length;
            result.add(new BluOctoImageClient.InputImage(content, "image/" + ("jpg".equals(probe.format()) ? "jpeg" : probe.format()), false));
        }
        if (maskAssetId != null) {
            var asset = repository.requireOwnedAsset(maskAssetId, tenant, user);
            byte[] mask = store.read(asset.storageKey());
            var probe = probe(mask);
            if (mask.length >= 4 * 1024 * 1024 || !"png".equalsIgnoreCase(probe.format()) || probe.width() != width || probe.height() != height) throw invalid("蒙版须为小于 4MB 的 PNG，尺寸须与原图一致");
            try {
                var image = ImageIO.read(new ByteArrayInputStream(mask));
                boolean transparent = false;
                if (image != null && image.getColorModel().hasAlpha()) {
                    scan: for (int y=0; y<height; y++) for (int x=0; x<width; x++) if ((image.getRGB(x,y) >>> 24) == 0) { transparent = true; break scan; }
                }
                if (!transparent) throw invalid("蒙版必须包含完全透明的可编辑区域");
            } catch (ImageTaskException e) { throw e; } catch (Exception e) { throw invalid("蒙版无法解码"); }
            total += mask.length;
            result.add(new BluOctoImageClient.InputImage(mask, "image/png", true));
        }
        if (total > 40L * 1024 * 1024) throw invalid("参考图合计须小于 40MB");
        return List.copyOf(result);
    }
    private static ImageAssetProbe.Probe probe(byte[] content) {
        if (content == null || content.length == 0 || content.length > 20 * 1024 * 1024) throw invalid("素材为空或超过 20MB");
        var p = ImageAssetProbe.probeBytes(content);
        if (!p.measured() || p.width() <= 0 || p.height() <= 0 || p.exceedsPixels(16 * 1024 * 1024) || !List.of("png", "jpeg", "jpg", "webp").contains(p.format().toLowerCase(java.util.Locale.ROOT))) throw invalid("参考图格式或像素大小无效");
        return p;
    }
    private static ImageTaskException invalid(String message) { return ImageTaskException.invalidContract(message); }
}
