package org.dromara.ai.video.comfy;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * ComfyUI 客户端抽象。
 *
 * <p>抽出接口是为了让服务层能用<b>可控替身</b>做离线接口测试，
 * 覆盖三种合法 payload、非法 workflowCode、缺失图片、ComfyUI 故障与超时、
 * 输出超 5 秒的处理，而不必占用 GPU。</p>
 */
public interface ComfyClient {

    /**
     * 上传图片到 ComfyUI 输入目录，返回其可访问的文件名。
     *
     * @param fileName 目标文件名（服务端生成，不使用浏览器原始文件名）
     * @param content  文件内容
     * @param mimeType MIME 类型
     * @return ComfyUI 可读取的输入文件名
     */
    String uploadImage(String fileName, byte[] content, String mimeType);

    /**
     * 提交节点图，返回 prompt_id。
     *
     * @param graph 已按契约填充的 API Format 节点图
     */
    String submitPrompt(JsonNode graph);

    /**
     * 查询一次提交的执行结果。
     *
     * @return 仍在执行时返回 {@link PollResult#running()}；完成时带出输出列表
     */
    PollResult poll(String promptId);

    /**
     * 下载输出文件内容。
     *
     * @param output 输出描述
     * @return 文件字节
     */
    byte[] fetchOutput(ComfyOutput output);

    /**
     * 服务可用性检查，用于在提交前发现网络不可达。
     */
    boolean isReachable();

    /**
     * 请求 ComfyUI 释放显存与已加载的模型缓存。
     *
     * <p>为什么需要它：ComfyUI 默认会把已加载的模型留在显存里不释放。多轮生成后
     * 显存被历史缓存占满，后续任务会在采样节点拿不到显存而失败。实测过一次：
     * A100 上只剩 14% 空闲、{@code torch_vram_free} 近乎 0，任务在
     * {@code MiniMaxH3Director} 节点被中断；调用 <b>POST /free</b> 后空闲显存
     * 恢复到 99%。</p>
     *
     * <p>默认实现返回 false（不做任何事），因此不会破坏测试替身；
     * 是否在提交前调用由 {@code video.comfy-free-before-submit} 控制，
     * 默认关闭——释放模型会让下一次生成重新加载权重（变慢），
     * 只应在显存确实紧张时打开。</p>
     *
     * @return 是否成功完成释放；实现不可用或调用失败时返回 false，不抛异常
     */
    default boolean freeMemory() {
        return false;
    }

    /**
     * 轮询结果。
     *
     * @param state   RUNNING / SUCCEEDED / FAILED
     * @param outputs 完成时的输出列表
     * @param error   失败原因（已脱敏）
     */
    record PollResult(State state, java.util.List<ComfyOutput> outputs, String error) {

        /**
         * 轮询状态。
         */
        public enum State {
            /**
             * 仍在队列或执行中。
             */
            RUNNING,
            /**
             * 已完成并有输出。
             */
            SUCCEEDED,
            /**
             * ComfyUI 报告执行失败。
             */
            FAILED
        }

        public static PollResult running() {
            return new PollResult(State.RUNNING, java.util.List.of(), null);
        }

        public static PollResult succeeded(java.util.List<ComfyOutput> outputs) {
            return new PollResult(State.SUCCEEDED, outputs, null);
        }

        public static PollResult failed(String error) {
            return new PollResult(State.FAILED, java.util.List.of(), error);
        }
    }
}
