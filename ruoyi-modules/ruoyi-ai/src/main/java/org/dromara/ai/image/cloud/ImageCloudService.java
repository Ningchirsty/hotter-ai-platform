package org.dromara.ai.image.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetProbe;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageTaskDispatchService;
import org.dromara.ai.image.service.ImageTaskExecutionService;
import org.dromara.ai.image.service.ImageTaskOrchestrator;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.springframework.dao.DuplicateKeyException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** 云端任务使用独立执行队列，复用现有图像任务、素材与归属校验。 */
public class ImageCloudService {
    public static final String WORKFLOW = "cloud-bluocto-t2i";
    private final ImageCloudProperties properties;
    private final BluOctoImageClient client;
    private final ImageTaskRepository repository;
    private final ImageAssetStore assets;
    private final ImageTaskExecutionService executor;
    private final ImageTaskDispatchService dispatch;
    private final java.util.function.LongSupplier ids;
    private final java.util.function.BiPredicate<String, String> verification;
    private final java.util.function.Consumer<CloudImageRequest> outputVerification;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImageCloudService(ImageCloudProperties properties, ImageTaskRepository repository, ImageAssetStore assets) {
        this(properties, new BluOctoImageClient(properties), repository, assets, IdGeneratorUtil::nextLongId);
    }

    ImageCloudService(ImageCloudProperties properties, BluOctoImageClient client,
                      ImageTaskRepository repository, ImageAssetStore assets, java.util.function.LongSupplier ids) {
        this(properties, client, repository, assets, ids, CloudImageRequest::verified);
    }

    // 离线回放只可在包内显式注入历史验收结果；生产控制器不接受此开关。
    ImageCloudService(ImageCloudProperties properties, BluOctoImageClient client,
                      ImageTaskRepository repository, ImageAssetStore assets, java.util.function.LongSupplier ids,
                      java.util.function.BiPredicate<String, String> verification) {
        this(properties,client,repository,assets,ids,verification,CloudImageOutputValidation::requireVerified);
    }

    // 包内离线测试的参数验收替身，不接受配置或浏览器开关。
    ImageCloudService(ImageCloudProperties properties, BluOctoImageClient client,
                      ImageTaskRepository repository, ImageAssetStore assets, java.util.function.LongSupplier ids,
                      java.util.function.BiPredicate<String,String> verification, java.util.function.Consumer<CloudImageRequest> outputVerification) {
        this.outputVerification=outputVerification;
        this.verification = verification;
        this.ids = ids;
        this.properties = properties;
        this.client = client;
        this.repository = repository;
        this.assets = assets;
        executor = new ImageTaskExecutionService(this::run, properties.getQueueCapacity(), properties.getConcurrency());
        dispatch = new ImageTaskDispatchService(repository, executor);
    }

    public boolean configured() { return properties.configured(); }
    public List<String> authorizedModels() { return client.authorizedModels(); }
    public static boolean isCloud(Map<String, Object> task) { return WORKFLOW.equals(task.get("workflow_code")); }

    /** 保留最小文生图入口，高级能力使用可审计的请求快照。 */
    public Map<String, Object> create(String tenant, long user, Long dept, String model, String prompt,
                                      String taskName, String idempotencyKey) {
        return create(tenant, user, dept, new CloudImageRequest(model, prompt, "T2I", List.of(), null), taskName, idempotencyKey);
    }

    public Map<String, Object> create(String tenant, long user, Long dept, CloudImageRequest input, String taskName, String idempotencyKey) {
        if (idempotencyKey != null && idempotencyKey.startsWith("tpl-")) throw invalid("模板请求须使用模板专用入口");
        return createBound(tenant,user,dept,input,taskName,idempotencyKey,false);
    }

    /** 服务端模板适配器调用，浏览器无法进入此内部入口。 */
    public Map<String,Object> createTemplate(String tenant,long user,Long dept,CloudImageRequest input,String taskName,String idempotencyKey) {
        if(idempotencyKey==null || !idempotencyKey.matches("tpl-[a-f0-9-]{36}")) throw invalid("模板幂等键无效");
        org.dromara.ai.image.template.TemplateRequestBuilder.requireTemplateInput(input);
        return createBound(tenant,user,dept,input,taskName,idempotencyKey,true);
    }

    private Map<String,Object> createBound(String tenant,long user,Long dept,CloudImageRequest input,String taskName,String idempotencyKey,boolean template) {
        client.requireConfigured();
        if(template) org.dromara.ai.image.template.TemplateRequestBuilder.requireTemplateInput(input); else verify(input);
        input.inputs(repository, assets, tenant, user);
        String model = input.model(), prompt = input.prompt();
        if (!BluOctoImageClient.MODELS.contains(model)) throw invalid("请选择已接入的云端图像模型");
        if (prompt == null || prompt.isBlank() || prompt.length() > (template ? 8000 : 1000)) throw invalid(template ? "模板提示词须为 1–8000 个字符" : "创作描述须为 1–1000 个字符");
        if (taskName != null && taskName.length() > 255) throw invalid("任务名称过长");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{8,128}")) throw invalid("缺少有效的提交幂等键");
        Long existing = repository.findByIdempotencyKey(tenant, user, idempotencyKey);
        if (existing != null) return existing(tenant, user, existing, input);
        long id = ids.getAsLong();
        String taskNo = "IMAGE-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + id;
        String snapshot;
        try {
            snapshot = mapper.writeValueAsString(Map.of("source", "cloud", "provider", "bluocto",
                "model", model, "capability", input.capability(), "prompt", prompt, "request", input));
        } catch (Exception e) { throw invalid("无法保存云端任务参数"); }
        try {
            repository.insertTask(new ImageTaskRepository.TaskRow(id, tenant, user, taskNo,
                taskName == null || taskName.isBlank() ? "文生图 · " + model : taskName,
                input.capability(), WORKFLOW, "v1", model, "QUEUED", input.output().size(), null, prompt, null, snapshot, idempotencyKey, dept));
        } catch (DuplicateKeyException e) {
            Long raced = repository.findByIdempotencyKey(tenant, user, idempotencyKey);
            if (raced == null) throw e;
            return existing(tenant, user, raced, input);
        }
        repository.appendEvent(ids.getAsLong(), id, tenant, 1, "CREATED", "已创建云端图像任务");
        return Map.of("taskId", id, "taskNo", taskNo, "status", "QUEUED", "idempotent", false);
    }

    private Map<String, Object> existing(String tenant, long user, long id, CloudImageRequest input) {
        Map<String, Object> task = repository.requireOwnedTask(id, tenant, user);
        if (!isCloud(task) || !Objects.equals(input, request(task))) {
            throw invalid("同一个提交幂等键不能用于不同的任务参数");
        }
        return Map.of("taskId", id, "taskNo", task.get("task_no"), "status", task.get("status"), "idempotent", true);
    }

    public String execute(long id, String tenant, long user) { return executeBound(id,tenant,user,false); }
    public String executeTemplate(long id,String tenant,long user) { return executeBound(id,tenant,user,true); }
    private String executeBound(long id,String tenant,long user,boolean template) {
        Map<String, Object> task = repository.requireOwnedTask(id, tenant, user);
        if (!isCloud(task)) throw invalid("任务不是云端任务");
        boolean templateTask=String.valueOf(task.get("idempotency_key")).startsWith("tpl-");
        if(templateTask != template) throw invalid("模板任务须通过模板请求账本执行");
        String status = String.valueOf(task.get("status"));
        if ("RUNNING".equals(status)) return "ALREADY_CLAIMED";
        if (!"QUEUED".equals(status)) throw invalid("任务当前状态不可执行");
        client.requireConfigured();
        if(template) org.dromara.ai.image.template.TemplateRequestBuilder.requireTemplateInput(request(task)); else verify(request(task));
        return dispatch.dispatch(id, () -> {
            int seq = repository.listEvents(id, tenant).stream()
                .mapToInt(row -> ((Number) row.get("sequence")).intValue()).max().orElse(0);
            return new ImageTaskOrchestrator.TaskContext(id, tenant, user, null, "T2I", WORKFLOW,
                String.valueOf(task.get("prompt")), null, null, null, List.of(), false,
                ids, new AtomicInteger(seq));
        }).name();
    }

    public String retry(long id, String tenant, long user) {
        Map<String, Object> task = repository.requireOwnedTask(id, tenant, user);
        if(String.valueOf(task.get("idempotency_key")).startsWith("tpl-")) throw invalid("模板请求须先对账，请使用模板恢复入口");
        if (!isCloud(task)) throw invalid("任务不是云端任务");
        ImageTaskStatus status = ImageTaskStatus.valueOf(String.valueOf(task.get("status")));
        if (status == ImageTaskStatus.RUNNING || status == ImageTaskStatus.QUEUED) return execute(id, tenant, user);
        if ("CLOUD_RESULT_UNKNOWN".equals(task.get("error_code")) || "ORPHANED_BY_RESTART".equals(task.get("error_code"))) {
            throw invalid("供应商结果未知，请先核对供应商记录；确认后用新的任务重新提交，避免重复计费");
        }
        if (status == ImageTaskStatus.SUCCEEDED) throw invalid("已完成任务不能重试");
        client.requireConfigured();
        if (repository.reopen(id, tenant, user, status) == 0) return "ALREADY_CLAIMED";
        return execute(id, tenant, user);
    }

    private ImageTaskOrchestrator.ExecutionResult run(ImageTaskOrchestrator.TaskContext context) {
        try {
            Map<String, Object> task = repository.requireOwnedTask(context.taskId(), context.tenantId(), context.userId());
            if (!isCloud(task)) throw invalid("任务来源不匹配");
            event(context, "SUBMITTED", "请求云端生成，不自动重试付费请求");
            CloudImageRequest request = request(task);
            if(String.valueOf(task.get("idempotency_key")).startsWith("tpl-")) org.dromara.ai.image.template.TemplateRequestBuilder.requireTemplateInput(request); else verify(request);
            List<byte[]> outputs = request.output().custom() ? client.generateBatch(request, request.inputs(repository, assets, context.tenantId(), context.userId()))
                : List.of("T2I".equals(request.capability()) ? client.generate(request.model(),request.prompt())
                    : client.generate(request,request.inputs(repository,assets,context.tenantId(),context.userId())));
            if(outputs.size() != request.output().n()) throw invalid("输出数量与任务快照不一致");
            // 先核对全部输出，不能把供应商忽略参数的结果当作验收成功。
            for(byte[] output : outputs) {
                var probe=ImageAssetProbe.probeBytes(output);
                if(!probe.measured() || probe.exceedsPixels(16*1024*1024)) throw invalid("输出图像无效或超过像素上限");
                if(request.output().size() != null && !(probe.width()+"x"+probe.height()).equals(request.output().size())) throw invalid("供应商返回尺寸与请求尺寸不一致");
                if(request.output().outputFormat() != null && !probe.format().equalsIgnoreCase(request.output().outputFormat())) throw invalid("供应商返回格式与请求格式不一致");
            }
            ImageTaskOrchestrator.ExecutionResult primary=null;
            int index=0;long primarySize=0;
            for(byte[] content : outputs) {
                ImageAssetProbe.Probe head = ImageAssetProbe.probeBytes(content);
                if (!head.measured() || head.width() <= 0 || head.height() <= 0 || head.exceedsPixels(16 * 1024 * 1024)) {
                    throw invalid("云端返回的图片无法识别或超过 16MP 像素上限");
                }
                String format = head.format().toLowerCase(java.util.Locale.ROOT);
                if (!List.of("png", "jpeg", "jpg", "webp").contains(format)) throw invalid("云端输出不是支持的图片格式");
                var image = ImageIO.read(new ByteArrayInputStream(content));
                if (image == null) throw invalid("云端图片无法解码");
                boolean alpha = image.getColorModel().hasAlpha();
                if ("TRANSPARENT".equals(request.capability())) {
                    boolean transparent = false;
                    if ("png".equals(format) && alpha) {
                        scan: for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++) {
                            if ((image.getRGB(x,y) >>> 24) == 0) { transparent = true; break scan; }
                        }
                    }
                    if (!transparent) throw new ImageTaskException("CLOUD_OUTPUT_INVALID", "透明背景输出未包含有效透明区域");
                }
                String mime = "jpg".equals(format) ? "image/jpeg" : "image/" + format;
                String file = "cloud-" + context.taskId() + "-" + (++index) + "." + format;
                String storage = assets.storeOutput(context.tenantId(), context.userId(), context.taskId(), file, content, mime);
                if (storage == null) throw invalid("云端图片归档失败");
                long assetId = ids.getAsLong();
                repository.insertAsset(new ImageTaskRepository.AssetRow(assetId, context.tenantId(), context.userId(),
                    context.taskId(), "IMAGE", "OUTPUT", file, storage, mime, content.length, null,
                    head.width(), head.height(), alpha, context.deptId()));
                if(primary == null) primary = new ImageTaskOrchestrator.ExecutionResult(context.taskId(), assetId, null, head.width(), head.height(), alpha);
                if(index == 1) primarySize=content.length;
            }
            repository.markSucceeded(context.taskId(),primary.outputAssetId(),primary.width(),primary.height(),primary.hasAlpha(),primarySize);
            event(context,"SUCCEEDED","云端图像已生成并归档，共 "+outputs.size()+" 张");
            return primary;
        } catch (Exception e) {
            String code = e instanceof ImageTaskException error ? error.getErrorCode() : "CLOUD_ARCHIVE_FAILED";
            String message = e instanceof ImageTaskException ? e.getMessage() : "云端结果归档失败，请先核对供应商记录";
            // 已发出请求后的归档失败不能自动重新生成，会产生重复费用。
            boolean templateTask=String.valueOf(repository.requireOwnedTask(context.taskId(),context.tenantId(),context.userId()).get("idempotency_key")).startsWith("tpl-");
            if (templateTask ? !List.of("CLOUD_AUTH_FAILED","CLOUD_HTTP_REJECTED","CLOUD_NOT_CONFIGURED").contains(code)
                : !List.of("CLOUD_AUTH_FAILED", "CLOUD_RATE_LIMITED", "CLOUD_NOT_CONFIGURED").contains(code)) {
                code = "CLOUD_RESULT_UNKNOWN";
            }
            repository.markFailedIfActive(context.taskId(), code, message);
            event(context, "FAILED", message);
            return null;
        }
    }

    private void verify(CloudImageRequest request) {
        request.validateShape();
        outputVerification.accept(request);
        if (!verification.test(request.model(), request.capability())) throw new ImageTaskException(
            "CLOUD_CAPABILITY_UNVERIFIED", "供应商最新接口验证未通过，该能力暂不可提交");
    }

    private CloudImageRequest request(Map<String, Object> task) {
        Object raw = task.get("input_json");
        if (raw != null) {
            try {
                var node = raw instanceof byte[] bytes ? mapper.readTree(bytes) : mapper.readTree(String.valueOf(raw));
                if (node.isTextual()) node = mapper.readTree(node.asText());
                if (node.has("request")) return mapper.treeToValue(node.get("request"), CloudImageRequest.class);
            } catch (Exception e) { throw invalid("云端任务快照无法读取"); }
        }
        return new CloudImageRequest(String.valueOf(task.get("model_code")), String.valueOf(task.get("prompt")), "T2I", List.of(), null);
    }

    private void event(ImageTaskOrchestrator.TaskContext context, String type, String message) {
        repository.appendEvent(context.nextEventId(), context.taskId(), context.tenantId(), context.nextSequence(), type, message);
    }

    public void shutdown() { executor.shutdown(); }
    private static ImageTaskException invalid(String message) { return new ImageTaskException("INVALID_CONTRACT", message); }
}
