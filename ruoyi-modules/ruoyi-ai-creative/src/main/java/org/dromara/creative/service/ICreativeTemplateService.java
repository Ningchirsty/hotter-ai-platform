package org.dromara.creative.service;

import org.dromara.creative.domain.vo.DpLayoutTemplateVo;

import java.util.List;

/**
 * 视觉模板服务（R3.2 模板发布门）。
 *
 * <p>与图像工作流同一条纪律：<b>只有 PUBLISHED 的模板能被排版选用</b>，而且每次选用前
 * 都要与渲染服务实时对账校验和——模板文件被换过（哪怕是同编码同版本）就必须拦下，
 * 否则「同一版本渲染出不同结果」会成为一笔糊涂账。</p>
 *
 * @author creative
 */
public interface ICreativeTemplateService {

    /**
     * 与渲染服务对账：登记尚未登记的模板；发现已登记模板的校验和变了就退回草稿。
     *
     * @return 同步后的模板列表
     */
    List<DpLayoutTemplateVo> sync();

    /**
     * 模板列表（含渲染服务实时校验和与是否一致）。
     *
     * @return 模板列表
     */
    List<DpLayoutTemplateVo> list();

    /**
     * 发布模板（要求校验和与渲染服务一致）。
     *
     * @param templateId 模板ID
     * @return 发布后的模板
     */
    DpLayoutTemplateVo publish(Long templateId);

    /**
     * 停用/退役模板。
     *
     * @param templateId 模板ID
     * @return 退役后的模板
     */
    DpLayoutTemplateVo retire(Long templateId);

    /**
     * 取可用于渲染的模板（必须 PUBLISHED 且校验和与渲染服务一致）。
     *
     * @param templateCode    模板编码
     * @param templateVersion 模板版本
     * @return 模板记录
     */
    DpLayoutTemplateVo requirePublished(String templateCode, String templateVersion);

}
