-- ------------------------------------------------------------------
-- V0.2 / R30：Renderer Hub（文档 §26）+ 多图交付（文档 §9.2 MULTI_IMAGE_EXPORT）
--
-- 背景：R30 之前"渲染"只有一个实现——CreativeLayoutServiceImpl 里写死的长图排版。
-- 交付类型一多，"渲染"就不是同一件事了：
--   * 详情页（ECOM_DETAIL，render_mode=LONGPAGE）→ 把 N 屏拼成**一张**长图；
--   * 商品主图（MAIN_IMAGE，render_mode=MULTI_IMAGE）→ 把 N 屏打成**一组**图。
-- 本轮把它抽成 Renderer Hub（渲染器按 dp_delivery_type.render_mode 解析），
-- 并给"一组图"这种交付形态一个留痕的地方：本表。
--
-- 本表只存**清单**，不存产物字节：
--   * 主图的每张交付图本来就已经是任务附件（dp_generation.output_file_id → cp_task_file）；
--   * 长图排版的那张 PNG 也已经落在 dp_detail_page_version.rendered_file_id；
--   * 交付包（ZIP）在下载时按清单**现拼**，因此"打包"不额外占存储。
-- 清单里记了每张图的附件ID与 sha256、以及清单自身的 checksum，可复现、可核对。
--
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ------------------------------------------------------------------

create table if not exists dp_delivery_artifact (
    id              bigint       not null                comment '主键（雪花ID）',
    task_id         bigint       not null                comment '视觉项目（cp_task.task_id）',
    delivery_type   varchar(64)  not null                comment '交付类型（ECOM_DETAIL / MAIN_IMAGE …）',
    renderer        varchar(32)  not null                comment '渲染器编码（LONG_PAGE / MULTI_IMAGE …，文档 §26）',
    version         int          not null default 1      comment '该项目该渲染器下的第几版交付产物',
    manifest_json   text         null                    comment '交付清单 JSON（schema=delivery-manifest/1：产物文件名/附件ID/尺寸/字节/sha256/checksum）',
    image_count     int          not null default 0      comment '产物张数（长图为 1；多图交付为屏数）',
    total_bytes     bigint       not null default 0      comment '产物字节合计',
    checksum        varchar(128) null                    comment '清单校验和（顺序+文件名+内容sha256+附件ID 的 sha256）',
    remark          varchar(500) null                    comment '备注（缺图屏、未规格化的屏等如实记录）',
    create_dept     bigint       null,
    create_by       bigint       null,
    create_time     datetime     null,
    update_by       bigint       null,
    update_time     datetime     null,
    del_flag        char(1)      default '0'             comment '删除标志（0存在 1删除）',
    primary key (id),
    unique key uk_dp_delivery_artifact (task_id, renderer, version),
    key idx_dp_delivery_artifact_task (task_id, del_flag)
) engine = innodb comment = '交付产物清单（Renderer Hub 每次渲染留痕；产物字节不重复存，下载时现拼）';

SELECT 'DP_CREATIVE_R30_RENDERER_HUB_DONE' AS marker;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_delivery_artifact' ORDER BY ORDINAL_POSITION;
