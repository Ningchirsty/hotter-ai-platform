package org.dromara.aigov.task.helper;

/**
 * 任务操作者解析（隔离 {@code LoginHelper} 的静态依赖）。
 *
 * <p><b>为什么要这一层，而不是直接调 {@code LoginHelper.getUserId()}</b>：
 * 推进任务状态的不只有 HTTP 请求——调度器重试、Provider 回调、超时扫描器
 * 都在<b>没有登录上下文</b>的线程里跑。「谁在操作」因此必须允许为空：
 * 系统触发的事件 actor 为空是<b>正常且正确</b>的语义，不是异常。
 * 做成可替换的接口，测试才能确定性地构造「无登录上下文」这一情形。</p>
 *
 * <p><b>更正一条曾被写错的结论</b>：本类早先的注释写着
 * 「{@code LoginHelper.getUserId()} 在无登录上下文时会直接抛异常」。实测并非如此——
 * {@code LoginHelper.getUserId()} = {@code Convert.toLong(getExtra(USER_KEY))}，
 * 而 {@code getExtra} 自己 {@code catch (Exception)} 并返回 null，
 * 所以在无登录上下文的进程里它是<b>安静地返回 null，不抛异常</b>
 * （实测探针输出 {@code returned=null}）。也就是说「不抛异常」这半边由
 * {@code LoginHelper} 自己保证；本层存在的理由是可替换与语义显式，
 * 而不是替它兜异常。{@link LoginTaskActorProvider} 里那层 try/catch 因此是防御性的冗余，
 * 保留是为了不依赖第三方实现细节，而不是因为它真的会被触发。</p>
 *
 * <p><b>但 null 有一个真实后果</b>：它曾直接落进 {@code aig_task.create_by}，
 * 而该列同时是幂等唯一键 {@code (project_type, create_by, idempotency_key)} 的一段。
 * NULL 在唯一键里不被约束、在等值查询里永不成立，于是系统触发的创建会静默失去幂等
 * （实测：同一幂等键在库里落下两行）。该问题在 {@code AigTaskServiceImpl#create}
 * 以「系统提交者哨兵值」解决，本接口的语义（取不到返回 null）保持不变。</p>
 *
 * @author ai-gov
 */
public interface AigTaskActorProvider {

    /**
     * 当前操作者ID。
     *
     * @return 用户ID；无登录上下文（系统触发）时返回 null
     */
    Long currentUserId();

    /**
     * 当前操作者名称。
     *
     * @return 账号名；无登录上下文时返回 null
     */
    String currentUserName();

}
