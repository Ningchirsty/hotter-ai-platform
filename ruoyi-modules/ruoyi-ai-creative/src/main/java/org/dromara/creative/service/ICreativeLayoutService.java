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
     * <p><b>空屏交付要显式确认</b>（内测 S21 / C9）：还有屏没有「已选定产出」时，
     * 上传等于交付一张带空白屏的长图。这条链路<b>不硬拦</b>（先交部分图是真实业务），
     * 但要求调用方明确表示"我知道缺屏，仍要交付"——{@code acknowledgeShortfall=false}
     * 时直接拒绝并列出缺哪些屏。</p>
     *
     * <p><b>尺寸只警告不拦</b>（内测 S22 / C10）：终版尺寸与默认输出规格不一致时照常收下，
     * 但把不一致写进版本备注、事件与详情（见 {@code DpDetailPageVo.finalSizeWarning}）。
     * 先让问题可见，稳定后再改为拦。</p>
     *
     * @param taskId               项目ID
     * @param file                 精修后的长图
     * @param comment              说明
     * @param acknowledgeShortfall 是否已确认"带空屏交付"
     * @return 详情页
     */
    DpDetailPageVo uploadFinal(Long taskId, MultipartFile file, String comment, boolean acknowledgeShortfall);

    /**
     * 读某个版本的渲染长图字节（页面预览走后端代理）。
     *
     * @param taskId    项目ID
     * @param versionId 版本ID
     * @return PNG/JPEG 字节
     */
    byte[] preview(Long taskId, Long versionId);

}
