-- ------------------------------------------------------------------
-- AI 治理：模型能力标签（aig_model_governance.capability_tags）增量迁移
--
-- 背景（为什么必须加这一列）：
--   aig_capability.required_tags 一直在登记（如 'VISION' / 'IMAGE'），但**从来没有任何
--   代码拿它去和模型比对**——模型侧根本没有「我支持什么能力」这一列。
--   后果不是"少了个校验"，而是**静默出错**：
--     把纯文本模型绑到「看图」能力上，路由判 MODEL 并"成功返回"，
--     产出的却是一份没看过图的结论（外形与真实结论一模一样，事后无法区分）。
--   creative 的 dp_creative_r8_dna_gov.sql 里就专门写过这段担忧——它靠"刻意不绑"
--   来规避，那是**数据层的纪律**，代码拦不住。本次把它变成代码能拦的。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema
--   判断后再动态执行 DDL，可安全重复执行（写法与
--   script/sql/cp_content_product_fields_migration.sql、ry_video_task_migration.sql 一致）。
--
-- 兼容性：只加列、不改既有数据、不动索引与唯一键；老数据该列为 NULL。
--   **NULL 的语义是「未声明」**，而不是「不支持」——见下面口径说明。
--
-- 口径（重要，决定了为什么默认不拦）：
--   模型标签**只能显式声明**，不从 sai_model_config.model_type 推导。
--   推导看着省事，实际会误伤：多模态对话模型（如 gpt-4o）在 model_type 上同样是
--   'CHAT'，推导成 TEXT 后会被「看图」能力判为不匹配而排除掉——那是**假排除**，
--   把本来能用的模型挡在外面，比漏拦更难查。
--   因此：
--     · 模型已声明标签 → 必须覆盖能力的 required_tags，缺一个即排除；
--     · 模型未声明（NULL/空） → 默认**放行但写入可见提示**（否则本列一上线，
--       所有既有模型会被一次性排除干净，路由全面失效）；
--       配置 aigov.route.require-model-tags=true 后改为严格排除，
--       用于"模型标签已补齐、要把口子彻底关掉"的部署。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_model_governance'
        AND COLUMN_NAME = 'capability_tags'),
    'SELECT ''aig_model_governance.capability_tags already exists'' AS note',
    'ALTER TABLE aig_model_governance ADD COLUMN capability_tags VARCHAR(255) NULL COMMENT ''模型声明的能力标签（逗号分隔，如 IMAGE,VISION）；与 aig_capability.required_tags 逐项比对；NULL=未声明（默认放行并提示）'' AFTER lifecycle_status')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列应存在，且既有行为 NULL（未声明）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_model_governance'
   AND COLUMN_NAME = 'capability_tags';

SELECT COUNT(*) AS total_models,
       SUM(CASE WHEN capability_tags IS NULL THEN 1 ELSE 0 END) AS undeclared_tags
  FROM aig_model_governance
 WHERE del_flag = '0';

SELECT 'AIG_MODEL_CAPABILITY_TAGS_DONE' AS marker;
