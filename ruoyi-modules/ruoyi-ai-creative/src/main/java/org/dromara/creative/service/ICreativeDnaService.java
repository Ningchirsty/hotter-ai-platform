package org.dromara.creative.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.creative.domain.bo.CreativeDnaBo;
import org.dromara.creative.domain.vo.DpVisualDnaVo;
import org.dromara.creative.helper.DnaPromptBuilder;

import java.util.List;

/**
 * Visual DNA 服务。
 *
 * <p>职责边界：<b>只负责视觉基因本身</b>（生成、编辑、版本、锁定、派生提示词）。
 * 事实从内容协同读、阶段与事件写进 dp_stage_event、出图交给图像内核——三条边界都不越。</p>
 *
 * @author creative
 */
public interface ICreativeDnaService {

    /**
     * 生成一版新的视觉基因（版本递增）。
     *
     * <p>输入是「已确认的产品事实 + 参考图 + 明确标注的默认值」；模型可用时用于补全，
     * 不可用时来源如实标为 FACTS，绝不把默认值包装成模型结论。</p>
     *
     * @param taskId 项目ID
     * @return 新版本
     */
    DpVisualDnaVo generate(Long taskId);

    /**
     * 最新一版基因。
     *
     * @param taskId 项目ID
     * @return 最新版本；从未生成过返回 null
     */
    DpVisualDnaVo latest(Long taskId);

    /**
     * 版本列表（倒序）。
     *
     * @param taskId 项目ID
     * @return 版本列表
     */
    List<DpVisualDnaVo> versions(Long taskId);

    /**
     * 保存编辑。
     *
     * <p>草稿/待审版本原地修改；<b>已锁定版本不可改</b>，保存会自动新建版本（v+1）并保留证据链。</p>
     *
     * @param taskId 项目ID
     * @param bo     编辑内容
     * @return 保存后的版本
     */
    DpVisualDnaVo save(Long taskId, CreativeDnaBo bo);

    /**
     * 锁定指定版本（默认最新一版）。
     *
     * <p>锁定前必须通过自洽校验；校验不通过时抛业务异常并列出问题——
     * 带着错误基因锁定的代价是后面所有分镜与出图都跟着错。</p>
     *
     * @param taskId 项目ID
     * @param dnaId  版本ID（可空＝最新一版）
     * @return 锁定后的版本
     */
    DpVisualDnaVo lock(Long taskId, Long dnaId);

    /**
     * 当前生效的基因：优先已锁定版本，其次最新版本。
     *
     * @param taskId 项目ID
     * @return 基因 json；从未生成过返回 null
     */
    ObjectNode activeDna(Long taskId);

    /**
     * 当前生效基因的版本ID（出图留痕用）。
     *
     * @param taskId 项目ID
     * @return 版本ID；无基因返回 null
     */
    Long activeDnaId(Long taskId);

    /**
     * 按当前生效基因派生提示词（预填到页面，用户可改）。
     *
     * @param taskId     项目ID
     * @param screenHint 画面用途提示（如「HERO 主图」）
     * @return 派生的提示词
     */
    DnaPromptBuilder.Prompt promptPreview(Long taskId, String screenHint);

}
