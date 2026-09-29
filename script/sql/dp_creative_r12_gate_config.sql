-- ------------------------------------------------------------------
-- V0.2 / R12：B2 第一步 —— 创作侧闸门项配置化（Gate Rule 配置化，文档 §25）
--
-- 背景：视觉门的项目清单原先**内联写死**在 CreativeGateServiceImpl#evaluate 里（7 项：
-- 4 项内联 + 品牌调性/品牌 Brief/禁用词三个辅助方法）。文档 §25 要求"Gate Rule 配置化、
-- 不同场景用不同 Gate Profile"。
--
-- 【改什么、不改什么（这条边界很重要）】
--   配置决定：**有哪些项、项的顺序、项的等级（BLOCK/CONDITION）、项的展示名**。
--   代码决定：**每一项怎么判**（读哪个服务、什么算通过、失败时说什么）—— 这不可能配置化，
--             也不可能让运营改（否则把质量红线交出去，见对照文档 D3）。
--   所以每项都有 item_code，代码里按 code 找"检查器"；配置里出现代码没有的 code 时
--   **按未通过处理**（fail-closed，与"治理默认拒绝"同口径），不会静默放过。
--
-- 【无配置时怎么办】
--   `CreativeGateServiceImpl` 保留一份与改造前**逐字一致**的默认清单（DEFAULT_ITEMS）。
--   查不到已发布的 Gate Profile 就回落到它 —— 这样"某场景还没配闸门"不会导致门禁失效或误封。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + NOT EXISTS 守卫，可重复执行。
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS dp_gate_profile (
  id            BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  profile_code  VARCHAR(64)  NOT NULL COMMENT '闸门档案编码（如 GATE_ECOM_DETAIL_V1）',
  delivery_type VARCHAR(64)  NOT NULL COMMENT '交付类型（与 cp_task.deliverable_type 同词表）',
  version       VARCHAR(32)  NOT NULL DEFAULT '1.0.0' COMMENT '版本',
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/PUBLISHED/RETIRED）',
  remark        VARCHAR(500) NULL,
  create_dept   BIGINT       NULL,
  create_by     BIGINT       NULL,
  create_time   DATETIME     NULL,
  update_by     BIGINT       NULL,
  update_time   DATETIME     NULL,
  del_flag      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_gate_profile (profile_code, version),
  KEY idx_dp_gate_profile_delivery (delivery_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·闸门档案（Gate Profile）';

CREATE TABLE IF NOT EXISTS dp_gate_item (
  id           BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  profile_id   BIGINT       NOT NULL COMMENT '所属闸门档案（dp_gate_profile.id）',
  item_code    VARCHAR(64)  NOT NULL COMMENT '闸门项编码（代码里按它找检查器）',
  item_label   VARCHAR(128) NOT NULL COMMENT '展示名',
  level        VARCHAR(16)  NOT NULL COMMENT '等级（BLOCK=阻断提交 / CONDITION=提示）',
  sort_no      INT          NOT NULL DEFAULT 0 COMMENT '顺序',
  options_json TEXT         NULL     COMMENT '可选参数（给具体检查器用；当前 7 项都不需要）',
  remark       VARCHAR(500) NULL,
  create_dept  BIGINT       NULL,
  create_by    BIGINT       NULL,
  create_time  DATETIME     NULL,
  update_by    BIGINT       NULL,
  update_time  DATETIME     NULL,
  del_flag     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_gate_item (profile_id, item_code),
  KEY idx_dp_gate_item_profile (profile_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·闸门项（按场景配置）';

-- ==================================================================
-- 种子：ECOM_DETAIL 的闸门档案（**逐字复现**改造前写死的 7 项与顺序/等级）
-- ==================================================================

INSERT INTO dp_gate_profile
(id, profile_code, delivery_type, version, status, remark, create_dept, create_by, create_time)
SELECT 1766000000000000001, 'GATE_ECOM_DETAIL_V1', 'ECOM_DETAIL', '1.0.0', 'PUBLISHED',
       'R12/B2：复现改造前 CreativeGateServiceImpl 内联的 7 项（4 内联 + 品牌调性/Brief/禁用词），顺序与等级一致；改这里即改该场景的门禁清单',
       1761000000000000103, 1761100000000000001, NOW()
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT profile_code FROM dp_gate_profile) t
                     WHERE t.profile_code = 'GATE_ECOM_DETAIL_V1');

INSERT INTO dp_gate_item
(id, profile_id, item_code, item_label, level, sort_no, remark,
 create_dept, create_by, create_time)
SELECT * FROM (
  SELECT 1766000000000000011 AS id, 1766000000000000001 AS profile_id, 'DNA_LOCKED' AS item_code,
         '视觉基因已锁定' AS item_label, 'BLOCK' AS level, 10 AS sort_no,
         '出图提示词与规范都从锁定基因派生' AS remark,
         1761000000000000103 AS create_dept, 1761100000000000001 AS create_by, NOW() AS create_time
  UNION ALL SELECT 1766000000000000012, 1766000000000000001, 'REFERENCE_IMAGE', '产品参考图已上传', 'BLOCK', 20,
         '出图要拿参考图当输入', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1766000000000000013, 1766000000000000001, 'DIRECTION_SELECTED', '视觉方向已选定', 'CONDITION', 30,
         '没有方向也能出图，但同屏取舍会不一致', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1766000000000000014, 1766000000000000001, 'STORYBOARD_LOCKED', '分镜已锁定', 'CONDITION', 40,
         '分镜未锁定也能出图，但屏数与文案可能还会变', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1766000000000000015, 1766000000000000001, 'BRAND_TONE_CONFIRMED', '品牌调性已确认', 'CONDITION', 50,
         '仅当该交付类型声明了品牌调性事实时才有意义；未声明时该项自动通过', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1766000000000000016, 1766000000000000001, 'BRAND_BRIEF_CONFIRMED', '品牌 Brief 已填写并确认', 'CONDITION', 60,
         '判据是 status=CONFIRMED（光填过不算）；品牌方要求必填时把 level 改成 BLOCK 即可', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1766000000000000017, 1766000000000000001, 'FORBIDDEN_WORDS_DECLARED', '已声明禁用词与合规红线', 'CONDITION', 70,
         '未声明时出图只能用默认禁忌词表', 1761000000000000103, 1761100000000000001, NOW()
) seed
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT profile_id FROM dp_gate_item) t
                    WHERE t.profile_id = 1766000000000000001);

-- 核对
SELECT p.profile_code, p.delivery_type, p.status, COUNT(i.id) AS items
  FROM dp_gate_profile p LEFT JOIN dp_gate_item i ON i.profile_id = p.id
 WHERE p.profile_code = 'GATE_ECOM_DETAIL_V1'
 GROUP BY p.profile_code, p.delivery_type, p.status;

SELECT item_code, item_label, level, sort_no FROM dp_gate_item
 WHERE profile_id = 1766000000000000001 ORDER BY sort_no;

SELECT 'DP_CREATIVE_R12_GATE_CONFIG_DONE' AS marker;
