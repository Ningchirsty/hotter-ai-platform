-- ============================================================================
-- 工作台装配：把「开工包」区块加进视觉项目页（内测 C5①）
-- ----------------------------------------------------------------------------
-- 背景：开工包原先在设计侧**零引用**（连字段都没有），"交接"只是内容侧的单方面动作（内测 S5）。
-- 现在它的定位是**跨部门交接凭证**，创作域已提供只读接口
-- （GET /creative/projects/{taskId}/work-package）与组件 ProjectWorkPackageBlock；
-- 本脚本把它登记进各交付类型的工作台装配（layout_json）——**配置不登记就渲染不出来**
-- （工作台只装配配置里声明过的组件）。
--
-- 位置：INPUT 步（第一步）。理由是它属于"开工前该看的东西"，
-- 与「产品图与参考图」「品牌要求」并列，设计侧一进项目页就能看到品牌部交接了什么。
--
-- 幂等：先查 JSON_SEARCH，已存在就不重复追加。
-- ⚠️ 前端组件注册表（frontend/src/views/creative/composables/workspaceAssembly.ts）
--    与它的单测是这份配置的对照表，代码与配置必须同批上线；否则对照会报"配置里有、注册表没有登记"。
-- ============================================================================

-- 1) 执行前：各交付类型的 INPUT 步组件
SELECT '=== 1) 执行前：INPUT 步组件 ===' AS s;
SELECT delivery_type, schema_code, status,
       JSON_EXTRACT(layout_json, '$.steps[0].code') AS first_step,
       JSON_EXTRACT(layout_json, '$.steps[0].components') AS input_components
  FROM dp_workspace_schema
 WHERE del_flag = '0' AND status = 'PUBLISHED'
 ORDER BY delivery_type;

-- 2) 追加组件（只对"第一步是 INPUT 且还没登记过"的已发布装配生效）
UPDATE dp_workspace_schema
   SET layout_json = JSON_ARRAY_APPEND(layout_json, '$.steps[0].components', 'ProjectWorkPackageBlock'),
       remark = CONCAT(COALESCE(remark, ''),
           ' ｜C5①：INPUT 步加「开工包」只读区块（跨部门交接凭证）'),
       update_time = sysdate()
 WHERE del_flag = '0' AND status = 'PUBLISHED'
   AND JSON_UNQUOTE(JSON_EXTRACT(layout_json, '$.steps[0].code')) = 'INPUT'
   AND JSON_SEARCH(JSON_EXTRACT(layout_json, '$.steps[0].components'), 'one', 'ProjectWorkPackageBlock') IS NULL;

-- 3) 执行后核对：期望每个已发布装配的 INPUT 步都含 ProjectWorkPackageBlock
SELECT '=== 3) 执行后：INPUT 步组件 ===' AS s;
SELECT delivery_type, schema_code,
       JSON_EXTRACT(layout_json, '$.steps[0].components') AS input_components
  FROM dp_workspace_schema
 WHERE del_flag = '0' AND status = 'PUBLISHED'
 ORDER BY delivery_type;

SELECT '=== 3.1 仍缺该组件的已发布装配（期望为空）===' AS s;
SELECT delivery_type, schema_code
  FROM dp_workspace_schema
 WHERE del_flag = '0' AND status = 'PUBLISHED'
   AND JSON_SEARCH(JSON_EXTRACT(layout_json, '$.steps[0].components'), 'one', 'ProjectWorkPackageBlock') IS NULL;

-- ---------------------------------------------------------------------------
-- 回滚（把该组件从 INPUT 步摘掉）
-- ---------------------------------------------------------------------------
-- 注意：JSON_REMOVE 要给出下标，先查出来再删。稳妥做法是用 JSON_SEARCH 拿到路径：
-- UPDATE dp_workspace_schema
--    SET layout_json = JSON_REMOVE(layout_json,
--          JSON_UNQUOTE(JSON_SEARCH(layout_json, 'one', 'ProjectWorkPackageBlock'))),
--        update_time = sysdate()
--  WHERE del_flag = '0' AND status = 'PUBLISHED'
--    AND JSON_SEARCH(layout_json, 'one', 'ProjectWorkPackageBlock') IS NOT NULL;
