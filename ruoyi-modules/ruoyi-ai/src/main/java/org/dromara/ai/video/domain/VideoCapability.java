package org.dromara.ai.video.domain;

/**
 * 视频创作能力编码。
 *
 * <p>仅这三个能力在当前分支有已导入的 H3 模板；其余能力（MFRAME/CAMMOVE/VEXT/VHD/LIP）
 * 在契约中仍为 DRAFT 占位，不得开放提交。</p>
 */
public enum VideoCapability {

    /**
     * 图生视频。
     */
    I2V("i2v"),
    /**
     * 文生视频。
     */
    T2V("t2v"),
    /**
     * 首尾帧生视频。
     */
    FL2V("fl2v");

    private final String taskTypeMarker;

    VideoCapability(String taskTypeMarker) {
        this.taskTypeMarker = taskTypeMarker;
    }

    /**
     * ComfyUI MiniMaxH3Director 节点的 task_type 前缀标记。
     */
    public String taskTypeMarker() {
        return taskTypeMarker;
    }

    /**
     * 解析能力编码，未知值返回 null 而不是抛异常，便于调用方统一报错。
     */
    public static VideoCapability parse(String code) {
        if (code == null) {
            return null;
        }
        for (VideoCapability item : values()) {
            if (item.name().equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }
}
