package org.dromara.creative.service;

import org.dromara.creative.domain.vo.DeliveryVo;

/**
 * 交付渲染服务（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * <p><b>它是"渲染"的统一入口</b>：调用方不需要知道这个交付类型是长图还是多图，
 * 说一句"渲染并留痕"，渲染器由 {@code dp_delivery_type.render_mode} 解析出来
 * （映射与 fail-closed 在 {@code CreativeRendererHub}）。</p>
 *
 * <p><b>产物不重复存字节</b>：清单落库，交付包（ZIP）在下载时现拼——见
 * {@code CreativeDeliveryManifest#zip}。这样"交付一次"不会让对象存储多出一份拷贝。</p>
 *
 * @author creative
 */
public interface ICreativeDeliveryService {

    /**
     * 按交付类型自动选渲染器并执行一次交付渲染。
     *
     * @param taskId 项目ID
     * @return 交付视图（含本次产物清单与全部历史版本）
     */
    DeliveryVo render(Long taskId);

    /**
     * 指定渲染器执行（用于验收与排障：验证"不匹配的渲染器会拒绝"）。
     *
     * @param taskId       项目ID
     * @param rendererCode 渲染器编码（可空＝按交付类型自动选）
     * @return 交付视图
     */
    DeliveryVo renderWith(Long taskId, String rendererCode);

    /**
     * 交付视图（只读）：历史版本 + 渲染器能力清单。
     *
     * @param taskId 项目ID
     * @return 交付视图
     */
    DeliveryVo view(Long taskId);

    /**
     * 确认交付（V0.2 R51）：把某一版交付产物定为最终交付物，项目置为「已完成」。
     *
     * <p><b>为什么需要它</b>：多图交付类型（主图、海报）的交付物是一组图，没有"长图精修版"可上传，
     * 于是原先唯一能推到 {@code COMPLETED} 的动作（{@code 上传精修最终版}）对它们永远不可用——
     * 项目会卡在「终审」走不到头（R50 真机干跑实测：阶段停在 FINAL_REVIEW）。</p>
     *
     * <p><b>只对多图交付开放</b>（判据取配置 {@code dp_delivery_type.render_mode}，不写死交付类型）：
     * 长图类的"交付完成"是设计师改过图之后上传精修版，不能由一次点击代替。两条路都指向
     * {@code COMPLETED}，但含义不同，混在一起会让人以为"点一下就交了精修版"。</p>
     *
     * <p><b>空屏也要显式确认</b>（内测 S21 / C9-c）：多图交付同样可能"7 屏只出了 2 屏"。
     * 内测时只给长图那条路加了确认闸，这条路上同一类缺陷原样存在。判据与措辞与长图共用
     * {@link org.dromara.creative.helper.CreativeScreenCoverage}。
     * 只确认、不硬拦——"先交做好的部分"是真实业务，要拦的是"没人注意到缺屏"。</p>
     *
     * @param taskId               项目ID
     * @param versionId            交付产物版本ID（可空＝当前最新一版）
     * @param comment              说明（可空；进事件留痕）
     * @param acknowledgeShortfall 是否已确认"带空屏交付"
     * @return 交付视图
     */
    DeliveryVo confirm(Long taskId, Long versionId, String comment, boolean acknowledgeShortfall);

    /**
     * 取某版本的交付产物字节。
     *
     * <p>单张（长图）直接返回图片；多图交付返回 ZIP（第一项是 manifest.json）。</p>
     *
     * @param taskId    项目ID
     * @param versionId 清单ID
     * @return 字节与内容类型
     */
    Download download(Long taskId, Long versionId);

    /**
     * 下载体。
     *
     * @param bytes       字节
     * @param contentType 内容类型
     * @param fileName    建议文件名
     */
    record Download(byte[] bytes, String contentType, String fileName) {
    }
}
