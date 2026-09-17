# D — Excel 导入导出 与 新业务模块接入方式（RuoYi-Vue-Plus v6.0.0 源码侦察）

> 侦察对象：`D:\DeepseekHarness\RuoYi-Vue-Plus`（v6.0.0，commit 7180b52）。
> 下文所有 `相对路径:行号` 均相对于 `D:\DeepseekHarness\RuoYi-Vue-Plus\`。
> 本文件为只读侦察结论，未修改被侦察目录中的任何文件。
> 版本确认：`ruoyi-common/ruoyi-common-bom/pom.xml:17`（`<revision>6.0.0</revision>`）。

---

## 1. Excel 模块：依赖坐标与注解写法

### 1.1 真实依赖坐标（Apache Fesod）

`ruoyi-common-excel` 自身只声明 `org.dromara:ruoyi-common-json` 与 `org.apache.fesod:fesod-sheet`：

`ruoyi-common/ruoyi-common-excel/pom.xml:18-30`
```xml
<dependencies>
    <!-- 序列化模块 -->
    <dependency>
        <groupId>org.dromara</groupId>
        <artifactId>ruoyi-common-json</artifactId>
    </dependency>
    <!-- excel -->
    <dependency>
        <groupId>org.apache.fesod</groupId>
        <artifactId>fesod-sheet</artifactId>
    </dependency>
</dependencies>
```

**Fesod 坐标 = `org.apache.fesod:fesod-sheet`，版本由根 pom 属性统一管理：`2.0.2-incubating`。**

`pom.xml:54`
```xml
<fesod.version>2.0.2-incubating</fesod.version>
```

`pom.xml:188-193`
```xml
<!-- fesod (EasyExcel/FastExcel的前身) 的依赖 -->
<dependency>
    <groupId>org.apache.fesod</groupId>
    <artifactId>fesod-sheet</artifactId>
    <version>${fesod.version}</version>
</dependency>
```

`ruoyi-common-excel` 的版本在 `ruoyi-common-bom` 中统一管理，业务模块引用时**只写 groupId + artifactId，不写 version**：

`ruoyi-common/ruoyi-common-bom/pom.xml:36-41`
```xml
<!-- excel -->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-common-excel</artifactId>
    <version>${revision}</version>
</dependency>
```

根 pom 通过 import 方式引入该 BOM：`pom.xml:158-165`
```xml
<!-- common 的依赖配置-->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-common-bom</artifactId>
    <version>${revision}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### 1.2 注解：哪些来自 Fesod、哪些是本项目扩展

**Fesod 原生注解**（包名 `org.apache.fesod.sheet.annotation`，用法与 EasyExcel 同名注解一致）：
- `@ExcelProperty(value = "列名", index = 0, converter = Xxx.class)`
- `@ExcelIgnoreUnannotated`（类级：未标注 `@ExcelProperty` 的字段不导出）
- `@ExcelIgnore`（字段级排除）
- `@DateTimeFormat("yyyy-MM-dd HH:mm:ss")`

`ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/domain/vo/TestDemoVo.java:5,26,78`
```java
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
...
@ExcelIgnoreUnannotated
...
@DateTimeFormat("yyyy-MM-dd HH:mm:ss")
```

**本项目扩展注解**（包名 `org.dromara.common.excel.annotation`），共 6 个：

| 注解 | 文件:行 | 关键属性 |
|---|---|---|
| `@ExcelDictFormat` | `ruoyi-common/ruoyi-common-excel/src/main/java/org/dromara/common/excel/annotation/ExcelDictFormat.java:15` | `dictType`、`readConverterExp`、`separator` |
| `@ExcelEnumFormat` | `.../annotation/ExcelEnumFormat.java:13` | `enumClass`、`codeField`(默认 code)、`textField`(默认 text) |
| `@ExcelRequired` | `.../annotation/ExcelRequired.java:17` | `fontColor`(默认 `IndexedColors.RED`) |
| `@ExcelNotation` | `.../annotation/ExcelNotation.java:15` | `value`（单元格批注，仅单级表头） |
| `@CellMerge` | `.../annotation/CellMerge.java:17` | `index`、`mergeBy` |
| `@ExcelDynamicOptions` | `.../annotation/ExcelDynamicOptions.java:15` | `providerClass`（实现 `ExcelOptionsProvider`） |

`ExcelDictFormat` 定义（`.../annotation/ExcelDictFormat.java:12-27`）：
```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ExcelDictFormat {
    String dictType() default "";
    String readConverterExp() default "";
    String separator() default StringUtils.SEPARATOR;
}
```

**准确写法范例**（导出 VO，同时含字典、枚举、忽略未标注）：

`ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/domain/vo/ExportDemoVo.java:26-60`
```java
@Data
@ExcelIgnoreUnannotated
@AllArgsConstructor
@NoArgsConstructor
public class ExportDemoVo implements Serializable {
    @ExcelProperty(value = "用户昵称", index = 0)
    private String nickName;

    @ExcelProperty(value = "用户类型", index = 1, converter = ExcelEnumConvert.class)
    @ExcelEnumFormat(enumClass = UserStatus.class, textField = "info")
    private String userStatus;

    @ExcelProperty(value = "性别", index = 2, converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_user_gender")
    private String gender;
```

> 列宽：**不使用注解**，通过 `ExcelBuilder.columnWidth(Integer)` 全局设置（`.../utils/ExcelBuilder.java:320-329`），未设置时自动匹配列宽。
> 日期：字段上使用 Fesod 的 `@DateTimeFormat`（见上）。

---

## 2. 导出：`ExcelBuilder` 能力与 Controller 标准写法

### 2.1 静态入口方法签名

`ruoyi-common/ruoyi-common-excel/src/main/java/org/dromara/common/excel/utils/ExcelBuilder.java:182,192,202,213`
```java
public static <T> ExcelBuilder<T> of(List<T> data, Class<T> headType)      // 常规导出
public static <T> ExcelBuilder<T> writer(Class<T> headType)                // 自定义写出(多 sheet)
public static TemplateBuilder template(String templatePath)                // 模板填充导出
public static <T> ReadBuilder<T> read(InputStream is, Class<T> clazz)      // 导入读取
```

### 2.2 实例方法（链式）

`.../utils/ExcelBuilder.java`：
- `sheetName(String)` :220、`sheetNo(Integer)` :228、`merge()` :236、`options(List<DropDownOptions>)` :251
- `password(String)` :259、`needHead(boolean)` :267、`automaticMergeHead(boolean)` :275
- `includeFields/excludeFields/includeIndexes/excludeIndexes` :283/291/299/307
- `columnWidth(Integer)` :323、`rowHeight(short, short)` :334
- `registerWriteHandler(WriteHandler)` :346、`registerConverter(Converter<?>)` :360
- `zip()` :374、`zip(int pageSize)` :383
- 终止方法：`toResponse(HttpServletResponse)` :395、`toStream(OutputStream)` :412、`toStream(OutputStream, Consumer<ExcelWriterWrapper<T>>)` :422、`toResponse(HttpServletResponse, Consumer<ExcelWriterWrapper<T>>)` :433

### 2.3 Controller 导出接口标准写法

`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysPostController.java:62-68`
```java
@Log(title = "岗位管理", businessType = BusinessType.EXPORT)
@SaCheckPermission("system:post:export")
@PostMapping("/export")
public void export(SysPostBo post, HttpServletResponse response) {
    List<SysPostVo> list = postService.selectPostList(post);
    ExcelBuilder.of(list, SysPostVo.class).sheetName("岗位数据").toResponse(response);
}
```

要点：方法返回 `void`、参数带 `HttpServletResponse`、导出前先 `@SaCheckPermission("xxx:export")`、`@Log(..., businessType = BusinessType.EXPORT)`。
响应头与文件名由构造器内部处理（`.../ExcelBuilder.java:620-633`，文件名会加 UUID 前缀 `IdUtil.fastSimpleUUID() + "_" + filename + ".xlsx"`）。

### 2.4 多 sheet

`ExcelBuilder.of(...)` 只能写单 sheet。多 sheet 使用 `writer(...)` + `toResponse(response, consumer)` + `ExcelWriterWrapper.sheetBuilder(...)`：

`ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/service/impl/ExportExcelServiceImpl.java:266-292`
```java
public void customExport(HttpServletResponse response) {
    ExcelBuilder.writer(ExportDemoVo.class).sheetName("自定义导出").toResponse(response, wrapper -> {
        ...
        WriteSheet sheet = ExcelWriterWrapper.sheetBuilder("自定义导出demo").build();
        wrapper.write(excelDataList, sheet);
        ...
    });
}
```
`ExcelWriterWrapper` 还提供 `sheetBuilder(Integer)`、`tableBuilder(...)`、`fill(...)` 等（`.../utils/ExcelWriterWrapper.java:135-245`）。

### 2.5 大数据量 ZIP 打包：有，但**单线程**

`zip(int pageSize)` 按 pageSize 把数据切分成多个 xlsx 放进一个 zip（默认 999 条/文件）：

`.../utils/ExcelBuilder.java:383-390`
```java
public ExcelBuilder<T> zip(int pageSize) {
    if (pageSize <= 0) {
        throw new IllegalArgumentException("pageSize 必须大于 0");
    }
    this.zip = true;
    this.pageSize = pageSize;
    return this;
}
```

`.../utils/ExcelBuilder.java:545-564`（分页 + 顺序写入 zip）：
```java
List<List<T>> pageList = ListUtil.partition(data, pageSize);
...
for (int i = 0; i < pageList.size(); i++) {
    int pageNum = i + 1;
    String exportSheetName = sheetName + "第" + pageNum + "页";
    byte[] bytes = buildZipEntry(pageList.get(i), exportSheetName);
    zipOut.putNextEntry(new ZipEntry(exportSheetName + ".xlsx"));
    zipOut.write(bytes);
    zipOut.closeEntry();
}
```

**重要限制**：`zip` 只能走 `toResponse(HttpServletResponse)`：`toStream` 会直接抛异常（`.../utils/ExcelBuilder.java:412-417`）：
```java
public void toStream(OutputStream outputStream) {
    if (zip) {
        throw new UnsupportedOperationException("ZIP导出请使用 toResponse(HttpServletResponse)");
    }
    writeSheet(outputStream);
}
```
> 「大数据量**多线程** ZIP 打包」在 `ExcelBuilder` 中**未确认存在**：源码未见任何线程池/并行调用（仅顺序 for 循环）。若需并行，可自行用 `ThreadUtils`（见 §4）并行生成各分片字节，但需自建 ZIP 响应逻辑。

### 2.6 自定义表头与单元格内容（含超链接列）

可用的扩展点：
1. `registerWriteHandler(WriteHandler)`（`.../ExcelBuilder.java:346`）注册 Fesod 的 `WriteHandler` 实现；框架自身就用它实现批注/必填样式：
   `.../handler/DataWriteHandler.java:30` — `public class DataWriteHandler implements SheetWriteHandler, CellWriteHandler`，`afterCellDispose(CellWriteHandlerContext)` :59。
2. `registerConverter(Converter<?>)` 自定义列值转换（如字典/枚举）。
3. `writer(...).toResponse(response, consumer)` 完全自定义写出（多 sheet/多 table）。
4. 模板导出 `ExcelBuilder.template("excel/xxx.xlsx").data(...)/.multiList(...)/.multiSheet(...)`（见 `.../ExcelBuilder.java:931-1073`，样例 `TestExcelController.java:50,87,145`）。

**「人才档案链接」列结论**：
- 作为**字符串列**（把完整 URL 当文本写入）→ 直接 `@ExcelProperty(value = "人才档案链接")` + String 字段即可，无额外代码。**证据充分。**
- 作为**真正的 Excel 超链接**（可点击）→ 项目全仓库检索 `Hyperlink`（`*.java`，排除 `target`）**零命中**，无现成范例；Fesod 是否暴露超链接 API、以及本项目 2.0.2-incubating 版本的确切类名，**未确认**（缺少 Fesod 源码/jar 内类清单证据，且被禁止查看仓库外目录）。可行路径是自定义 `CellWriteHandler` 并通过 `registerWriteHandler(...)` 注册，但需在本地依赖上核实 API。

---

## 3. 导入：接口写法、校验与错误回显、`ExcelResult`

### 3.1 Controller 导入标准写法

`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysUserController.java:96-104`
```java
@Log(title = "用户管理", businessType = BusinessType.IMPORT)
@SaCheckPermission("system:user:import")
@PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<Void> importData(@RequestPart("file") MultipartFile file, boolean updateSupport) throws Exception {
    ExcelResult<SysUserImportVo> result = ExcelBuilder.read(file.getInputStream(), SysUserImportVo.class)
        .listener(new SysUserImportListener(updateSupport))
        .doRead();
    return R.ok(result.getAnalysis());
}
```

导入模板下载接口（导出空集合，只出表头）：

`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysUserController.java:111-114`
```java
@PostMapping("/importTemplate")
public void importTemplate(HttpServletResponse response) {
    ExcelBuilder.of(new ArrayList<>(), SysUserImportVo.class).sheetName("用户数据").toResponse(response);
}
```

`ReadBuilder` 其他可用开关：`validate(boolean)`、`failFast(boolean)`、`headRowNumber`、`ignoreEmptyRow`、`autoTrim`、`autoStrip`、`numRows`、`password`、`registerConverter`、`sheetNo/sheetName`；终止方法 `doRead()/doReadSync()/doReadAll()/doReadAllSync()`（`.../ExcelBuilder.java:724-865`）。

### 3.2 校验与错误回显

默认监听器逐行做 Jakarta Validation 校验，并在异常时累积错误信息；`failFast=true` 时立刻抛 `ExcelAnalysisException`：

`ruoyi-common/ruoyi-common-excel/src/main/java/org/dromara/common/excel/core/DefaultExcelListener.java:100-103,125-129`
```java
excelResult.getErrorList().add(errMsg);
if (failFast) {
    throw new ExcelAnalysisException(errMsg);
}
...
public void invoke(T data, AnalysisContext context) {
    if (isValidate) {
        ValidatorUtils.validate(data);
    }
    excelResult.getList().add(data);
}
```

业务级失败回显：自定义监听器在 `getAnalysis()` 里抛 `ServiceException`，由全局异常处理统一回给前端。

`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/listener/SysUserImportListener.java:33-33,134-142`
```java
public class SysUserImportListener extends AnalysisEventListener<SysUserImportVo> implements ExcelListener<SysUserImportVo> {
    ...
    @Override
    public String getAnalysis() {
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }
```

### 3.3 `ExcelResult` 结构

`ruoyi-common/ruoyi-common-excel/src/main/java/org/dromara/common/excel/core/ExcelResult.java:10-25`
```java
public interface ExcelResult<T> {
    /** 对象列表 */
    List<T> getList();
    /** 错误列表 */
    List<String> getErrorList();
    /** 导入回执 */
    String getAnalysis();
}
```

默认实现 `DefaultExcelResult`（`.../core/DefaultExcelResult.java:15-96`）持有 `list` + `errorList`，`getAnalysis()` 生成「共N条，成功导入X条，错误Y条」文案（:84-95）。
监听器侧接口 `ExcelListener<T>`（`.../core/ExcelListener.java`），基类 `DefaultExcelListener<T>`，自定义监听器可继承它并覆写 `invoke` 直接操作 `getExcelResult().getList()`（范例 `ruoyi-demo/.../listener/ExportDemoListener.java:19-76`）。

导入 VO 注解范例（字典 + 动态下拉）：`ruoyi-modules/ruoyi-system/.../domain/vo/SysUserImportVo.java:38-70`。

---

## 4. 异步导出：框架现状与可复用能力

### 4.1 结论：**没有现成的异步导出实现，也没有导出任务表**

- 全仓库检索 `AsyncExport|ExportTask|导出任务|export_task|exportTask`（`*.java`、`*.sql`，排除 `target`）→ **零命中**。
- `script/sql/*.sql` 中与导出相关的只有 `sys_menu` 权限按钮记录（例如 `script/sql/ry_vue.sql:254`），**无导出任务表**。
- 因此 talent-library 的异步台账导出需要**自己新增**异步执行 + 结果落地能力。

### 4.2 可复用的能力一：Spring 线程池 + `@Async`（推荐）

框架已开启异步：`ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/config/ApplicationConfig.java:12-15`
```java
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableAsync(proxyTargetClass = true)
public class ApplicationConfig {
}
```

线程池由 Spring Boot 3.5 自带，配置在 `ruoyi-admin/src/main/resources/application.yml:53-59`
```yaml
  task:
    execution:
      # 从 springboot 3.5 开始 spring自带线程池
      # 不再需要 AsyncConfig与ThreadPoolConfig 可直接注入线程池使用
      thread-name-prefix: async-
      # 由spring自己初始化线程池
      mode: force
```
现成范例：`ruoyi-modules/ruoyi-system/.../service/impl/SysOperLogServiceImpl.java:44-46`
```java
@Async
@EventListener
public void recordOper(OperLogEvent operLogEvent) {
```

### 4.3 可复用的能力二：调度线程池与虚拟线程工具

`ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/config/ThreadPoolConfig.java:34-35`
```java
@Bean(name = "scheduledExecutorService")
protected ScheduledExecutorService scheduledExecutorService() {
```
`ruoyi-common/.../core/utils/ThreadUtils.java:26,53` 提供 `virtualInvokeAll(Runnable...)` / `virtualSubmitAll(Supplier<T>...)`（`Executors.newVirtualThreadPerTaskExecutor()`，:28）。

### 4.4 可复用的能力三：SnailJob 分布式任务（后台定时/重试）

- 客户端开关：`ruoyi-common/ruoyi-common-job/src/main/java/org/dromara/common/job/config/SnailJobConfig.java:21-25`
```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "snail-job", name = "enabled", havingValue = "true")
@EnableScheduling
@EnableSnailJob
public class SnailJobConfig {
```
- 配置位置与开关默认值：`ruoyi-admin/src/main/resources/application-dev.yml:14-20`（`snail-job.enabled: false`）
- 任务范例目录：`ruoyi-modules/ruoyi-job/src/main/java/org/dromara/job/snailjob/`（如 `SummaryBillTask.java`、`TestAnnoJobExecutor.java`）
- 任务相关库表在 `script/sql/ry_job.sql`（SnailJob 自带 `sj_*` 表），**不是导出任务表**。

### 4.5 可复用的能力四：导出结果落地（OSS）

异步导出产物通常要落到对象存储再回传 URL：
`ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/client/OssClient.java:471`
```java
PutObjectResult upload(String key, InputStream in, long contentLength);
```
（同文件 :481 `upload(String key, byte[] data)`；上传相关异步执行器配置见 `.../oss/config/OssAsyncExecutorConfig.java:18-37`，默认启用虚拟线程。）

> **落地注意**：`ExcelBuilder.zip()` 只能写 `HttpServletResponse`（§2.5），因此「异步导出 + ZIP 分片」不能直接复用 `zip()` 写文件/OSS；异步路径应使用 `toStream(OutputStream)` / `toStream(OutputStream, Consumer)` 自行产出字节，再上传 OSS。

---

## 5. 代码生成器：模板位置、清单与产出目录

> 注意版本事实修正：6.0.0 的模板**目录名**是 `fm`，模板文件**后缀是 `.ftl`**（FreeMarker），不是扩展名 `.fm`。

### 5.1 模板根与清单

`ruoyi-modules/ruoyi-gen/src/main/java/org/dromara/gen/constant/GenConstants.java:276-302`
```java
String TEMPLATE_ROOT_PATH = "fm";
String TEMPLATE_FILE_SUFFIX = ".ftl";
String TEMPLATE_RESOURCE_PREFIX = "classpath*:";
String JAVA_TEMPLATE_ROOT_PATH = TEMPLATE_ROOT_PATH + "/java";
String JAVA_DOMAIN_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/domain.java" + TEMPLATE_FILE_SUFFIX;
String JAVA_VO_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/vo.java" + TEMPLATE_FILE_SUFFIX;
String JAVA_BO_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/bo.java" + TEMPLATE_FILE_SUFFIX;
String JAVA_MAPPER_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/mapper.java" + TEMPLATE_FILE_SUFFIX;
String JAVA_SERVICE_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/service.java" + TEMPLATE_FILE_SUFFIX;
String JAVA_SERVICE_IMPL_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/serviceImpl.java" + TEMPLATE_FILE_SUFFIX;
String JAVA_CONTROLLER_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/controller.java" + TEMPLATE_FILE_SUFFIX;
String XML_TEMPLATE_ROOT_PATH = TEMPLATE_ROOT_PATH + "/xml";
String XML_MAPPER_TEMPLATE_PATH = XML_TEMPLATE_ROOT_PATH + "/mapper.xml" + TEMPLATE_FILE_SUFFIX;
```

实际模板清单（`ruoyi-modules/ruoyi-gen/src/main/resources/fm/`）：

| 分类 | 文件 |
|---|---|
| 后端 Java | `java/domain.java.ftl`、`vo.java.ftl`、`bo.java.ftl`、`mapper.java.ftl`、`service.java.ftl`、`serviceImpl.java.ftl`、`controller.java.ftl` |
| MyBatis XML | `xml/mapper.xml.ftl` |
| SQL | `sql/mysql.sql.ftl`、`oracle.sql.ftl`、`postgres.sql.ftl`、`sqlserver.sql.ftl` |
| Vue 前端 | `vue/api.ts.ftl`、`vue/types.ts.ftl`、`vue/index.vue.ftl`、`vue/index-tree.vue.ftl` |
| React 前端 | `react/api.ts.ftl`、`react/types.ts.ftl`、`react/index.tsx.ftl`、`react/index-tree.tsx.ftl` |

模板注册与选择逻辑：`ruoyi-modules/ruoyi-gen/src/main/java/org/dromara/gen/util/TemplateEngineUtils.java:289-323`（后端 7 个 + xml + 前端 api/types + SQL + 页面）。
模板引擎为 Hutool + FreeMarker：`.../util/TemplateEngineUtils.java:62-69`。

### 5.2 生成产出的目录/包结构（权威约定）

`.../util/TemplateEngineUtils.java:43-48`
```java
private static final String PROJECT_PATH = "main/java";
private static final String MYBATIS_PATH = "main/resources/mapper";
```

`.../util/TemplateEngineUtils.java:358-379`
```java
String javaPath = PROJECT_PATH + "/" + StringUtils.replace(packageName, ".", "/");
String mybatisPath = MYBATIS_PATH + "/" + moduleName;
...
fileName = StringUtils.format("{}/domain/{}.java", javaPath, className);
fileName = StringUtils.format("{}/domain/vo/{}Vo.java", javaPath, className);
fileName = StringUtils.format("{}/domain/bo/{}Bo.java", javaPath, className);
fileName = StringUtils.format("{}/mapper/{}Mapper.java", javaPath, className);
fileName = StringUtils.format("{}/service/I{}Service.java", javaPath, className);
fileName = StringUtils.format("{}/service/impl/{}ServiceImpl.java", javaPath, className);
fileName = StringUtils.format("{}/controller/{}Controller.java", javaPath, className);
fileName = StringUtils.format("{}/{}Mapper.xml", mybatisPath, className);
```

前端（Vue）产出路径：`.../util/TemplateEngineUtils.java:382-391`
```java
fileName = StringUtils.format("{}/api/{}/{}/index.ts", frontendPath, moduleName, businessName);
fileName = StringUtils.format("{}/api/{}/{}/types.ts", frontendPath, moduleName, businessName);
fileName = StringUtils.format("{}/{}/{}/{}/index.{}", frontendPath, frontendPagePath, moduleName, businessName, ...);
```
其中 `frontendPath` 解析为模板目录名（Vue 为 `vue`，`.../util/TemplateEngineUtils.java:452-458`、`GenConstants.java:86`），`frontendPagePath` 对 Vue 为 `views`（`.../util/TemplateEngineUtils.java:412-414`）。
即 Vue 产物目录为：
- `vue/api/{moduleName}/{businessName}/index.ts`、`types.ts`
- `vue/views/{moduleName}/{businessName}/index.vue`（树表为 `index-tree.vue`）

生成器默认包名与表前缀：`ruoyi-modules/ruoyi-gen/src/main/resources/generator.yml:2-10`
```yaml
gen:
  # 作者
  author: Lion Li
  # 默认生成包路径 system 需改成自己的模块名称 如 system monitor tool
  packageName: org.dromara.system
  # 自动去除表前缀，默认是false
  autoRemovePre: false
  # 表前缀（生成类名不会包含表前缀，多个用逗号分隔）
  tablePrefix: sys_
```

---

## 6. 新模块接入方式（以 `ruoyi-system` / `ruoyi-demo` 为样板）

### 6.1 需要改动的文件清单（含真实行号）

**(a) 在 `ruoyi-modules/pom.xml` 注册 module**

`ruoyi-modules/pom.xml:18-25`
```xml
<modules>
    <module>ruoyi-demo</module>
    <module>ruoyi-gen</module>
    <module>ruoyi-job</module>
    <module>ruoyi-system</module>
    <module>ruoyi-workflow</module>
    <module>ruoyi-ai</module>
</modules>
```

**(b) 在根 `pom.xml` 的 `dependencyManagement` 为新模块声明 `${revision}`**（否则 `ruoyi-admin` 引用时无法省略 version）

`pom.xml:411-416`（`ruoyi-system` 范例）
```xml
<!-- 系统模块 -->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-system</artifactId>
    <version>${revision}</version>
</dependency>
```

**(c) 新建模块自身 `pom.xml`**，parent 为 `ruoyi-modules`，依赖写法**不写 version**（BOM/根 pom 管理）。

`ruoyi-modules/ruoyi-system/pom.xml:5-29`
```xml
<parent>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-modules</artifactId>
    <version>${revision}</version>
</parent>
...
    <artifactId>ruoyi-system</artifactId>
...
    <!-- 通用工具-->
    <dependency>
        <groupId>org.dromara</groupId>
        <artifactId>ruoyi-common-core</artifactId>
    </dependency>
    <!-- api模块 -->
    <dependency>
        <groupId>org.dromara</groupId>
        <artifactId>ruoyi-api</artifactId>
    </dependency>
```
业务模块通常还需：`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-excel`（:61-65）、`ruoyi-common-log`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-redis`、`ruoyi-common-translation`、`ruoyi-common-oss` 等（完整样板见 `ruoyi-modules/ruoyi-demo/pom.xml:18-128`）。

`ruoyi-api` 自身坐标定义：`ruoyi-api/pom.xml:12`（`<artifactId>ruoyi-api</artifactId>`，依赖仅 `ruoyi-common-core` :20-23），版本在根 pom 管理：`pom.xml:453-458`。

**(d) 在 `ruoyi-admin/pom.xml` 加入模块依赖**（启动模块可见性）

`ruoyi-admin/pom.xml:77-99`
```xml
<!-- 系统模块 -->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-system</artifactId>
</dependency>
<!-- 调度任务模块 -->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-job</artifactId>
</dependency>
<!-- AI业务模块 -->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-ai</artifactId>
</dependency>
<!--  demo模块  -->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-demo</artifactId>
</dependency>
```

**(e) 启动类：`ruoyi-admin` 的启动类本身无需改**

`ruoyi-admin/src/main/java/org/dromara/DromaraApplication.java:13-14`
```java
@SpringBootApplication
public class DromaraApplication {
```
只要新模块的包名使用 `org.dromara.*` 前缀（与 `DromaraApplication` 同根），`@SpringBootApplication` 的组件扫描即可覆盖，无需额外 `scanBasePackages`。

**(f) 包名约定**

模块 Java 包 = `org.dromara.{模块名}`，内部固定子包 `controller / service / service.impl / mapper / domain / domain.bo / domain.vo`（样板见 `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/` 目录：controller、domain、event、listener、mapper、runner、service）。
`ruoyi-demo` 的 `mapper` 包声明：`ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/mapper/package-info.java`。

**(g) Mapper 扫描：不需要 `@MapperScan`，由全局配置覆盖**

`ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/config/MybatisPlusConfig.java:33-38`
```java
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan("${mybatis-plus.mapperPackage}")
@PropertySource(value = "classpath:common-mybatis.yml", factory = YmlPropertySourceFactory.class)
@EnableConfigurationProperties(SqlLogProperties.class)
public class MybatisPlusConfig {
```

对应配置（`ruoyi-admin/src/main/resources/application.yml:117-125`）
```yaml
mybatis-plus:
  # 自定义配置 是否全局开启逻辑删除 关闭后 所有逻辑删除功能将失效
  enableLogicDelete: true
  # 多包名使用 例如 org.dromara.**.mapper,org.xxx.**.mapper
  mapperPackage: org.dromara.**.mapper
  # 对应的 XML 文件位置
  mapperLocations: classpath*:mapper/**/*Mapper.xml
  # 实体扫描，多个package用逗号或者分号分隔
  typeAliasesPackage: org.dromara.**.domain
```
结论：新模块只要满足 `org.dromara.{module}.mapper` 包名与 `classpath:mapper/**/*Mapper.xml` 路径，即被自动扫描；**无需改配置、无需加注解**。

**(h) Mapper XML 落位约定**

`resources/mapper/{moduleName}/{ClassName}Mapper.xml`（与生成器 `MYBATIS_PATH + "/" + moduleName` 一致，见 §5.2）。
样板：`ruoyi-modules/ruoyi-demo/src/main/resources/mapper/demo/TestDemoMapper.xml`、`ruoyi-modules/ruoyi-gen/src/main/resources/mapper/gen/GenTableMapper.xml`。

### 6.2 新模块接入改动清单（文件级）

1. `ruoyi-modules/pom.xml` — `<modules>` 增加 `<module>ruoyi-talent</module>`。
2. `pom.xml`（根）— `dependencyManagement` 增加 `org.dromara:ruoyi-talent:${revision}`。
3. `ruoyi-modules/ruoyi-talent/pom.xml` — 新建，parent = `ruoyi-modules`；依赖 `ruoyi-common-core`、`ruoyi-api`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-excel`、`ruoyi-common-log`、`ruoyi-common-security`、`ruoyi-common-web`（按需加 redis/oss）。
4. `ruoyi-admin/pom.xml` — 增加 `org.dromara:ruoyi-talent` 依赖。
5. `ruoyi-admin/src/main/java/org/dromara/DromaraApplication.java` — **无需修改**。
6. Java 源码包 `org.dromara.talent.{controller,service,service.impl,mapper,domain,domain.bo,domain.vo}`。
7. Mapper XML：`ruoyi-modules/ruoyi-talent/src/main/resources/mapper/talent/*Mapper.xml`。
8. `script/sql/*.sql` — 新增建表 SQL 与 `sys_menu` 权限记录（含 `talent:xxx:export` 等按钮权限）。

---

## 7. 配置文件的读取与自有配置前缀

### 7.1 与业务模块相关的现有配置项（`ruoyi-admin/src/main/resources/application.yml`）

- `mybatis-plus.mapperPackage / mapperLocations / typeAliasesPackage`：:117-125（见 §6.1(g)）
- 免鉴权路径白名单：`security.excludes` :100-113
```yaml
security:
  # 排除路径
  excludes:
    - /*.html
    - /**/*.html
    - /**/*.css
    - /**/*.js
    - /favicon.ico
    - /error
    - /*/api-docs
    - /*/api-docs/**
```
- 异步线程池：`spring.task.execution` :53-59
- 文件上传大小：`spring.servlet.multipart.max-file-size: 10MB` / `max-request-size: 20MB` :66-72（**大台账导出若走上传/下载需注意，此限制只影响上传**）

### 7.2 `@ConfigurationProperties` 范例（项目内全部命中）

| 前缀 | 文件:行 |
|---|---|
| `springdoc` | `ruoyi-common/ruoyi-common-doc/.../config/properties/SpringDocProperties.java:21` |
| `api-decrypt` | `ruoyi-common/ruoyi-common-encrypt/.../properties/ApiDecryptProperties.java:12` |
| `mybatis-encryptor` | `ruoyi-common/ruoyi-common-encrypt/.../properties/EncryptorProperties.java:15` |
| `mail` | `ruoyi-common/ruoyi-common-mail/.../config/properties/MailProperties.java:13` |
| `mybatis-plus.sql-log` | `ruoyi-common/ruoyi-common-mybatis/.../config/properties/SqlLogProperties.java:12` |
| `message` | `ruoyi-common/ruoyi-common-push/.../properties/MessageProperties.java:13` |
| `redisson` | `ruoyi-common/ruoyi-common-redis/.../config/properties/RedissonProperties.java:15` |
| `security` | `ruoyi-common/ruoyi-common-security/.../config/properties/SecurityProperties.java:12` |
| `justauth` | `ruoyi-common/ruoyi-common-social/.../config/properties/SocialProperties.java:16` |
| `captcha` | `ruoyi-common/ruoyi-common-web/.../config/properties/CaptchaProperties.java:12` |
| `web.cors` | `ruoyi-common/ruoyi-common-web/.../config/properties/CorsProperties.java:13` |
| `xss` | `ruoyi-common/ruoyi-common-web/.../config/properties/XssProperties.java:15` |
| `gen` | `ruoyi-modules/ruoyi-gen/.../config/properties/GenProperties.java:16` |

**模块级配置的最佳样板**（`@Component` + `@ConfigurationProperties` + 自带 yml 通过 `YmlPropertySourceFactory` 加载）：

`ruoyi-modules/ruoyi-gen/src/main/java/org/dromara/gen/config/properties/GenProperties.java:14-18`
```java
@Data
@Component
@ConfigurationProperties(prefix = "gen")
@PropertySource(value = "classpath:generator.yml", factory = YmlPropertySourceFactory.class)
public class GenProperties {
```
配套默认值文件 `ruoyi-modules/ruoyi-gen/src/main/resources/generator.yml`（见 §5.2），并可在 `application.yml` 中用同名前缀覆盖。
被 `@EnableConfigurationProperties` 显式启用的写法见 `MybatisPlusConfig.java:37`。

**为 talent-library 增加自有前缀的建议做法**（与样板一致）：
- 前缀如 `talent`（或 `talent.export`），类 `org.dromara.talent.config.properties.TalentProperties`，加 `@Component` + `@ConfigurationProperties(prefix = "talent")`；
- 若需模块内自带默认配置，新增 `ruoyi-modules/ruoyi-talent/src/main/resources/talent.yml`，并在类上加 `@PropertySource(value = "classpath:talent.yml", factory = YmlPropertySourceFactory.class)`；
- 也可直接在 `ruoyi-admin/src/main/resources/application.yml` 追加 `talent:` 段落覆盖。

---

## 8. 人才库模块落地清单

> 目标：新增 `ruoyi-talent`（talent-library）模块，并实现「异步 Excel 台账导出」。
> 以下每一项均为**文件级**改动；`原样样板` 列给出可直接抄的现有文件。

1. **`script/sql/ry_vue.sql`（或新增 `script/sql/talent.sql`）**：建 `talent_archive` 业务表 + 插入 `sys_menu` 记录（列表/查询/新增/修改/删除/导出/导入按钮权限，前缀 `talent:archive:*`）；若做异步导出，另建 `talent_export_task` 任务表（框架无现成表，见 §4.1）。样板：`script/sql/ry_vue.sql:254` 的导出按钮记录。
2. **`ruoyi-modules/pom.xml`**：在 `<modules>`（:18-25）追加 `<module>ruoyi-talent</module>`。
3. **`pom.xml`（根）**：在 `dependencyManagement`（参照 :411-416）追加 `org.dromara:ruoyi-talent:${revision}`。
4. **`ruoyi-modules/ruoyi-talent/pom.xml`**：新建，parent=`ruoyi-modules`，依赖 `ruoyi-common-core`、`ruoyi-api`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-excel`、`ruoyi-common-log`、`ruoyi-common-security`、`ruoyi-common-web`（异步+OSS 场景再加 `ruoyi-common-redis`、`ruoyi-common-oss`）。样板：`ruoyi-modules/ruoyi-system/pom.xml:18-103`。
5. **`ruoyi-admin/pom.xml`**：追加 `org.dromara:ruoyi-talent` 依赖（参照 :77-99）。`DromaraApplication.java` **不改**。
6. **`ruoyi-modules/ruoyi-talent/src/main/java/org/dromara/talent/domain/TalentArchive.java`**：实体（`@TableName("talent_archive")`，继承 `BaseEntity` 视需要）；样板 `ruoyi-modules/ruoyi-demo/.../domain/TestDemo.java`。
7. **`.../domain/vo/TalentArchiveVo.java`**：导出/查询 VO，加 `@ExcelIgnoreUnannotated`、`@ExcelProperty`、字典用 `@ExcelDictFormat(dictType=...)` + `converter = ExcelDictConvert.class`；**「人才档案链接」列先用 String 字段 + `@ExcelProperty`**（真超链接需自定义 `CellWriteHandler`，见 §2.6，未确认 Fesod API）；需要列宽用 `ExcelBuilder.columnWidth(...)`；日期字段用 `@DateTimeFormat("yyyy-MM-dd HH:mm:ss")`。样板：`ruoyi-modules/ruoyi-demo/.../domain/vo/ExportDemoVo.java:26-74`。
8. **`.../domain/vo/TalentArchiveImportVo.java` + `.../listener/TalentArchiveImportListener.java`**：导入 VO 与监听器（继承/实现 `ExcelListener`，错误累积后 `getAnalysis()` 抛 `ServiceException`）。样板：`ruoyi-modules/ruoyi-system/.../domain/vo/SysUserImportVo.java`、`.../listener/SysUserImportListener.java`。
9. **`.../domain/bo/TalentArchiveBo.java`**：查询/新增 BO（`@ExcelIgnoreUnannotated` 可省）。
10. **`.../mapper/TalentArchiveMapper.java` 与 `.../resources/mapper/talent/TalentArchiveMapper.xml`**：包名必须落在 `org.dromara.**.mapper`，XML 必须落在 `classpath*:mapper/**/*Mapper.xml`（§6.1(g)(h)），**无需 `@MapperScan`**。样板：`ruoyi-modules/ruoyi-demo/.../mapper/TestDemoMapper.java` + `.../resources/mapper/demo/TestDemoMapper.xml`。
11. **`.../service/ITalentArchiveService.java` + `.../service/impl/TalentArchiveServiceImpl.java`**：同步查询/导入逻辑；台账导出方法返回待导出数据（分页/流式）。
12. **`.../controller/TalentArchiveController.java`**：按 §2.3 写同步导出 `POST /talent/archive/export`（`void export(bo, HttpServletResponse)` + `@SaCheckPermission("talent:archive:export")` + `@Log(businessType = EXPORT)` + `ExcelBuilder.of(list, TalentArchiveVo.class).sheetName("人才台账").toResponse(response)`）；按 §3.1 写 `POST /talent/archive/importData` 与 `POST /talent/archive/importTemplate`；多 sheet 场景用 `ExcelBuilder.writer(...).toResponse(response, consumer)`（§2.4）。样板：`ruoyi-modules/ruoyi-system/.../controller/system/SysPostController.java:62-68`、`SysUserController.java:82-114`。
13. **异步导出台账（新增能力，框架无现成实现）**：
    - 新增 `.../service/ITalentExportTaskService.java` / `impl/TalentExportTaskServiceImpl.java`，用 `@Async`（已由 `ApplicationConfig.java:14` 开启，线程池见 `application.yml:53-59`）执行导出；
    - 产出写法：`ExcelBuilder.of(list, TalentArchiveVo.class).sheetName("人才台账").toStream(outputStream)`（`.../ExcelBuilder.java:412`）；**不要用 `.zip(...)`**，因为 zip 仅支持 `toResponse(HttpServletResponse)`（`.../ExcelBuilder.java:412-417`）；若需分片 ZIP，需自建 ZIP 且可参考 `.../ExcelBuilder.java:541-569` 的切分逻辑（单线程）；
    - 结果落地：`OssClient.upload(String key, byte[] data)`（`OssClient.java:481`）或 `upload(String key, InputStream, long)`（:471），把 URL 写回 `talent_export_task`；
    - 任务表状态查询接口 `GET /talent/export/task/{id}`，前端轮询；
    - 如需失败重试/分布式调度，用 SnailJob：`ruoyi-common-job` 客户端（`SnailJobConfig.java:21-25`）、任务类放 `org.dromara.talent.job`，配置 `snail-job.enabled=true`（`application-dev.yml:14-16`）。
14. **`ruoyi-modules/ruoyi-talent/src/main/resources/talent.yml` + `.../config/properties/TalentProperties.java`**：自有配置前缀（如 `talent.export.page-size`、`talent.export.max-rows`、档案链接前缀 `talent.archive.base-url`）。样板：`ruoyi-modules/ruoyi-gen/.../config/properties/GenProperties.java:14-18`。
15. **`ruoyi-admin/src/main/resources/application.yml`**：如需环境相关覆盖，追加 `talent:` 段落（不改动 `mybatis-plus` 既有扫描配置）。
16. **前端（Vue）**：按生成器约定落位 `src/api/talent/archive/index.ts`、`types.ts`、`src/views/talent/archive/index.vue`（生成器产出目录形如 `vue/api/{module}/{business}/index.ts`、`vue/views/{module}/{business}/index.vue`，见 §5.2）。
17. **验证点**：① `mvn -pl ruoyi-modules/ruoyi-talent -am compile`；② 新 Mapper XML 被 `classpath*:mapper/**/*Mapper.xml` 命中；③ `/talent/archive/export` 返回 xlsx；④ 异步导出任务最终可在 OSS 取到文件并可下载。

---

## 9. 未确认项（明确列出，不做猜测）

1. **Excel 真超链接写入 API**：仓库内 `Hyperlink` 全量检索零命中，本环境的 Fesod `2.0.2-incubating` 超链接类名/签名**未确认**（缺少依赖源码证据，且禁止查看仓库外目录）。
2. **「多线程 ZIP 打包」**：`ExcelBuilder` 内**未确认**存在任何多线程实现；现有 `zip()` 为单线程顺序分片（`.../utils/ExcelBuilder.java:541-569`）。
3. **异步导出的任务表/状态机**：仓库**不存在**（检索零命中）；具体表结构与重试策略需按新需求设计，本项目无既有约定。
4. **`ruoyi-gen` 是否默认参与打包**：`ruoyi-admin/pom.xml:135-151` 以 `gen` profile 默认激活方式引入；若要关闭需自行确认 profile 行为。
5. **前端工程目录**（`plus-ui`/`ruoyi-vue-plus-ui`）不在本次侦察目录内，前端产物落位的最终根路径**未确认**；本文只给出生成器的相对产出约定。
