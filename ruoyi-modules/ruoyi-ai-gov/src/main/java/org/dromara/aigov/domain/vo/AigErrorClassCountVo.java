package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一个错误分类在灰度窗口内的出现次数（{@code aig_invocation_audit} 的 error_class 分组计数）。
 *
 * <p>只为「按错误分类汇总」这一种查询服务：灰度的「无严重错误」判据需要知道
 * <b>哪些</b>严重错误出现了多少次——只给一个总数，运维拿到「有 1 个严重错误」之后
 * 还得自己去翻日志才知道是策略拒绝还是鉴权失败。</p>
 *
 * <p>字段名与 SQL 别名（{@code error_class} / {@code error_count}）按下划线转驼峰对应，
 * 因此不需要 {@code @AutoMapper}：它不由 MapStruct 转换生成，只由 MyBatis 直接映射。</p>
 *
 * @author ai-gov
 */
@Data
public class AigErrorClassCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误分类编码（{@code AigErrorClassEnum.code}）
     */
    private String errorClass;

    /**
     * 出现次数
     */
    private Long errorCount;

}
