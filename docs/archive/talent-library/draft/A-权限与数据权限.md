# A. 权限校验与数据权限 API 参考（RuoYi-Vue-Plus v6.0.0）

> 适用版本：**v6.0.0 正式版**，校验用 commit `7180b529776834fee912113b23f0bd7a387a8222`（`git log -1` 输出 `发布 6.0.0 正式版 大版本发布!!!`）。
> 校验时工作区干净（`git status --porcelain` 无输出），以下所有行号均针对该 commit 的检出副本。
>
> **重要**：6.0.0 是破坏性大版本，**禁止**套用 RuoYi 5.x / RuoYi-Vue-Plus 5.x 的经验。经全仓检索，本版本中**不存在** `@RequiresPermissions` / `@RequiresRoles` / `@PreAuthorize` / Shiro 相关代码（`grep` 无任何匹配），权限注解统一为 **Sa-Token**。
>
> 所有引用路径均相对于仓库根 `D:\DeepseekHarness\RuoYi-Vue-Plus`。

---

## 1. 权限注解（Sa-Token）

### 1.1 注解族与来源

权限注解全部来自 Sa-Token（`cn.dev33.satoken.annotation.*`），而非 Spring Security / Shiro。

```java
// ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/controller/SaTokenTestController.java:1-18
package org.dromara.demo.controller;

import cn.dev33.satoken.annotation.*;
...
@RestController
@RequestMapping("/demo/saTokenDoc")
public class SaTokenTestController {
```

```java
// ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/controller/SaTokenTestController.java:25-30
    @SaCheckLogin
    @GetMapping("/basic/loginOnly")
    public R<Void> loginOnly() {
        log.info("【场景1】仅登录校验通过");
        return R.ok("仅登录校验通过，无需角色/权限");
    }
```

```java
// ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/controller/SaTokenTestController.java:55-57
    @SaIgnore
    @SaCheckRole("none_exist") // 该注解会被忽略
    @GetMapping("/basic/ignoreAll")
```
说明：`@SaIgnore` 优先级最高，可覆盖同处的 `@SaCheckRole`（源码注释 `SaTokenTestController.java:52-54`）。
另外 `@SaCheckLogin(type = "PC")` 可按用户类型限定登录态（`SaTokenTestController.java:192`）。

**注意**：`@SaCheckLogin` 在真实业务 Controller 中**未被使用**，仅出现在 demo 控制器里；业务 Controller 统一用具体权限串 `@SaCheckPermission`。真实业务代码只使用 `@SaCheckPermission` / `@SaCheckRole`。

### 1.2 业务 Controller 的典型写法

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysRoleController.java:54
    @SaCheckPermission("system:role:list")

// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysUserController.java:70
    @SaCheckPermission("system:user:list")
```

权限串固定格式 `模块:业务:操作`，全仓一致（`system:*`、`monitor:*`、`tool:*`）。
角色 + OR 模式的写法（超级管理员或拥有该权限即可）：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysMenuController.java:56-61
    @SaCheckRole(value = {
        SystemConstants.SUPER_ADMIN_ROLE_KEY,
    }, mode = SaMode.OR)
    @SaCheckPermission("system:menu:list")
    @GetMapping("/list")
    public R<List<SysMenuVo>> list(SysMenuBo menu) {
        List<SysMenuVo> menus = menuService.selectMenuList(menu, LoginHelper.getUserId());
```

菜单的增删改则**仅允许超级管理员角色**：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysMenuController.java:116-117
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:add")
```

### 1.3 超级管理员与权限字符串常量

```java
// ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/constant/SystemConstants.java:63,68,73
    Long SUPER_ADMIN_USER_ID = 1761100000000000001L;
    Long SUPER_ADMIN_ROLE_ID = 1761300000000000001L;
    String SUPER_ADMIN_ROLE_KEY = "superadmin";
```

超级管理员在登录时被直接注入通配权限与角色，绕过 `sys_role_menu`：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysPermissionServiceImpl.java:53-63
    @Override
    public Set<String> getMenuPermission(Long userId) {
        Set<String> perms = new HashSet<>();
        // 管理员拥有所有权限
        if (LoginHelper.isSuperAdmin(userId)) {
            perms.add("*:*:*");
        } else {
            perms.addAll(menuService.selectMenuPermsByUserId(userId));
        }
        return perms;
    }
```

> 注意 `SUPER_ADMIN_ROLE_KEY = "superadmin"`（无下划线），不是 5.x 常见的 `admin`。

### 1.4 Sa-Token 如何拿到权限集合

`StpInterface` 实现由 `SaPermissionImpl` 提供，优先读当前会话 `LoginUser` 里的缓存，读不到再回退到 `PermissionService`（跨服务场景）：

```java
// ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/core/service/SaPermissionImpl.java:31-34
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return resolvePermissionList(loginId, LoginUser::getMenuPermission, PermissionService::getMenuPermission);
    }
```

```java
// ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/config/SaTokenConfig.java:38-41
    @Bean
    public StpInterface stpInterface() {
        return new SaPermissionImpl();
    }
```

### 1.5 `LoginHelper` 常用静态方法（准确签名）

文件：`ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/utils/LoginHelper.java`

| 方法签名 | 行号 | 说明 |
|---|---|---|
| `public static <T extends LoginUser> T getLoginUser()` | 102 | 当前会话登录用户，未登录返回 `null`（内部捕获 `NotLoginException`） |
| `public static <T extends LoginUser> T getLoginUser(String token)` | 116 | 按 token 取登录用户 |
| `public static Long getUserId()` | 148 | 当前用户 ID |
| `public static String getUserIdStr()` | 157 | 当前用户 ID 字符串 |
| `public static String getUsername()` | 166 | 当前用户名 |
| `public static Long getDeptId()` | 175 | 当前部门 ID |
| `public static String getDeptName()` | 184 | 当前部门名 |
| `public static String getDeptCategory()` | 193 | 当前部门类别编码 |
| `public static UserType getUserType()` | 216 | 当前用户类型枚举 |
| `public static boolean isSuperAdmin(Long userId)` | 227 | 指定用户是否超管 |
| `public static boolean isSuperAdmin()` | 236 | 当前用户是否超管 |
| `public static boolean isLogin()` | 245 | 是否已登录 |

```java
// ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/utils/LoginHelper.java:227-238
    public static boolean isSuperAdmin(Long userId) {
        return SystemConstants.SUPER_ADMIN_USER_ID.equals(userId);
    }

    /**
     * 是否为超级管理员
     *
     * @return 是否为超级管理员
     */
    public static boolean isSuperAdmin() {
        return isSuperAdmin(getUserId());
    }
```

**没有** `getUserIdStr` 以外的字符串化工具，也**没有** `getRoleList()` 之类的便捷方法；取角色列表请用 `LoginHelper.getLoginUser().getRoles()`（返回 `List<RoleDTO>`）。

**注意（易错点）**：`getUserId()` / `getUsername()` / `getDeptId()` 都是从 Sa-Token 的 **token extra** 里读（`getExtra` 内部捕获所有异常返回 `null`），因此未登录时**不会抛异常，而是返回 `null`**，业务代码需自行判空。

```java
// ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/utils/LoginHelper.java:148-150
    public static Long getUserId() {
        return Convert.toLong(getExtra(USER_KEY));
    }
```

---

## 2. 数据权限（6.0.0 精细化「角色 → 菜单 → 数据权限」）

### 2.1 注解定义

`@DataPermission` 是**容器注解**，本身只承载 `@DataColumn` 数组和拼接符：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/annotation/DataPermission.java:11-28
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /**
     * 数据权限配置数组，用于指定数据权限的占位符关键字和替换值
     */
    DataColumn[] value();

    /**
     * 权限拼接标识符(用于指定连接语句的sql符号)
     * 如不填 默认 select 用 OR 其他语句用 AND
     * 内容 OR 或者 AND
     */
    String joinStr() default "";
}
```

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/annotation/DataColumn.java:14-31
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataColumn {

    /**
     * 数据权限模板的占位符关键字，默认为 "deptName"
     */
    String[] key() default "deptName";

    /**
     * 数据权限模板的占位符替换值，默认为 "dept_id"
     */
    String[] value() default "dept_id";
}
```

- `key()`：SpEL 模板里的**占位符名字**（如 `deptName`、`userName`），会被解析成变量 `#deptName`、`#userName`。
- `value()`：该占位符替换成的**真实 SQL 列**，直接字符串拼进 SQL。**联表查询必须自带表别名**（见 2.9）。
- `joinStr()`：多角色条件之间的连接符；select 默认 `OR`，update/delete 默认 `AND`。
- `@DataPermission` 可用于**方法或类**（`@Target({METHOD, TYPE})`），但 `@DataColumn` 只能用于方法（`@Target(METHOD)`）。

### 2.2 枚举 `DataScopeType`

文件：`ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/enums/DataScopeType.java`

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/enums/DataScopeType.java:31-56
    ALL("1", "", ""),
    CUSTOM("2", " #{#deptName} IN ( #{@sdss.getRoleCustom( #roleId )} ) ", " 1 = 0 "),
    DEPT("3", " #{#deptName} = #{#user.deptId} ", " 1 = 0 "),
    DEPT_AND_CHILD("4", " #{#deptName} IN ( #{@sdss.getDeptAndChild( #user.deptId )} )", " 1 = 0 "),
    SELF("5", " #{#userName} = #{#user.userId} ", " 1 = 0 "),
    DEPT_AND_CHILD_OR_SELF("6", " #{#deptName} IN ( #{@sdss.getDeptAndChild( #user.deptId )} ) OR #{#userName} = #{#user.userId} ", " 1 = 0 ");
```

三个字段：`code`（入库值）、`sqlTemplate`（SpEL 模板）、`elseSql`（模板不满足时的兜底）。
`findCode(String)` 按 code 反查枚举，找不到返回 `null`（`DataScopeType.java:79-89`）。

`SELF` 用的是 `#userName = #user.userId`，即"仅本人"以「注解 `userName` 占位符所指列」比对当前用户 ID；因此业务表必须把"所属人"列传给 `userName` 占位符。

### 2.3 装配链路（谁在什么时候生效）

1. **AOP 捕获注解**：`DataPermissionPointcutAdvisor` 注册切面，`DataPermissionPointcut` 匹配带注解的方法/类，`DataPermissionAdvice` 在方法执行前把注解塞进 ThreadLocal。

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/config/MybatisPlusConfig.java:60-71
    public PlusDataPermissionInterceptor dataPermissionInterceptor() {
        return new PlusDataPermissionInterceptor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DataPermissionPointcutAdvisor dataPermissionPointcutAdvisor() {
        return new DataPermissionPointcutAdvisor();
    }
```

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/aspect/DataPermissionAdvice.java:26-37
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        Method method = invocation.getMethod();
        // 设置权限注解
        DataPermissionHelper.setPermission(getDataPermissionAnnotation(target, method));
        try {
            // 执行代理方法
            return invocation.proceed();
        } finally {
            // 清除权限注解
            DataPermissionHelper.removePermission();
        }
    }
```

> **注解不继承**。切点显式声明：注解不向父类/接口继承，只检查当前方法本身。

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/aspect/DataPermissionPointcut.java:24-34
    public boolean matches(Method method, Class<?> targetClass) {
        // 优先匹配方法
        // 数据权限注解不对继承生效，所以检查当前方法是否有注解即可，不再往上匹配父类或接口
        if (method.isAnnotationPresent(DataPermission.class)) {
            return true;
        }
        // MyBatis 的 Mapper 就是通过 JDK 动态代理实现的，所以这里需要检查是否匹配 JDK 的动态代理
        Class<?> targetClassRef = resolveTargetClass(targetClass);
        return targetClassRef.isAnnotationPresent(DataPermission.class);
    }
```

2. **MyBatis 拦截器改写 SQL**：`PlusDataPermissionInterceptor` 继承 MP 的 `BaseMultiTableInnerInterceptor`，在 `beforeQuery` / `beforePrepare` 中改写。

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/interceptor/PlusDataPermissionInterceptor.java:55-67
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 检查是否需要忽略数据权限处理
        if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
            return;
        }
        // 检查是否缺少有效的数据权限注解
        if (dataPermissionHandler.invalid()) {
            return;
        }
        // 解析 sql 分配对应方法
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        mpBs.sql(parserSingle(mpBs.sql(), ms.getId()));
    }
```

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/interceptor/PlusDataPermissionInterceptor.java:77-93
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        // 获取 SQL 命令类型（增、删、改、查）
        SqlCommandType sct = ms.getSqlCommandType();

        // 只处理更新和删除操作的 SQL 语句
        if (sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE) {
            if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
                return;
            }
            ...
            mpBs.sql(parserMulti(mpBs.sql(), ms.getId()));
```

拦截器链顺序：数据权限 → 分页 → 乐观锁（`MybatisPlusConfig.java:46-55`）。

3. **SQL 片段构建**：`PlusDataPermissionHandler`。

### 2.4 业务 Mapper 上的确切写法（**注解加在 Mapper 方法，不是 Service**）

单表 + 默认 `OR`：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/mapper/SysUserMapper.java:37-43
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<SysUserVo> selectPageUserList(Page<SysUser> page, Wrapper<SysUser> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }
```

联表（MPJ）必须写别名限定列：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/mapper/SysUserMapper.java:66-70
    @DataPermission({
        @DataColumn(key = "deptName", value = "d.dept_id"),
        @DataColumn(key = "userName", value = "u.create_by")
    })
    default List<SysUserExportVo> selectUserExportList(SysUserBo user, List<Long> deptIds) {
```

显式指定 `joinStr`（把多角色条件由 `OR` 改 `AND`）：

```java
// ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/mapper/TestDemoMapper.java:77-81
    @DataPermission(value = {
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "user_id")
    }, joinStr = "AND")
    List<TestDemo> selectByIds(@Param(Constants.COLL) Collection<? extends Serializable> idList);
```

重写 `BaseMapperPlus` 的默认方法时也要重新打注解（因为注解不继承）：

```java
// ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/mapper/TestDemoMapper.java:46-53
    @Override
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "user_id")
    })
    default <P extends IPage<TestDemoVo>> P selectVoPage(IPage<TestDemo> page, Wrapper<TestDemo> wrapper) {
        return selectVoPage(page, wrapper, this.currentVoClass());
    }
```

`update` / `updateById` 同样可加（此时默认 `AND` 连接，且**必须满足所有条件**）：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/mapper/SysUserMapper.java:145-163
    @Override
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    int update(@Param(Constants.ENTITY) SysUser user, @Param(Constants.WRAPPER) Wrapper<SysUser> updateWrapper);

    @Override
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    int updateById(@Param(Constants.ENTITY) SysUser user);
```

**结论 / 落地建议**：`@DataPermission` 打在 **Mapper 接口方法**上（含 `default` 方法）。Service 层加注解**不会**生效，因为切点只匹配 Mapper 的 JDK 动态代理（`DataPermissionPointcut.java:42-55`），且 SQL 改写依赖 `DataPermissionHelper` 的 ThreadLocal + MyBatis 拦截器。

> 注意：任务描述中"业务 Service 方法上加注解"在本版本中**不成立**。事实是加在 Mapper 方法上。

### 2.5 SQL 如何拼进查询

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:72-91
    public Expression getSqlSegment(Expression where, boolean isSelect) {
        try {
            LoginUser currentUser = currentUser();
            // 如果是超级管理员或租户管理员，则不过滤数据
            if (LoginHelper.isSuperAdmin()) {
                return where;
            }
            // 构造数据过滤条件的 SQL 片段
            String dataFilterSql = buildDataFilter(getDataPermission(), currentUser, isSelect);
            if (StringUtils.isBlank(dataFilterSql)) {
                return where;
            }
            Expression expression = CCJSqlParserUtil.parseExpression(dataFilterSql);
            // 数据权限使用单独的括号 防止与其他条件冲突
            ParenthesedExpressionList<Expression> parenthesis = new ParenthesedExpressionList<>(expression);
            if (ObjectUtil.isNotNull(where)) {
                return new AndExpression(where, parenthesis);
            } else {
                return parenthesis;
            }
```

要点：
- **超级管理员直接跳过**（`LoginHelper.isSuperAdmin()`）。
- 生成的条件片段被**一对括号包裹**，再以 `AND` 与原 `where` 组合，避免优先级冲突。
- 条件片段由 JSqlParser 解析后写回 `PlainSelect/Update/Delete` 的 `where`（`PlusDataPermissionInterceptor.java:106-158`）。
- 解析失败抛 `ServiceException("数据权限解析异常 => ...")`（`PlusDataPermissionHandler.java:92-93`）。

多角色条件拼接：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:107-113
    private String buildDataFilter(DataPermission dataPermission, LoginUser user, boolean isSelect) {
        // 更新或删除需满足所有条件
        String joinStr = isSelect ? " OR " : " AND ";
        if (StringUtils.isNotBlank(dataPermission.joinStr())) {
            joinStr = " " + dataPermission.joinStr() + " ";
        }
        Object defaultValue = "-1";
```

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:147-150
            // 全部数据权限直接返回
            if (type == DataScopeType.ALL) {
                return StringUtils.EMPTY;
            }
```

> 只要命中任意一个 `ALL` 角色，整个条件为空串 ⇒ **不加任何过滤**（白名单语义）。

**模板与占位符的匹配规则（很关键）**：某个数据范围模板只在「注解中声明的 key」出现在模板里时才拼接；否则用 `elseSql` 兜底。

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:151-172
            boolean isSuccess = false;
            for (DataColumn dataColumn : dataPermission.value()) {
                // 不包含 key 变量 则不处理
                if (!StringUtils.containsAny(type.getSqlTemplate(), keys.toArray(String[]::new))) {
                    continue;
                }
                // 当前注解不满足模板 不处理
                if (!StringUtils.containsAny(type.getSqlTemplate(), dataColumn.key())) {
                    continue;
                }
                // 忽略数据权限 防止spel表达式内有其他sql查询导致死循环调用
                String sql = DataPermissionHelper.ignore(() ->
                    parser.parseExpression(type.getSqlTemplate(), parserContext).getValue(context, String.class)
                );
                // 解析sql模板并填充
                conditions.add(joinStr + sql);
                isSuccess = true;
            }
            // 未处理成功则填充兜底方案
            if (!isSuccess && StringUtils.isNotBlank(type.getElseSql())) {
                conditions.add(joinStr + type.getElseSql());
            }
```

**实践含义**：
- 若表只有部门列、注解只声明了 `deptName`，而角色数据范围是 `SELF("5")`（模板用 `#userName`），则该角色拿不到任何条件 ⇒ 落入 `1 = 0`，**查不到数据**。给 talent-library 建表时，建议**同时提供部门列与「所属人」列**，并在 `@DataPermission` 里同时声明 `deptName` 与 `userName` 两个 `@DataColumn`。
- `key` 与 `value` 数组长度必须一一对应，否则抛 `ServiceException("角色数据范围异常 => key与value长度不匹配")`（`PlusDataPermissionHandler.java:130-132`）。
- 数据范围编码非法（非 1–6）抛 `ServiceException("角色数据范围异常 => " + role.getDataScope())`（`PlusDataPermissionHandler.java:143-146`）。

### 2.6 自定义 SQL 片段与 SpEL（**支持**）

模板本身就是 SpEL 表达式，使用 `TemplateParserContext`（`#{...}` 模板语法），并在解析前注册了 `BeanFactoryResolver`，因此支持 `@bean.method(args)`：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:53-63
    private final ExpressionParser parser = new SpelExpressionParser();
    /**
     * SpEL 模板解析上下文。
     */
    private final ParserContext parserContext = new TemplateParserContext();
    /**
     * bean解析器 用于处理 spel 表达式中对 bean 的调用
     */
    private final BeanResolver beanResolver = new BeanFactoryResolver(SpringUtils.getBeanFactory());
```

内置可用对象（枚举 javadoc 明确）：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/enums/DataScopeType.java:11-19
 * 支持使用 SpEL 模板表达式定义 SQL 查询条件
 * 内置数据：
 * - {@code user}: 当前登录用户信息，参考 {@link LoginUser}
 * 内置服务：
 * - {@code sdss}: 系统数据权限服务，参考 ISysDataScopeService
 * 如需扩展数据，可以通过 {@link DataPermissionHelper} 进行操作
 * 如需扩展服务，可以通过 ISysDataScopeService 自行编写
```

内置服务实现（bean 名 `sdss`），返回逗号分隔的 id 串，未配置返回 `-1`：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysDataScopeServiceImpl.java:27-29
@RequiredArgsConstructor
@Service("sdss")
public class SysDataScopeServiceImpl implements ISysDataScopeService {
```

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysDataScopeServiceImpl.java:40-54
    @Cacheable(cacheNames = CacheNames.SYS_ROLE_CUSTOM, key = "#roleId", condition = "#roleId != null")
    @Override
    public String getRoleCustom(Long roleId) {
        if (ObjectUtil.isNull(roleId)) {
            return "-1";
        }
        List<SysRoleDept> list = roleDeptMapper.lambda()
            .select(SysRoleDept::getDeptId)
            .eq(SysRoleDept::getRoleId, roleId)
            .list();
        if (CollUtil.isNotEmpty(list)) {
            return StreamUtils.join(list, rd -> Convert.toStr(rd.getDeptId()));
        }
        return "-1";
```

**自定义变量的扩展点**（可注入业务自定义数据用于 SpEL）：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/helper/DataPermissionHelper.java:72-86
    public static <T> T getVariable(String key) {
        Map<String, Object> context = getContext();
        return (T) context.get(key);
    }

    /**
     * 向上下文中设置指定键的变量值
     */
    public static void setVariable(String key, Object value) {
        Map<String, Object> context = getContext();
        context.put(key, value);
    }
```

上下文存储于 Sa-Token 的 `SaStorage`（请求级），key 为 `"data:permission"`（`DataPermissionHelper.java:28,112-123`）；注解里声明的 `@DataColumn.key` 会在构建时被 `context.setVariable(key, value)` 覆盖为**列名**（`PlusDataPermissionHandler.java:134-136`）。

**空值安全**：未定义变量与 `null` 属性都回退为字符串 `"-1"`，避免生成非法 SQL：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:349-357
        @Override
        public Object lookupVariable(String name) {
            Object obj = super.lookupVariable(name);
            // 如果读取到的值是 null，则返回默认值
            if (obj == null) {
                return defaultValue;
            }
            return obj;
        }
```

**限制（重要）**：`DataScopeType` 是**枚举**，code 固定为 `1`–`6`，`sqlTemplate`/`elseSql` 是 `final` 字段（`DataScopeType.java:58-71`）。因此：
- 「用自定义 SQL 片段」只能在**已有 6 种模板**内通过 `@DataColumn` 换列、通过 `DataPermissionHelper.setVariable` 换数据、通过自定义 bean（`@yourBean.method(#...)`）换数据源来实现；
- 若要新增第 7 种数据范围编码，除非**修改 `DataScopeType` 枚举源码**，否则无法实现。
- 任务描述里的"角色 → 菜单 → 数据权限"并不存在一张可配置自定义 SQL 的关联表，见第 3 节。

### 2.7 忽略数据权限

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/helper/DataPermissionHelper.java:130-152
    public static void ignore(Runnable handle) {
        DataPermissionIgnoreContext.enable();
        try {
            handle.run();
        } finally {
            DataPermissionIgnoreContext.disable();
        }
    }

    /**
     * 在忽略数据权限中执行
     */
    public static <T> T ignore(Supplier<T> handle) {
```

实际用法示例：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysUserController.java:126
        SysUserVo user = DataPermissionHelper.ignore(() -> userService.selectUserById(loginUser.getUserId()));
```

```java
// ruoyi-admin/src/main/java/org/dromara/web/service/SysLoginService.java:190
        DataPermissionHelper.ignore(() -> userMapper.updateById(sysUser));
```

底层通过 MP 的 `InterceptorIgnoreHelper` 设置 `dataPermission(true)`，并用栈支持嵌套恢复（`DataPermissionIgnoreContext.java:23-56`）。
**陷阱**：`SysDataScopeServiceImpl` 的类注释明确禁止在数据权限服务里调用带数据权限注解的方法，否则循环解析：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysDataScopeServiceImpl.java:20-23
 * 注意: 此Service内不允许调用标注`数据权限`注解的方法
 * 例如: deptMapper.selectList 此方法标注了`数据权限`注解 会出现循环解析的问题
```

### 2.8 「角色 → 菜单 → 数据权限」精细控制的真实机制

6.0.0 新增的不是新表，而是 **`perm → roleId` 映射 + 当前接口注解解析**，让角色的数据范围**只在该角色被授予对应菜单权限的接口上生效**。

登录时按角色的菜单权限构建映射：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysPermissionServiceImpl.java:71-85
    @Override
    public Map<String, List<Long>> getDataScopeRoleMap(List<RoleDTO> roles) {
        if (CollUtil.isEmpty(roles)) {
            return Map.of();
        }
        List<Long> roleIds = StreamUtils.toList(roles, RoleDTO::getRoleId);
        Map<Long, Set<String>> permsRoleIds = menuService.selectMenuPermsByRoleIds(roleIds);
        Map<String, List<Long>> rolePermsMap = new LinkedHashMap<>();
        permsRoleIds.forEach((roleId, perms) ->
            perms.forEach(perm ->
                rolePermsMap.computeIfAbsent(perm, key -> new ArrayList<>()).add(roleId)
            )
        );
        return rolePermsMap;
    }
```

```java
// ruoyi-admin/src/main/java/org/dromara/web/service/SysLoginService.java:166-170
        }, () -> {
            List<SysRoleVo> roles = roleService.selectRolesByUserId(userId);
            List<RoleDTO> roleDtos = BeanUtil.copyToList(roles, RoleDTO.class);
            loginUser.setRoles(roleDtos);
            loginUser.setDataScopeRoleMap(permissionService.getDataScopeRoleMap(roleDtos));
```

映射来源就是 `sys_role_menu` ⋈ `sys_menu`：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/mapper/SysMenuMapper.java:76-85
        List<SysRoleMenuPermVo> list = this.selectJoinList(SysRoleMenuPermVo.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .selectAs("srm", SysRoleMenu::getRoleId, SysRoleMenuPermVo::getRoleId)
            .selectAs(SysMenu::getPerms, SysRoleMenuPermVo::getPerms)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .in("srm", SysRoleMenu::getRoleId, roleIds)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
            .isNotNull("m", SysMenu::getPerms)
            .build());
```

运行时从当前请求的 `@SaCheckPermission` / `@SaCheckRole` 反解出访问约束：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:266-280
        Set<String> perms = new LinkedHashSet<>();
        Set<String> roleKeys = new LinkedHashSet<>();
        SaCheckPermission saCheckPermission = findAnnotation(handlerMethod, SaCheckPermission.class);
        if (saCheckPermission != null) {
            perms.addAll(toSet(saCheckPermission.value()));
            roleKeys.addAll(toSet(saCheckPermission.orRole()));
        }
        SaCheckRole saCheckRole = findAnnotation(handlerMethod, SaCheckRole.class);
        if (saCheckRole != null) {
            roleKeys.addAll(toSet(saCheckRole.value()));
        }
        if (perms.isEmpty() && roleKeys.isEmpty()) {
            return DataPermissionAccess.EMPTY;
        }
        return new DataPermissionAccess(Set.copyOf(perms), Set.copyOf(roleKeys));
```

按约束筛选参与计算的角色：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:219-250
    private List<RoleDTO> scopeRoles(LoginUser user, DataPermissionAccess access) {
        List<RoleDTO> roles = user.getRoles();
        if (!access.constrained()) {
            return roles;
        }
        ...
        Map<String, List<Long>> dataScopeRoleMap = user.getDataScopeRoleMap();
        if (CollUtil.isNotEmpty(dataScopeRoleMap)) {
            access.perms().forEach(perm -> {
                List<Long> roleIds = dataScopeRoleMap.get(perm);
```

**决定性分支（talent-library 必读）**：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:118-126
        Set<String> conditions = new HashSet<>();
        DataPermissionAccess access = currentAccess();
        List<RoleDTO> scopeRoles = scopeRoles(user, access);
        if (CollUtil.isEmpty(scopeRoles)) {
            if (access.constrained()) {
                return " 1 = 0 ";
            }
            return StringUtils.EMPTY;
        }
```

语义总结：
- 接口**没有** `@SaCheckPermission` / `@SaCheckRole` ⇒ 约束为空 ⇒ 用户**全部**角色的数据范围都生效（OR 叠加）。
- 接口**有** `@SaCheckPermission("talent:xxx:list")` ⇒ 只有**在 `sys_role_menu` 中绑定了 `talent:xxx:list` 这个 `sys_menu.perms` 的角色**参与数据范围计算。
- 有约束但一个匹配角色都没有 ⇒ 返回 `1 = 0`，**查询结果为空**（不会抛异常）。
- `@SaCheckPermission` 的 `orRole` 与 `@SaCheckRole` 的值会按**角色 key**（`role_key`）匹配参与（`PlusDataPermissionHandler.java:243-248`），注意这里是 `StringUtils.splitList(role.getRoleKey())` 的**子串集合**匹配。

> **上线检查清单**：新增 talent-library 菜单/按钮时，必须为相应角色在 `sys_role_menu` 中绑定该权限菜单，否则一旦接口标了 `@SaCheckPermission`，非超管用户即使被 Sa-Token 放行（如通过 `orRole`），数据查询也会因 `1=0` 返回空。

### 2.9 与 MyBatis-Plus-Join（MPJ）联表查询的已知行为

**(a) 列必须自带别名。** 数据权限片段是**文本级拼接**进整条 SQL 的 WHERE，处理器不感知表别名，也不做列名限定。因此联表场景必须手写别名：

```java
// 单表写法（无别名）—— ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/mapper/SysUserMapper.java:38-39
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")

// 联表写法（带别名）—— 同文件 SysUserMapper.java:67-68
        @DataColumn(key = "deptName", value = "d.dept_id"),
        @DataColumn(key = "userName", value = "u.create_by")
```

**(b) `@DataPermission` 必须打在你**自己声明的那个 Mapper 方法上**。MPJ 的 `selectJoinList` / `selectJoinPage` 是 `BaseMapperPlus`/`MPJBaseMapper` 的默认方法，注解不继承，所以本版本的做法是**包一层自己的 `default` 方法并打注解**，内部再调 MPJ：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/mapper/SysMenuMapper.java:142-158
    default List<SysMenuVo> selectMenuListByUserId(SysMenuBo menu, Long userId) {
        return this.selectJoinList(SysMenuVo.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .selectAll(SysMenu.class)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            ...
```

在 `SysUserMapper` 中的 MPJ 用法（注解在外层 default 方法，内部构建 `MPJLambdaWrapper` 后调 `selectJoinList`）：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/mapper/SysUserMapper.java:70-85
    default List<SysUserExportVo> selectUserExportList(SysUserBo user, List<Long> deptIds) {
        MPJLambdaWrapper<SysUser> wrapper = QueryBuilder.lambdaJoin("u", SysUser.class)
            .selectAll(SysUser.class)
            .selectAs("u1", SysUser::getUserName, SysUserExportVo::getLeaderName)
            .leftJoin(SysDept.class, "d", SysDept::getDeptId, SysUser::getDeptId)
            ...
        return this.selectJoinList(SysUserExportVo.class, wrapper);
```

**(c) 子查询/联合查询都会被处理**：`processSelect` 对 `PlainSelect` 与 `SetOperationList` 都设置 where：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/interceptor/PlusDataPermissionInterceptor.java:105-113
    protected void processSelect(Select select, int index, String sql, Object obj) {
        if (select instanceof PlainSelect) {
            this.setWhere((PlainSelect) select, (String) obj);
        } else if (select instanceof SetOperationList setOperationList) {
            List<Select> selectBodyList = setOperationList.getSelects();
            selectBodyList.forEach(s -> this.setWhere((PlainSelect) s, (String) obj));
        }
    }
```

**(d) 潜在死代码 / 类型不一致（未确认是否可达）**：`PlusDataPermissionInterceptor.buildTableExpression` 把处理器强转为 `MultiDataPermissionHandler`，但 `PlusDataPermissionHandler` 的类声明并未实现该接口：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/PlusDataPermissionHandler.java:48
public class PlusDataPermissionHandler {
```

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/interceptor/PlusDataPermissionInterceptor.java:168-173
    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        // 只有新版数据权限处理器才会执行到这里
        final MultiDataPermissionHandler handler = (MultiDataPermissionHandler) dataPermissionHandler;
        return handler.getSqlSegment(table, where, whereSegment);
    }
```

本版本三个扩展点 `processSelect` / `processUpdate` / `processDelete` **均已覆写**，且 `setWhere` 直接调用 `dataPermissionHandler.getSqlSegment(...)`（`PlusDataPermissionInterceptor.java:153-158`），因此**按源码推断** `buildTableExpression` 不会被调用，强转不会触发。
「未确认」：该分支在运行时是否真的不可达，**需要实际运行时验证**（未启动应用 / 未跑 SQL 日志）；本次为纯静态源码排查，未做运行时验证。

### 2.10 数据权限相关的未确认项

- 未确认：`buildTableExpression` 是否绝对不可达（见 2.9d）。缺少的证据是运行时 SQL 日志或调试断点。
- 未确认：`DataPermission` 打在 **类**上时，MyBatis Mapper 的场景是否真的走到类级匹配（切点有 `resolveTargetClass` 处理 JDK 代理，`DataPermissionAdvice.getProxyClassDataPermission` 只扫接口）。全仓**没有任何 Mapper 在类上使用 `@DataPermission`** 的实例，无先例可循。

---

## 3. 角色与菜单权限的数据模型

### 3.1 `sys_role`（角色信息表）

```sql
-- script/sql/ry_vue.sql:151-170
create table sys_role (
    role_id              bigint(20)      not null                   comment '角色ID',
    role_name            varchar(30)     not null                   comment '角色名称',
    role_key             varchar(100)    not null                   comment '角色权限字符串',
    role_sort            int(4)          not null                   comment '显示顺序',
    data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限 6：部门及以下或本人数据权限）',
    menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
    dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
    status               char(1)         not null                   comment '角色状态（0正常 1停用）',
    del_flag             char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    ...
    primary key (role_id),
```

Domain 类：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysRole.java:44-52
    /**
     * 数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限 6：部门及以下或本人数据权限）
     */
    private String dataScope;

    /**
     * 菜单树选择项是否关联显示（ 0：父子不互相关联显示 1：父子互相关联显示）
     */
    private Boolean menuCheckStrictly;
```

初始化数据（注意已内置 `data_scope=4` 与 `data_scope=5` 两个测试角色）：

```sql
-- script/sql/ry_vue.sql:175-177
insert into sys_role values(1761300000000000001, '超级管理员', 'superadmin', 1, 1, 1, 1, '0', '0', ...);
insert into sys_role values(1761300000000000003, '本部门及以下', 'test1', 3, 4, 1, 1, '0', '0', ...);
insert into sys_role values(1761300000000000004, '仅本人', 'test2', 4, 5, 1, 1, '0', '0', ...);
```

### 3.2 `sys_menu`（菜单权限表）

```sql
-- script/sql/ry_vue.sql:182-206
create table sys_menu (
    menu_id           bigint(20)      not null                   comment '菜单ID',
    menu_name         varchar(50)     not null                   comment '菜单名称',
    parent_id         bigint(20)      default 0                  comment '父菜单ID',
    order_num         int(4)          default 0                  comment '显示顺序',
    path              varchar(200)    default ''                 comment '路由地址',
    component         varchar(255)    default null               comment '组件路径',
    query_param       varchar(255)    default null               comment '路由参数',
    is_frame          char(1)         default 'N'                comment '是否为外链（Y是 N否）',
    is_cache          char(1)         default 'Y'                comment '是否缓存（Y缓存 N不缓存）',
    menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
    visible           char(1)         default 0                  comment '显示状态（0显示 1隐藏）',
    status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
    perms             varchar(100)    default null               comment '权限标识',
    icon              varchar(100)    default '#'                comment '菜单图标',
    active_menu       varchar(255)    default ''                 comment '激活菜单路径',
    ext               varchar(2000)   default ''                 comment '扩展字段',
    ...
    primary key (menu_id)
) engine=innodb comment = '菜单权限表';
```

### 3.3 `sys_role_menu`（角色和菜单关联表）

```sql
-- script/sql/ry_vue.sql:363-370
-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
create table sys_role_menu (
    role_id   bigint(20) not null comment '角色ID',
    menu_id   bigint(20) not null comment '菜单ID',
    primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';
```

Domain（只有两个字段，**没有** data_scope 列）：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysRoleMenu.java:14-28
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    /**
     * 角色ID
     */
    @TableId(type = IdType.INPUT)
    private Long roleId;

    /**
     * 菜单ID
     */
    private Long menuId;

}
```

Mapper XML 为空（逻辑全在 `SysMenuMapper` 的 default 方法与 MPJ wrapper 中）：

```xml
<!-- ruoyi-modules/ruoyi-system/src/main/resources/mapper/system/SysRoleMenuMapper.xml:5-7 -->
<mapper namespace="org.dromara.system.mapper.SysRoleMenuMapper">

</mapper>
```

写入由 `SysRoleServiceImpl` 负责：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysRoleServiceImpl.java:373-386
    private int insertRoleMenu(SysRoleBo role) {
        int rows = 1;
        // 新增用户与角色管理
        List<SysRoleMenu> list = new ArrayList<>();
        for (Long menuId : role.getMenuIds()) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(role.getRoleId());
            rm.setMenuId(menuId);
            list.add(rm);
        }
        if (CollUtil.isNotEmpty(list)) {
            rows = roleMenuMapper.insertBatch(list) ? list.size() : 0;
        }
        return rows;
    }
```

### 3.4 自定义（`data_scope=2`）数据范围使用 `sys_role_dept`

```sql
-- script/sql/ry_vue.sql:494-500
-- 8、角色和部门关联表  角色1-N部门
-- ----------------------------
create table sys_role_dept (
    role_id   bigint(20) not null comment '角色ID',
    dept_id   bigint(20) not null comment '部门ID',
    primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';
```

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysRoleDept.java:14-27
@Data
@TableName("sys_role_dept")
public class SysRoleDept {

    /**
     * 角色ID
     */
    @TableId(type = IdType.INPUT)
    private Long roleId;

    /**
     * 部门ID
     */
    private Long deptId;

}
```

### 3.5 **明确不存在**「sys_role_menu_data_scope」之类的新关联表

对全部 4 份初始化 SQL（`script/sql/ry_vue.sql`、`script/sql/postgres/postgres_ry_vue.sql`、`script/sql/oracle/oracle_ry_vue.sql`、`script/sql/sqlserver/sqlserver_ry_vue.sql`）检索 `data_scope`，**只命中 `sys_role.data_scope` 一个字段**，不存在任何其它含 `data_scope` 的表或列：

```sql
-- script/sql/ry_vue.sql:156（唯一命中）
    data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 ...）',
```

`script/sql/ry_vue.sql` 的全部 `create table` 清单（共 22 张表，**无**任何数据权限关联表）：
`sys_social`(4) `sys_dept`(40) `sys_user`(81) `sys_post`(119) `sys_role`(151) `sys_menu`(182) `sys_user_role`(349) `sys_role_menu`(366) `sys_role_dept`(496) `sys_user_post`(505) `sys_oper_log`(520) `sys_dict_type`(555) `sys_dict_data`(585) `sys_config`(644) `sys_login_info`(667) `sys_notice`(688) `sys_message`(713) `gen_table`(737) `gen_table_column`(766) `sys_oss`(796) `sys_oss_config`(815) `sys_client`(847)。

**因此，6.0.0 的「角色 → 菜单 → 数据权限」精细化不是通过新增关联表实现的**，而是：
`sys_role.data_scope`（角色级数据范围） + `sys_role_menu ⋈ sys_menu.perms`（决定该数据范围在哪些接口权限上生效） + 运行时解析 `@SaCheckPermission` / `@SaCheckRole`（见 2.8）。

> 「未确认」：上述结论仅限于**本 commit 的源码/SQL**。任务描述若期望存在 `sys_role_menu_data_scope`，则与本检出副本不符——证据缺失项为「仓库中任何该表名的定义」。

---

## 4. 登录用户上下文与客户端

### 4.1 `LoginUser` 字段全集

文件：`ruoyi-api/src/main/java/org/dromara/system/api/model/LoginUser.java`

```java
// ruoyi-api/src/main/java/org/dromara/system/api/model/LoginUser.java:26-59
    /** 用户ID */
    private Long userId;
    /** 部门ID */
    private Long deptId;
    /** 部门类别编码 */
    private String deptCategory;
    /** 部门名 */
    private String deptName;
    /** 用户唯一标识 */
    private String token;
    /** 用户类型 */
    private String userType;
    /** 登录时间 */
    private Long loginTime;
    /** 过期时间 */
    private Long expireTime;
    /** 登录IP地址 */
    private String ipaddr;
    /** 登录地点 */
    private String loginLocation;
    /** 浏览器类型 */
    private String browser;
    /** 操作系统 */
    private String os;
```

```java
// ruoyi-api/src/main/java/org/dromara/system/api/model/LoginUser.java:86-134
    /** 菜单权限 */
    private Set<String> menuPermission;
    /** 角色权限 */
    private Set<String> rolePermission;
    /** 用户名 */
    private String username;
    /** 用户昵称 */
    private String nickname;
    /** 角色对象 */
    private List<RoleDTO> roles;
    /**
     * 数据权限角色映射 key 为权限码 value 为可参与数据权限计算的角色ID列表
     */
    private Map<String, List<Long>> dataScopeRoleMap;
    /** 岗位对象 */
    private List<PostDTO> posts;
    /** 数据权限 当前角色ID */
    private Long roleId;
    /** 客户端 */
    private String clientKey;
    /** 设备类型 */
    private String deviceType;
```

### 4.2 `clientId` / 客户端与设备类型

**关键：常量名是 `CLIENT_KEY = "clientid"`，不是 `clientId`。**

```java
// ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/utils/LoginHelper.java:37-45
    public static final String LOGIN_USER_KEY = "loginUser";
    public static final String USER_KEY = "userId";
    public static final String USER_NAME_KEY = "userName";
    public static final String DEPT_KEY = "deptId";
    public static final String DEPT_NAME_KEY = "deptName";
    public static final String DEPT_CATEGORY_KEY = "deptCategory";
    public static final String CLIENT_KEY = "clientid";
    public static final String CLIENT_ACCESS_PATH_KEY = "clientAccessPath";
    public static final String CLIENT_IP_WHITELIST_KEY = "clientIpWhitelist";
```

该常量同时作为 **HTTP 请求头 / 参数名**使用：

```java
// ruoyi-common/ruoyi-common-security/src/main/java/org/dromara/common/security/config/SecurityConfig.java:98-100
                        String headerCid = request.getHeader(LoginHelper.CLIENT_KEY);
                        String paramCid = ServletUtils.getParameter(LoginHelper.CLIENT_KEY);
                        String clientId = StpUtil.getExtra(LoginHelper.CLIENT_KEY).toString();
```

登录时写入 token extra 与 `LoginUser`：

```java
// ruoyi-admin/src/main/java/org/dromara/web/service/IAuthStrategy.java:60-63
        model.setDeviceType(client.getDeviceType());
        ...
        model.setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
```

```java
// ruoyi-admin/src/main/java/org/dromara/web/service/impl/PasswordAuthStrategy.java:73-74
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
```

同一模式出现在 `EmailAuthStrategy.java:61-62`、`SmsAuthStrategy.java:61-62`、`SocialAuthStrategy.java:74-75`、`XcxAuthStrategy.java:81-82`。

`SaLoginParameter` 的 `deviceType` 也会回填到 `LoginUser`：

```java
// ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/utils/LoginHelper.java:92-94
        if (StringUtils.isBlank(loginUser.getDeviceType()) && StringUtils.isNotBlank(model.getDeviceType())) {
            loginUser.setDeviceType(model.getDeviceType());
        }
```

> **注意**：`LoginHelper` **没有** `getClientKey()` / `getDeviceType()` 静态方法，也没有 `getRoleList()`。要取客户端信息必须 `LoginHelper.getLoginUser().getClientKey()`，或直接读 Sa-Token extra：`StpUtil.getExtra(LoginHelper.CLIENT_KEY)`。

### 4.3 用户类型与登录 ID

```java
// ruoyi-api/src/main/java/org/dromara/system/api/model/LoginUser.java:141-149
    public String getLoginId() {
        if (userType == null) {
            throw new IllegalArgumentException("用户类型不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return userType + ":" + userId;
    }
```

用户类型枚举只有两个值：

```java
// ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/enums/UserType.java:19-24
    SYS_USER("sys_user"),
    /**
     * 移动客户端用户
     */
    APP_USER("app_user");
```

`SaPermissionImpl.resolveUserId` 依赖 `userType:userId` 的冒号格式解析 ID（`SaPermissionImpl.java:93-100`）。

### 4.4 **确认无 tenantId 依赖**

对全仓 Java 源码检索 `tenantId|tenant_id|TenantHelper`，**仅命中 2 处与多租户无关的社交登录配置**：

```
ruoyi-common/ruoyi-common-social/src/main/java/org/dromara/common/social/utils/SocialUtils.java:78
ruoyi-common/ruoyi-common-social/src/main/java/org/dromara/common/social/config/properties/SocialLoginConfigProperties.java:38
```

```java
// ruoyi-common/ruoyi-common-social/src/main/java/org/dromara/common/social/config/properties/SocialLoginConfigProperties.java:38
    private String tenantId;
```

该字段是微软 OAuth 的 tenant 参数（`SocialUtils.java:78` 传入 `AuthMicrosoftRequest`），**与数据权限/多租户无关**。

同时：
- `LoginUser` 中**不存在** `tenantId` 字段（见 4.1 完整字段清单）。
- `LoginHelper` 中**不存在**任何 tenant 相关方法或常量。
- 唯一残留痕迹是 MP 的忽略策略判断里仍检查 `getTenantLine()`，属于 MP `IgnoreStrategy` API 的固有字段，**不是本项目启用多租户**：

```java
// ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/helper/DataPermissionIgnoreContext.java:79-85
    private static boolean isOnlyDataPermissionIgnored(IgnoreStrategy ignoreStrategy) {
        return !Boolean.TRUE.equals(ignoreStrategy.getDynamicTableName())
            && !Boolean.TRUE.equals(ignoreStrategy.getBlockAttack())
            && !Boolean.TRUE.equals(ignoreStrategy.getIllegalSql())
            && !Boolean.TRUE.equals(ignoreStrategy.getTenantLine())
            && CollectionUtil.isEmpty(ignoreStrategy.getOthers());
    }
```

`MybatisPlusConfig` 的拦截器链也**没有**注册 `TenantLineInnerInterceptor`（`MybatisPlusConfig.java:46-55`；类尾注释仅保留文档链接，`MybatisPlusConfig.java:146`）。

**结论**：6.0.0 已移除多租户，talent-library 的业务表**不需要** `tenant_id` 列，也不应依赖任何租户上下文。

---

## 5. 权限字符串下发前端 + `SysMenu` 字段含义

### 5.1 下发给前端的权限形式

`getInfo` 接口把**权限字符串集合**与**角色 key 集合**原样下发：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysUserController.java:121-133
    @GetMapping("/getInfo")
    public R<UserInfoVo> getInfo() {
        UserInfoVo userInfoVo = new UserInfoVo();
        LoginUser loginUser = LoginHelper.getLoginUser();

        SysUserVo user = DataPermissionHelper.ignore(() -> userService.selectUserById(loginUser.getUserId()));
        if (ObjectUtil.isNull(user)) {
            return R.fail("没有权限访问用户数据!");
        }
        userInfoVo.setUser(user);
        userInfoVo.setPermissions(loginUser.getMenuPermission());
        userInfoVo.setRoles(loginUser.getRolePermission());
        return R.ok(userInfoVo);
    }
```

- `permissions` = `Set<String>`，元素形如 `system:user:list`；**超级管理员为单个通配串 `*:*:*`**（`SysPermissionServiceImpl.java:58`）。
- `roles` = `Set<String>`，元素为 `role_key`；**超级管理员为 `superadmin`**（`SysPermissionServiceImpl.java:40`）。
- 角色（非权限）来源：`roleService.selectRolePermissionByUserId(userId)`（`SysPermissionServiceImpl.java:42`）。

菜单/路由通过 `getRouters` 下发（`SysMenuController.java:45-47`），字段结构见 `RouterVo`。

### 5.2 `SysMenu` 字段含义（domain 类注释原文）

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysMenu.java:48-101
    /**
     * 路由地址
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 路由参数
     */
    private String queryParam;

    /**
     * 是否为外链（Y是 N否）
     */
    private String isFrame;

    /**
     * 是否缓存（Y缓存 N不缓存）
     */
    private String isCache;

    /**
     * 类型（M目录 C菜单 F按钮）
     */
    private String menuType;
    ...
    /**
     * 权限字符串
     */
    private String perms;
```

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysMenu.java:98-106
    /**
     * 激活菜单路径
     */
    private String activeMenu;

    /**
     * 扩展字段
     */
    private String ext;
```

字段对照表：

| 字段 | 数据库列 | 含义 |
|---|---|---|
| `menuId` | `menu_id` | 菜单ID |
| `parentId` | `parent_id` | 父菜单ID（顶级为 `0`，见 `Constants.TOP_PARENT_ID`） |
| `menuName` | `menu_name` | 菜单名称 |
| `orderNum` | `order_num` | 显示顺序 |
| `path` | `path` | 路由地址 |
| `component` | `component` | 组件路径 |
| `queryParam` | `query_param` | 路由参数（下发前端时映射为 `RouterVo.query`） |
| `isFrame` | `is_frame` | 是否外链（Y/N） |
| `isCache` | `is_cache` | 是否缓存（Y/N） |
| `menuType` | `menu_type` | `M` 目录 / `C` 菜单 / `F` 按钮 |
| `visible` | `visible` | 0 显示 / 1 隐藏 |
| `status` | `status` | 0 正常 / 1 停用 |
| `perms` | `perms` | **权限字符串**，即 `@SaCheckPermission` 的值 |
| `icon` | `icon` | 菜单图标 |
| `activeMenu` | `active_menu` | 激活菜单路径 |
| `ext` | `ext` | 扩展字段（varchar 2000） |

`menu_type` 常量：

```java
// ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/constant/SystemConstants.java:33,38,43
    String TYPE_DIR = "M";
    String TYPE_MENU = "C";
    String TYPE_BUTTON = "F";
```

**`queryParam` → 前端 `query` 的映射**：`SysMenuVo` 保留原名 `queryParam`，而 `RouterVo` 用 `query`：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/vo/SysMenuVo.java:59
    private String queryParam;
```

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/vo/RouterVo.java:45
    private String query;
```

（映射动作在 `SysMenuServiceImpl.buildMenus` 中完成，本次未逐行展开。）

`path` / `component` 在组装路由时会被重写，规则集中在 `SysMenu` 的三个判定方法里：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysMenu.java:140-156
    public String getRouterPath() {
        String routerPath = this.path;
        // 内链打开外网方式
        if (!Constants.TOP_PARENT_ID.equals(getParentId()) && isInnerLink()) {
            routerPath = innerLinkReplaceEach(routerPath);
        }
        // 非外链并且是一级目录（类型为目录）
        if (Constants.TOP_PARENT_ID.equals(getParentId()) && SystemConstants.TYPE_DIR.equals(getMenuType())
            && SystemConstants.NO.equals(getIsFrame())) {
            routerPath = "/" + this.path;
        }
```

`component` 为空时会按场景回退为 `Layout` / `InnerLink` / `ParentView`：

```java
// ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysMenu.java:161-171
    public String getComponentInfo() {
        String component = SystemConstants.LAYOUT;
        if (StringUtils.isNotEmpty(this.component) && !isMenuFrame()) {
            component = this.component;
        } else if (StringUtils.isEmpty(this.component) && !Constants.TOP_PARENT_ID.equals(getParentId()) && isInnerLink()) {
            component = SystemConstants.INNER_LINK;
        } else if (StringUtils.isEmpty(this.component) && isParentView()) {
            component = SystemConstants.PARENT_VIEW;
        }
        return component;
    }
```

**talent-library 菜单登记要点**：按钮级权限（新增/修改/删除/导出）应建成 `menu_type='F'` 的菜单行，`perms` 填与 Controller `@SaCheckPermission` 完全一致的字符串，并绑到 `sys_role_menu`（否则触发 2.8 的 `1=0` 空结果）。

---

## 6. talent-library 落地要点（基于以上证据）

1. `@DataPermission` 打在 **Mapper 接口方法**（含 `default` 方法）上，不是 Service 上（2.4）。
2. 表必须有「部门列」与「所属人列」两个维度，`@DataPermission` 同时声明 `deptName` 与 `userName` 两个 `@DataColumn`，否则 `SELF`/`DEPT` 角色会因模板不匹配落到 `1 = 0`（2.5）。
3. 联表查询的 `@DataColumn.value` 必须写**带别名**的列（2.9a）。
4. Controller 用 `@SaCheckPermission("talent:xxx:yyy")`；超管判断用 `LoginHelper.isSuperAdmin()` / `SystemConstants.SUPER_ADMIN_ROLE_KEY`（值为 `"superadmin"`）（1.2、1.3）。
5. 新菜单的 `perms` 必须与 Controller 权限串一致并绑定到角色，否则数据权限会静默返回空集（2.8）。
6. 无 `tenant_id`，无需租户上下文（4.4）。
7. 需要跨权限查询（如更新自己的资料）时用 `DataPermissionHelper.ignore(...)`（2.7）。
8. 不要试图在 `SysDataScopeServiceImpl` 内调用带数据权限注解的方法（死循环）（2.7）。
