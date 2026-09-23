package org.dromara.content.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 产品主数据 → 产品事实 的同步结果。
 *
 * <p>把「同步了几条、跳过了几条、有几条因与既有取值冲突而未自动确认」如实回报。
 * 只回一个成功/失败会让「为什么闸门没动」再次变成猜谜。</p>
 *
 * @author content
 */
@Data
public class ContentFactSyncVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本次写入为「已确认事实」的条数
     */
    private int synced;

    /**
     * 已有同值已确认事实、无需处理的条数
     */
    private int skipped;

    /**
     * 因与既有取值不同而<b>未自动确认</b>、改以待确认落库（构成冲突候选）的条数
     */
    private int conflicts;

    /**
     * 逐条说明（给用户看懂发生了什么）
     */
    private List<String> messages = new ArrayList<>();

}
