package org.dromara.content.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.domain.vo.CpTaskVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.domain.vo.ContentFactSyncVo;
import org.dromara.content.helper.ContentGateEngine;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 内容生产任务服务。
 *
 * @author content
 */
public interface IContentTaskService {

    /**
     * 分页查询任务。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<CpTaskVo> queryPage(ContentTaskBo bo, PageQuery pageQuery);

    /**
     * 任务详情（含附件、事实、卡片、作业、闸门结论）。
     *
     * @param taskId 任务ID
     * @return 详情
     */
    ContentTaskDetailVo getDetail(Long taskId);

    /**
     * 新建任务。
     *
     * @param bo 任务参数
     * @return 任务ID
     */
    Long create(ContentTaskBo bo);

    /**
     * 修改任务。
     *
     * @param bo 任务参数
     */
    void update(ContentTaskBo bo);

    /**
     * 删除任务（逻辑删除）。
     *
     * @param taskId 任务ID
     */
    void remove(Long taskId);

    /**
     * 上传资料附件。
     *
     * @param taskId 任务ID
     * @param dataLevel 该文件的数据等级（可空，取任务等级）
     * @param file   文件
     * @return 附件ID
     */
    Long uploadFile(Long taskId, String dataLevel, MultipartFile file);

    /**
     * 附件列表。
     *
     * @param taskId 任务ID
     * @return 附件列表
     */
    List<CpTaskFileVo> listFiles(Long taskId);

    /**
     * 触发解析（异步）。
     *
     * @param taskId 任务ID
     * @return 作业ID
     */
    Long triggerParse(Long taskId);

    /**
     * 触发预检（异步）。
     *
     * @param taskId 任务ID
     * @return 作业ID
     */
    Long triggerPrecheck(Long taskId);

    /**
     * 重算闸门并刷新任务状态。
     *
     * @param taskId 任务ID
     * @return 判定结论
     */
    ContentGateEngine.GateResult recheck(Long taskId);

    /**
     * 把任务所选产品在「产品与SKU」模块里的主数据同步为产品事实。
     *
     * <p>解决的实际问题：用户在新增任务时选中了产品，但闸门只认 {@code cp_fact_snapshot}
     * 里已确认的事实——产品主数据里有名称和 SKU 也不算数，于是「选了产品还要再录一遍」。</p>
     *
     * <p><b>边界（重要）</b>：只同步产品与SKU模块<b>确实拥有</b>的两个字段
     * （{@code product_name}、{@code sku_code}）。主体版本/颜色/数量/参数/包装版本
     * 在该模块里本就没有对应列，只能来自产品资料（解析+确认）或人工录入——
     * 这也守住了「产品事实只能来自经确认的产品资料」的红线。</p>
     *
     * <p><b>不覆盖人的判断</b>：若同字段已存在<b>不同取值</b>，本次写入降级为「待确认」，
     * 交给事实清单/互动卡由人裁定，绝不自动把主数据值确认成事实。</p>
     *
     * @param taskId 任务ID
     * @return 同步结果（写入/跳过/冲突条数与逐条说明）
     */
    ContentFactSyncVo syncProductFacts(Long taskId);

}
