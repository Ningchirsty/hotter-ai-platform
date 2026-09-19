# 实现规范：导入简历直接建档

> 基线：`hotter-ai-platform` 仓库，分支 `feature/talent-library`，模块 `ruoyi-modules/ruoyi-talent`。
> 本文是编码唯一契约，类名/字段名/方法签名以此为准。

---

## 1. 目标与合规口径

在人才档案页新增「导入简历」：上传简历文件 → 服务端**本地**提取字段 → 人工确认 → 直接建档。

**合规边界（重要，必须按此实现）**：

- 本功能**不调用任何外部 AI/OCR 服务**，全部在服务端进程内完成（PDFBox / POI / 正则）。
  数据不出服务器，因此**不触发**设计文档 §7.2 中「解析服务需个人信息处理审批」的前置条件。
- 因此使用**独立开关** `talent.resume-import-enabled`（默认 `true`），
  与后续 AI 解析的 `talent.parse-enabled`（默认 `false`）**互不影响**。
- 抽取结果一律作为**候选值**返回，必须经人工确认才写入主档；禁止静默覆盖。
- 抽取结果同时留痕到 `tl_parse_task` / `tl_parse_field`，`parser_version` 记为 `local-rule-v1`。

---

## 2. 样例分析（结构真实、数值已脱敏）

> ⚠️ **本文件不得写入任何真实简历的个人信息**。下方示例已把姓名/手机/邮箱/地址/学校替换为占位值，
> 仅保留**结构与规则**。真实样例文件与提取结果只放在 git 仓库之外的本地目录用于自测，不得提交。

文件名（结构真实，姓名已脱敏）：

```
【玩具_潮玩_快消品大区销售经理_深圳 8-13K】{姓名} {经验年限}.pdf
```

**文件名规范**（解析优先级最高）：

```
【{岗位段1}_{岗位段2}_..._{城市} {薪资下限}-{薪资上限}K】{姓名} {经验}
```

**注意：岗位是下划线连接的多个段，中间还可能夹着城市，不能用 `【([^_】]+)` 截取**（那样只会得到「玩具」）。
必须用下面的**已验证规则**（已在真实样例上跑通，13/13 字段命中）：

```java
String base = originalFileName.replaceAll("\\.[^.]+$", "");          // 去扩展名
String inner = firstGroup(base, "【([^】]+)】");                       // 玩具_潮玩_快消品大区销售经理_深圳 8-13K
// 尾部「城市 8-13K」：岗位=前面的段（_ 还原为 /），城市与薪资分别取出
Matcher m = Pattern.compile("^(.*?)_?([^_\\s]*)\\s*(\\d+)\\s*-\\s*(\\d+)\\s*[Kk]\\s*$").matcher(inner);
if (m.matches()) {
    position   = String.join("/", m.group(1).split("_"));            // 玩具/潮玩/快消品大区销售经理
    city       = m.group(2);                                        // 深圳
    salaryMin  = Integer.parseInt(m.group(3)) * 1000;               // 8000
    salaryMax  = Integer.parseInt(m.group(4)) * 1000;               // 13000
}
```

无薪资后缀时的兜底：按 `_` 切分，若最后一段是城市名则作为 `city`，其余段 join `/` 作为 `position`。

**样例文件名解析结果**（脱敏）：

| 字段 | 值 |
|---|---|
| position | `玩具/潮玩/快消品大区销售经理` |
| city / regionCode | `深圳` / `SZ` |
| salary | `8000` – `13000` |
| experience | `10年以上` |

正文结构与抽取目标（**数值全部为占位，非真实**）：

```
个人简历
★个人概况
姓 名： {姓名}      性 别：男
学 历：本科         专 业：{专业}
出生年月： {年} 年 {月} 月
邮箱： {邮箱}
通讯地址：{城市}{区}{路}
联系方式：{11 位手机号}
★教育背景
...
```

**规则必须容错**：任何字段抽不到就是「未识别」，绝不能因此报错或阻塞导入。

---

## 3. 字段映射与抽取规则

| 目标字段（`tl_talent`） | 来源优先级 | 规则 | 置信度 |
|---|---|---|---|
| `name` 姓名 | 文件名 → 正文 | 文件名 `】\s*([^\s【】]+)\s*`；正文 `姓\s*名\s*[:：]\s*([^\s性]+)` | 0.85 / 0.90 |
| `gender` 性别 | 正文 | `性\s*别\s*[:：]\s*(男\|女)` → `1`/`2`；否则 `0` | 0.95 |
| `education` 学历 | 正文 | `学\s*历\s*[:：]\s*(\S+)`，按下表映射字典码 | 0.85 |
| `birthDate` 出生日期 | 正文 | `出生年月\s*[:：]\s*(\d{4})\s*年\s*(\d{1,2})?\s*月?` → `YYYY-MM-01`；只有年份时只填年，落在 `remark` 提示 | 0.80 |
| `ageOnly` 识别年龄 | 正文 | 若只有「年龄：xx」而无出生日期 | 0.70 |
| `phone` 手机 | 正文 | `(?<!\d)(1[3-9]\d{9})(?!\d)`；多命中取第一个 | 0.95 |
| `email` 邮箱 | 正文 | 标准邮箱正则 | 0.95 |
| `position` 岗位 | 文件名 | 用 §2 的**已验证规则**：取 `【...】` 内部，剥掉尾部「城市 薪资K」，剩余段以 `_` 分隔后 join `/`。**不要用 `【([^_】]+)`** | 0.90 |
| `regionCode` 区域 | 文件名 → 正文地址 | 文件名中城市名或正文地址含「深圳」→`SZ`、「汕头」→`ST`；无法判定 → 不填（由用户选） | 0.85 / 0.60 |
| `expectSalaryMin/Max` | 文件名 | `(\d+)\s*-\s*(\d+)\s*[Kk]` → 各 ×1000；单值 `(\d+)\s*[Kk]` 时只填 min | 0.90 |
| `experienceText` 经验（不入主档） | 文件名 | `(\d+)\s*年以上\|\d+\s*年经验` | 0.80 |
| `contactDate` 联系日期 | 缺省 | 默认导入当日（服务端填） | — |

**学历字典映射**（`tl_education`）：

| 简历用词 | 字典码 |
|---|---|
| 博士 / 研究生（博士） | `DOCTOR` |
| 硕士 / 研究生 / MBA | `MASTER` |
| 本科 / 学士 / 大学本科 | `BACHELOR` |
| 大专 / 专科 / 高职 | `COLLEGE` |
| 高中 / 中专 / 技校 / 职高 | `HIGH_SCHOOL` |
| 其他可识别但不匹配者 | `OTHER` |

> `email` 与 `experienceText` **不是** `tl_talent` 字段：请放入预览结果供人工查看，
> 并在确认时把 `email` / `experienceText` 追加进 `remark`（格式：`邮箱：xxx；经验：10年以上`），
> 不得新增主档列。

---

## 4. 流程与接口

```
① POST /talent/profile/import/preview   (multipart/form-data: file)
   → 校验开关/权限/扩展名/大小
   → 写临时文件（talent.import-temp-dir，默认 ${java.io.tmpdir}/talent-import）
   → 本地提取 + 规则抽取
   → 返回 { importToken, originalName, ext, size, textLength, textExtracted,
            candidates: [ {field, label, value, confidence, source, hint} ], warnings[] }

② POST /talent/profile/import/confirm   (application/json: ResumeImportConfirmBo)
   → 按 importToken 取回临时文件
   → 执行与新增完全一致的校验：可写区域、手机号规范化与哈希、重复预检
   → 建档（复用 ITalentProfileService 的创建逻辑）
   → 把简历上传到 talent-private/{talentId}/{attachmentId}/v1/original{ext}
     并插入 tl_talent_attachment（attachment_type=RESUME, is_current='1', version=1,
     scan_status 按 talent.virus-scan-enabled）
   → 写 tl_parse_task(parser_version='local-rule-v1') + tl_parse_field 留痕
   → 删除临时文件，写 UPLOAD 审计
   → 返回 { talentId, attachmentId }
```

**`importToken` 设计**：服务端生成的不可猜 UUID（`IdUtil.fastSimpleUUID()`），
仅用于定位临时文件；临时文件命名 `<token>.<ext>`，**不落数据库**。
有效期 `talent.import-temp-ttl-minutes`（默认 30）；确认成功后立即删除；
超过 TTL 的文件在下次调用 preview 时顺带清理（不要引入定时任务）。

---

## 5. 新增/修改的文件清单

### 5.1 后端（`ruoyi-modules/ruoyi-talent/`）

| 文件 | 说明 |
|---|---|
| `pom.xml` | **新增依赖** `org.apache.pdfbox:pdfbox:3.0.7`（父 pom 未管理版本，需显式写；POI 已由 fesod-sheet 传递提供，无需声明） |
| `config/TalentProperties.java` | 新增 `resumeImportEnabled`(默认 true)、`importTempDir`(默认 `${java.io.tmpdir}/talent-import`)、`importTempTtlMinutes`(默认 30)、`importMaxTextLength`(默认 200000) |
| `constant/TalentConstants.java` | 新增 `PERM_PROFILE_IMPORT = "talent:profile:import"`；新增 `PARSER_VERSION_LOCAL = "local-rule-v1"` |
| `helper/TalentResumeExtractor.java` | **核心**：文本提取 + 规则抽取 |
| `domain/vo/ResumeImportPreviewVo.java` | 预览结果 |
| `domain/vo/ResumeFieldCandidateVo.java` | 单字段候选 |
| `domain/bo/ResumeImportConfirmBo.java` | 确认入参 |
| `service/ITalentResumeImportService.java` | 预览 / 确认 |
| `service/impl/TalentResumeImportServiceImpl.java` | 实现 |
| `controller/TalentResumeImportController.java` | 两个接口 |

### 5.2 前端（`frontend/`）

| 文件 | 说明 |
|---|---|
| `src/api/talent/profile/index.ts` | 新增 `previewResumeImport(file)`、`confirmResumeImport(data)` |
| `src/api/talent/profile/types.ts` | 新增 `ResumeImportPreviewVO`、`ResumeFieldCandidateVO`、`ResumeImportConfirmForm` |
| `src/views/talent/profile/index.vue` | 工具栏加「导入简历」按钮（`v-hasPermi="['talent:profile:import']"`）+ 预览确认弹窗 |

### 5.3 SQL

| 文件 | 说明 |
|---|---|
| `script/sql/ry_talent_menu.sql` | 新增按钮菜单 `talent:profile:import`（menu_id `1762000000000001107`，parent=`1762000000000000101`，type `F`）+ 绑定到 `talent_admin`/`talent_hr_group`/`talent_hr_sz`/`talent_hr_st` |
| `script/sql/ry_talent_migration.sql` | 同步追加同样内容（`insert ignore into`，保持幂等） |

---

## 6. 类签名（不得改动）

```java
// helper/TalentResumeExtractor.java
@Component
@RequiredArgsConstructor
public class TalentResumeExtractor {
    /** 从文件名 + 文件内容抽取候选字段。任何字段抽不到都返回空候选，不抛异常。 */
    public ResumeImportPreviewVo extract(String originalFileName, byte[] bytes, String ext);
    /** 仅提取纯文本；不支持的类型或提取失败返回空串，不抛异常。 */
    public String extractText(byte[] bytes, String ext);
}

// ResumeFieldCandidateVo：field, label, value(String), confidence(Double 0-1), source(FILENAME|TEXT|DEFAULT), hint(String)
// ResumeImportPreviewVo：importToken, originalName, ext, size(Long), textExtracted(Boolean),
//                        textLength(Integer), candidates(List<ResumeFieldCandidateVo>), warnings(List<String>)
// ResumeImportConfirmBo：importToken(@NotBlank), TlTalentBo talent(@NotNull @Valid)
//   说明：确认时以 talent 里的值为准（用户可改），未填字段不回填服务端抽取值——
//   因此前端必须把预览值写进表单再提交。

// service/ITalentResumeImportService.java
public interface ITalentResumeImportService {
    ResumeImportPreviewVo preview(MultipartFile file);
    /** 返回 [talentId, attachmentId] */
    Long[] confirm(ResumeImportConfirmBo bo);
}

// controller/TalentResumeImportController.java  @RequestMapping("/talent/profile/import")
@SaCheckPermission(TalentConstants.PERM_PROFILE_IMPORT) @RepeatSubmit @PostMapping("/preview")
public R<ResumeImportPreviewVo> preview(@RequestPart("file") MultipartFile file)
@SaCheckPermission(TalentConstants.PERM_PROFILE_ADD) @RepeatSubmit @PostMapping("/confirm")
public R<Long[]> confirm(@Valid @RequestBody ResumeImportConfirmBo bo)
```

`TlTalentBo` 需支持接收 `email`、`experienceText`（用于写 remark）——**若 `TlTalentBo` 已有 `remark`，
则前端把「邮箱/经验」拼进 `remark` 提交即可，后端不新增字段**。请按此实现，不要改 `TlTalentBo`。

---

## 7. 硬性约束

1. **必须复用既有安全逻辑**：确认建档走 `ITalentProfileService` 的创建路径
   （或调用其等价的校验：`TalentScopeHelper.canWriteRegion`、`TalentPhoneHelper.normalize/hash/tail4`、重复预检）。
   禁止绕过这些校验另写一套建档 SQL。
2. **禁止调用外部服务**：不得引入任何 AI/OCR 依赖，不发任何外部 HTTP 请求。
3. **临时文件必须清理**：确认成功、确认失败、以及超 TTL 三条路径都要清理。
4. **扩展名白名单**沿用 `TalentConstants.ALLOWED_EXT`（pdf/docx/doc/jpg/jpeg/png）；
   其中 **jpg/jpeg/png 无法提取文本**，此时 `textExtracted=false`，只做文件名解析，
   并在 `warnings` 里明确提示「图片型简历无法提取文字，请手工补全」。
5. **不得记录简历正文到日志**（设计文档 §10 硬约束）。日志只允许记录文件名、字节数、抽取到的字段名与数量。
6. 所有新增类/方法写中文 Javadoc；注释使用中文。
7. 前端弹窗必须**逐字段显示来源与置信度**，低置信度（<0.8）用醒目样式提示人工核对。
8. 前端不得渲染任何对象存储地址。
