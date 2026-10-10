package org.dromara.aigov.constant;

/**
 * AI 治理模块通用常量
 * <p>权限标识与菜单 SQL（{@code script/sql/aig_ai_gov_menu.sql}）中的 {@code perms} 一一对应，
 * 修改时必须同步菜单数据，否则前端按钮与后端鉴权会不一致。</p>
 *
 * @author ai-gov
 */
public interface AigConstants {

    /**
     * 平台费用币种：<b>美元（USD）</b>——全平台<b>唯一</b>的金额口径（2026-10-08 定）。
     *
     * <p><b>为什么是「统一一种币种」而不是「每行记一个币种」</b>：所有金额列
     * （调用审计的 {@code cost}、模型治理的 {@code cost_limit_amount}、任务快照的
     * {@code budget_amount}、评测用例的 {@code cost_min}/{@code cost_max}）都是纯 decimal，
     * 而且彼此要<b>互相比较</b>（本次预算 vs 该模型的单次上限）。混用币种而不换算，
     * 会让比较结果看起来正常却完全错误——那种错不报错、不抛异常，只有对账时才发现。
     * 因此口径取「平台内只有一个币种」，而不是每行带币种再到处换算。</p>
     *
     * <p><b>为什么是美元</b>：当前唯一真接的外部供应商（bluocto）就是按美元计价的——
     * 余额不足时上游回的是「剩余额度: ＄0.000000」（见 {@code AigErrorClassEnum}），
     * 此前的「人民币元」假设与实际报价币种<b>相反</b>。接入按其它币种计价的供应商时，
     * 要么在调用器里换算成美元再回填，要么另定口径；<b>不要直接回填原币金额</b>。</p>
     *
     * <p>本常量是口径的<b>唯一声明处</b>：前端在金额列/字段上以标签形式镜像同一口径
     * （形如「成本（USD）」），DDL 的列注释与运维手册 §十 亦同。
     * 运行时没有需要换汇的分支，所以它不是「配置项」而是「口径」——
     * 改它意味着改全平台金额的含义，不是一次热更新。</p>
     */
    String COST_CURRENCY = "USD";

    /**
     * 能力目录-列表
     */
    String PERM_CAPABILITY_LIST = "aig:capability:list";
    /**
     * 能力目录-查询
     */
    String PERM_CAPABILITY_QUERY = "aig:capability:query";
    /**
     * 能力目录-新增
     */
    String PERM_CAPABILITY_ADD = "aig:capability:add";
    /**
     * 能力目录-修改
     */
    String PERM_CAPABILITY_EDIT = "aig:capability:edit";
    /**
     * 能力目录-删除
     */
    String PERM_CAPABILITY_REMOVE = "aig:capability:remove";
    /**
     * 模型治理-列表
     */
    String PERM_MODEL_LIST = "aig:model:list";
    /**
     * 模型治理-查询
     */
    String PERM_MODEL_QUERY = "aig:model:query";
    /**
     * 模型治理-新增模型（登记 sai_model_config 主数据 + 首份治理属性）
     */
    String PERM_MODEL_ADD = "aig:model:add";
    /**
     * 模型治理-修改
     */
    String PERM_MODEL_EDIT = "aig:model:edit";
    /**
     * 模型治理-密钥引用查看（控制 secretRef / api_endpoint 下发）
     */
    String PERM_MODEL_SECRET = "aig:model:secret";
    /**
     * 路由策略-列表
     */
    String PERM_ROUTE_LIST = "aig:route:list";
    /**
     * 路由策略-查询
     */
    String PERM_ROUTE_QUERY = "aig:route:query";
    /**
     * 路由策略-新增
     */
    String PERM_ROUTE_ADD = "aig:route:add";
    /**
     * 路由策略-修改
     */
    String PERM_ROUTE_EDIT = "aig:route:edit";
    /**
     * 路由策略-删除
     */
    String PERM_ROUTE_REMOVE = "aig:route:remove";
    /**
     * 场景强制绑定-维护（新增/修改/删除）
     * <p>与 {@code aig:route:edit} 分开：路由策略管的是「能不能外发」这类治理口径，
     * 场景绑定只决定「在已允许的候选里先试谁」。把两者合成一个权限，等于让
     * 「改首选供应商」的人顺带获得「开外发」的能力。</p>
     */
    String PERM_ROUTE_BINDING = "aig:route:binding";
    /**
     * 调用审计-列表
     */
    String PERM_AUDIT_LIST = "aig:audit:list";
    /**
     * AI任务-列表
     */
    String PERM_TASK_LIST = "aig:task:list";
    /**
     * AI任务-详情
     */
    String PERM_TASK_QUERY = "aig:task:query";
    /**
     * AI任务-操作（人工取消/复核/手动触发调度扫描）
     * <p>与「只看」分开：这些操作会改变任务状态或触发重试与计费，
     * 而查看任务只是读。合并的话，一个只该看进度的人就能替所有人取消在跑的任务。</p>
     */
    String PERM_TASK_OPERATE = "aig:task:operate";
    /**
     * AI任务-选定交付物（人工选定候选资产）
     * <p>与 {@link #PERM_TASK_OPERATE} 分开：「运维取消/重跑」与「决定交付哪一张」
     * 是两类人做的决定，后者有业务后果且需要担责。合并的话，一个只负责重跑任务的人
     * 就能替业务方敲定最终交付物。</p>
     */
    String PERM_TASK_SELECT = "aig:task:select";

    /**
     * Agent/Skill/Package 注册中心-列表（三类对象的清单都归这一个读权限）
     */
    String PERM_AGENT_LIST = "aig:agent:list";

    /**
     * Agent/Skill/Package 注册中心-详情
     */
    String PERM_AGENT_QUERY = "aig:agent:query";

    /**
     * 发布状态推进（§5.4 的唯一写入口）
     *
     * <p><b>为什么必须单独一个权限</b>：这个接口能把版本推成 STABLE——也就是把某个 Agent 的配置
     * 真正放给业务用。它与「看清单」不是一件事：把两者合并，一个只该看进度的人就能把没验过的版本发出去。
     * 而且该接口的入参里有 {@code passedGates}（本次依据哪些门槛），服务层虽然会核对证据
     * （Manifest 扫描结论、评测账本），但「谁按下了这一步」必须能追到人。</p>
     */
    String PERM_AGENT_RELEASE = "aig:agent:release";

    /**
     * Agent 版本绑定管理（品牌/部门/测试项目范围）
     */
    String PERM_AGENT_BINDING = "aig:agent:binding";

    /**
     * Skill 清单（读）
     */
    String PERM_SKILL_LIST = "aig:skill:list";

    /**
     * Skill 详情（读）
     */
    String PERM_SKILL_QUERY = "aig:skill:query";

    /**
     * Package 清单（读）
     */
    String PERM_PACKAGE_LIST = "aig:package:list";

    /**
     * Package 详情（读）
     */
    String PERM_PACKAGE_QUERY = "aig:package:query";

    /**
     * Manifest 扫描（§6.2 五类拒绝规则）
     *
     * <p>与「看清单」分开：扫描会把结论写进版本行（{@code scan_result/scan_detail}），
     * 而该结论是「Manifest 校验」这道门槛的<b>唯一证据</b>——能改证据的人不该只是"查看者"。</p>
     */
    String PERM_PACKAGE_SCAN = "aig:package:scan";

    /**
     * 黄金用例与评测-列表（用例清单/运行清单）
     */
    String PERM_EVALUATION_LIST = "aig:evaluation:list";

    /**
     * 黄金用例与评测-详情（含「黄金用例是否通过」的证据）
     */
    String PERM_EVALUATION_QUERY = "aig:evaluation:query";

    /**
     * 定义黄金用例
     */
    String PERM_EVALUATION_DEFINE = "aig:evaluation:define";

    /**
     * 跑评测（会产生评测账本，可能产生外部调用成本）
     */
    String PERM_EVALUATION_RUN = "aig:evaluation:run";

    /**
     * 人工评测录入（平台没有该对象的执行器时，由管理员产出黄金用例证据）
     *
     * <p><b>为什么从 {@link #PERM_EVALUATION_RUN} 拆出来独立授权</b>：两者都是"产出评测结论"，
     * 但机器评测的可信度来自<b>平台的判据在同样输入上判过了</b>，人工录入的可信度只来自
     * <b>一个人签了字</b>——而它直接决定版本能不能进灰度。按 ADR-014「由管理员来评测」的口径
     * 收紧为管理角色：能跑评测不等于能录人工结论。</p>
     */
    String PERM_EVALUATION_MANUAL = "aig:evaluation:manual";

    /**
     * 人工复核评测结论（Rubric 用例的最后一道判断）
     *
     * <p>按设计 §5.4/§6.3-5，三方审批分别是业务 Owner / AI 管理员 / 平台管理员，
     * 因此复核结论不是"谁都能看的人顺手点的"——它是放行链条上的一环。</p>
     */
    String PERM_EVALUATION_REVIEW = "aig:evaluation:review";

    /**
     * Package 上传（携包体登记包与版本）
     *
     * <p>与「看清单」分开：上传会在库里落下包与版本行（含 Manifest 原文），
     * 而包体哈希是服务端算的——这是一个写动作，不是查看。</p>
     */
    String PERM_PACKAGE_UPLOAD = "aig:package:upload";

    /**
     * Package 安装（把声明的内容物建成 Agent/Skill 版本）
     *
     * <p>与上传分开：安装会真的建出 Agent/Skill 版本行——那是「这个包带进来的东西」，
     * 从此进入发布链路。上传者未必是决定「要不要装」的人。</p>
     */
    String PERM_PACKAGE_INSTALL = "aig:package:install";

    /**
     * Package 停用（把该包带进来的 Agent/Skill 版本批量下线）
     *
     * <p>与安装分开的理由与「上传 vs 安装」同源：安装是<b>带进来</b>，停用是<b>撤下去</b>。
     * 停用会把已经发布出去、正在被业务使用的版本下线，影响面与安装完全不同，
     * 因此单独授权——只给能承担这个影响面的人。</p>
     */
    String PERM_PACKAGE_DISABLE = "aig:package:disable";

    /**
     * 沙箱运行登记（把宿主侧执行器的 result.json 登记为 SANDBOX_RUN 门槛的证据）
     *
     * <p>单独授权而不是复用「看清单」：它直接决定版本能否从 VALIDATED 走到 SANDBOX_TESTED，
     * 与查看不是同一件事（口径同 {@link #PERM_EVALUATION_MANUAL}）。</p>
     */
    String PERM_SANDBOX_RECORD = "aig:sandbox:record";

    /**
     * 沙箱运行证据查看（某个版本有没有跑过、为什么不满足）
     */
    String PERM_SANDBOX_LIST = "aig:sandbox:list";


    /**
     * 人均配额清单（读）：谁有多少额度、当前用了多少
     */
    String PERM_QUOTA_LIST = "aig:quota:list";

    /**
     * 人均配额编辑（写）：新增/修改/删除某个人的额度
     *
     * <p>删除与修改共用一个权限点，因为它们改变的是同一件事——「这个人还能不能被调用」。
     * 删除的含义是回到「不限」，那同样是放宽额度，不该由只能看清单的人执行。</p>
     */
    String PERM_QUOTA_EDIT = "aig:quota:edit";

    /**
     * 调用授权清单（读）：谁申请了什么、批没批、哪张授权还在有效期内
     */
    String PERM_APPROVAL_LIST = "aig:approval:list";

    /**
     * 提交/撤回调用授权申请（写）
     *
     * <p>三个角色都给：谁都可能碰到「这个能力需要审批」的提示，
     * 申请与撤回自己那张单子不需要治理权限。</p>
     */
    String PERM_APPROVAL_APPLY = "aig:approval:apply";

    /**
     * 审批调用授权（批准/驳回）
     *
     * <p><b>与申请分开</b>：申请是"我需要"，审批是"我替你担这个责任"。
     * 只给 AI 管理员与安全（默认不给自己），且服务层强制<b>申请人不得自审</b>——
     * 靠页面藏按钮不算约束。</p>
     */
    String PERM_APPROVAL_APPROVE = "aig:approval:approve";

    /**
     * 训练草稿清单（读）：有哪些草稿、归谁、改到第几版
     */
    String PERM_STUDIO_DRAFT_LIST = "aig:studio:draft:list";

    /**
     * 训练草稿详情与修订历史（读）
     */
    String PERM_STUDIO_DRAFT_QUERY = "aig:studio:draft:query";

    /**
     * 新建训练草稿（写）
     */
    String PERM_STUDIO_DRAFT_CREATE = "aig:studio:draft:create";

    /**
     * 编辑/回滚/归档训练草稿（写）
     *
     * <p><b>为什么不按动作拆成三个权限点</b>：这三者改变的都是"草稿内容/状态"，
     * 责任边界相同；真正需要单独授权的是<b>下一步的 submit</b>（它会产生正式版本候选）——
     * 把"能改草稿"与"能把草稿变成版本"分成两件事，才是有意义的分权。</p>
     */
    String PERM_STUDIO_DRAFT_EDIT = "aig:studio:draft:edit";

    /**
     * 草稿预检（写：会落一条预检结论）
     */
    String PERM_STUDIO_DRAFT_VALIDATE = "aig:studio:draft:validate";

    /**
     * 服务令牌管理权限码的公共前缀。
     *
     * <p><b>它同时是一条安全边界</b>：签发令牌时，凡以此前缀开头的 scope 一律拒绝
     * （见 {@code AigServiceTokenServiceImpl#issue}）。机器身份若能管理令牌，
     * 就能自我提权/自我续期——这条通道必须在<b>源头不可表达</b>，
     * 而不是只靠"管理接口的 URL 机器访问不到"。</p>
     *
     * <p><b>刻意不以 {@code PERM_} 开头</b>：{@code AigPermissionSeedCoverageTest} 把
     * "名字以 {@code PERM_} 开头的静态 String" 当作权限串、要求必须在菜单脚本里种子，
     * 而前缀本身不是一个权限码（它没有对应的菜单行）。名字若图好看写成
     * {@code PERM_SERVICE_TOKEN_PREFIX}，那条守卫会误报——把常量叫成它真实的东西更省事。</p>
     */
    String SERVICE_TOKEN_PERM_PREFIX = "aig:service-token:";

    /**
     * 服务令牌清单（读）：有哪些机器身份、各自能做什么、最近谁在用
     */
    String PERM_SERVICE_TOKEN_LIST = SERVICE_TOKEN_PERM_PREFIX + "list";

    /**
     * 签发服务令牌（写）：<b>明文只在签发那一刻可见一次</b>，因此这是"发凭据"的动作，
     * 与"看清单"分开授权。
     */
    String PERM_SERVICE_TOKEN_ISSUE = SERVICE_TOKEN_PERM_PREFIX + "issue";

    /**
     * 停用服务令牌（写）：影响面是"某个正在跑的外部调用方立刻全部 401"，
     * 因此与签发分开授权。
     */
    String PERM_SERVICE_TOKEN_REVOKE = SERVICE_TOKEN_PERM_PREFIX + "revoke";

    /**
     * 系统提交者ID：任务由调度器/Agent 发起（无登录上下文）时，{@code create_by} 用它占位。
     *
     * <p><b>为什么不能留 NULL</b>：{@code aig_task.create_by} 同时是幂等唯一键
     * {@code uk_aig_task_idem (project_type, create_by, idempotency_key)} 的一段。
     * 而 NULL 在唯一键里<b>不被约束</b>（MySQL/MariaDB 的唯一键不约束 NULL），
     * 在等值查询里也永不成立（{@code create_by = NULL} 恒为 UNKNOWN）——
     * 两处叠加的结果是「同一个 Agent 重复提交同一个幂等键会建出两个任务」，
     * 于是变成两次真实模型调用、两次计费，而每一步单看都很正常。
     * 实测（真实 MariaDB）：无登录上下文下重复提交同一键 → 库里两行。</p>
     *
     * <p>用一个稳定的非空哨兵值把「系统」变成一个真实的提交者身份，上述两处才都成立。
     * 取值 0：RuoYi 的用户ID 从 1 起（admin=1），0 不会被真实用户占用；
     * 这个位只用于<b>幂等作用域</b>，事件/审计里的 {@code actor_id}/{@code caller_id}
     * 在系统触发时仍如实写 NULL——那里要表达的是「没有人操作」，与「谁提交的」不是同一件事。</p>
     */
    Long SYSTEM_SUBMITTER_ID = 0L;

    /**
     * 阶段1 首个能力编码：人才能力匹配
     */
    String CAP_TALENT_MATCH = "talent_match";

    /**
     * 审计摘要最大长度
     */
    int AUDIT_SUMMARY_MAX = 500;

    /**
     * 模型标识的合法形态（新增与编辑共用，避免两处漂移）。
     *
     * <p>必须允许斜杠、冒号与开头的波浪号：主流聚合网关（如 OpenRouter）的模型 ID 是
     * {@code vendor/model}，免费档带 {@code :free}，浮动别名以 {@code ~} 开头。
     * 实测其公开目录 443 个 ID 全部含斜杠，收窄会整类挡掉。</p>
     */
    String MODEL_KEY_PATTERN = "^[A-Za-z0-9~][A-Za-z0-9._:/-]*$";

    /**
     * 模型标识校验失败时的提示（与 {@link #MODEL_KEY_PATTERN} 配套）。
     */
    String MODEL_KEY_PATTERN_MESSAGE =
        "模型标识只能由字母、数字、点、下划线、中划线、冒号、斜杠组成，且以字母、数字或波浪号开头"
            + "（如 glm-5.1、openrouter/free、deepseek/deepseek-r1:free）";

}
