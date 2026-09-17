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
