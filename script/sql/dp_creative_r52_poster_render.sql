-- =====================================================================
-- R52：品牌海报的渲染模式从 MULTI_IMAGE 改成 POSTER（海报渲染器落地）
-- =====================================================================
--
-- 背景（R50 真机干跑 → R51 收尾 → 本轮补齐）：
--   海报的「版式与多尺寸适配」这一步（POSTER_LAYOUT，step_type=LAYOUT）此前没有任何渲染器接，
--   点渲染得到「流程里没有排版环节」；交付渲染则按 render_mode=MULTI_IMAGE 把各屏原图打了个 ZIP。
--   本轮落地的 POSTER 渲染器按 dp_output_spec **逐档排版成品图**（3:4 / 9:16 / 16:9 各一张），
--   所以渲染模式必须是 POSTER —— 渲染器由渲染模式决定，不由交付类型编码决定。
--
-- 依赖（顺序不能反）：
--   1) 渲染服务镜像里要有模板 templates/poster/1.0.0/template.html（deploy-renderer.sh 部署）；
--   2) 后端把 POSTER 模式映射到 POSTER 渲染器（CreativeRendererHub.MODE_TO_RENDERER，R52 已加）；
--   3) 人在「视觉模板库」对账并发布 poster@1.0.0（POST /creative/templates/sync →
--      POST /creative/templates/{id}/publish）；否则海报渲染会因「模板未登记/未发布」被拒。
--   本脚本只做第 3 步里的配置改动；模板登记走接口（校验和由渲染服务算，不能手写）。
--
-- 回滚：UPDATE dp_delivery_type SET render_mode = 'MULTI_IMAGE' WHERE delivery_type = 'BRAND_POSTER';
--       （回滚后海报恢复"多图打包"交付——能用，但没有逐档版式）
-- =====================================================================

UPDATE dp_delivery_type
SET render_mode = 'POSTER',
    update_time = NOW()
WHERE delivery_type = 'BRAND_POSTER'
  AND del_flag = '0';

-- 核对：必须恰好 1 行、且 render_mode='POSTER'
SELECT delivery_type, render_mode, enabled
FROM dp_delivery_type
WHERE delivery_type = 'BRAND_POSTER';
