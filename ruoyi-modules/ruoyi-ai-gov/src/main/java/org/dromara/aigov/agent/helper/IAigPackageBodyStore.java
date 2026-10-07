package org.dromara.aigov.agent.helper;

/**
 * Package 包体留存（对象存储的窄接口）。
 *
 * <p><b>为什么抽一层接口而不是在服务里直接调 {@code OssFactory}</b>：
 * {@code OssFactory.instance()} 是静态方法，服务层若直接调它，
 * 「留存成功 / 留存失败 / 没开开关」这三条路径就只能靠真对象存储来测；
 * 抽成接口后，服务层的判断（开关、键、失败即整笔拒绝）可以用替身完整覆盖，
 * 真 OSS 的接线只集中在一个实现类里。</p>
 *
 * <p><b>两条安全约定</b>（与内容域的 {@code ContentOssHelper} 同口径）：</p>
 * <ul>
 *     <li>对象写在私有前缀下，且<b>不登记 {@code sys_oss}</b>——否则任何持有
 *     {@code system:oss:download} 的账号都能绕过治理模块的授权直接取到第三方包体；</li>
 *     <li>对象键不含凭据、不带查询串；日志与错误里不回显对象内容。</li>
 * </ul>
 *
 * @author ai-gov
 */
public interface IAigPackageBodyStore {

    /**
     * 构造包体对象键。
     *
     * <p>键里带包编码、版本号与包体哈希前缀：<b>不依赖入库后才有自增ID</b>，
     * 因此可以在写库之前就把对象放好（放失败就不落库，不产生半截状态）；
     * 同内容重复上传也落在同一个键上，覆盖是幂等的。</p>
     *
     * @param packageCode 包编码
     * @param version     版本号
     * @param bodySha256  包体 SHA-256（十六进制）
     * @return 对象键
     */
    String buildKey(String packageCode, String version, String bodySha256);

    /**
     * 写入包体。
     *
     * @param key  对象键
     * @param body 包体字节
     * @throws org.dromara.common.core.exception.ServiceException 写入失败（必须让调用方看见）
     */
    void put(String key, byte[] body);

    /**
     * 读回包体（复核用：可重新核对哈希，确认留存的对象与当初登记的是同一份）。
     *
     * @param key 对象键
     * @return 包体字节
     * @throws org.dromara.common.core.exception.ServiceException 读取失败
     */
    byte[] get(String key);

}
