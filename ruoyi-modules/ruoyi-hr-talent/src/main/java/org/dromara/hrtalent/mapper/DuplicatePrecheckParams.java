package org.dromara.hrtalent.mapper;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才重复预检查询参数（设计文档 §21.15「人才重复预检」）。
 *
 * <p>所有字段都是<b>规范化之后</b>的值：电话/邮箱为 SHA-256 小写十六进制哈希，
 * 姓名/公司/岗位为去除首尾空白后的原值，简历哈希为 {@code hr_talent_resume.file_hash} 同口径值。</p>
 *
 * <p>空字段表示不参与匹配，禁止把空串当作匹配条件传入。</p>
 *
 * @param name             姓名
 * @param currentCompany   当前公司
 * @param schoolName       毕业学校名称
 * @param resumeHash       简历文件哈希
 * @param expectedPosition 期望岗位
 * @param phoneHash        电话标准化哈希
 * @param emailHash        邮箱标准化哈希
 * @author hr-talent
 */
public record DuplicatePrecheckParams(String name,
                                      String currentCompany,
                                      String schoolName,
                                      String resumeHash,
                                      String expectedPosition,
                                      String phoneHash,
                                      String emailHash) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

}
