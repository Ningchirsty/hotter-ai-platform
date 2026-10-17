package org.dromara.ai.video.domain;

import java.util.List;

/**
 * 一个可提交的工作流版本（契约中的一个 binding）。
 *
 * @param workflowCode      工作流编码，如 wf-t2v-h3
 * @param capabilityCode    能力编码
 * @param modelCode         底模编码
 * @param version           版本号
 * @param status            DRAFT / TESTING / PUBLISHED / RETIRED
 * @param apiJsonFile       后端受控目录下 API Format JSON 的相对路径
 * @param checksum          模板文件 SHA-256，加载时必须校验
 * @param mapping           字段映射白名单
 * @param fixedFieldValidation 输出档位、时长以及原生节点帧数约束
 * @param maxDurationSeconds 成片时长上限（秒）；超出必须在服务端截断
 * @param outputNodeId      输出节点 ID
 * @param outputField       输出字段名
 */
public record WorkflowVersion(
    String workflowCode,
    String capabilityCode,
    String modelCode,
    String version,
    String status,
    String apiJsonFile,
    String checksum,
    List<WorkflowMapping> mapping,
    FixedFieldValidation fixedFieldValidation,
    Integer maxDurationSeconds,
    String outputNodeId,
    String outputField
) {

    /**
     * 仅 PUBLISHED 可用于正式环境提交；TESTING 只允许在受控联调环境使用。
     */
    public boolean isPublished() {
        return "PUBLISHED".equalsIgnoreCase(status);
    }

    /**
     * 是否可在联调环境提交。
     */
    public boolean isTestable() {
        return isPublished() || "TESTING".equalsIgnoreCase(status);
    }

    /**
     * 契约中声明的固定字段取值。
     *
     * @param tier           固定输出档位；多档位时取 {@code supportedTiers} 的第一个（仅作展示/兜底）
     * @param dur            固定时长档位
     * @param supportedTiers 允许的输出档位集合。契约声明多个时为多档位；
     *                       为空表示沿用旧的单一 {@code tier} 语义
     */
    public record FixedFieldValidation(String tier, String dur, java.util.Set<String> supportedTiers,
                                       java.util.List<String> supportedDurations, DurationControl durationControl,
                                       java.util.List<OutputProfile> outputProfiles, OutputControl outputControl) {
        public FixedFieldValidation(String tier, String dur, java.util.Set<String> supportedTiers) {
            this(tier, dur, supportedTiers, java.util.List.of(), null);
        }

        public FixedFieldValidation(String tier, String dur, java.util.Set<String> supportedTiers,
                                    java.util.List<String> supportedDurations, DurationControl durationControl) {
            this(tier, dur, supportedTiers, supportedDurations, durationControl, java.util.List.of(), null);
        }

        /**
         * 该版本允许的输出档位。契约未声明 {@code supportedTiers} 时退化为单一 {@code tier}。
         */
        public java.util.Set<String> allowedTiers() {
            if (supportedTiers != null && !supportedTiers.isEmpty()) {
                return supportedTiers;
            }
            return tier == null ? java.util.Set.of() : java.util.Set.of(tier);
        }

        public java.util.List<String> allowedDurations() {
            return supportedDurations == null ? java.util.List.of() : supportedDurations;
        }

        public java.util.List<OutputProfile> profiles() {
            return outputProfiles == null ? java.util.List.of() : outputProfiles;
        }

        public OutputProfile profileOf(String selectedTier) {
            return profiles().stream().filter(p -> p.tier().equals(selectedTier)).findFirst().orElse(null);
        }

        public java.util.List<String> durationsOf(String selectedTier) {
            OutputProfile profile = profileOf(selectedTier);
            return profile == null ? allowedDurations() : profile.durations();
        }
    }

    /** Public output dimensions and the actual model canvas; legacy profiles preserve existing tasks. */
    public record OutputProfile(String tier, String ratio, int width, int height,
                                int generationWidth, int generationHeight, java.util.List<String> durations,
                                String mode, boolean legacy) {
        public OutputProfile {
            if (tier == null || tier.isBlank() || ratio == null || ratio.isBlank()
                || width <= 0 || height <= 0 || width > 3840 || height > 3840
                || width % 2 != 0 || height % 2 != 0 || generationWidth <= 0 || generationHeight <= 0
                || durations == null || durations.isEmpty()
                || !("NATIVE".equals(mode) || "RESIZED".equals(mode) || "SUPER_RESOLUTION".equals(mode))) {
                throw new IllegalArgumentException("视频输出规格无效");
            }
            durations = java.util.List.copyOf(durations);
        }
    }

    /** Explicit native output edge; a 2x latent upsampler receives half-size latent inputs. */
    public record OutputControl(String videoNodeId, String resizeNodeId, int latentDivisor, int alignment, String upscaleModel) {
        public OutputControl {
            if (videoNodeId == null || videoNodeId.isBlank() || resizeNodeId == null || resizeNodeId.isBlank()
                || videoNodeId.equals(resizeNodeId) || (latentDivisor != 1 && latentDivisor != 2)
                || alignment < 16 || alignment > 128) {
                throw new IllegalArgumentException("视频尺寸映射无效");
            }
        }
    }

    /** Frame grid declared by the reviewed workflow; no model or node guessing at runtime. */
    public record DurationControl(int fps, int frameStep, int frameOffset) {
        public DurationControl {
            if (fps <= 0 || frameStep <= 0 || frameOffset < 0 || frameOffset >= frameStep) {
                throw new IllegalArgumentException("视频帧数约束无效");
            }
        }

        public int frames(int seconds) {
            if (seconds <= 0 || seconds > 20) throw new IllegalArgumentException("视频时长超出范围");
            int target = Math.multiplyExact(seconds, fps);
            return target + Math.floorMod(frameOffset - target, frameStep);
        }
    }
}
