# C — 对象存储（OSS）与文件能力 API 参考（RuoYi-Vue-Plus v6.0.0）

> 调研对象：`D:\DeepseekHarness\RuoYi-Vue-Plus`，commit `7180b52`（`git describe` = `v6.0.0`，`pom.xml:17` → `<revision>6.0.0</revision>`）。
> 下文所有 `相对路径:行号` 均相对于仓库根 `D:\DeepseekHarness\RuoYi-Vue-Plus`。
> 本次为只读侦察，未修改被调研目录中的任何文件。

---

## 1. 模块结构与客户端接口

### 1.1 `ruoyi-common-oss` 包结构

`ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/` 下的包（完整文件清单来自目录枚举）：

| 包 | 类 |
| --- | --- |
| `client` | `OssClient`（接口）、`AbstractOssClientImpl`（抽象实现）、`DefaultOssClientImpl`（唯一具体实现） |
| `config` | `Config`、`OssClientConfig`、`AccessControlPolicyConfig`、`OssAsyncExecutorConfig` |
| `constant` | `OssConstant` |
| `enums` | `AccessPolicy` |
| `exception` | `S3StorageException` |
| `factory` | `OssFactory` |
| `io` | `OutputStreamDownloadSubscriber` |
| `model` | `GetObjectResult`、`PutObjectResult`、`Options`、`HandleAsyncResult` |
| `properties` | `OssProperties` |
| `util` | `BucketUrlUtil` |

注意：**6.0.0 中不存在按厂商拆分的客户端类**（没有 `MinioOssClient`、`S3OssClient`、`CosOssClient` 之类），整个模块只有一个 `DefaultOssClientImpl`。这与 5.x 的 `IOssClient`/`AbstractOssClient`/`MinioOssClient`/`AliyunOssClient` 结构完全不同。

### 1.2 客户端接口：`org.dromara.common.oss.client.OssClient`

`ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/client/OssClient.java:43`

```java
public interface OssClient extends AutoCloseable {
    default String clientId() { return IdUtil.fastSimpleUUID(); }   // :52
    OssClientConfig config();                                       // :59
    boolean isInitialized();                                        // :64
    void initialize();                                              // :69
    boolean verifyConfig(Function<OssClientConfig, Boolean> verifyConfigAction); // :79
    boolean verifyConfig(OssClientConfig verifyConfig);             // :89
```

接口按「显式桶（`bucketXxx`）」与「默认桶（无前缀）」两套对称 API 设计（`OssClient.java:37-39`）。关键签名：

**上传**

```java
PutObjectResult bucketUpload(String bucket, String key, Path path, Options options);      // :142
PutObjectResult bucketUpload(String bucket, String key, File file, Options options);      // :163
PutObjectResult bucketUpload(String bucket, String key, InputStream in, long contentLength, Options options); // :229
PutObjectResult bucketUpload(String bucket, String key, byte[] data, Options options);    // :251
PutObjectResult upload(String key, File file, Options options);                           // :401
PutObjectResult upload(String key, InputStream in, long contentLength, Options options);  // :461
```

**下载 / 取对象**

```java
GetObjectResult bucketDownload(String bucket, String key, OutputStream out);                                  // :342
GetObjectResult bucketDownload(String bucket, String key, OutputStreamDownloadSubscriber downloadSubscriber); // :282
<T> T bucketDownload(String bucket, String key, BiFunction<GetObjectResult, InputStream, T> downloadTransformer); // :292
GetObjectResult download(String key, OutputStream out);                                                       // :553
<T> T download(String key, BiFunction<GetObjectResult, InputStream, T> downloadTransformer);                  // :508
```

**删除 / 预签名 / 对象键**

```java
boolean bucketDelete(String bucket, String key);                                        // :351
boolean delete(String key);                                                             // :561
String bucketPresignGetUrl(String bucket, String key, Duration expiredTime);            // :361
String bucketPresignPutUrl(String bucket, String key, Duration expiredTime, Map<String,String> metadata); // :372
String presignGetUrl(String key, Duration expiredTime);                                 // :570
String presignPutUrl(String key, Duration expiredTime, Map<String,String> metadata);    // :580
String buildPathKey(String fileName);                                                   // :588
String buildPathKey(String businessPrefix, String fileName);                            // :597
```

上传/下载的可选参数载体是 `Options`（`model/Options.java:24`），字段为 `length` / `md5Digest` / `contentType` / `metadata` / `transferListeners`，并用 `Options.builder()` 构建（`Options.java:76`）。

返回模型：`PutObjectResult(url, key, eTag, size)`（`model/PutObjectResult.java:12`）、`GetObjectResult(key, eTag, lastModified, size, contentType, contentDisposition, contentRange, contentEncoding, contentLanguage, metadata)`（`model/GetObjectResult.java:21`）。

---

## 2. 客户端实现：MinIO / S3 / 腾讯 COS

### 2.1 单一 S3 协议实现，无厂商专属客户端

`ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/client/DefaultOssClientImpl.java:60`

```java
this.s3AsyncClient = S3AsyncClient.builder()
    .credentialsProvider(credentialsProvider)
    .endpointOverride(URI.create(endpointUrl))
    .region(region)
    .forcePathStyle(usePathStyleAccess)
    .serviceConfiguration(S3Configuration.builder().build())
    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
    ...
```

`ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/factory/OssFactory.java:66` 是唯一实例化点，硬编码 `DefaultOssClientImpl`：

```java
OssClient newClient = new DefaultOssClientImpl(configKey, config);
CLIENT_CACHE.put(configKey, newClient);
```

依赖只有 AWS SDK v2（`ruoyi-common/ruoyi-common-oss/pom.xml:32-64`：`software.amazon.awssdk:s3`、`netty-nio-client`、`s3-transfer-manager`），**没有任何 `io.minio` 或腾讯云 COS SDK 依赖**。

### 2.2 腾讯 COS 是否通过 S3 兼容协议接入 —— 是

证据链：
- `OssConstant.CLOUD_SERVICE = {"aliyun", "qcloud", "qiniu", "obs"}`（`constant/OssConstant.java:38`），`qcloud` 是内置云厂商标识；
- 路径风格推断逻辑按该常量判断（`config/OssClientConfig.java:247-250`）：

```java
private static boolean resolvePathStyleAccess(OssProperties properties) {
    // 旧配置没有显式路径风格字段，只能继续按内置云厂商 endpoint 做兼容推断。
    return !StringUtils.containsAny(properties.getEndpoint(), OssConstant.CLOUD_SERVICE);
}
```

- SQL 种子数据里 `qcloud` 条目就是 COS 的 S3 兼容域名（`script/sql/ry_vue.sql:841`）：

```sql
insert into sys_oss_config values (1761900000000000004, 'qcloud', 'XXXXXXXXXXXXXXX', 'XXXXXXXXXXXXXXX',
  'ruoyi-1240000000', '', 'cos.ap-beijing.myqcloud.com', '', 'N', 'ap-beijing', '1', 'N', '', ...);
```

即配置方式为：`config_key='qcloud'`、`endpoint='cos.<region>.myqcloud.com'`、`bucket_name='<bucket>-<APPID>'`、`region='ap-beijing'`、`is_https='N'`、`access_policy='1'`。**COS 没有专属代码分支**，走的就是 S3 兼容协议 + 上述 endpoint。

### 2.3 配置项真实字段名：`application.yml` 里没有 OSS 配置

`ruoyi-admin/src/main/resources/application.yml` 中与上传相关只有 Spring 原生 multipart 限制（`:66-72`）：

```yaml
  # 文件上传
  servlet:
    multipart:
      # 单个文件大小
      max-file-size: 10MB
      # 设置总上传的文件大小
      max-request-size: 20MB
```

全仓库 grep `oss|upload|max-file` 在 `*.yml` 中仅命中上述一处。**OSS 配置完全由数据库 `sys_oss_config` 驱动**：启动时 `SysOssConfigServiceImpl.init()` 把每行配置序列化成 JSON 写入缓存（`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysOssConfigServiceImpl.java:56-62`）：

```java
for (SysOssConfig config : list) {
    String configKey = config.getConfigKey();
    if (SystemConstants.YES.equals(config.getStatus())) {
        RedisUtils.setCacheObject(OssConstant.DEFAULT_CONFIG_KEY, configKey);
    }
    CacheUtils.put(CacheNames.SYS_OSS_CONFIG, config.getConfigKey(), JsonUtils.toJsonString(config));
}
```

`OssFactory.instance(String configKey)` 再从缓存 JSON 反序列化为 `OssProperties`（`factory/OssFactory.java:50-55`）。`OssProperties` 没有 `@ConfigurationProperties` 注解，字段名（`properties/OssProperties.java:16-56`）即 `sys_oss_config` 的驼峰映射：

```java
private String endpoint;      // -> endpoint
private String domainUrl;     // -> domain_url
private String prefix;        // -> prefix
private String accessKey;     // -> access_key
private String secretKey;     // -> secret_key
private String bucketName;    // -> bucket_name
private String region;        // -> region
private String isHttps;       // -> is_https
private String accessPolicy;  // -> access_policy
```

`Config.getEndpointUrl()` / `getDomainUrl()` 会按 `isHttps` 重写协议头，域名优先于站点（`config/OssClientConfig.java:186-202`）：

```java
public String getDomainUrl() {
    return domain()
        .filter(StringUtils::isNotBlank)
        .map(domain -> BucketUrlUtil.rebuildUrlHeader(useHttps, domain.trim()))
        .orElseGet(this::getEndpointUrl);
}
```

`isHttps` 判定用 `SystemConstants.YES`（= `"Y"`，`ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/constant/SystemConstants.java:23`）：`config/OssClientConfig.java:176`。注意 `SysOssConfig.java:61` 的注释写「0否 1是」，但 SQL 列注释与种子数据用的是 `Y/N`（`script/sql/ry_vue.sql:824`、`:838`）——**实际生效的是 `Y/N`**。

---

## 3. 私有桶访问：预签名 URL 与受控下载

### 3.1 预签名下载 URL

`ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/client/AbstractOssClientImpl.java:730`

```java
public String bucketPresignGetUrl(String bucket, String key, Duration expiredTime) {
    if (useBucketBoundDomain(bucket)) {
        return bucketBoundDomainPresignUrl(SdkHttpMethod.GET, key, expiredTime, Collections.emptyMap());
    }
    try {
        return s3Presigner.presignGetObject(getObjectPresignRequestBuilder -> {
                getObjectPresignRequestBuilder.signatureDuration(expiredTime)
                    .getObjectRequest(getObjectRequestBuilder -> getObjectRequestBuilder.bucket(bucket).key(key));
            })
            .url()
            .toExternalForm();
```

过期时间参数类型是 `java.time.Duration`（`expiredTime`），无默认值，必须由调用方传入。

### 3.2 自定义域名支持（6.0.0 重写点）

当配置了 `domain_url` **且** bucket 等于默认桶时，走自研签名路径，绕开 SDK 自动拼接桶名（`AbstractOssClientImpl.java:778-784`）：

```java
private boolean useBucketBoundDomain(String bucket) {
    return config.domain()
        .filter(StringUtils::isNotBlank)
        .isPresent() && config.bucket()
        .filter(defaultBucket -> Objects.equals(defaultBucket, bucket))
        .isPresent();
}
```

自研签名用 `AwsV4HttpSigner` 直接把签名写进 query string（`AbstractOssClientImpl.java:809-832`）：

```java
AwsCredentialsIdentity credentials = AwsCredentialsIdentity.create(
    config.accessKey().filter(StringUtils::isNotBlank)
        .orElseThrow(() -> S3StorageException.form("accessKey is not configured.")),
    config.secretKey().filter(StringUtils::isNotBlank)
        .orElseThrow(() -> S3StorageException.form("secretKey is not configured.")));
Clock signingClock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
return AwsV4HttpSigner.create()
    .sign(SignRequest.builder(credentials)
        .request(requestBuilder.build())
        .putProperty(AwsV4HttpSigner.REGION_NAME, config.region().orElse(Region.US_EAST_1).id())
        .putProperty(AwsV4FamilyHttpSigner.SERVICE_SIGNING_NAME, "s3")
        .putProperty(AwsV4FamilyHttpSigner.AUTH_LOCATION, AwsV4FamilyHttpSigner.AuthLocation.QUERY_STRING)
        .putProperty(AwsV4FamilyHttpSigner.PAYLOAD_SIGNING_ENABLED, false)
        .putProperty(AwsV4FamilyHttpSigner.EXPIRATION_DURATION, expiredTime)
        .putProperty(HttpSigner.SIGNING_CLOCK, signingClock)
        .putProperty(AwsV4FamilyHttpSigner.DOUBLE_URL_ENCODE, false)
        .putProperty(AwsV4FamilyHttpSigner.NORMALIZE_PATH, false)
        .build())
    .request().getUri().toString();
```

路径拼接会保留自定义域名的 base path 并对 key 做 `urlEncodeIgnoreSlashes`（`AbstractOssClientImpl.java:845-857`）。

预签名上传同理：`AbstractOssClientImpl.java:756`，metadata 会写成 `x-amz-meta-<k>` 头（`:802-808`）。

### 3.3 「私有桶」的判定开关

私有桶判定不是看桶本身，而是看配置里的 `access_policy` 字符串（`config/AccessControlPolicyConfig.java` + `enums/AccessPolicy.java:23`）：

```java
PRIVATE(0, BucketCannedACL.PRIVATE, ObjectCannedACL.PRIVATE),
PUBLIC_READ_WRITE(1, BucketCannedACL.PUBLIC_READ_WRITE, ObjectCannedACL.PUBLIC_READ_WRITE),
PUBLIC_READ(2, BucketCannedACL.PUBLIC_READ, ObjectCannedACL.PUBLIC_READ);
```

`OssClientConfig.resolveAccessControlPolicy` 将字符串映射为枚举，且注释明确「当前业务只用访问策略判断是否生成预签名 URL」（`config/OssClientConfig.java:258-267`）。

框架自带的自动降级在 `SysOssServiceImpl.matchingUrl`（`SysOssServiceImpl.java:324-331`）：

```java
private SysOssVo matchingUrl(SysOssVo oss) {
    OssClient instance = OssFactory.instance(oss.getService());
    // 仅修改桶类型为 private 的URL，临时URL时长为120s
    if (instance.verifyConfig(config -> AccessPolicy.PRIVATE.equals(config.accessControlPolicyConfig().accessPolicy()))) {
        oss.setUrl(instance.presignGetUrl(oss.getFileName(), Duration.ofSeconds(120)));
    }
    return oss;
}
```

即：**把 `sys_oss_config.access_policy` 设为 `0`，则所有读取 `SysOssVo.url` 的入口（分页列表、`listByIds`、上传返回）都会自动换成 120 秒有效的预签名 URL**。种子数据默认是 `'1'`（public read/write），因此默认环境下 `url` 是永久直链。

### 3.4 受控流式（服务端代理）下载应调用哪个方法

服务端代理下载的正确客户端方法是 `OssClient#download(String key, BiFunction<GetObjectResult, InputStream, T>)`（`OssClient.java:508`，实现见 `AbstractOssClientImpl.java:599`，内部用 `AsyncResponseTransformer.toBlockingInputStream()` 拿到阻塞流）。框架自身就用了它（`SysOssServiceImpl.java:204-221`）。

但要明确：**框架现成的 `ISysOssService.download` 不是流式，而是全量载入内存**——`AbstractOssClientImpl` 虽然提供了 `InputStream`，`SysOssServiceImpl` 最后用 `IoUtil.readBytes(inputStream)` 把整个文件读成 `byte[]`（`SysOssServiceImpl.java:220`）。若要真流式，需要自带 controller 直接调 `OssFactory.instance(service).download(key, (result, in) -> ...)` 并配合 `StreamingResponseBody` / `OutputStream` 重载；仓库内**未确认**有现成的流式下载示例。

---

## 4. 系统表与领域对象

### 4.1 `sys_oss`（`script/sql/ry_vue.sql:796-810`）

```sql
create table sys_oss (
    oss_id          bigint(20)   not null                   comment '对象存储主键',
    file_name       varchar(255) not null default ''        comment '文件名',
    original_name   varchar(255) not null default ''        comment '原名',
    file_suffix     varchar(10)  not null default ''        comment '文件后缀名',
    url             varchar(500) not null                   comment 'URL地址',
    ext1            text                  default null      comment '扩展字段',
    create_dept     bigint(20)            default null      comment '创建部门',
    create_time     datetime              default null      comment '创建时间',
    create_by       bigint(20)            default null      comment '上传人',
    update_time     datetime              default null      comment '更新时间',
    update_by       bigint(20)            default null      comment '更新人',
    service         varchar(20)  not null default 'minio'   comment '服务商',
    primary key (oss_id)
) engine=innodb comment ='OSS对象存储表';
```

对应 domain：`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysOss.java:17-53`（`@TableName("sys_oss")`，`@TableId(value = "oss_id")`，继承 `BaseEntity`）。

**关键语义**（由 `SysOssServiceImpl.buildResultEntity` 决定了 `SysOssServiceImpl.java:286-297`）：

```java
SysOss oss = new SysOss();
oss.setUrl(result.url());
oss.setFileSuffix(suffix);
oss.setFileName(result.key());     // file_name 实际存的是 S3 对象键(object key)!
oss.setOriginalName(originalfileName);
oss.setService(configKey);
oss.setExt1(JsonUtils.toJsonString(ext1));
ossMapper.insert(oss);
```

所以：`file_name` = object key（不是展示名），`original_name` = 用户原始文件名，`service` = `sys_oss_config.config_key`。

### 4.2 `sys_oss_config`（`script/sql/ry_vue.sql:815-836`）

```sql
create table sys_oss_config (
    oss_config_id   bigint(20)    not null                  comment '主键',
    config_key      varchar(20)   not null  default ''      comment '配置key',
    access_key      varchar(255)            default ''      comment 'accessKey',
    secret_key      varchar(255)            default ''      comment '秘钥',
    bucket_name     varchar(255)            default ''      comment '桶名称',
    prefix          varchar(255)            default ''      comment '前缀',
    endpoint        varchar(255)            default ''      comment '访问站点',
    domain_url      varchar(255)            default ''      comment '自定义域名',
    is_https        char(1)                 default 'N'     comment '是否https（Y=是,N=否）',
    region          varchar(255)            default ''      comment '域',
    access_policy   char(1)       not null  default '1'     comment '桶权限类型(0=private 1=public 2=custom)',
    status          char(1)                 default 'N'     comment '是否默认（Y=是,N=否）',
    ext1            varchar(255)            default ''      comment '扩展字段',
    ... remark varchar(500) ...
```

对应 domain `SysOssConfig.java:17-89`；BO 校验 `SysOssConfigBo.java:25-111`（`configKey` 2-100、`accessKey`/`secretKey`/`bucketName`/`endpoint` 2-100、`accessPolicy` 必填）。`ext1` 在本版本**未被任何代码读取**（grep `getExt1` 仅出现在 `updateByBo` 的置空逻辑中，`SysOssConfigServiceImpl.java:139`），`OssClientConfig.formProperties` 也没有解析它 → 想用它传 `asyncExecutorConfig` 需要自行改造。

### 4.3 `SysOssVo`（`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/vo/SysOssVo.java:20-76`）

字段：`ossId`、`fileName`、`originalName`、`fileSuffix`、`url`、`ext1`、`createTime`、`createBy`、`createByName`（`@Translation(USER_ID_TO_NAME)`）、`service`。
**注意 `SysOssVo` 没有 `contentType` / `fileSize` 的独立字段**——它们在 `ext1`（JSON 字符串）里。

`OssDTO`（跨模块只读契约对象，`ruoyi-api/src/main/java/org/dromara/system/api/domain/OssDTO.java:16-45`）更精简：只有 `ossId`、`fileName`、`originalName`、`fileSuffix`、`url`。

### 4.4 6.0.0 新增的「上传附件保存扩展信息」

新增类 `SysOssExt`，落在 `SysOss.ext1` 的 JSON 里（`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/SysOssExt.java:15-75`）：

```java
public class SysOssExt implements Serializable {
    private String bizType;      // 所属业务类型（如 avatar、report、contract）
    private Long fileSize;       // 文件大小（单位：字节）
    private String contentType;  // 文件类型（MIME类型，如 image/png）
    private String source;       // 来源标识（如 userUpload、systemImport）
    private String uploadIp;     // 上传 IP 地址，便于审计和追踪
    private String remark;       // 附件说明或备注
    private List<String> tags;   // 附件标签，如 ["图片", "证件"]
    private String refId;        // 业务绑定ID（如某业务记录ID）
    private String refType;      // 绑定业务类型
    private Boolean isTemp;      // 是否为临时文件，用于区分正式或待清理
    private String md5;          // 文件MD5值（可用于去重或校验）
}
```

上传时框架会自动回填 `fileSize` 与 `contentType`（`SysOssServiceImpl.java:243-245`）：

```java
ossExt = ossExt == null ? new SysOssExt() : ossExt;
ossExt.setFileSize(file.getSize());
ossExt.setContentType(file.getContentType());
```

`refId` / `refType` / `bizType` / `isTemp` / `tags` / `md5` 等**框架不会自动填**，必须由调用方传入（HTTP 上传时通过表单字段 `ossExt` 传 JSON，见 §6）。

### 4.5 业务表关联 OSS 的官方推荐字段

官方做法是**业务表存 `oss_id`（bigint），展示时用翻译注解还原 URL**。证据：`sys_user.avatar` 由 5.x 的 varchar URL 改为 bigint oss_id（`script/sql/ry_vue.sql:90` → `avatar bigint(20) comment '头像地址'`），配合 `SysUserVo.java:81`：

```java
@Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "avatar")
private String avatar;
```

`TransConstant.OSS_ID_TO_URL = "oss_id_to_url"`（`ruoyi-common/ruoyi-common-translation/src/main/java/org/dromara/common/translation/constant/TransConstant.java:33`），实现为 `OssUrlTranslationImpl`（同目录 `.../core/impl/OssUrlTranslationImpl.java:36-43`），内部调 `OssService.selectUrlByIds`。**结论：业务表存 `oss_id`，不要存 URL。**

---

## 5. 上传链路

### 5.1 `ISysOssService` 准确方法（全部 7 个）

`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/ISysOssService.java`

```java
PageResult<SysOssVo> queryPageList(SysOssBo sysOss, PageQuery pageQuery);   // :29
List<SysOssVo> listByIds(Collection<Long> ossIds);                          // :37
SysOssVo getById(Long ossId);                                               // :45
SysOssVo upload(MultipartFile file, SysOssExt ossExt);                      // :54
SysOssVo upload(File file, SysOssExt ossExt);                               // :63
ResponseEntity<byte[]> download(Long ossId);                                // :70
Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);        // :79
```

**不存在任何分片上传 / 断点续传方法。** 全仓库 grep `分片|断点续传|createMultipartUpload|UploadPart` 在 OSS 相关代码中零命中（命中的只有 SQL 日志分片、WebSocket 分片、SnailJob 任务分片等无关项）。底层用的是 `S3TransferManager`（`AbstractOssClientImpl.java:234`），它内部对大文件可自动分包，但**没有暴露 multipart 会话、part 列表或断点续传接口**。

### 5.2 上传时对象键（object key）的生成规则

`ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/client/AbstractOssClientImpl.java:210-219`

```java
public String buildPathKey(String businessPrefix, String fileName) {
    String defaultPrefix = config.prefix().orElse("");
    String mergedPrefix = mergePrefix(defaultPrefix, businessPrefix);
    String suffix = suffix(fileName);
    String datePath = DateUtils.format(new Date(), "yyyy/MM/dd");
    String uuid = IdUtil.fastSimpleUUID();
    String path = mergedPrefix.isEmpty() ? datePath + StringUtils.SLASH + uuid
        : mergedPrefix + StringUtils.SLASH + datePath + StringUtils.SLASH + uuid;
    return path + suffix;
}
```

规则：`[配置prefix]/[businessPrefix/]yyyy/MM/dd/<fastSimpleUUID><.原始扩展名>`。前缀为空时省略前导段；`mergePrefix` 会去掉两侧多余 `/`（`AbstractOssClientImpl.java:1153-1183`）；扩展名取自原始文件名最后一个 `.`（`:1191-1200`，可能带 `.`，见 §4.1 中 `StringUtils.substring(originalfileName, lastIndexOf("."), ...)`）。

### 5.3 是否支持自定义对象键 / 目录前缀 —— 分层结论

- **底层 `OssClient` 支持自定义 key**：`upload(String key, ...)` 系列直接接收调用方给的 key（`OssClient.java:401` 等），`bucketUpload` 同理。
- **`OssClient` 也支持业务前缀**：`buildPathKey(String businessPrefix, String fileName)` 是公开接口方法（`OssClient.java:597`）。
- **但 `ISysOssService` 完全不透出**：两个 `upload` 重载都写死调用一参版本（`SysOssServiceImpl.java:240` 与 `:267`）：

```java
OssClient instance = OssFactory.instance();
String pathKey = instance.buildPathKey(originalfileName);
```

全仓库对 `buildPathKey` 的引用只有：接口声明 2 处 + 抽象实现 3 处 + 上面这 2 处一参调用。**没有任何业务代码调用过二参（businessPrefix）版本。**

**结论（对本项目最关键的一条）**：通过框架上传入口 `ISysOssService.upload(...)` 或 HTTP `POST /resource/oss/upload` 上传时，**调用方无法指定对象键、无法指定目录前缀，key 100% 由框架生成**。因此 `talent-private/resumes/{talentId}/{attachmentId}/original` 这种按业务目录组织的设计**无法用现成接口实现**。

可用的扩展点（按侵入性从低到高）：
1. 把 `sys_oss_config.prefix` 设为 `talent-private`（每套 OSS 配置一个全局前缀，`AbstractOssClientImpl.java:211`）——只能得到 `talent-private/yyyy/MM/dd/<uuid>.pdf`，拿不到 `{talentId}/{attachmentId}`。
2. 自行构造 key 后直接用 `OssFactory.instance().upload(key, file, Options.builder()...)` 拿到 `PutObjectResult`，然后**自己写自己的业务表存 key**，不落 `sys_oss` 表（放弃 `sys_oss` 的管理能力与翻译功能）。
3. 自行构造 key 并想同时落 `sys_oss`：需要复用 `SysOssServiceImpl` 的逻辑，但 `SysOssMapper`、`ISysOssService` 都在 `ruoyi-modules/ruoyi-system` 内，只有 `ruoyi-admin` 依赖它（全仓库 grep `ruoyi-system` 仅命中根 `pom.xml:414`、`ruoyi-admin/pom.xml:80`），**其他业务模块默认拿不到 `ISysOssService`**。要么让 `talent-library` 显式依赖 `ruoyi-system`，要么在 `ruoyi-system` 内新增一个「按自定义 key 上传」的服务方法。
4. 新增自己的 `OssClient` 装饰/子类（`OssClient` 是接口，`DefaultOssClientImpl` 可继承）或包一层 Factory。

### 5.4 跨模块上传能力的现状

`ruoyi-api/src/main/java/org/dromara/system/api/OssService.java:12-28` 只暴露两个**只读**方法：

```java
public interface OssService {
    String selectUrlByIds(String ossIds);       // :20
    List<OssDTO> selectByIds(String ossIds);    // :28
}
```

grep `OssService|ISysOssService` 在 `ruoyi-modules/**` 下仅命中 `ruoyi-system` 自身。**`ruoyi-api` 没有对外暴露「上传」能力**；新模块若无额外依赖，只能走 HTTP 接口上传。

---

## 6. 下载链路

### 6.1 Controller 实现

`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysOssController.java`

```java
@SaCheckPermission("system:oss:download")                      // :90
@GetMapping("/download/{ossId}")                               // :91
public ResponseEntity<byte[]> download(@PathVariable Long ossId) throws IOException {
    return ossService.download(ossId);                         // :93
}
```

**会校验权限**（Sa-Token `system:oss:download`），且**返回的是二进制流（`ResponseEntity<byte[]>`），不是 URL**。基路径 `@RequestMapping("/resource/oss")`（`:37`）。

上传接口的入参/返回（`:75-82`、`:117`）：

```java
@SaCheckPermission("system:oss:upload")
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<SysOssUploadVo> upload(@RequestPart("file") MultipartFile file, @RequestParam(value = "ossExt", required = false) String ossExtJson) {
    SysOssVo oss = ossService.upload(file, JsonUtils.parseObject(ossExtJson, SysOssExt.class));
    SysOssUploadVo uploadVo = new SysOssUploadVo(oss.getUrl(), oss.getOriginalName(), oss.getOssId().toString());
    return R.ok(uploadVo);
}
...
public record SysOssUploadVo(String url, String fileName, String ossId) { }   // :117
```

注意 `SysOssUploadVo.fileName` 填的是 `originalName`（原始文件名），`ossId` 是 **String**（`oss.getOssId().toString()`），`url` 在私有桶配置下是 120s 预签名 URL。

### 6.2 下载实现细节与返回结构

`SysOssServiceImpl.java:198-223`：

```java
public ResponseEntity<byte[]> download(Long ossId) {
    SysOssVo sysOss = SpringUtils.getAopProxy(this).getById(ossId);
    if (ObjectUtil.isNull(sysOss)) {
        throw new ServiceException("文件数据不存在!");
    }
    String percentEncodedFileName = FileUtils.percentEncode(sysOss.getOriginalName());
    return OssFactory.instance(sysOss.getService())
        .download(sysOss.getFileName(), (result, inputStream) -> {
            MediaType mediaType;
            try { mediaType = MediaType.parseMediaType(result.contentType()); }
            catch (Exception e) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
            return ResponseEntity.ok()
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition,download-filename")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=%s;filename*=utf-8''%s".formatted(percentEncodedFileName, percentEncodedFileName))
                .header("download-filename", percentEncodedFileName)
                .contentType(mediaType).contentLength(result.size())
                .body(IoUtil.readBytes(inputStream));      // ← 全量载入内存
        });
}
```

- 下载走 `OssFactory.instance(sysOss.getService())`，用记录里的 `service` 定位配置 → 跨桶/多配置可用。
- `contentType` 取自 S3 响应头，缺失则 `application/octet-stream`。
- 文件名做了 `percentEncode`，同时通过 `download-filename` 头暴露给前端（`ACCESS_CONTROL_EXPOSE_HEADERS`）。
- **这是「服务端代理下载」的现成实现**，但**非流式**、无 Range 支持（未设置 `Accept-Ranges`/`Content-Range` 处理）。
- 查询 `list`（`:49-53`）与 `listByIds`（`:61-67`）走的是 JSON + `SysOssVo.url`，与二进制下载是两条不同链路。

### 6.3 前端返回结构

本检出中**不包含前端工程**（仓库根只有 `ruoyi-admin`、`ruoyi-api`、`ruoyi-common`、`ruoyi-extend`、`ruoyi-modules`、`script`；grep `resource/oss` 在 `*.ts/*.js/*.vue` 中零命中）。前端侧的具体封装/返回值解析：**未确认**。缺失证据：`plus-ui`/`ruoyi-ui` 前端源码不在本检出内。后端契约以上文 `SysOssUploadVo` 与 `ResponseEntity<byte[]>` 为准。

---

## 7. 删除与引用

### 7.1 删除实现

`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysOssServiceImpl.java:306-316`

```java
public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
    if (isValid) {
        // 做一些业务上的校验,判断是否需要校验
    }
    List<SysOss> list = ossMapper.selectByIds(ids);
    for (SysOss sysOss : list) {
        OssFactory.instance(sysOss.getService()).delete(sysOss.getFileName());
    }
    return ossMapper.deleteByIds(ids) > 0;
}
```

**没有任何引用校验**：`isValid` 分支是空实现（只有注释）。也就是说，删除接口不会检查该 `oss_id` 是否仍被业务表引用，会直接删掉物理对象 + 数据库记录。

Controller 侧（`SysOssController.java:102-108`）固定传 `true`，仅受 `system:oss:remove` 权限保护。

### 7.2 附带风险

- `getById(Long)` 带 `@Cacheable(cacheNames = CacheNames.SYS_OSS, key = "#ossId")`（`SysOssServiceImpl.java:185`），但**删除路径没有任何 `@CacheEvict`**（grep 该文件仅有 `Cacheable` 一处）。删掉的 `ossId` 若已被缓存，仍会被 `listByIds`/`selectUrlByIds` 读到，进而用已删除的 `file_name` 去生成 URL。
- 物理删除失败会抛 `S3StorageException`（`AbstractOssClientImpl.java:712-719`），数据库删除不会执行；但先删物理再删库、且无事务注解（`deleteWithValidByIds` 上没有 `@Transactional`），存在不一致窗口。

### 7.3 业务侧删除附件的正确调用方式

框架内没有「业务引用校验」的钩子。基于现有代码，业务模块应当：

1. **自己维护引用关系**（业务表存 `oss_id`），删除前先查自己的业务表确认无引用；
2. 只在确认无引用后，才调用 `ISysOssService.deleteWithValidByIds(ids, true)`（或 HTTP `DELETE /resource/oss/{ossIds}`，需 `system:oss:remove` 权限）；
3. 若做的是「软删除 / 版本化附件」，**不要调用框架的删除接口**——框架没有软删字段（`sys_oss` 没有 `del_flag`/`status`），删除即物理删除且不可恢复；
4. 注意跨模块调用限制（见 §5.4）：业务模块默认只能通过 HTTP 删除，或者需要显式依赖 `ruoyi-system`。

---

## 对人才库附件设计的约束与建议

1. **目录设计无法用现成上传接口实现**：`ISysOssService` 的两个 `upload` 固定调用 `buildPathKey(originalFileName)`，key 恒为 `[prefix/]yyyy/MM/dd/<uuid><ext>`；`talent-private/resumes/{talentId}/{attachmentId}/original` 需要自建上传通道（直接用 `OssFactory.instance().upload(customKey, ...)`，或在 `ruoyi-system` 内新增按自定义 key 上传的方法），并把该 key 存进自建附件表。
2. **业务表关联必须存 `oss_id`（bigint），不要存 URL**：官方范式见 `sys_user.avatar bigint(20)` + `@Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "avatar")`；私桶下 URL 是 120s 过期的预签名串，落库必然失效。
3. **私有桶靠 `sys_oss_config.access_policy='0'` 驱动**：设为 `0` 后框架自动把 `SysOssVo.url` 换成 120s 预签名 URL；默认种子值是 `'1'`（公读），人才库简历等敏感附件必须新增/改用 `access_policy='0'` 的配置，并注意 `is_https` 要用 `Y/N`（不是 `0/1`）。
4. **自定义域名签名只对「默认桶」生效**：`useBucketBoundDomain` 要求 `domain_url` 非空 **且** bucket == 配置里的默认桶；若人才库用独立桶名，自定义域名预签名路径不会生效，会回退到 SDK 的 `S3Presigner`（路径含桶名）。
5. **短时 URL 的过期时间需自取**：`presignGetUrl(key, Duration)` 无默认值；框架内部固定 120s。人才库若有更长时间窗口需求（如邮件里的简历链接），必须自己调 `OssFactory.instance(service).presignGetUrl(key, Duration.ofMinutes(n))`。
6. **受控下载不要复用 `SysOssController#download`**：它只校验 `system:oss:download` 这一个粗粒度权限、且 `IoUtil.readBytes` 全量进内存，无 Range/流式。人才库需自建 controller，在业务层校验「当前用户是否有权看这份简历」后再调用 `OssClient#download(key, BiFunction)`（或 `OutputStream` 重载）做流式代理。
7. **附件版本信息不要塞 `sys_oss.ext1`**：`SysOssExt` 虽有 `refId`/`refType`/`bizType`/`tags`/`md5`/`isTemp` 等字段，但 `ext1` 在本版本不被任何代码读取或索引，也无法做外键/唯一约束；版本号、上传人、状态等应放在 talent-library 自己的附件表里，`ext1` 只用于冗余展示。
8. **删除是物理删除且无引用校验**：`deleteWithValidByIds` 的 `isValid` 分支为空、无 `@CacheEvict`、无 `@Transactional`、`sys_oss` 无软删字段。人才库必须自建引用校验与软删除（只删自己的附件记录，物理对象交给异步清理任务），并且业务模块默认拿不到 `ISysOssService`（`ruoyi-api` 只暴露只读的 `OssService`），需先决定是「新增 ruoyi-system 依赖」还是「走 HTTP 接口」。
