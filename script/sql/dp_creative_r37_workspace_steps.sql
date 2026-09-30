-- =====================================================================
-- R37（V0.2）：工作台装配的步骤组件改成**代码里真实存在的组件名**（并允许一步多个组件）
--
-- 背景：R17 起 `dp_workspace_schema.layout_json` 里声明的步骤组件名（ProjectInputPanel /
-- FactPanel / GenerationBoard …）是"照文档写的占位名"，代码里从来没有这些组件；
-- 而 R32/R33 真拆出来的六个区块叫 ProjectAssetsBlock / ProjectBriefBlock / ProjectFactsBlock /
-- ProjectCopyBlock / ProjectHeroBlock / ProjectGenerationsBlock。
-- 于是"工作台装配 5/15"这个数字一直卡着：不是代码没做，而是**配置里写的名字与代码对不上**。
--
-- 这一步把配置改成事实，并支持一步多个组件（项目页上「资料」步是"附件 + 品牌要求"两块、
-- 「事实」步是"事实 + 文案"两块、「出图」步是"发起出图 + 候选"两块）：
--   steps: [{"code":"INPUT","components":["ProjectAssetsBlock","ProjectBriefBlock"]}, …]
-- 旧写法 `{"code":"X","component":"Y"}` 仍然解析（前端 parseWorkspaceLayout 兼容），
-- 所以回滚只要把 layout_json 换回下面 comment 里的旧值。
--
-- 影响面：这份定义决定"工作台按配置装配哪些槽位/步骤组件"，改了它 = 改了工作台的装配清单。
-- 页面渲染在同一个版本里一起改（先应用本 SQL，再发前端），期间旧前端只是芯片文案略有出入
-- （面板清单未变，页面照常渲染），不会白屏。
--
-- 执行方式（生产）：
--   docker exec -i ai-video-poc-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -p"$MYSQL_ROOT_PASSWORD" ai_video_poc < dp_creative_r37_workspace_steps.sql
-- =====================================================================

-- 1) 改前留档（执行时可先跑这一条，把旧值抄进变更记录）
SELECT schema_code, version, status, layout_json
  FROM dp_workspace_schema
 WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');

-- 2) WS_LONG_PAGE（ECOM_DETAIL，长图详情页：10 步）
UPDATE dp_workspace_schema
   SET layout_json = '{"workspace":"LONG_PAGE","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],"steps":[{"code":"INPUT","components":["ProjectAssetsBlock","ProjectBriefBlock"]},{"code":"FACT","components":["ProjectFactsBlock","ProjectCopyBlock"]},{"code":"DNA","components":["VisualDnaPanel"]},{"code":"DIRECTION","components":["DirectionBoard"]},{"code":"STORYBOARD","components":["StoryboardBoard"]},{"code":"GATE","components":["GatePanel"]},{"code":"GENERATION","components":["ProjectHeroBlock","ProjectGenerationsBlock"]},{"code":"QA","components":["QaPanel"]},{"code":"LAYOUT","components":["LongPageCanvas"]},{"code":"FINAL","components":["FinalReviewPanel"]}]}',
       version = '1.1.0',
       remark = CONCAT(IFNULL(remark, ''), '｜R37：步骤组件改用代码里的真实组件名，并支持一步多组件')
 WHERE schema_code = 'WS_LONG_PAGE';

-- 3) WS_MAIN_IMAGE（MAIN_IMAGE，多图交付：9 步，没有 LAYOUT）
UPDATE dp_workspace_schema
   SET layout_json = '{"workspace":"MULTI_IMAGE","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],"steps":[{"code":"INPUT","components":["ProjectAssetsBlock","ProjectBriefBlock"]},{"code":"FACT","components":["ProjectFactsBlock","ProjectCopyBlock"]},{"code":"DNA","components":["VisualDnaPanel"]},{"code":"DIRECTION","components":["DirectionBoard"]},{"code":"STORYBOARD","components":["StoryboardBoard"]},{"code":"GATE","components":["GatePanel"]},{"code":"GENERATION","components":["ProjectHeroBlock","ProjectGenerationsBlock"]},{"code":"QA","components":["QaPanel"]},{"code":"FINAL","components":["FinalReviewPanel"]}]}',
       version = '1.1.0',
       remark = CONCAT(IFNULL(remark, ''), '｜R37：步骤组件改用代码里的真实组件名，并支持一步多组件')
 WHERE schema_code = 'WS_MAIN_IMAGE';

-- 4) 核对（两行都应是 v1.1.0，且 layout_json 里出现 components）
SELECT schema_code, version, status,
       JSON_VALID(layout_json) AS json_ok,
       JSON_LENGTH(JSON_EXTRACT(layout_json, '$.steps')) AS step_count
  FROM dp_workspace_schema
 WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');

-- =====================================================================
-- 回滚（把两步的装配定义改回 R17 的占位名）
--
-- UPDATE dp_workspace_schema SET version = '1.0.0',
--   layout_json = '{"workspace":"LONG_PAGE","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],"steps":[{"code":"INPUT","component":"ProjectInputPanel"},{"code":"FACT","component":"FactPanel"},{"code":"DNA","component":"VisualDnaPanel"},{"code":"DIRECTION","component":"DirectionBoard"},{"code":"STORYBOARD","component":"StoryboardBoard"},{"code":"GATE","component":"GatePanel"},{"code":"GENERATION","component":"GenerationBoard"},{"code":"QA","component":"QaPanel"},{"code":"LAYOUT","component":"LongPageCanvas"},{"code":"FINAL","component":"FinalReviewPanel"}]}'
--  WHERE schema_code = 'WS_LONG_PAGE';
-- UPDATE dp_workspace_schema SET version = '1.0.0',
--   layout_json = '{"workspace":"MULTI_IMAGE","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],"steps":[{"code":"INPUT","component":"ProjectInputPanel"},{"code":"FACT","component":"FactPanel"},{"code":"DNA","component":"VisualDnaPanel"},{"code":"DIRECTION","component":"DirectionBoard"},{"code":"STORYBOARD","component":"StoryboardBoard"},{"code":"GATE","component":"GatePanel"},{"code":"GENERATION","component":"GenerationBoard"},{"code":"QA","component":"QaPanel"},{"code":"FINAL","component":"FinalReviewPanel"}]}'
--  WHERE schema_code = 'WS_MAIN_IMAGE';
-- =====================================================================
