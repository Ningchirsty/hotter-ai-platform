package org.dromara.content.domain.vo;

import lombok.Data;
import org.dromara.content.helper.ContentGateEngine;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 内容任务详情视图对象（任务 + 附件 + 事实 + 卡片 + 作业 + 闸门结论）。
 *
 * <p>一次给全，避免前端为一个详情页打五六个接口——设计文档 §18.1 把
 * 「因系统增加录入工作而产生的绕过率」列为验收指标，交互成本本身是被考核的。</p>
 *
 * @author content
 */
@Data
public class ContentTaskDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务主体
     */
    private CpTaskVo task;

    /**
     * 附件列表
     */
    private List<CpTaskFileVo> files = new ArrayList<>();

    /**
     * 事实快照（含待确认候选与已确认事实）
     */
    private List<CpFactSnapshotVo> facts = new ArrayList<>();

    /**
     * 互动确认卡
     */
    private List<CpInteractionCardVo> cards = new ArrayList<>();

    /**
     * 异步作业
     */
    private List<CpAsyncJobVo> jobs = new ArrayList<>();

    /**
     * 当前闸门判定结论（含未满足的强制项/条件项清单）
     */
    private ContentGateEngine.GateResult gate;

    /**
     * 最新开工包（如有）
     */
    private CpWorkPackageVo workPackage;

}
