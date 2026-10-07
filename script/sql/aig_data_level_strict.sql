-- ----------------------------------------------------------------------------
-- AI 数据等级：新增第 4 级 STRICT（严格级）
--
-- 背景（设计文档 §4.3 与仓库既有口径对齐）：
--   设计文档写的是 PUBLIC / INTERNAL / SENSITIVE / STRICT，仓库既有的是
--   PUBLIC / INTERNAL / RESTRICTED。经确认的映射是：
--       SENSITIVE  -> RESTRICTED（沿用既有值，语义已覆盖「个人信息、敏感资料」）
--       STRICT     -> 新增第 4 级（HR、证件、客户名单等「任何情况都不外发」的数据）
--
-- 为什么必须新增一级而不是合并进 RESTRICTED：
--   RESTRICTED 仍可由路由策略显式放行外发（aig_route_policy.allow_external='Y'）；
--   而 STRICT 要求「任何策略都不允许外发」。两者是不同强度的约束，
--   合并后无法表达「这条策略就算配成 Y 也不能放行」。
--   代码侧对应 AigDataLevelEnum.STRICT + externalForbidden()，
--   在 AigRouteServiceImpl 里硬置 allowExternal=false（策略写错也拦得住）。
--
-- 兼容性：既有 5 张表（aig_capability / aig_model_governance / aig_route_policy /
--   aig_invocation_audit / cp_task）的 data_level 列均为 varchar，**无需回填**，
--   旧值 PUBLIC/INTERNAL/RESTRICTED 语义不变。
--
-- 幂等：全部 insert ignore / 条件 update，可安全重复执行。
-- ----------------------------------------------------------------------------

-- 1、字典项：aig_data_level 增加 STRICT
insert ignore into sys_dict_data values(1763300000000000004, 4, '严格', 'STRICT', 'aig_data_level', '', 'danger', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'HR/证件/客户名单等；任何路由策略都不允许外发');

-- 2、字典类型备注补充第 4 级
update sys_dict_type
   set remark = '公开/内部/限制/严格'
 where dict_type = 'aig_data_level'
   and (remark is null or remark <> '公开/内部/限制/严格');

-- 3、既有路由策略的口径核对：STRICT 不允许存在 allow_external='Y' 的策略。
--    代码层已硬拒绝，这里只是把「配错了」暴露出来，不做自动改写（人工确认后再改）。
select p.policy_id,
       p.capability_code,
       p.data_level,
       p.allow_external,
       'STRICT 级策略不应允许外发，请人工核对' as warning
  from aig_route_policy p
 where p.data_level = 'STRICT'
   and p.allow_external = 'Y'
   and p.del_flag = '0';

-- 4、核对：字典项应为 4 条
select dict_sort, dict_label, dict_value, list_class
  from sys_dict_data
 where dict_type = 'aig_data_level'
 order by dict_sort;

select 'AIG_DATA_LEVEL_STRICT_DONE' as marker;
