package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.format.DateTimeFormat;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.talent.domain.TlTalent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人才台账导入视图对象
 * <p>用于 Excel 批量导入解析；手机号列为明文入参，服务端标准化后加密落库。</p>
 *
 * @author talent
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = TlTalent.class, reverseConvertGenerate = false)
public class TlTalentImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 姓名
     */
    @ExcelProperty(value = "姓名")
    private String name;

    /**
     * 性别（字典 tl_gender）
     */
    @ExcelProperty(value = "性别", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_gender")
    private String gender;

    /**
     * 学历（字典 tl_education）
     */
    @ExcelProperty(value = "学历", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_education")
    private String education;

    /**
     * 应聘/意向岗位
     */
    @ExcelProperty(value = "岗位")
    private String position;

    /**
     * 联系日期
     */
    @DateTimeFormat("yyyy-MM-dd")
    @ExcelProperty(value = "联系日期")
    private LocalDate contactDate;

    /**
     * 归属区域（字典 tl_region）
     */
    @ExcelProperty(value = "区域", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_region")
    private String regionCode;

    /**
     * 人才状态（字典 tl_talent_status）
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_talent_status")
    private String status;

    /**
     * 来源（字典 tl_source）
     */
    @ExcelProperty(value = "来源", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_source")
    private String source;

    /**
     * 期望薪资下限（整数元/月）
     */
    @ExcelProperty(value = "期望薪资下限")
    private Integer expectSalaryMin;

    /**
     * 期望薪资上限（整数元/月）
     */
    @ExcelProperty(value = "期望薪资上限")
    private Integer expectSalaryMax;

    /**
     * 手机号（明文，服务端标准化后加密落库）
     */
    @ExcelProperty(value = "手机号")
    private String phone;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

}
