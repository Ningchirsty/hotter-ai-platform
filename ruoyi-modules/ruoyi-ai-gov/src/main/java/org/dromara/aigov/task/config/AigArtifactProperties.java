package org.dromara.aigov.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 制品账本策略（{@code aigov.artifact.*}）。
 *
 * <p><b>为什么这类校验要可配置、而不是写死在代码里</b>：MIME 允许清单与大小上限是
 * <b>策略</b>而不是事实——业务域接入新模型（如开始出 AVIF/WebP 动图）时，
 * 该改的是配置，不该改代码再发一版平台。写死以后，生产方的合法产出会被判成
 * {@code ARTIFACT_INVALID}，而那条路径是「转人工」，于是每次扩展类型都要惊动运维。</p>
 *
 * <p><b>清单为空 = 全部拒绝</b>（不是「全部允许」）：空清单最可能的成因是配置写错
 * （比如 YAML 里键名拼错、被覆盖成空列表）。把它当「不限制」，会让一次配置事故变成
 * 「任何文件都能登记为制品」——这类默认值必须选失败的那一边。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.artifact")
public class AigArtifactProperties {

    /**
     * 单个制品大小上限（字节），默认 256MB。
     *
     * <p>取值依据：现有最大产出是渲染成品图与短视频，256MB 已远超实际；
     * 上限的作用是拦住「把整个数据集当制品传进来」这类误用。</p>
     */
    private long maxSizeBytes = 256L * 1024 * 1024;

    /**
     * 允许的 MIME 类型清单（大小写不敏感）。
     *
     * <p>支持 {@code image/*} 这类<b>族通配</b>：只按 {@code type/} 前缀匹配，
     * 不支持 {@code image/png+json} 之类的子类型通配（那种写法在这里没有语义，
     * 静默当成普通字符串反而会让人以为它生效了）。</p>
     *
     * <p>默认清单刻意<b>不含 SVG</b>：SVG 是脚本载体，登记为制品后若被当成图片
     * 直接渲染，等于把 XSS 通道交给生产方。要收就显式加，并说明谁来承担这个风险。</p>
     */
    private List<String> allowedMimeTypes = new ArrayList<>(Arrays.asList(
        "image/png",
        "image/jpeg",
        "image/webp",
        "image/avif",
        "image/gif",
        "video/mp4",
        "application/json",
        "application/pdf",
        "text/plain",
        "text/markdown"));

}
