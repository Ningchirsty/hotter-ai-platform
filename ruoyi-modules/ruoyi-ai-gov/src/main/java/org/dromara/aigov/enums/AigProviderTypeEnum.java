package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Provider 能力类型（设计文档 §4.1 的 7 类）。
 *
 * <p><b>它解决什么问题</b>：此前调用器只按「部署类型」认领（{@code LOCAL} / {@code GROUP} /
 * {@code EXTERNAL_API}…），而每种部署类型只能有一个认领者——否则挑选结果取决于
 * Spring Bean 装配顺序，是不确定的。这条不变式在「一个部署类型下只有一种能力」时够用，
 * 但外部聚合网关打破了它：同一个 {@code EXTERNAL_API} 下既有对话模型
 * （{@code /v1/chat/completions}）又有图像模型（{@code /v1/images/generations}），
 * 端点与请求体都不一样。</p>
 *
 * <p>因此在部署类型之外补一层 <b>模型类型</b>维度的派发：调用器用
 * {@code supportsModelType(modelType)} 声明自己认领哪些模型类型，
 * 路由据此挑选。这样「每种部署类型只有一个认领者」放宽为
 * 「每种 <b>(部署类型, 模型类型)</b> 只有一个认领者」，既消除不确定性，
 * 又能容纳同一网关下的多种能力。</p>
 *
 * <p>本枚举用于<b>诊断与展示</b>（写入决策说明，便于排障时一眼看出"这次走的是哪类能力"）；
 * 真正的派发依据是 {@code supportsModelType}，因为那才是数据库里能表达的事实
 * （{@code sai_model_config.model_type}）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigProviderTypeEnum {

    /**
     * 文本（对话补全）
     */
    TEXT("TEXT", "文本"),
    /**
     * 视觉理解（看图出结构化结论）
     */
    VISION("VISION", "视觉理解"),
    /**
     * 图像生成（文生图 / 图生图）
     */
    IMAGE("IMAGE", "图像生成"),
    /**
     * 视频生成
     */
    VIDEO("VIDEO", "视频生成"),
    /**
     * 企业设计平台（本平台自身部署时即 self 适配器）
     */
    DESIGN("DESIGN", "设计平台"),
    /**
     * 本地 ComfyUI 工作流
     */
    COMFYUI("COMFYUI", "ComfyUI 工作流"),
    /**
     * 决策（Choice / Score / Noul）
     */
    DECISION("DECISION", "决策"),
    /**
     * 未声明：未覆写 {@code providerType()} 的既有调用器走这里
     */
    UNKNOWN("UNKNOWN", "未声明");

    /**
     * 编码（入库与日志口径）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigProviderTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigProviderTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
