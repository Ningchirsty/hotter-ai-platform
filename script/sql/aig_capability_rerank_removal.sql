-- ----------------------------------------------------------------------------
-- 去掉能力上的 RERANK 要求（M-001 的"唯一待决项"在生产侧已落地，仓库需追上）
--
-- 背景（为什么会有这个脚本）：
--   2026-10-09 对生产库做只读盘点时发现：`aig_capability.required_tags` 里**已经没有 RERANK**
--   （见 _local/prod-upgrade/r92-tags.out：8 个能力分别是 TEXT / TEXT / TEXT / TEXT / TEXT /
--   IMAGE / TEXT / VISION）。而仓库里的种子仍是 `TEXT,RERANK`：
--     · script/sql/aig_ai_gov.sql   → talent_match
--     · script/sql/cp_content_aigov.sql → brief_precheck
--   也就是说：**按仓库重建环境，得到的不是生产现状**——重建出来的环境里
--   `talent_match` / `brief_precheck` 在严格模式（aigov.route.require-model-tags=true）下
--   会因为"没有任何模型声明 RERANK"而永远没有候选（协议里这个标签当前无模型可提供）。
--   生产侧显然是有人直接改了数据（改法正确），但既没有迁移也没有更新文档。
--
-- 本脚本做什么：
--   把这两个能力的 required_tags 收敛为 `TEXT`（= 生产现状），且**幂等**：
--   只在当前值恰为 'TEXT,RERANK' 时更新，重复执行无副作用，也不会覆盖别人更细的取值。
--
-- 刻意不做的事：
--   · 不"顺手"改其它能力的 required_tags（它们与生产一致，改它们属于新决策）；
--   · 不删除能力定义里的 RERANK 词表（`aig_ai_gov.sql` 的列注释仍列出 RERANK：
--     将来若有真正的重排模型，重新要求该标签是**另一个决策**，那时应有一条新的迁移，
--     而不是把这次的口径当成"RERANK 不可用"）。
--
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
-- ----------------------------------------------------------------------------

-- 1) 收敛（幂等；只在旧值时更新）
update aig_capability
   set required_tags = 'TEXT',
       update_by      = 1761100000000000001,
       update_time    = now(),
       remark         = concat(ifnull(remark, ''),
                               '｜2026-10-09 迁移：去掉 RERANK（当前无模型可提供该标签，'
                               '严格模式下会让本能力永远没有候选；生产已于同日实测为该状态）')
 where capability_code in ('talent_match', 'brief_precheck')
   and required_tags = 'TEXT,RERANK';

-- 2) 核对：应看到这两个能力是 TEXT，且没有任何能力仍要求 RERANK
select capability_code, required_tags
  from aig_capability
 where capability_code in ('talent_match', 'brief_precheck')
 order by capability_code;

select count(*) as still_requires_rerank
  from aig_capability
 where required_tags like '%RERANK%';

select 'AIG_CAPABILITY_RERANK_REMOVAL_DONE' as marker;
