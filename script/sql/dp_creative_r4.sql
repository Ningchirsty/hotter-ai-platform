-- ------------------------------------------------------------------
-- AI 视觉工厂：R4 增量迁移（产品图打通 + 图角色 + 质检双基准）
--
-- 背景（生产实测 2026-09-24）：
--   1. cp_product 45 行、product_image 全部为 NULL —— 平台里没有任何产品图；
--      业务上传的产品照片（如 鸢尾花.jpg）只以「任务附件」存在，file_kind=IMAGE，
--      与产品主数据毫无关联，而且生成结果也落在同一张附件表、同一个 file_kind 里，
--      产品图/参考图/生成图在数据模型上没有角色区分。
--   2. 出图输入 dp_generation.input_file_id 指的是那张上传图；质检基准也是它，
--      没有「以产品图为基准」的落点，也没有记录「本次基准到底是哪张图」。
--
-- 本迁移做四件事：
--   A. cp_product 补产品图的来源留痕（哪个任务、谁、什么时候设的）；
--   B. 统一 cp_task_file.source_type 的角色口径并回填「系统生成」的历史候选；
--   C. dp_generation 补「产品图基准 / 产品基准质检结论」三列；
--   D. 给附件表加 (task_id, source_type) 索引，供按角色取图。
--
-- 说明：
--   1. 幂等：用 information_schema 判断后再动态执行 DDL（写法与 dp_creative.sql 一致）；
--   2. 只加列/加索引/回填角色，不删除、不改既有业务含义；
--   3. 回填只把「确实是系统生成结果」的附件标成 GENERATED（依据 dp_generation.output_file_id
--      这条硬关联），不做任何推测；
--   4. 必须在部署带产品图逻辑的后端镜像之前执行。
-- ------------------------------------------------------------------

-- A. 产品图来源留痕 --------------------------------------------------

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cp_product'
        AND COLUMN_NAME = 'product_image_task_id'
    ),
    'SELECT ''cp_product.product_image_task_id already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN product_image_task_id BIGINT NULL COMMENT ''产品图来源任务（cp_task.task_id）：便于追溯这张图是谁在哪个项目里设的'' AFTER product_image'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cp_product'
        AND COLUMN_NAME = 'product_image_set_at'
    ),
    'SELECT ''cp_product.product_image_set_at already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN product_image_set_at DATETIME NULL COMMENT ''产品图设定时间'' AFTER product_image_task_id'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cp_product'
        AND COLUMN_NAME = 'product_image_set_by'
    ),
    'SELECT ''cp_product.product_image_set_by already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN product_image_set_by BIGINT NULL COMMENT ''产品图设定人（sys_user.user_id）'' AFTER product_image_set_at'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

-- B. 附件来源角色口径 + 回填系统生成 --------------------------------

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cp_task_file'
        AND COLUMN_NAME = 'source_type'
        AND COLUMN_COMMENT LIKE '%PRODUCT%'
    ),
    'SELECT ''cp_task_file.source_type comment already updated'' AS note',
    'ALTER TABLE cp_task_file MODIFY COLUMN source_type VARCHAR(16) NULL DEFAULT ''UPLOAD'' COMMENT ''来源角色：UPLOAD 人工上传 / REFERENCE 被引用为参考图 / PRODUCT 产品图 / GENERATED 系统生成'' '
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

-- 回填：dp_generation.output_file_id 指向的附件就是系统生成的结果图（硬关联，非推测）
UPDATE cp_task_file f
  JOIN dp_generation g ON g.output_file_id = f.file_id
   SET f.source_type = 'GENERATED'
 WHERE f.source_type = 'UPLOAD'
   AND f.del_flag = '0';

-- C. 出图记录补「产品图基准」三列 -----------------------------------

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'dp_generation'
        AND COLUMN_NAME = 'product_file_id'
    ),
    'SELECT ''dp_generation.product_file_id already exists'' AS note',
    'ALTER TABLE dp_generation ADD COLUMN product_file_id BIGINT NULL COMMENT ''本次出图作为产品保真基准的产品图附件（cp_task_file.file_id，source_type=PRODUCT）'' AFTER input_file_id'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'dp_generation'
        AND COLUMN_NAME = 'product_check_id'
    ),
    'SELECT ''dp_generation.product_check_id already exists'' AS note',
    'ALTER TABLE dp_generation ADD COLUMN product_check_id BIGINT NULL COMMENT ''以产品图为基准的成品一致性检查（cp_output_check.check_id）'' AFTER qa_check_id'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'dp_generation'
        AND COLUMN_NAME = 'product_verdict'
    ),
    'SELECT ''dp_generation.product_verdict already exists'' AS note',
    'ALTER TABLE dp_generation ADD COLUMN product_verdict VARCHAR(16) NULL COMMENT ''以产品图为基准的质检结论（CONSISTENT/INCONSISTENT/UNCERTAIN）：只提示，不自动筛除'' AFTER product_check_id'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

-- D. 按角色取图的索引 ----------------------------------------------

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cp_task_file'
        AND INDEX_NAME = 'idx_cp_task_file_task_source'
    ),
    'SELECT ''idx_cp_task_file_task_source already exists'' AS note',
    'ALTER TABLE cp_task_file ADD INDEX idx_cp_task_file_task_source (task_id, source_type)'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

SELECT 'dp_creative_r4 迁移完成' AS note;
