package org.dromara.creative.service;

import org.dromara.creative.domain.vo.DpDetailPageVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 详情页排版服务（R3.3）。
 *
 * <p>链路：已锁定分镜 + 每屏已选定产出 → 组装排版数据 → 交给独立渲染服务出 750×N 长图 →
 * 长图登记为任务附件、版本落库（可回溯）→ 人工终审 → 精修后上传 V1.0。</p>
 *
 * <p><b>不做的事</b>：不自己画图（渲染交给渲染服务）、不编造缺失素材（缺哪屏就在图上写明缺），
 * 不自动通过终审（终审永远是人）。</p>
 *
 * @author creative
 */
public interface ICreativeLayoutService {

    /**
     * 渲染一版机排版（V0.8）。
     *
     * @param taskId 项目ID
     * @return 详情页（含版本列表与缺图屏提示）
     */
    DpDetailPageVo render(Long taskId);

    /**
     * 详情页与版本列表。
     *
     * @param taskId 项目ID
     * @return 详情页；尚未渲染过时返回 currentVersion=0 的空壳
     */
    DpDetailPageVo detail(Long taskId);

    /**
     * 终审：通过或打回某个版本。
     *
     * @param taskId    项目ID
     * @param versionId 版本ID
     * @param approve   是否通过
     * @param comment   审核意见
     * @return 更新后的版本
     */
    DpDetailPageVo.DpDetailPageVersionVo review(Long taskId, Long versionId, boolean approve, String comment);

    /**
     * 上传人工精修后的最终版（V1.0）。
     *
     * @param taskId  项目ID
     * @param file    精修后的长图
     * @param comment 说明
     * @return 详情页
     */
    DpDetailPageVo uploadFinal(Long taskId, MultipartFile file, String comment);

    /**
     * 读某个版本的渲染长图字节（页面预览走后端代理）。
     *
     * @param taskId    项目ID
     * @param versionId 版本ID
     * @return PNG/JPEG 字节
     */
    byte[] preview(Long taskId, Long versionId);

}
