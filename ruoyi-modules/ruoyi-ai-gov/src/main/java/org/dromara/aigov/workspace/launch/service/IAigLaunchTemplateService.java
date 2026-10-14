package org.dromara.aigov.workspace.launch.service;

import org.dromara.aigov.workspace.launch.domain.AigLaunchTemplate;
import org.dromara.aigov.workspace.launch.domain.bo.AigLaunchRequestBo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchTemplateVo;

/**
 * 启动任务模板服务（设计 D11、§6.8）。
 *
 * <p>它把"员工只填业务输入、执行字段由服务端推导"落成一条可复用的解析路径：
 * prepare 与 commit 都在解析卡片之后调用 {@link #apply}，因此两次看到的执行字段一致
 * （提交时不会因为字段来源不同而改变请求摘要）。</p>
 *
 * @author ai-gov
 */
public interface IAigLaunchTemplateService {

    /**
     * 解析该卡片适用的模板（精确 {@code action_code} 优先，其次该岗位的默认模板）。
     *
     * @param roleCode   岗位编码
     * @param actionCode 卡片编码
     * @return 模板；未配置返回 null
     */
    AigLaunchTemplate resolve(String roleCode, String actionCode);

    /**
     * 把模板推导出的执行字段写入请求（就地修改）。
     *
     * <p><b>服务端结论优先</b>：数据等级一律以模板为准——"不允许放宽"在这里体现为
     * 客户端传什么都不影响最终值。没有模板时不做任何修改（向后兼容）。</p>
     *
     * @param bo 启动请求
     * @return 套用了模板返回 true；无模板返回 false
     */
    boolean apply(AigLaunchRequestBo bo);

    /**
     * 取模板视图（供前端渲染业务表单）；未配置返回 null。
     *
     * @param roleCode   岗位编码
     * @param actionCode 卡片编码
     * @return 视图或 null
     */
    AigLaunchTemplateVo describe(String roleCode, String actionCode);

}
