-- ------------------------------------------------------------------
-- 内容生产：cp_product 产品/SKU 主要内容字段 增量迁移
--
-- 背景：
--   原表只有 product_code / product_name / sku_code / sku_name / category / version
--   六个业务字段，装不下「新品官宣」这类产品资料的主要信息（品牌、二级分类、
--   产品图、主推说明、产品经理、尺寸、价格、结构工艺、设计灵感）。
--   本次按《上海展官宣新品-五大系列-趣往单枝花》的 A~K 列补齐。
--
-- 说明：
--   1. 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用
--      information_schema 判断后再动态执行 DDL，可安全重复执行
--      （写法与 script/sql/ry_video_task_migration.sql 一致）。
--   2. 只加列、不改既有数据、不动索引与唯一键；老数据新列均为 NULL。
--   3. 必须在部署带新字段写库逻辑的后端镜像「之前」执行，
--      否则新增/编辑产品会因 Unknown column 直接失败。
--   4. 字段与来源列的对应（附件《上海展官宣新品-五大系列 - 趣往单枝花.csv》）：
--        A 品牌        → brand
--        B 二级分类    → sub_category
--        C 产品名称    → product_name（已存在）
--        D 产品图      → product_image
--        E 主推（待定）→ main_push
--        F 产品经理    → product_manager
--        G SKU编码     → sku_code（已存在）
--        H 尺寸        → size_spec
--        I 价格        → price
--        J 结构/工艺   → craft
--        K 设计灵感    → design_inspiration
--      L 列（产品故事）及其后各列本次不纳入。
-- ------------------------------------------------------------------

-- A 品牌
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'brand'),
    'SELECT ''cp_product.brand already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN brand VARCHAR(64) NULL COMMENT ''品牌（如 趣往）'' AFTER category')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- B 二级分类
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'sub_category'),
    'SELECT ''cp_product.sub_category already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN sub_category VARCHAR(64) NULL COMMENT ''二级分类（如 解构花园-静态花）'' AFTER brand')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- D 产品图
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'product_image'),
    'SELECT ''cp_product.product_image already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN product_image VARCHAR(500) NULL COMMENT ''产品图引用（对象存储键或 URL；业务库不存文件本体）'' AFTER sub_category')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- E 主推（附件该列填的是售卖形态说明，故按文本存，不用布尔）
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'main_push'),
    'SELECT ''cp_product.main_push already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN main_push VARCHAR(500) NULL COMMENT ''主推说明（原表该列常填售卖形态/口径说明）'' AFTER product_image')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- F 产品经理
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'product_manager'),
    'SELECT ''cp_product.product_manager already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN product_manager VARCHAR(64) NULL COMMENT ''产品经理'' AFTER main_push')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- H 尺寸
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'size_spec'),
    'SELECT ''cp_product.size_spec already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN size_spec VARCHAR(255) NULL COMMENT ''尺寸规格（如 257.60*149.30；多形态用换行分隔）'' AFTER product_manager')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- I 价格
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'price'),
    'SELECT ''cp_product.price already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN price DECIMAL(12,2) NULL COMMENT ''价格（元）'' AFTER size_spec')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- J 结构/工艺
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'craft'),
    'SELECT ''cp_product.craft already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN craft VARCHAR(255) NULL COMMENT ''结构/工艺（如 UV+喷漆、喷漆+镀铬）'' AFTER price')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- K 设计灵感
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_product' AND COLUMN_NAME = 'design_inspiration'),
    'SELECT ''cp_product.design_inspiration already exists'' AS note',
    'ALTER TABLE cp_product ADD COLUMN design_inspiration VARCHAR(1000) NULL COMMENT ''设计灵感'' AFTER craft')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;
