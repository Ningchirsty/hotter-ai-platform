package org.dromara.aigov.workspace.launch.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条启动问题（码 + 可直接展示的文案）。
 *
 * <h3>为什么码与文案一起返回，而不是只给码</h3>
 * <p>如果只返回码，前端就得再写一份"码 → 中文"的映射表。于是同一句提示有两个来源：
 * 后端改了措辞（例如把"这个岗位还没有对你开放"改成更具体的说明），前端仍显示老文案——
 * 而这类不一致没人会报 bug，只会让人觉得界面和提示对不上。
 * 文案是**后端已有的知识**（{@code AigLaunchErrorEnum}），就由后端给。</p>
 *
 * <p>码仍然保留：前端要按码分支（例如缺必填输入时高亮表单、票据过期时提示重新发起），
 * 不能靠匹配文案。</p>
 *
 * @author ai-gov
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AigLaunchProblemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码（对外稳定标识，前端按它分支）
     */
    private String code;

    /**
     * 可直接展示给员工的说明
     */
    private String message;

}
