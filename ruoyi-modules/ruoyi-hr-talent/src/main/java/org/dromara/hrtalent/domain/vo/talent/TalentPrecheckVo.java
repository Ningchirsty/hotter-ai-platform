package org.dromara.hrtalent.domain.vo.talent;

import lombok.Data;
import org.dromara.hrtalent.enums.TalentStatusEnum;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 人才重复预检结果视图对象（SPEC-P3 §2.1 {@code POST /talent/profiles/precheck}
 * 与 {@code POST /recruit/candidates/precheck}）。
 *
 * <p><b>分级口径</b>（设计文档 §21.15）：强匹配 = 电话或邮箱哈希命中；
 * 中匹配 = 姓名 +（公司 或 学校 或 简历哈希）；弱匹配 = 姓名 + 期望岗位。
 * <b>姓名单独命中不构成任何级别</b>（§8.4）。</p>
 *
 * <p><b>只回可展示摘要</b>：每个命中项只包含人才编号、姓名、脱敏电话/邮箱、
 * 状态与少量画像字段，<b>不返回</b>任何密文或明文联系方式。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentPrecheckVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否存在任何级别的命中
     */
    private boolean duplicated;

    /**
     * 是否存在强匹配（存在时<b>禁止</b>静默创建，需人工确认或复用主档）
     */
    private boolean strongDuplicated;

    /**
     * 是否存在中匹配（存在时需人工确认或复用主档）
     */
    private boolean mediumDuplicated;

    /**
     * 是否存在弱匹配（仅提示，不阻塞创建）
     */
    private boolean weakDuplicated;

    /**
     * 强匹配项
     */
    private List<TalentPrecheckItemVo> strongMatches = new ArrayList<>();

    /**
     * 中匹配项
     */
    private List<TalentPrecheckItemVo> mediumMatches = new ArrayList<>();

    /**
     * 弱匹配项
     */
    private List<TalentPrecheckItemVo> weakMatches = new ArrayList<>();

    /**
     * 提示语（命中强/中匹配时给出统一中文提示，取自错误码常量）
     */
    private String message;

    /**
     * 预检使用的规范化电话哈希（仅供前端排障与审计对齐，不可逆）
     */
    private String normalizedPhoneHash;

    /**
     * 预检使用的规范化邮箱哈希（仅供前端排障与审计对齐，不可逆）
     */
    private String normalizedEmailHash;

    /**
     * 单个命中项：只包含可展示的摘要字段。
     *
     * @author hr-talent
     */
    @Data
    public static class TalentPrecheckItemVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 人才主档ID
         */
        private Long talentId;

        /**
         * 人才编号
         */
        private String talentNo;

        /**
         * 姓名
         */
        private String name;

        /**
         * 脱敏电话
         */
        private String phoneMasked;

        /**
         * 脱敏邮箱
         */
        private String emailMasked;

        /**
         * 当前公司
         */
        private String currentCompany;

        /**
         * 当前所在城市
         */
        private String currentCity;

        /**
         * 期望岗位
         */
        private String expectedPosition;

        /**
         * 人才状态编码
         */
        private String talentStatus;

        /**
         * 归属负责人用户ID
         */
        private Long ownerId;

        /**
         * 归属部门ID
         */
        private Long ownerDeptId;

        /**
         * 创建日期（用于人工判断新旧）
         */
        private LocalDate createDate;

        /**
         * 命中原因（如「电话命中」「姓名+当前公司命中」）
         */
        private String reason;

        /**
         * 命中级别编码（strong/medium/weak）
         */
        private String matchLevel;

        /**
         * 人才状态中文兜底。
         *
         * @return 中文标签，未知编码返回 null
         */
        public String getTalentStatusText() {
            TalentStatusEnum status = TalentStatusEnum.find(talentStatus);
            return status == null ? null : status.getDesc();
        }

    }

}
