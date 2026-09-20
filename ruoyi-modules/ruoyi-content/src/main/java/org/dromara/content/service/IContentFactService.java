package org.dromara.content.service;

import org.dromara.content.domain.vo.CpFactSnapshotVo;

import java.util.List;

/**
 * 产品事实快照服务：候选值的查看与人工确认。
 *
 * <p><b>为什么需要这个服务</b>：预检只为「冲突」与「缺失」生成互动卡（设计文档 §3.1
 * 要求「只有缺失、冲突、低可信或需要业务判断的内容，才生成互动任务卡」）。
 * 但闸门只认已确认事实，于是**只有一个候选值的字段既没有卡、也无法被确认**，
 * 任务就会永远停在「待确认」。本服务补上这条路径：无争议项由人一键确认，
 * 有争议项仍走互动卡逐条裁定。</p>
 *
 * <p><b>红线</b>：所有确认动作都由人发起（带登录身份），不存在任何自动确认分支。</p>
 *
 * @author content
 */
public interface IContentFactService {

    /**
     * 按任务列出全部事实行（含待确认候选与已确认事实）。
     *
     * @param taskId 任务ID
     * @return 事实行
     */
    List<CpFactSnapshotVo> list(Long taskId);

    /**
     * 确认单条候选值为产品事实。
     *
     * @param snapshotId 快照行ID
     */
    void confirm(Long snapshotId);

    /**
     * 否决单条候选值。
     *
     * @param snapshotId 快照行ID
     */
    void reject(Long snapshotId);

    /**
     * 一键确认「无争议项」：同一字段只有一个待确认候选时予以确认；
     * 存在多个不同取值的字段（冲突）一律跳过，仍由互动卡逐条裁定。
     *
     * @param taskId 任务ID
     * @return 本次确认的条数
     */
    int confirmUnambiguous(Long taskId);

    /**
     * 手工录入一条事实（无候选值但有闸门要求时使用）。
     *
     * @param taskId    任务ID
     * @param fieldCode 字段编码
     * @param value     值
     * @param remark    说明
     * @return 新增快照行ID
     */
    Long addManual(Long taskId, String fieldCode, String value, String remark);

}
