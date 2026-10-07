package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Package 链路行为配置。
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.package")
public class AigPackageProperties {

    /**
     * 是否把上传的包体留存在对象存储（对象键写进 {@code aig_package_version.body_ref}）。
     *
     * <p><b>默认 false（不存），这是刻意的</b>，取舍与 {@link AigRouteProperties#isRequireModelTags()}
     * 同一类：把一个新的「必须依赖外部设施」的动作做成默认开启，会让一次升级在他人的环境里
     * 静默改变既有行为——此处后果尤其直接：**目标环境若没有一个可用（已启用、凭据真实）的
     * OSS 配置，上传会立刻从「能用」变成「报错」**，而这是发布链路的第一步。</p>
     *
     * <p>默认关不等于「不生效也不报错」：无论开关状态，上传响应里的
     * {@code bodyStored} / {@code bodyRef} 都会如实说明本次包体有没有被留存，
     * 页面也会显示出来。要留存包体，把本开关置 true，并确认
     * {@code sys_oss_config} 有启用中的配置。</p>
     *
     * <p>开启后，留存失败<b>不会静默跳过</b>：整笔上传失败、不落任何行
     * （宁可不上传，也不留下「以为存了其实没存」的包）。</p>
     */
    private boolean storeBody = false;

}
