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
