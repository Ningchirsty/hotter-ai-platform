package org.dromara.aigov.agent.helper;

/**
 * 沙箱产物字节的存取（ADR-016）。
 *
 * <p><b>平台不读宿主归档目录</b>（ADR-015 的边界：后端无 docker CLI/socket，也不分享宿主挂载）：
 * 产物字节由运维导出后经平台接口交上来，平台校验后存<b>对象存储</b>，下载一律走平台鉴权。</p>
 *
 * <p><b>对象键由服务端按 sha256 生成</b>：{@link #keyOf} 只接受 runId 与摘要，
 * 根本不接收路径——"用上传方的 path 拼键"这条路径穿越在这份接口上就不存在，
 * 而不是靠"记得把 path 洗干净"。</p>
 *
 * @author ai-gov
 */
public interface IAigSandboxArtifactStore {

    /**
     * 由服务端生成对象键。
     *
     * @param sandboxRunId 沙箱运行记录ID
     * @param sha256       服务端实算的摘要（小写十六进制）
     * @return 对象键
     */
    String keyOf(Long sandboxRunId, String sha256);

    /**
     * 写入字节（失败必须响亮：写成"记日志继续"会得到"账本说已入库、对象存储里什么都没有"）。
     *
     * @param key  对象键
     * @param body 字节（可为空数组：空产物是合法的声明）
     */
    void put(String key, byte[] body);

    /**
     * 读取字节。
     *
     * @param key 对象键
     * @return 字节
     */
    byte[] get(String key);

}
