package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.DpScenarioStep;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.helper.CreativeDeliveryManifest;
import org.dromara.creative.helper.CreativeRenderer;
import org.dromara.creative.helper.CreativeScreenModuleConfig;
import org.dromara.creative.helper.CreativeStepTypes;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeGenerationService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 多图交付渲染器（V0.2 R30，文档 §26 的 MultiImageRenderer；对应 §9.2 的 MULTI_IMAGE_EXPORT）。
 *
 * <p><b>它解决什么</b>：商品主图（MAIN_IMAGE）的交付物不是一张长图，而是**一组图**
 * （白底/卖点/场景/细节/尺寸…）。R29 之前每张图各自是任务附件，没有"这一版交付包"的概念——
 * 运营要一张张点开下载，也没法回答"这次交付的是哪几张、什么时候包的"。</p>
 *
 * <p><b>产物只记引用，不重复存字节</b>：清单里写的是各屏交付图的附件ID与 sha256；
 * ZIP 在下载时按清单现拼。因此"打包"这件事**不额外占存储**——主图的交付图本来就已经存了一份。</p>
 *
 * <p><b>缺图不编造</b>：某屏还没选定产出时，这一屏**不进包**，屏号记进为 missing 由服务层
 * 写进备注与事件；一张都没有时直接失败（"交付了一个空包"是最坏的结果）。</p>
 *
 * @author creative
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiImageRenderer implements CreativeRenderer {

    /** 渲染器编码（文档 §26 的 MULTI_IMAGE） */
    public static final String CODE = "MULTI_IMAGE";

    private final ICreativeProjectService projectService;
    private final ICreativeStoryboardService storyboardService;
    private final ICreativeGenerationService generationService;
    private final ICreativeScenarioConfigService scenarioConfigService;
    private final DpGenerationMapper generationMapper;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "多图交付渲染器";
    }

    @Override
    public String targetStep() {
        return "FINAL";
    }

    @Override
    public boolean implemented() {
        return true;
    }

    @Override
    public String note() {
        return "把各屏已选定的交付图按屏序打成一组（ZIP 按需现拼，不重复占存储）";
    }

    @Override
    public Outcome render(Context context) {
        Long taskId = context.taskId();
        if (taskId == null) {
            throw new ServiceException("taskId 不能为空。");
        }
        CreativeProjectVo project = projectService.getProject(taskId);
        // 有 LAYOUT 环节的交付类型该走长图渲染器：这里拒绝并说清，避免"主图被排成长图"或反过来
        requireNoLayoutStep(project.getDeliverableType());

        DpStoryboardVo storyboard = storyboardService.latest(taskId);
        if (storyboard == null || storyboard.getScreens() == null || storyboard.getScreens().isEmpty()) {
            throw new ServiceException("还没有分镜，无法生成交付包");
        }

        List<Product> products = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();
        int index = 0;
        for (DpStoryboardScreenVo screen : storyboard.getScreens()) {
            DpGeneration selected = approvedGeneration(taskId, screen.getId());
            if (selected == null) {
                missing.add(StringUtils.blankToDefault(screen.getScreenNo(), String.valueOf(screen.getId())));
                continue;
            }
            index++;
            String moduleCode = CreativeScreenModuleConfig.moduleCodeOf(screen.getSpecJson());
            byte[] bytes;
            Long fileId = selected.getOutputFileId();
            String sourceName;
            if (fileId != null) {
                ICreativeProjectService.FileContent content = projectService.readFileContent(taskId, fileId);
                bytes = content.bytes();
                sourceName = content.fileName();
            } else {
                // 没走过规格化（例如老数据）时退回候选产出本身，并如实记进备注——
                // 交付的是"原始候选尺寸"，这一点必须在清单与备注里看得见。
                bytes = generationService.preview(selected.getId());
                sourceName = "candidate-" + selected.getCandidateNo() + ".png";
                fallbacks.add(StringUtils.blankToDefault(screen.getScreenNo(), "") + "（原始候选，未规格化）");
            }
            int[] size = readSize(bytes);
            String ext = extOf(sourceName);
            products.add(new Product(
                CreativeRenderer.fileNameOf(index, screen.getScreenNo(), moduleCode, ext),
                CreativeDeliveryManifest.ROLE_SCREEN,
                screen.getScreenNo(),
                screen.getScreenType(),
                moduleCode,
                fileId,
                selected.getId(),
                size[0],
                size[1],
                (long) bytes.length,
                CreativeDeliveryManifest.sha256(bytes)));
        }
        if (products.isEmpty()) {
            throw new ServiceException("还没有任何「已选定」的交付图，无法生成交付包："
                + "请先在出图页逐屏出图并选定候选（缺图屏：" + String.join("、", missing) + "）");
        }
        StringBuilder remark = new StringBuilder();
        remark.append("多图交付包：").append(products.size()).append(" 张");
        if (!missing.isEmpty()) {
            remark.append("；未选定的屏 ").append(missing.size()).append(" 个（")
                .append(String.join("、", missing)).append("）不进包");
        }
        if (!fallbacks.isEmpty()) {
            remark.append("；其中 ").append(String.join("、", fallbacks)).append(" 用的是原始候选尺寸");
        }
        log.info("多图渲染器完成 taskId={} 产物={} 缺图屏={}", taskId, products.size(), missing);
        return new Outcome(products, remark.toString());
    }

    /**
     * 交付类型不该配排版类步骤（那属于会排版的渲染器：长图 / 海报）。
     *
     * <p><b>R52 改判据</b>：从「步骤编码 == LAYOUT」改成「步骤种类 == LAYOUT」。
     * 按编码判时海报的 {@code POSTER_LAYOUT} 会被这里放行（编码不叫 LAYOUT），
     * 于是"有排版环节的交付类型"被多图打包装走了——同一份配置在两个渲染器那里得出相反结论。</p>
     *
     * @param deliveryType 交付类型
     */
    private void requireNoLayoutStep(String deliveryType) {
        List<DpScenarioStep> steps = scenarioConfigService.listSteps(deliveryType);
        if (CreativeStepTypes.hasLayout(steps)) {
            throw new ServiceException("交付类型「" + deliveryType
                + "」的流程里有排版环节（step_type=LAYOUT）：它的交付物要排版，应该用会排版的渲染器"
                + "（长图 / 海报）。渲染器由交付类型的渲染模式决定，"
                + "见 POST /creative/projects/{taskId}/delivery/render。");
        }
    }

    /**
     * 某屏已选定的候选。
     *
     * @param taskId   项目ID
     * @param screenId 屏ID
     * @return 候选；没有返回 null
     */
    private DpGeneration approvedGeneration(Long taskId, Long screenId) {
        if (screenId == null) {
            return null;
        }
        List<DpGeneration> rows = generationMapper.selectList(new LambdaQueryWrapper<DpGeneration>()
            .eq(DpGeneration::getTaskId, taskId)
            .eq(DpGeneration::getScreenId, screenId)
            .eq(DpGeneration::getStatus, DpGenerationStatusEnum.APPROVED.getCode())
            .orderByDesc(DpGeneration::getId)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 读图片实际尺寸（交付包清单要写清"这张是多少像素"，读不出就记 0，不编造）。
     *
     * @param bytes 图片字节
     * @return [宽, 高]；读不出返回 [0, 0]
     */
    private static int[] readSize(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image != null) {
                return new int[] {image.getWidth(), image.getHeight()};
            }
        } catch (Exception e) {
            log.warn("读取交付图尺寸失败：{}", e.getMessage());
        }
        return new int[] {0, 0};
    }

    private static String extOf(String fileName) {
        String name = StringUtils.trimToNull(fileName);
        if (name == null) {
            return "png";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "png";
        }
        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return ext.matches("[a-z0-9]{2,4}") ? ext : "png";
    }
}
