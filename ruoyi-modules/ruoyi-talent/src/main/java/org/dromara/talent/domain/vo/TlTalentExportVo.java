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
 * 人才台账导出视图对象
 * <p>列顺序严格按实现规范 §6.5；链接列只指向应用入口，绝不出现 OSS URL / 预签名 URL / object_key。</p>
 *
 * @author talent
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = TlTalent.class)
public class TlTalentExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才编号
     */
    @ExcelProperty(value = "人才编号", index = 0)
    private String talentNo;

    /**
     * 岗位
     */
    @ExcelProperty(value = "岗位", index = 1)
    private String position;

    /**
     * 联系日期
     */
    @DateTimeFormat("yyyy-MM-dd")
    @ExcelProperty(value = "联系日期", index = 2)
    private LocalDate contactDate;

    /**
     * 姓名
     */
    @ExcelProperty(value = "姓名", index = 3)
    private String name;

    /**
     * 性别（字典 tl_gender）
     */
    @ExcelProperty(value = "性别", index = 4, converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_gender")
    private String gender;

    /**
     * 学历（字典 tl_education）
     */
    @ExcelProperty(value = "学历", index = 5, converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_education")
    private String education;

    /**
     * 薪资范围（服务端格式化，如 8K-12K）
     */
    @ExcelProperty(value = "薪资范围", index = 6)
    private String salaryText;

    /**
     * 区域（字典 tl_region）
     */
    @ExcelProperty(value = "区域", index = 7, converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_region")
    private String regionCode;

    /**
     * 状态（字典 tl_talent_status）
     */
    @ExcelProperty(value = "状态", index = 8, converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_talent_status")
    private String status;

    /**
     * 重复状态（字典 tl_duplicate_conclusion）
     */
    @ExcelProperty(value = "重复状态", index = 9, converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "tl_duplicate_conclusion")
    private String duplicateStatus;

    /**
     * 人才档案链接（应用入口，含 talentId，不含对象存储信息）
     */
    @ExcelProperty(value = "人才档案链接", index = 10)
    private String profileLink;

    /**
     * 当前简历受控入口（应用入口，下载需二次鉴权，不含对象存储信息）
     */
    @ExcelProperty(value = "当前简历受控入口", index = 11)
    private String resumeLink;

    /**
     * 附件目录入口（应用入口，不含对象存储信息）
     */
    @ExcelProperty(value = "附件目录入口", index = 12)
    private String attachmentLink;

}
