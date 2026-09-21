package org.dromara.hrtalent.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 招聘业务编号生成器（需求、月度计划、计划任务、岗位、结转批次）。
 * <p>编号只用于对外展示，数据库主键不直接展示（设计文档 §9.4）。格式为
 * {@code 前缀 + 时间戳 + 三位进程内序号}，例如 {@code REQ20260921153012345-001}。</p>
 *
 * <p><b>唯一性说明</b>：本生成器只保证单进程内不重复。跨实例并发或时钟回拨时仍可能重复，
 * 因此各业务表的业务编号唯一索引（{@code demand_no} / {@code plan_no} / {@code item_no} 等）
 * 才是最终约束；调用方须对唯一键冲突做重试或友好提示。禁止在此处拼接任何密钥或地址。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
public class RecruitBusinessNoGenerator {

    /**
     * 招聘需求编号前缀。
     */
    private static final String PREFIX_DEMAND = "REQ";

    /**
     * 公司月度计划编号前缀。
     */
    private static final String PREFIX_PLAN = "PLN";

    /**
     * 月度计划任务编号前缀。
     */
    private static final String PREFIX_PLAN_ITEM = "ITM";

    /**
     * 岗位执行项编号前缀。
     */
    private static final String PREFIX_JOB = "JOB";

    /**
     * 月度结转批次号前缀。
     */
    private static final String PREFIX_ROLLOVER = "ROL";

    /**
     * 人才编号前缀（P3 追加，沿用本生成器风格）。
     */
    private static final String PREFIX_TALENT = "TAL";

    /**
     * 应聘记录编号前缀（P3 追加，沿用本生成器风格）。
     */
    private static final String PREFIX_APPLICATION = "APP";

    /**
     * 人才池编码前缀（P4 追加，沿用本生成器风格）。
     */
    private static final String PREFIX_POOL = "POOL";

    /**
     * 人才标签编码前缀（P4 追加，沿用本生成器风格）。
     */
    private static final String PREFIX_TAG = "TAG";

    /**
     * 人才分组编码前缀（P4 追加，沿用本生成器风格）。
     */
    private static final String PREFIX_GROUP = "GRP";

    /**
     * 人才导出任务编号前缀（P4 F 线追加，沿用本生成器风格）。
     */
    private static final String PREFIX_EXPORT = "EXP";

    /**
     * 时间戳部分：精确到毫秒。
     */
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 月份部分。
     */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 进程内序号，溢出后回绕，仅用于降低同毫秒碰撞概率。
     */
    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * 生成招聘需求编号。
     *
     * @param applyDate 申请日期，用于日志追溯；为空时取当天
     * @return 需求编号
     */
    public String nextDemandNo(LocalDate applyDate) {
        return next(PREFIX_DEMAND, applyDate == null ? LocalDate.now() : applyDate);
    }

    /**
     * 生成公司月度计划编号。
     *
     * @param planMonth 计划月份
     * @return 计划编号
     */
    public String nextPlanNo(YearMonth planMonth) {
        return next(PREFIX_PLAN, planMonth);
    }

    /**
     * 生成月度计划任务编号。
     *
     * @param planMonth 计划月份
     * @return 计划任务编号
     */
    public String nextPlanItemNo(YearMonth planMonth) {
        return next(PREFIX_PLAN_ITEM, planMonth);
    }

    /**
     * 生成岗位执行项编号。
     *
     * @return 岗位编号
     */
    public String nextJobNo() {
        return next(PREFIX_JOB, LocalDate.now());
    }

    /**
     * 生成月度结转批次号。
     *
     * @param targetMonth 目标月份
     * @return 结转批次号
     */
    public String nextRolloverBatchNo(YearMonth targetMonth) {
        return next(PREFIX_ROLLOVER, targetMonth);
    }

    /**
     * 生成人才主档编号（P3 追加）。
     * <p>人才编号只用于对外展示，{@code hr_talent_profile.uk_hr_talent_profile_no} 是最终唯一约束。</p>
     *
     * @return 人才编号，形如 {@code TAL20260921...-001}
     */
    public String nextTalentNo() {
        return next(PREFIX_TALENT, LocalDate.now());
    }

    /**
     * 生成应聘记录编号（P3 追加）。
     * <p>应聘编号只用于对外展示，{@code hr_recruit_application.uk_hr_recruit_application_no} 是最终唯一约束。</p>
     *
     * @return 应聘编号，形如 {@code APP20260921...-001}
     */
    public String nextApplicationNo() {
        return next(PREFIX_APPLICATION, LocalDate.now());
    }

    /**
     * 生成人才池编码（P4 追加）。
     * <p>池编码只用于对外展示，{@code hr_talent_pool.uk_hr_talent_pool_code} 是最终唯一约束。</p>
     *
     * @return 人才池编码，形如 {@code POOL20260921...-001}
     */
    public String nextPoolNo() {
        return next(PREFIX_POOL, LocalDate.now());
    }

    /**
     * 生成人才标签编码（P4 追加）。
     * <p>标签编码只用于对外展示，{@code hr_talent_tag.uk_hr_talent_tag_code} 是最终唯一约束。</p>
     *
     * @return 标签编码，形如 {@code TAG20260921...-001}
     */
    public String nextTagNo() {
        return next(PREFIX_TAG, LocalDate.now());
    }

    /**
     * 生成人才分组编码（P4 追加）。
     * <p>分组编码在 DDL 中可选，这里统一生成以便排查与对接外部系统，
     * 不作为唯一约束（{@code hr_talent_group} 未建编码唯一索引）。</p>
     *
     * @return 分组编码，形如 {@code GRP20260921...-001}
     */
    public String nextGroupNo() {
        return next(PREFIX_GROUP, LocalDate.now());
    }

    /**
     * 生成人才导出任务编号（P4 F 线追加）。
     * <p>任务编号只用于对外展示与排查，{@code hr_talent_export_task.uk_hr_talent_export_task_no}
     * 是最终唯一约束。</p>
     *
     * @return 导出任务编号，形如 {@code EXP20260921...-001}
     */
    public String nextExportNo() {
        return next(PREFIX_EXPORT, LocalDate.now());
    }

    /**
     * 生成 {@code 前缀 + 月份 + 时间戳 + 序号} 形式的编号。
     *
     * @param prefix    业务前缀
     * @param planMonth 业务月份
     * @return 业务编号
     */
    private String next(String prefix, YearMonth planMonth) {
        return "%s%s%s-%03d".formatted(
            prefix,
            planMonth.format(MONTH),
            LocalDateTime.now().format(STAMP),
            Math.floorMod(sequence.incrementAndGet(), 1000));
    }

    /**
     * 生成 {@code 前缀 + 日期 + 时间戳 + 序号} 形式的编号。
     *
     * @param prefix 业务前缀
     * @param date   业务日期
     * @return 业务编号
     */
    private String next(String prefix, LocalDate date) {
        return "%s%s%s-%03d".formatted(
            prefix,
            date.format(DateTimeFormatter.BASIC_ISO_DATE),
            LocalDateTime.now().format(STAMP),
            Math.floorMod(sequence.incrementAndGet(), 1000));
    }

}
