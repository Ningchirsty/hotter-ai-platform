package org.dromara.aigov.tool.service;

import org.dromara.aigov.tool.domain.AigToolDefinitionVersion;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionBo;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionVersionBo;
import org.dromara.aigov.tool.domain.vo.AigToolDefinitionVersionVo;
import org.dromara.aigov.tool.domain.vo.AigToolDetailVo;
import org.dromara.aigov.tool.exec.AigToolCallRequest;
import org.dromara.aigov.tool.exec.AigToolCallResult;

import java.util.List;

/**
 * 工具注册与执行服务（设计 §6.5、§7.5）。
 *
 * <p><b>登记与可执行分开</b>：登记工具（定义 + 版本契约）不等于它能跑；只有注册了对应执行器的版本
 * 才可执行，否则一律按"已登记、待接入"拒绝（设计 §4.1）。</p>
 *
 * @author ai-gov
 */
public interface IAigToolRegistryService {

    /**
     * 新建工具定义。
     *
     * @param bo      入参
     * @param actorId 操作用户
     * @return 工具ID
     */
    Long createDefinition(AigToolDefinitionBo bo, Long actorId);

    /**
     * 为已登记的工具新增一个版本（执行契约）。
     *
     * @param bo 入参
     * @return 版本ID
     */
    Long addVersion(AigToolDefinitionVersionBo bo);

    /**
     * 取工具版本。
     *
     * @param toolCode 工具编码
     * @param version  版本号
     * @return 版本；不存在抛错
     */
    AigToolDefinitionVersion getVersion(String toolCode, String version);

    /**
     * 列出可绑定工具（含"是否已接入执行器"与"是否需人工确认"）。
     *
     * @return 工具版本清单
     */
    List<AigToolDefinitionVersionVo> listVersions();

    /**
     * 取工具契约详情（设计 §6.5：用途、输入输出、数据范围、责任人、接入状态）。
     *
     * <p>列表视图刻意不带契约长文本（{@code inputSchema/outputSchema}），
     * "看一眼有哪些工具"不该等于把每个版本的契约全文都拉一遍。</p>
     *
     * @param toolCode 工具编码
     * @param version  版本号
     * @return 契约详情；工具或版本不存在/已停用时抛错
     */
    AigToolDetailVo getVersionDetail(String toolCode, String version);

    /**
     * 执行一次工具调用（幂等；写操作需人工确认）。
     *
     * @param request 调用请求
     * @return 结果
     */
    AigToolCallResult execute(AigToolCallRequest request);

}
