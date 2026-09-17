# B - 通用基础设施约定（RuoYi-Vue-Plus 6.0.0 / commit 7180b52）

> 目标读者：新业务模块 `talent-library` 的开发者。
> 所有结论均来自本仓库源码，路径相对于仓库根 `D:\DeepseekHarness\RuoYi-Vue-Plus`。
> 6.0.0 是破坏性大版本，**不要**套用 5.x 的 `TableDataInfo`、`String createBy`、`ruoyi-common-idempotent` 等旧约定。

---

## 0. 先看这些结论（最容易踩坑的 5 条）

| 项 | 5.x 旧约定 | 6.0.0 实际 |
|---|---|---|
| 分页返回类 | `TableDataInfo<T>` | **已删除**，改用 `org.dromara.common.core.domain.PageResult<T>`（无 `TableDataInfo` 类文件） |
| `BaseEntity` | 含 `id` / `delFlag` | **不含**，`id`、`delFlag` 需在业务实体里各自声明 |
| `createBy` / `updateBy` | `String` 用户名 | **`Long` 用户ID**（6.0.0 统一），用户名要用 `@Translation` 派生字段 |
| 幂等 / 限流模块 | 独立 `ruoyi-common-idempotent` / `ruoyi-common-ratelimiter` | **合并进 `ruoyi-common-redis`** |
| 脱敏生效链路 | Jackson `JsonSerializer` / `ResponseBodyAdvice`（5.x 有 `SensitiveService` 默认实现） | 走 `ruoyi-common-json` 的 `JsonValueEnhancer`；**仓库内没有 `SensitiveService` 实现类，默认不脱敏** |

---

## 1. 基础实体 BaseEntity

### 1.1 准确字段列表

`ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/core/domain/BaseEntity.java:16-51`

```java
@Data
public class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableField(fill = FieldFill.INSERT)
    private Long createDept;      // 创建部门

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;        // 创建者（用户ID，Long！）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

要点：
- 全限定名 `org.dromara.common.mybatis.core.domain.BaseEntity`。
- 五个字段：`createDept`、`createBy`、`createTime`、`updateBy`、`updateTime`。
- `createTime` / `updateTime` 是 `java.time.LocalDateTime`（`BaseEntity.java:9` 的 import，`BaseEntity.java:38/50`）。
- **没有** `id`、**没有** `delFlag`、**没有** `tenantId`、**没有** `params`（6.0.0 用 `params` 的实体自查）。
- **`createBy`/`updateBy` 是 `Long`**，不是用户名 `String`。用户名必须在 VO 上用翻译字段派生（见第 8 节）。

### 1.2 主键与逻辑删除的注解写法（每个实体自己声明）

以 `ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/domain/TestDemo.java:26-66` 为模板：

```java
@TableId(value = "id")
private Long id;

// ... 业务字段 ...

@Version
private Long version;

@TableLogic
private Long delFlag;
```

字段类型不统一，两种官方写法并存，**照抄 demo 的 `Long` 更省事**：

- `TestDemo.java:65-66`：`@TableLogic private Long delFlag;`（配合 MySQL DDL `del_flag int(0) NULL DEFAULT 0`，见 `script/sql/ry_vue.sql:886`）
- `SysUser.java:88-89`：`@TableLogic private String delFlag;`（配合 `del_flag char(1) default '0'`，见 `script/sql/ry_vue.sql:160`）

主键名随表而异：`TestDemo` 用 `id`（`TestDemo.java:27`），`SysUser` 用 `user_id`（`SysUser.java:27`），`SysDept` 用 `dept_id`。

### 1.3 雪花 ID 生成方式

全局主键策略在 `ruoyi-admin/src/main/resources/application.yml:126-131`：

```yaml
mybatis-plus:
  global-config:
    dbConfig:
      # AUTO 自增 NONE 空 INPUT 用户输入 ASSIGN_ID 雪花 ASSIGN_UUID 唯一 UUID
      idType: ASSIGN_ID
```

生成器 Bean 在 `MybatisPlusConfig.java:107-114`，用网卡信息绑定 workerId：

```java
/**
 * 使用网卡信息绑定雪花生成器
 * 防止集群雪花ID重复
 */
@Bean
public IdentifierGenerator idGenerator() {
    return new DefaultIdentifierGenerator(NetUtil.getLocalhost());
}
```

因此 `@TableId(value = "id")` **不需要写 `type = IdType.ASSIGN_ID`**，默认即雪花；仅关联表用 `@TableId(type = IdType.INPUT)`（如 `SysUserRole.java:21`）。

手工生成 ID 用 `org.dromara.common.mybatis.utils.IdGeneratorUtil`（`IdGeneratorUtil.java:30/42/92`）：`nextId()`、`nextLongId()`、`nextUUID()`。

### 1.4 自动填充实现（涉及"无登录用户"场景必读）

`ruoyi-common/ruoyi-common-mybatis/.../handler/InjectionMetaObjectHandler.java:36-60`：

```java
@Override
public void insertFill(MetaObject metaObject) {
    if (ObjectUtil.isNotNull(metaObject) && metaObject.getOriginalObject() instanceof BaseEntity baseEntity) {
        LocalDateTime current = ObjectUtils.notNull(baseEntity.getCreateTime(), LocalDateTime.now());
        baseEntity.setCreateTime(current);
        baseEntity.setUpdateTime(current);
        if (ObjectUtil.isNull(baseEntity.getCreateBy())) {
            LoginUser loginUser = getLoginUser();
            if (ObjectUtil.isNotNull(loginUser)) {
                Long userId = loginUser.getUserId();
                baseEntity.setCreateBy(userId);
                baseEntity.setUpdateBy(userId);
                baseEntity.setCreateDept(ObjectUtils.notNull(baseEntity.getCreateDept(), loginUser.getDeptId()));
            } else {
                baseEntity.setCreateBy(DEFAULT_USER_ID);   // -1L，见 :29
                ...
```

即：填充只在实体 `extends BaseEntity` 时按字段名直填；**脱离 BaseEntity 的实体走 `strictInsertFill("createTime", LocalDateTime.class, ...)`**（`InjectionMetaObjectHandler.java:61-68`）。定时任务/异步线程无登录态时 `createBy = -1L`。

---

## 2. 分页

### 2.1 三件套的准确类名与包名

| 用途 | 类 | 包 |
|---|---|---|
| 分页入参 | `PageQuery` | `org.dromara.common.mybatis.core.page`（`ruoyi-common-mybatis/.../core/page/PageQuery.java:1`） |
| 分页出参 | `PageResult<T>` | `org.dromara.common.core.domain`（`ruoyi-common-core/.../domain/PageResult.java:1`） |
| MyBatis-Plus 分页对象 | `com.baomidou.mybatisplus.extension.plugins.pagination.Page<T>` | 第三方 |

**`TableDataInfo` 在 6.0.0 不存在**——全仓库无该类文件（glob `**/TableDataInfo.java` 无结果）。

`PageResult` 字段与工厂（`PageResult.java:26-56`）：

```java
private long total;
private Collection<T> rows;

public PageResult(Collection<T> list, long total) { ... }

public static <T> PageResult<T> build(Collection<T> list, long total) {
    PageResult<T> rspData = new PageResult<>();
    rspData.setRows(emptyIfNull(list));
    rspData.setTotal(total);
    return rspData;
}
```

`PageQuery` 字段与 `build()`（`PageQuery.java:34-49, 67-79`）：

```java
private Integer pageSize;
private Integer pageNum;
private String orderByColumn;
private String isAsc;

public static final int DEFAULT_PAGE_NUM = 1;
public static final int DEFAULT_PAGE_SIZE = Integer.MAX_VALUE;   // 默认查全部！

public <T> Page<T> build() {
    Integer pageNum = ObjectUtil.defaultIfNull(getPageNum(), DEFAULT_PAGE_NUM);
    Integer pageSize = ObjectUtil.defaultIfNull(getPageSize(), DEFAULT_PAGE_SIZE);
    ...
    Page<T> page = new Page<>(pageNum, pageSize);
```

`buildOrderItem()` 支持 `orderByColumn="id,createTime"` + `isAsc="asc,desc"` 逐字段排序（`PageQuery.java:81-120`），并在参数非法时抛 `ServiceException("排序参数有误")`。

### 2.2 Controller 分页接口标准签名

`ruoyi-modules/ruoyi-demo/.../controller/TestDemoController.java:42-57`：

```java
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/demo/demo")
public class TestDemoController extends BaseController {

    private final ITestDemoService testDemoService;

    @SaCheckPermission("demo:demo:list")
    @GetMapping("/list")
    public R<PageResult<TestDemoVo>> list(@Validated(QueryGroup.class) TestDemoBo bo, PageQuery pageQuery) {
        return R.ok(testDemoService.queryPageList(bo, pageQuery));
    }
}
```

Service 侧（`TestDemoServiceImpl.java:52-57`）：

```java
@Override
public PageResult<TestDemoVo> queryPageList(TestDemoBo bo, PageQuery pageQuery) {
    LambdaQueryWrapper<TestDemo> lqw = buildQueryWrapper(bo);
    Page<TestDemoVo> result = demoMapper.selectVoPage(pageQuery.build(), lqw);
    return PageResult.build(result.getRecords(), result.getTotal());
}
```

`selectVoPage` 是 `BaseMapperPlus` 的默认方法（`BaseMapperPlus.java:348-372`），内部 `selectList(page, wrapper)` 后用 `MapstructUtils.convert(list, voClass)` 转 VO。

### 2.3 MyBatis-Plus 分页插件配置位置

`ruoyi-common/ruoyi-common-mybatis/.../config/MybatisPlusConfig.java:45-81`：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(dataPermissionInterceptor());   // 数据权限
    interceptor.addInnerInterceptor(paginationInnerInterceptor());  // 分页
    interceptor.addInnerInterceptor(optimisticLockerInnerInterceptor()); // 乐观锁
    return interceptor;
}

public PaginationInnerInterceptor paginationInnerInterceptor() {
    PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
    paginationInnerInterceptor.setOverflow(true);   // 分页合理化
    return paginationInnerInterceptor;
}
```

同时 `MybatisPlusConfig.java:35` 用 `@MapperScan("${mybatis-plus.mapperPackage}")` 扫描 Mapper，配置值见 `application.yml:121-125`：

```yaml
mapperPackage: org.dromara.**.mapper
mapperLocations: classpath*:mapper/**/*Mapper.xml
typeAliasesPackage: org.dromara.**.domain
```

> `talent-library` 的实体必须放在 `org.dromara.**.domain` 下、Mapper 放 `org.dromara.**.mapper` 下，XML 放 `classpath*:mapper/**/*Mapper.xml`，否则扫描不到。

---

## 3. 统一响应与异常

### 3.1 `R<T>` 构造方法（`R.data(...)` 已确认存在）

`ruoyi-common/ruoyi-common-core/.../domain/R.java:19-190`：

```java
@Data
@NoArgsConstructor
public class R<T> implements Serializable {
    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok()               { return restResult(null, SUCCESS, "操作成功"); }
    public static <T> R<T> ok(T data)         { return restResult(data, SUCCESS, "操作成功"); }
    public static <T> R<T> data(T data)       { return restResult(data, SUCCESS, "操作成功"); }  // :69 6.0.0 新增
    public static <T> R<T> ok(String msg)     { ... }
    public static <T> R<T> ok(String msg, T data) { ... }
    public static <T> R<T> fail()             { return restResult(null, ERROR, "操作失败"); }
    public static <T> R<T> fail(String msg)   { ... }
    public static <T> R<T> fail(T data)       { ... }
    public static <T> R<T> fail(String msg, T data) { ... }
    public static <T> R<T> fail(int code, String msg) { ... }
    public static <T> R<T> warn(String msg)   { ... }
    public static <T> R<T> warn(String msg, T data) { ... }
    public static <T> Boolean isError(R<T> ret)  { ... }
    public static <T> Boolean isSuccess(R<T> ret) { ... }
}
```

`ok(data)` 与 `data(data)` 行为完全一致（都走 `restResult(data, SUCCESS, "操作成功")`，`R.java:58-71`）。两者都在用，例如 `RedisRateLimiterController.java:29` 用 `R.data(value)`，`TestDemoController.java:56` 用 `R.ok(...)`。

`BaseController` 提供 `toAjax`（`BaseController.java:19-31`）：

```java
protected R<Void> toAjax(int rows)  { return rows > 0 ? R.ok() : R.fail(); }
protected R<Void> toAjax(boolean result) { return result ? R.ok() : R.fail(); }
```

### 3.2 异常体系与业务校验失败的抛法

`ServiceException`：`ruoyi-common/ruoyi-common-core/.../exception/ServiceException.java:16-79`，`final` 类，继承 `RuntimeException`，可带 `code`：

```java
@NoArgsConstructor
@AllArgsConstructor
public final class ServiceException extends RuntimeException {
    private Integer code;
    private String message;
    private String detailMessage;

    public ServiceException(String message) { this.message = message; }
    public ServiceException(String message, Integer code) { ... }
    public ServiceException(String message, Throwable cause) { ... }
    public ServiceException(String message, Object... args) { this.message = StrFormatter.format(message, args); }
```

业务代码直接 `throw`，例如 `TestDemoServiceImpl.java:150-153`：

```java
List<TestDemo> list = demoMapper.selectByIds(ids);
if (list.size() != ids.size()) {
    throw new ServiceException("您没有删除权限!");
}
```

带错误码：`throw new ServiceException("自动注入异常 => " + e.getMessage(), HttpStatus.HTTP_INTERNAL_ERROR)`（`InjectionMetaObjectHandler.java:70`）。

### 3.3 全局异常处理器

`ruoyi-common/ruoyi-common-web/src/main/java/org/dromara/common/web/handler/GlobalExceptionHandler.java:42-65`：

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.error(e.getMessage());
        Integer code = e.getCode();
        return ObjectUtil.isNotNull(code) ? R.fail(code, e.getMessage()) : R.fail(e.getMessage());
    }
```

其它已覆盖的异常（同文件）：`HttpRequestMethodNotSupportedException`(:49)、`SseException`(:71)、`ServletException`(:81)、`BaseException`(:91)、`MissingPathVariableException`(:100)、`MethodArgumentTypeMismatchException`(:110)、`NoHandlerFoundException`(:120)、`IOException`(:131)、`AsyncRequestTimeoutException`(:145)、`RuntimeException`(:152)、`Exception`(:163)、`BindException`(:174)、`ConstraintViolationException`(:184)、`MethodArgumentNotValidException`(:194)、`HandlerMethodValidationException`(:204)、`JsonParseException`(:215)、`HttpMessageNotReadableException`(:225)、`ExpressionException`(:234)。

注意：`RuntimeException`/`Exception` 兜底会返回随机错误编号（`GlobalExceptionHandler.java:152-168`），业务异常**必须**用 `ServiceException` 才能把中文提示直出给前端。

---

## 4. 操作日志

### 4.1 `@Log` 注解字段

`ruoyi-common/ruoyi-common-log/src/main/java/org/dromara/common/log/annotation/Log.java:13-47`：

```java
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    String title() default "";
    BusinessType businessType() default BusinessType.OTHER;
    OperatorType operatorType() default OperatorType.MANAGE;
    boolean isSaveRequestData() default true;
    boolean isSaveResponseData() default true;
    String[] excludeParamNames() default {};
}
```

用法（`TestDemoController.java:115-124`、`:129-135`）：

```java
@SaCheckPermission("demo:demo:add")
@Log(title = "测试单表", businessType = BusinessType.INSERT)
@RepeatSubmit(interval = 2, timeUnit = TimeUnit.SECONDS, message = "{repeat.submit.message}")
@PostMapping()
public R<Void> add(@RequestBody TestDemoBo bo) { ... }

@Log(title = "测试单表", businessType = BusinessType.UPDATE)
@RepeatSubmit
@PutMapping()
public R<Void> edit(@Validated(EditGroup.class) @RequestBody TestDemoBo bo) { ... }
```

查询接口（`/list`、`/{id}`）**不加** `@Log`（`TestDemoController.java:53-57, 105-110`）。

### 4.2 `BusinessType` 枚举取值

`ruoyi-common-log/.../enums/BusinessType.java:8-58`，共 10 个：

`OTHER`、`INSERT`、`UPDATE`、`DELETE`、`GRANT`、`EXPORT`、`IMPORT`、`FORCE`、`GENCODE`、`CLEAN`。

配套 `org.dromara.common.log.enums.OperatorType`（`@Log.operatorType` 默认 `MANAGE`）与 `BusinessStatus`。

### 4.3 `sys_oper_log` 字段

实体（`ruoyi-modules/ruoyi-system/.../domain/SysOperLog.java:17-138`，`@TableName("sys_oper_log")`，`@TableId(value = "oper_id")`）：

```java
private Long operId;            // 日志主键
private String title;           // 操作模块
private Integer businessType;   // 业务类型（0其它 1新增 2修改 3删除）
private String method;          // 请求方法
private String requestMethod;   // 请求方式
private Integer operatorType;   // 操作类别（0其它 1后台用户 2手机端用户）
private String operName;        // 操作人员
private Long userId;            // 操作用户ID
private Long deptId;            // 操作部门ID
private String deptName;
private String clientKey;
private String deviceType;
private String browser;
private String os;
private String operUrl;
private String operIp;
private String operLocation;
private String operParam;       // 请求参数
private String jsonResult;      // 返回参数
private Integer status;         // 操作状态（0正常 1异常）
private String errorMsg;
private LocalDateTime operTime; // LocalDateTime
private Long costTime;          // 消耗时间
```

DDL：`script/sql/ry_vue.sql:520-549`（`business_type int(2)`、`oper_param varchar(4000)`、`json_result varchar(4000)`、`oper_time datetime`、`cost_time bigint`，主键 `oper_id`，并建了 `business_type`/`user_id`/`status`/`oper_time` 四个索引）。

注意：`SysOperLog` **不继承 `BaseEntity`**，它自己带 `operTime`（`SysOperLog.java:19, 133`）。

---

## 5. 幂等与限流（6.0.0 已合并进 redis 模块）

`ruoyi-common` 下**没有** `ruoyi-common-idempotent`、`ruoyi-common-ratelimiter` 目录（目录清单见 `ruoyi-common/`，共 25 个子模块，只有 `ruoyi-common-redis`）。两个注解分别位于：

- 幂等：`org.dromara.common.redis.annotation.RepeatSubmit`
- 限流：`org.dromara.common.redis.annotation.RateLimiter`

### 5.1 `@RepeatSubmit`（防重复提交）

`ruoyi-common/ruoyi-common-redis/.../annotation/RepeatSubmit.java:11-31`：

```java
@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatSubmit {

    /** 间隔时间(ms)，小于此时间视为重复提交 */
    int interval() default 5000;

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /** 提示消息 支持国际化 格式为 {code} */
    String message() default "{repeat.submit.message}";
}
```

实现：`ruoyi-common-redis/.../aspectj/RepeatSubmitAspect.java:38-78`（`@Aspect` + `@Before("@annotation(repeatSubmit)")`，用 `RedisUtils.setObjectIfAbsent(...)` 写 key）。注册方式 `ruoyi-common-redis/.../config/IdempotentConfig.java:13-24`：

```java
@AutoConfiguration(after = RedisConfiguration.class)
public class IdempotentConfig {
    @Bean
    public RepeatSubmitAspect repeatSubmitAspect() {
        return new RepeatSubmitAspect();
    }
}
```

硬约束：`interval`（换算成 ms）**不能小于 1000**，否则直接抛 `ServiceException("重复提交间隔时间不能小于'1'秒")`（`RepeatSubmitAspect.java:52-56`）。

用法（`TestDemoController.java:117` / `:131`，注意必须在 **Controller 方法**上）：

```java
@RepeatSubmit                                          // 默认 5s
@RepeatSubmit(interval = 2, timeUnit = TimeUnit.SECONDS, message = "{repeat.submit.message}")
```

### 5.2 `@RateLimiter`（限流）

`ruoyi-common/ruoyi-common-redis/.../annotation/RateLimiter.java:12-46`：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {
    /** 限流key,支持使用Spring el表达式来动态获取方法上的参数值 格式类似于 #code.id #{#code} */
    String key() default "";
    int time() default 60;                                  // 限流时间,单位秒
    int count() default 100;                                // 限流次数
    LimitType limitType() default LimitType.DEFAULT;
    String message() default "{rate.limiter.message}";
    int timeout() default 86400;                            // 限流策略超时时间，默认一天
}
```

`LimitType`（`ruoyi-common-redis/.../enums/LimitType.java:9-24`）：`DEFAULT`（全局限流）、`IP`、`CLUSTER`（集群多实例）。

实现：`ruoyi-common-redis/.../aspectj/RateLimiterAspect.java:37-86`（`@Before("@annotation(rateLimiter)")`，基于 Redisson `RedisUtils.rateLimiter(...)`，`LimitType.CLUSTER` 映射 `RateType.PER_CLIENT`，其余 `RateType.OVERALL`）。注册方式 `RateLimiterConfig.java:14-25`。

用法（`ruoyi-demo/.../controller/RedisRateLimiterController.java:26-62`）：

```java
@RateLimiter(count = 2, time = 10)
@GetMapping("/test")
public R<String> test(String value) { return R.data(value); }

@RateLimiter(count = 2, time = 10, limitType = LimitType.IP)
public R<String> testip(String value) { ... }

@RateLimiter(count = 2, time = 10, limitType = LimitType.IP, key = "#value")
public R<String> testObj(String value) { ... }
```

> `talent-library` 要用这两个注解，pom 里需显式依赖 `ruoyi-common-redis`（参考 `ruoyi-demo/pom.xml:50-54`）。`ruoyi-system/pom.xml` **没有**该依赖，不要照抄 system 的依赖清单来做限流。

---

## 6. 参数校验

### 6.1 校验分组：存在

`ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/validate/` 下有：

- `AddGroup.java:8` — `public interface AddGroup {}`
- `EditGroup.java`
- `QueryGroup.java`

包名 `org.dromara.common.core.validate`。

BO 上的写法（`ruoyi-demo/.../domain/bo/TestDemoBo.java:1-67`）：

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;

@Data
@AutoMapper(target = TestDemo.class, reverseConvertGenerate = false)
public class TestDemoBo implements Serializable {

    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    @NotBlank(message = "key键不能为空", groups = {AddGroup.class, EditGroup.class})
    private String testKey;
}
```

用 **`jakarta.validation.constraints`**（不是 `javax.validation`）。

### 6.2 Controller / Service 两种触发方式

Controller 上 `@Validated(分组)`（`TestDemoController.java:133` / `:55` / `:107`）：

```java
public R<Void> add(@RequestBody TestDemoBo bo) { ... }                       // 手动校验
public R<Void> edit(@Validated(EditGroup.class) @RequestBody TestDemoBo bo)   // 分组校验
public R<PageResult<TestDemoVo>> list(@Validated(QueryGroup.class) TestDemoBo bo, PageQuery pageQuery)
public R<TestDemoVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable("id") Long id)
```

类级别 `@Validated` 在 `TestDemoController.java:42`。

非 Controller 场景用 `ValidatorUtils`（`ruoyi-common-core/.../utils/ValidatorUtils.java:16-36`）：

```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ValidatorUtils {

    private static final Validator VALID = SpringUtils.getBean(Validator.class);

    public static <T> void validate(T object, Class<?>... groups) {
        if (object == null) {
            throw new RuntimeException("请求参数不能为空");
        }
        Set<ConstraintViolation<T>> validate = VALID.validate(object, groups);
        if (!validate.isEmpty()) {
            throw new ConstraintViolationException("参数校验异常", validate);
        }
    }
}
```

用法（`TestDemoController.java:119-123`）：

```java
// 使用校验工具对标 @Validated(AddGroup.class) 注解
// 用于在非 Controller 的地方校验对象
ValidatorUtils.validate(bo, AddGroup.class);
```

校验失败由 `GlobalExceptionHandler` 的 `ConstraintViolationException`(`:184`)/`MethodArgumentNotValidException`(`:194`) 分支拼成 `R.fail(message)`。

额外可用的自定义校验注解（`ruoyi-common-core/.../validate/`）：`@DictPattern`、`@EnumPattern`；`ruoyi-common-json/.../validate/` 下有 `@JsonPattern`。

---

## 7. 脱敏（ruoyi-common-sensitive）

### 7.1 注解与策略枚举

`ruoyi-common/ruoyi-common-sensitive/.../annotation/Sensitive.java:12-32`：

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Sensitive {
    /** 脱敏策略。 */
    SensitiveStrategy strategy();

    /** 角色标识符 多个角色满足一个即可 */
    String[] roleKey() default {};

    /** 权限标识符 多个权限满足一个即可 */
    String[] perms() default {};
}
```

`SensitiveStrategy` 取值（`ruoyi-common-sensitive/.../core/SensitiveStrategy.java:17-104`）：
`ID_CARD`、`PHONE`、`ADDRESS`、`EMAIL`、`BANK_CARD`、`CHINESE_NAME`、`FIXED_PHONE`、`USER_ID`、`PASSWORD`、`IPV4`、`IPV6`、`CAR_LICENSE`、`FIRST_MASK`、`STRING_MASK`、`MASK_HIGH_SECURITY`、`CLEAR`、`CLEAR_TO_NULL`。
（`STRING_MASK` = 前4后4可见中间4个`*`，`MASK_HIGH_SECURITY` = 前2后2可见；注释见 `:84-94`）

### 7.2 生效链路：**不是 Jackson Serializer，而是 JsonValueEnhancer + ResponseBodyAdvice**

链路：`ResponseEnhancementAdvice`（`ResponseBodyAdvice`）→ `JsonValueEnhancer.enhance(body)` → 遍历 `JsonFieldProcessor` 列表 → `SensitiveJsonFieldProcessor`。

`ruoyi-common/ruoyi-common-web/.../advice/ResponseEnhancementAdvice.java:17-40`：

```java
@RestControllerAdvice
@RequiredArgsConstructor
public class ResponseEnhancementAdvice implements ResponseBodyAdvice<Object> {

    private final JsonValueEnhancer jsonValueEnhancer;

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, ...) {
        if (!selectedContentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return body;
        }
        return jsonValueEnhancer.enhance(body);
    }
}
```

注册在 `ruoyi-common-web/.../config/ResourcesConfig.java:105`。处理器本体 `ruoyi-common-sensitive/.../handler/SensitiveJsonFieldProcessor.java:16-52`：

```java
@Slf4j
@Order(100)
public class SensitiveJsonFieldProcessor implements JsonFieldProcessor {

    @Autowired(required = false)
    private SensitiveService sensitiveService;

    @Override
    public boolean supports(JsonFieldContext fieldContext) {
        return fieldContext.getAnnotation(Sensitive.class) != null;
    }

    @Override
    public Object process(JsonFieldContext fieldContext, Object value, JsonEnhancementContext context) {
        Sensitive sensitive = fieldContext.getAnnotation(Sensitive.class);
        if (sensitive == null || !(value instanceof String text)) {
            return value;
        }
        if (ObjectUtil.isNotNull(sensitiveService) && sensitiveService.isSensitive(sensitive.roleKey(), sensitive.perms())) {
            return sensitive.strategy().desensitizer().apply(text);
        }
        return text;
    }
}
```

### 7.3 ⚠️ 关键坑：`SensitiveService` 无默认实现 ⇒ 开箱不脱敏

- `SensitiveService` 只有一个接口文件（`ruoyi-common-sensitive/.../core/SensitiveService.java:11-18`），**全仓库无任何 `implements SensitiveService`**（glob `**/SensitiveService.java` 仅 1 个结果）。
- 处理器是 `@Autowired(required = false)`（`SensitiveJsonFieldProcessor.java:20-21`），因此没有 bean 时 `sensitiveService == null`，`process` 直接 `return text`（`:51`）。
- 结论：6.0.0 里脱敏**默认不生效**，必须由业务方（或 system 模块）自己提供一个 `SensitiveService` Bean。若 `talent-library` 没有该 Bean，`@Sensitive` 只是"标记"，不会改变响应。

另注意：脱敏只对 **`String` 类型字段**生效（`SensitiveJsonFieldProcessor.java:45`），只对 **`application/json`** 响应生效（`ResponseEnhancementAdvice.java:36-38`），并且 `JsonValueEnhancer` 会把整个响应体重新渲染（`JsonValueEnhancer.java:62-73`）。底层 JSON 库是 **Jackson 3（`tools.jackson.*`）**，见 `JsonEnhancementConfig.java:7`。

### 7.4 可直接照抄的示例

VO/响应对象上（照抄 `ruoyi-demo/.../controller/TestSensitiveController.java:44-77`）：

```java
import org.dromara.common.sensitive.annotation.Sensitive;
import org.dromara.common.sensitive.core.SensitiveStrategy;

@Data
public class TalentVo {

    @Sensitive(strategy = SensitiveStrategy.PHONE)
    private String phone;

    @Sensitive(strategy = SensitiveStrategy.ID_CARD)
    private String idCard;

    /** 仅当当前用户拥有 common 角色 或 system:user:query 权限时才脱敏 */
    @Sensitive(strategy = SensitiveStrategy.EMAIL, roleKey = "common", perms = "system:user:query")
    private String email;
}
```

必须自己补的 `SensitiveService` 实现（否则上面的注解不生效）：

```java
import org.dromara.common.sensitive.core.SensitiveService;
import org.springframework.stereotype.Service;

/**
 * 脱敏判定：返回 true = 需要脱敏（即"看不到明文"的用户）
 */
@Service
public class TalentSensitiveService implements SensitiveService {

    @Override
    public boolean isSensitive(String[] roleKey, String[] perms) {
        // 示例：非管理员即脱敏；roleKey/perms 非空时表示"拥有该角色/权限的人不脱敏"
        return !LoginHelper.isSuperAdmin() && !LoginHelper.isTenantAdmin(null);
    }
}
```

> `isSensitive(...)` 的语义约定需自行确定：`SensitiveJsonFieldProcessor.java:48` 里 `true` 才执行脱敏。仓库未提供参考实现，接口注释仅写「默认管理员不过滤 需自行根据业务重写实现」(`SensitiveService.java:5-6`)。具体角色/权限判断工具：`org.dromara.common.satoken.utils.LoginHelper`（用法可参考 `InjectionMetaObjectHandler.java:11, 108`）。

---

## 8. 翻译（ruoyi-common-translation）

### 8.1 注解与常量

`ruoyi-common/ruoyi-common-translation/.../annotation/Translation.java:10-31`：

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface Translation {
    /** 类型 (需与实现类上的 @TranslationType 注解 type 对应) */
    String type();

    /** 映射字段 (如果不为空则取此字段的值) */
    String mapper() default "";

    /** 其他条件 例如: 字典type(sys_user_gender) */
    String other() default "";
}
```

`TransConstant`（`ruoyi-common-translation/.../constant/TransConstant.java:8-34`）：

```java
String USER_ID_TO_NAME     = "user_id_to_name";      // 用户id转账号
String USER_ID_TO_NICKNAME = "user_id_to_nickname";  // 用户id转用户昵称
String DEPT_ID_TO_NAME     = "dept_id_to_name";      // 部门id转名称
String DICT_TYPE_TO_LABEL  = "dict_type_to_label";   // 字典值转标签（other 传字典 type）
String OSS_ID_TO_URL       = "oss_id_to_url";        // ossId转url
```

内置实现（`ruoyi-common-translation/.../core/impl/`）：`UserNameTranslationImpl`、`NicknameTranslationImpl`、`DeptNameTranslationImpl`、`DictTypeTranslationImpl`、`OssUrlTranslationImpl`。

### 8.2 生效链路

`TranslationJsonFieldProcessor implements JsonFieldProcessor`，`@Order(0)`，位于 `ruoyi-common-translation/.../core/handler/TranslationJsonFieldProcessor.java:20-85`：

```java
@Order(0)
public class TranslationJsonFieldProcessor implements JsonFieldProcessor {

    @Override
    public boolean supports(JsonFieldContext fieldContext) {
        return fieldContext.getAnnotation(Translation.class) != null;
    }

    @Override
    public void collect(JsonFieldContext fieldContext, JsonEnhancementContext context) {
        // 按 (type, other) 分组，收集需要翻译的原始值，供 prepare() 批量翻译
    }
```

与脱敏**共用**同一条 `JsonValueEnhancer` + `ResponseEnhancementAdvice` 响应管线，因此：只对 JSON 响应生效，且翻译结果写在**响应体**上（不影响 service 返回值）。翻译值取自 `mapper()` 指定的字段（`TranslationJsonFieldProcessor.java:169-174` 用反射 `ReflectUtils.invokeGetter`）。

字典翻译实现依赖 `org.dromara.common.core.service.DictService`（`DictTypeTranslationImpl.java:22-40`），用户/部门翻译依赖对应 api 服务 —— 运行期需要有这些 Bean。

### 8.3 可直接照抄的示例

**用户ID → 账号名**（照抄 `ruoyi-demo/.../domain/vo/TestDemoVo.java:82-112`）：

```java
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

/** 创建人（用户ID） */
private Long createBy;

/** 创建人账号（由 createBy 翻译而来） */
@Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
private String createByName;

/** 部门ID → 部门名称 */
@Translation(type = TransConstant.DEPT_ID_TO_NAME, mapper = "deptId")
private String deptName;
```

**字典值 → 字典标签**（照抄 `ruoyi-workflow/.../domain/vo/FlowTaskVo.java:130`）：

```java
@Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "flowStatus", other = "wf_business_status")
private String flowStatusLabel;
```

**自定义翻译类型**（需要新 type 时）：

```java
@TranslationType(type = "talent_edu_to_label")
@AllArgsConstructor
public class TalentEducationTranslationImpl implements TranslationInterface<String> {
    @Override
    public String translation(Object key, String other) { ... }
}
```

（`@TranslationType` 与 `TranslationInterface` 见 `ruoyi-common-translation/.../annotation/TranslationType.java`、`.../core/TranslationInterface.java`；注册方式见 `TranslationJsonFieldProcessor.java:44-53`，按注解 type 收集为 Map。）

---

## 9. 加密（ruoyi-common-encrypt）

### 9.1 字段加密注解与算法

`ruoyi-common/ruoyi-common-encrypt/.../annotation/EncryptField.java:13-43`：

```java
@Documented
@Inherited
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptField {
    AlgorithmType algorithm() default AlgorithmType.DEFAULT;   // 默认走 yml 配置
    String password() default "";      // 秘钥。AES、SM4 需要
    String publicKey() default "";     // 公钥。RSA、SM2 需要
    String privateKey() default "";    // 私钥。RSA、SM2 需要
    EncodeType encode() default EncodeType.DEFAULT;
}
```

`AlgorithmType`（`.../enums/AlgorithmType.java:15-45`）：`DEFAULT`（走 yml）、`BASE64`、`AES`、`RSA`、`SM2`、`SM4`。
`EncodeType`（`.../enums/EncodeType.java`）：`DEFAULT`、`BASE64`、`HEX`（对 BASE64 算法无效，见 `EncryptField.java:41`）。

### 9.2 配置项与开关

`ruoyi-common-encrypt/.../properties/EncryptorProperties.java:14-47`，前缀 `mybatis-encryptor`：

```java
@Data
@ConfigurationProperties(prefix = "mybatis-encryptor")
public class EncryptorProperties {
    private Boolean enable;                                      // 过滤开关
    private AlgorithmType algorithm = AlgorithmType.BASE64;      // 默认算法
    private String password;
    private String publicKey;
    private String privateKey;
    private EncodeType encode = EncodeType.BASE64;
}
```

默认配置（`ruoyi-admin/src/main/resources/application.yml:133-145`）：

```yaml
# 数据加密
mybatis-encryptor:
  # 是否开启加密
  enable: false
  # 默认加密算法
  algorithm: BASE64
  # 编码方式 BASE64/HEX。默认BASE64
  encode: BASE64
  # 安全秘钥 对称算法的秘钥 如：AES，SM4
  password:
  # 公私钥 非对称算法的公私钥 如：SM2，RSA
  publicKey:
  privateKey:
```

**`enable` 默认 `false`**，整条链路被条件装配关掉（`EncryptorAutoConfiguration.java:25-29`）：

```java
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class)
@EnableConfigurationProperties(EncryptorProperties.class)
@ConditionalOnProperty(value = "mybatis-encryptor.enable", havingValue = "true")
@Slf4j
public class EncryptorAutoConfiguration {
```

### 9.3 生效链路（MyBatis 拦截器，非 JSON）

- 入参加密：`.../interceptor/MybatisEncryptInterceptor.java:18-52`，`@Intercepts` 拦截 `ParameterHandler.setParameters`，对参数对象里带 `@EncryptField` 的字段加密，执行后 `snapshot.restore()` 还原原值。
- 出参解密：`.../interceptor/MybatisDecryptInterceptor.java`。
- 字段扫描范围来自 `mybatis-plus.typeAliasesPackage`（`EncryptorAutoConfiguration.java:41-43` 传入 `MybatisPlusProperties.getTypeAliasesPackage()`，即 `org.dromara.**.domain`）。
- 只处理**非 String 的实体/集合/Map 参数对象**（`MybatisEncryptInterceptor.java:40-43` 显式排除 `String`；`EncryptedFieldProcessor.java:68-71` 同样跳过 `String`）。

### 9.4 可直接照抄的示例

（照抄 `ruoyi-demo/.../domain/TestDemoEncrypt.java:17-33`，长密钥已截断）

```java
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.encrypt.annotation.EncryptField;
import org.dromara.common.encrypt.enums.AlgorithmType;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_info")
public class TalentInfoEncrypt extends TalentInfo {

    /** 手机号：AES 对称加密，密钥走注解或 yml */
    @EncryptField(algorithm = AlgorithmType.AES, password = "10rfylhtccpuyke5")
    private String phone;

    /** 身份证：SM4 */
    @EncryptField(algorithm = AlgorithmType.SM4, password = "10rfylhtccpuyke5")
    private String idCardNo;

    /** 什么也不写走默认 yml 配置 */
    @EncryptField
    private String remark;
}
```

使用前必须：① `mybatis-encryptor.enable: true`；② 若用 `algorithm: DEFAULT` 则 `password`/公私钥不能为空（否则 `EncryptorAutoConfiguration.java:99/104` 抛 `IllegalArgumentException`）。

### 9.5 是否适合用于手机号密文存储？——**部分适合，有明确限制**

证据支持的事实：

1. 是**字段级透明加解密**，读写走实体字段时自动处理；但拦截点在 `ParameterHandler.setParameters`（`MybatisEncryptInterceptor.java:18-22`），**只对"参数对象上的 `@EncryptField` 字段"加密**，`String` 类型的直接参数被显式排除（`:41`）。
2. 因此：**不能用 `LambdaQueryWrapper.eq(TalentInfo::getPhone, 明文)` 之类的条件查询**——wrapper 里的条件值不会被该拦截器加密成密文，SQL 会拿明文跟密文列比较，永远查不到。仓库内**未找到**任何「对加密字段做条件查询」的示例（`ruoyi-demo` 的加密示例只做 `insert` + `selectById`，见 `TestEncryptController.java:44-50`）。这一点**未确认**是否有其它机制支持；缺失证据：无针对 `Wrapper` 条件值的加密代码路径。
3. 默认 `algorithm: BASE64` + `enable: false`（`application.yml:136-138`），BASE64 不是加密，必须显式改成 AES/SM4 并开启开关。
4. 对称算法（AES/SM4）密钥明文写在 `application.yml` 或注解里（`TestDemoEncrypt.java:31` 直接硬编码密钥），属于"防拖库不防内部"的级别。

**结论（建议）**：手机号若只需"加密落库 + 按主键读取"，用 `@EncryptField(algorithm = AlgorithmType.AES, ...)` 可行；若业务需要对手机号做**等值查询、模糊搜索或唯一索引**，该机制不直接支持，需要额外设计（例如另存 `phone_hash` 检索列），本仓库未提供该模式 —— 该结论基于上述代码路径，属于推断而非仓库既有做法。

### 9.6 补充：接口级加解密（`api-decrypt`）

另有整包 API 加解密：`@ApiEncrypt`（`ruoyi-common-encrypt/.../annotation/ApiEncrypt.java:10-19`，`@Target(ElementType.METHOD)`，`boolean response() default false`）、`ApiDecryptProperties`（前缀 `api-decrypt`）、`CryptoFilter`。配置见 `application.yml:147-154`（`enabled: true`，`headerFlag: encrypt-key`）。与字段级加密是两套独立机制。

---

## 10. MyBatis-Plus-Join（MPJ）与业务模块 pom

### 10.1 依赖坐标

版本在根 `pom.xml:31`：`<mybatis-plus-join.version>1.5.9</mybatis-plus-join.version>`，dependencyManagement 在 `pom.xml:232-234`：

```xml
<groupId>com.github.yulichang</groupId>
<artifactId>mybatis-plus-join-boot-starter</artifactId>
<version>${mybatis-plus-join.version}</version>
```

实际引入位置是 `ruoyi-common/ruoyi-common-mybatis/pom.xml:55-59`：

```xml
<!-- MyBatis Plus Join -->
<dependency>
    <groupId>com.github.yulichang</groupId>
    <artifactId>mybatis-plus-join-boot-starter</artifactId>
</dependency>
```

⇒ **业务模块只需依赖 `ruoyi-common-mybatis` 即可传递获得 MPJ**，不要在业务模块再声明一次（`ruoyi-system/pom.xml` 与 `ruoyi-demo/pom.xml` 均未声明 MPJ）。

### 10.2 Mapper 基类

```java
public interface SysUserMapper extends BaseMapperPlus<SysUser, SysUserVo>, MPJBaseMapper<SysUser> {
```

（`ruoyi-modules/ruoyi-system/.../mapper/SysUserMapper.java:28`；同理 `SysRoleMapper.java:24`、`SysPostMapper.java:22`、`SysMenuMapper.java:25`、`SysDeptMapper.java:28`。）

注意：`BaseMapperPlus<T, V>` 是 RuoYi 自定义（`ruoyi-common-mybatis/.../core/mapper/BaseMapperPlus.java:34`），泛型是「实体 + VO」；MPJ 需**额外**再继承 `MPJBaseMapper<T>`，单表 CRUD 用不到 MPJ 时可以不继承（如 `TestDemoMapper.java:24` 只继承 `BaseMapperPlus`）。

### 10.3 联表查询典型写法

**方式 A —— 直接用 `MPJLambdaWrapper` + `selectJoinList/selectJoinPage`**（`SysUserMapper.java:70-85`）：

```java
default List<SysUserExportVo> selectUserExportList(SysUserBo user, List<Long> deptIds) {
    MPJLambdaWrapper<SysUser> wrapper = QueryBuilder.lambdaJoin("u", SysUser.class)
        .selectAll(SysUser.class)
        .selectAs("u1", SysUser::getUserName, SysUserExportVo::getLeaderName)
        .leftJoin(SysDept.class, "d", SysDept::getDeptId, SysUser::getDeptId)
        .leftJoin(SysUser.class, "u1", SysUser::getUserId, SysDept::getLeader)
        .likeIfText("u", SysUser::getUserName, user.getUserName())
        .eqIfText("u", SysUser::getStatus, user.getStatus())
        .betweenParams("u", SysUser::getCreateTime, user.getParams(), "beginTime", "endTime")
        .inIfNotEmpty("u", SysUser::getDeptId, deptIds)
        .orderByAsc("u", SysUser::getUserId)
        .build();
    return this.selectJoinList(SysUserExportVo.class, wrapper);
}
```

分页版（`SysUserMapper.java:98-103`）：

```java
default Page<SysUserVo> selectAllocatedList(Page<SysUserVo> page, SysUserBo user) {
    MPJLambdaWrapper<SysUser> wrapper = this.buildUserRoleJoinWrapper(user)
        .eq(user.getRoleId() != null, "r", SysRole::getRoleId, user.getRoleId())
        .orderByAsc("u", SysUser::getUserId);
    return this.selectJoinPage(page, SysUserVo.class, wrapper);
}
```

**方式 B —— Mapper 里声明 default 方法承接 Mapper 之外的 wrapper**（`ruoyi-modules/ruoyi-workflow/.../mapper/FlwInstanceMapper.java:15-26`）：

```java
public interface FlwInstanceMapper extends MPJBaseMapper<FlowInstance> {

    default Page<FlowInstanceVo> selectInstanceList(Page<FlowInstanceVo> page, MPJLambdaWrapper<FlowInstance> queryWrapper) {
        return this.selectJoinPage(page, FlowInstanceVo.class, queryWrapper);
    }
}
```

### 10.4 `QueryBuilder`（6.0.0 新增的链式封装，推荐）

`ruoyi-common/ruoyi-common-mybatis/.../core/query/QueryBuilder.java:10-50`：

```java
public final class QueryBuilder {
    private QueryBuilder() { }

    public static <T> LambdaQueryBuilder<T> lambda(Class<T> entityClass) {
        return new LambdaQueryBuilder<>(new AggregateLambdaQueryWrapper<>(entityClass));
    }

    public static <T> LambdaJoinQueryBuilder<T> lambdaJoin(Class<T> entityClass) {
        return new LambdaJoinQueryBuilder<>(JoinWrappers.lambda(entityClass));
    }

    public static <T> LambdaJoinQueryBuilder<T> lambdaJoin(String alias, Class<T> entityClass) {
        return new LambdaJoinQueryBuilder<>(JoinWrappers.lambda(alias, entityClass));
    }
}
```

`LambdaJoinQueryBuilder` 提供 `selectAll / selectAs / leftJoin / likeIfText / eqIfText / betweenParams / inIfNotEmpty / orderByAsc / apply / build` 等（`.../core/query/LambdaJoinQueryBuilder.java:31-38, 872, 915`）。`build()` 返回 `MPJLambdaWrapper<T>`（`:915`）。

> 若只想写原生 MPJ，`com.github.yulichang.toolkit.JoinWrappers.lambda(...)` 也可直接用（`QueryBuilder.java:3`）。

### 10.5 业务模块 `pom.xml` 依赖写法参考

`ruoyi-modules/ruoyi-system/pom.xml:18-103`（完整清单，`talent-library` 可直接裁剪）：

```xml
<dependencies>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-core</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-api</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-doc</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-mybatis</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-translation</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-oss</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-log</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-excel</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-sms</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-security</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-web</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-sensitive</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-encrypt</artifactId></dependency>
    <dependency><groupId>org.dromara</groupId><artifactId>ruoyi-common-push</artifactId></dependency>
</dependencies>
```

parent 写法（`ruoyi-system/pom.xml:5-12`）：

```xml
<parent>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-modules</artifactId>
    <version>${revision}</version>
</parent>
<modelVersion>4.0.0</modelVersion>
<artifactId>ruoyi-system</artifactId>
```

所有 `org.dromara:ruoyi-common-*` 的版本由 `ruoyi-common-bom`（`ruoyi-common/ruoyi-common-bom/pom.xml:7-20`，通过根 `pom.xml:161` import）统一管理，**依赖声明不写 `<version>`**。

**接入新模块还需要改两处**（否则模块不生效）：

1. `ruoyi-modules/pom.xml:18-25` 的 `<modules>` 里加 `<module>talent-library</module>`；
2. `ruoyi-admin/pom.xml` 里加 `ruoyi-talent-library` 依赖（现有写法见 `ruoyi-admin/pom.xml:80` `ruoyi-system`、`:98` `ruoyi-demo`）。

若需要 `@RepeatSubmit` / `@RateLimiter`，额外加 `ruoyi-common-redis`（照抄 `ruoyi-demo/pom.xml:50-54`）：

```xml
<!-- 缓存服务 -->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-common-redis</artifactId>
</dependency>
```

---

## 11. 未确认项清单

| 项 | 状态 | 缺失的证据 |
|---|---|---|
| 加密字段能否参与 `Wrapper` 条件查询 | **未确认** | 仓库内无相关代码路径与示例；`MybatisEncryptInterceptor` 仅处理非 `String` 参数对象字段 |
| `SensitiveService` 在生产配置中的默认实现 | **未确认** | 仓库内无 `implements SensitiveService`；可能在私有扩展模块，本仓库不可见 |
| 是否存在租户基类（`TenantEntity`） | **不存在** | glob `**/TenantEntity.java` 无结果；`BaseEntity` 也无 `tenantId` |
| `TableDataInfo` | **不存在** | glob `**/TableDataInfo.java` 无结果，6.0.0 已由 `PageResult` 取代 |
| 独立 idempotent / ratelimiter 模块与注解（如 `@Idempotent`） | **不存在** | `ruoyi-common/` 下无对应目录；仅 `ruoyi-common-redis` 内的 `@RepeatSubmit` / `@RateLimiter` |
