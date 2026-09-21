package org.dromara.hrtalent.domain.vo.talent;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才台账导出行对象（SPEC-P4 §2.6 F 线、设计文档 §8.20）。
 *
 * <p><b>定位</b>：本对象只服务于「生成结果文件」这一动作的内部装配，
 * <b>不是</b>对外接口的出参模型（对外只返回 {@link TalentExportTaskVo}）。</p>
 *
 * <p><b>字段与导出类型的关系</b>：</p>
 * <ul>
 *     <li>普通台账：{@code talentNo / name（脱敏）/ positionDirection / education / region / talentStatus / tags / owner}；</li>
 *     <li>敏感台账：在普通台账字段之上追加 {@code phone / email / salary / profileAccess / resumeAccess}，
 *     且 {@code name} 输出明文；调用方必须已通过独立权限校验、用途校验并写审计（§8.20）。</li>
 * </ul>
 * <p>导出时通过 {@code ExcelBuilder#includeFields} 按字段清单裁剪列，因此本对象同时承载两类台账的列定义。</p>
 *
 * <p><b>附件/简历访问地址</b>：只允许写入<b>系统内受控访问地址</b>
 * （{@code /talent/profiles/{id}}、{@code /talent/resumes/{id}/download}），
 * <b>严禁</b>写入对象存储永久地址或预签名地址（设计文档 §8.13、§11.1）。</p>
 *
 * @author hr-talent
 */
@Data
@ExcelIgnoreUnannotated
public class TalentExportRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才编号
     */
    @ExcelProperty(value = "人才编号", index = 0)
    private String talentNo;

    /**
     * 姓名（普通台账为脱敏值，敏感台账为明文）
     */
    @ExcelProperty(value = "姓名", index = 1)
    private String name;

    /**
     * 岗位方向（优先取意向岗位，缺失时取当前职位）
     */
    @ExcelProperty(value = "岗位方向", index = 2)
    private String positionDirection;

    /**
     * 最高学历（中文兜底文本）
     */
    @ExcelProperty(value = "学历", index = 3)
    private String education;

    /**
     * 区域（当前所在城市）
     */
    @ExcelProperty(value = "区域", index = 4)
    private String region;

    /**
     * 人才状态（中文兜底文本）
     */
    @ExcelProperty(value = "状态", index = 5)
    private String talentStatus;

    /**
     * 人才标签（多个以英文逗号分隔）
     */
    @ExcelProperty(value = "标签", index = 6)
    private String tags;

    /**
     * 负责人（昵称，取不到时回落为用户ID）
     */
    @ExcelProperty(value = "负责人", index = 7)
    private String owner;

    /**
     * 联系方式-电话（<b>仅敏感台账</b>）
     */
    @ExcelProperty(value = "联系电话", index = 8)
    private String phone;

    /**
     * 联系方式-邮箱（<b>仅敏感台账</b>）
     */
    @ExcelProperty(value = "联系邮箱", index = 9)
    private String email;

    /**
     * 期望薪资区间（<b>仅敏感台账</b>）
     */
    @ExcelProperty(value = "期望薪资", index = 10)
    private String salary;

    /**
     * 人才档案受控访问地址（<b>仅敏感台账</b>，系统内地址）
     */
    @ExcelProperty(value = "档案访问地址", index = 11)
    private String profileAccess;

    /**
     * 当前简历受控下载地址（<b>仅敏感台账</b>，系统内地址）
     */
    @ExcelProperty(value = "简历下载地址", index = 12)
    private String resumeAccess;

}
