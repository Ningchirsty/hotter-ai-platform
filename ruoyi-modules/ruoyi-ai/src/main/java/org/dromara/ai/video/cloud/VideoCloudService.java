package org.dromara.ai.video.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.AssetStorage;
import org.dromara.ai.video.service.MediaProbe;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;

/** 云端视频独立队列，复用视频任务、素材、事件和租户隔离。 */
public class VideoCloudService {
    public static final String WORKFLOW="cloud-bluocto-video";
    public record Ticket(String key,String mime,long expires) { }
    private final VideoCloudProperties properties;
    private final VideoTaskRepository repository;
    private final AssetStorage storage;
    private final MediaProbe probe;
    private final BluOctoVideoClient client;
    private final VideoCloudVerification verification;
    private final java.util.function.LongSupplier ids;
    private final ObjectMapper mapper=new ObjectMapper();
    private final JsonNode catalog;
    private final ExecutorService pool;
    private final Map<String,Ticket> tickets=new ConcurrentHashMap<>();

    public VideoCloudService(VideoCloudProperties properties,VideoTaskRepository repository,AssetStorage storage,MediaProbe probe) {
        this(properties,repository,storage,probe,new BluOctoVideoClient(properties));
    }
    VideoCloudService(VideoCloudProperties properties,VideoTaskRepository repository,AssetStorage storage,MediaProbe probe,BluOctoVideoClient client) {
        this(properties,repository,storage,probe,client,IdGeneratorUtil::nextLongId);
    }
    VideoCloudService(VideoCloudProperties properties,VideoTaskRepository repository,AssetStorage storage,MediaProbe probe,BluOctoVideoClient client,java.util.function.LongSupplier ids) {
        this.ids=ids;
        this.properties=properties;this.repository=repository;this.storage=storage;this.probe=probe;this.client=client;this.verification=new VideoCloudVerification(properties);
        try(var in=getClass().getResourceAsStream("/video/bluocto-catalog.json")){catalog=mapper.readTree(in);}catch(Exception e){throw new IllegalStateException("视频型号目录无法读取",e);}
        pool=new ThreadPoolExecutor(Math.max(1,properties.getConcurrency()),Math.max(1,properties.getConcurrency()),0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(Math.max(1,properties.getQueueCapacity())),r->{Thread t=new Thread(r,"video-cloud");t.setDaemon(true);return t;});
    }
    public Map<String,Object> models(){return models(-1);}
    public Map<String,Object> models(long user){return Map.of("configured",properties.configured(),"referenceDeliveryConfigured",properties.configured()&&deliveryConfigured(),"profiles",catalog.path("profiles"),"verifiedVariants",verification.verifiedVariants(),"validationVariants",verification.candidates(user),"validationRemaining",verification.candidates(user).isEmpty()?0:verification.remaining());}
    public JsonNode profile(String model){for(JsonNode p:catalog.path("profiles"))if(p.path("id").asText().equals(model))return p;return null;}
    public static boolean isCloud(Map<String,Object> task){return WORKFLOW.equals(task.get("workflow_code"));}
    private boolean deliveryConfigured(){try{var u=java.net.URI.create(properties.getPublicAssetBaseUrl());return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getRawUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null;}catch(Exception e){return false;}}
    private void verify(CloudVideoRequest request,long user) {
        JsonNode p=profile(request.model());request.validate(p);
        if(!properties.configured())throw new VideoTaskException("CLOUD_NOT_CONFIGURED","服务端尚未启用蓝章鱼视频");
        if(p.path("protocol").asText().equals("unconfirmed"))throw new VideoTaskException("CLOUD_PROTOCOL_UNCONFIRMED","该型号的供应商视频协议待确认");
        verification.requireAllowed(request,user);
        if(!request.references().isEmpty()&&!deliveryConfigured())throw new VideoTaskException("CLOUD_REFERENCE_NOT_CONFIGURED","参考素材外部读取地址尚未配置");
    }
    public Map<String,Object> create(CloudVideoRequest request,String tenant,long user,Long dept) {
        request.validate(profile(request.model()));
        Long existingId=repository.findByIdempotencyKey(tenant,user,request.idempotencyKey());
        if(existingId!=null)return existing(existingId,request,tenant,user);
        verify(request,user);
        long videoMillis=0,audioMillis=0;
        for(var ref:request.references()) {
            var asset=repository.requireOwnedAsset(ref.assetId(),tenant,user);
            String expected=ref.role().equals("reference_video")?"VIDEO":ref.role().equals("reference_audio")?"AUDIO":"IMAGE";
            if(!expected.equals(asset.assetType())||asset.sizeBytes()==null||asset.sizeBytes()>64L*1024*1024)throw VideoTaskException.invalidContract("素材类型或大小与创作能力不匹配");
            if(expected.equals("VIDEO")&&(asset.durationMillis()==null||asset.durationMillis()>profile(request.model()).path("maxVideoSeconds").asLong(15)*1000))throw VideoTaskException.invalidContract("参考视频需完成时长探测且不超过该型号时长上限");
            if(expected.equals("VIDEO"))videoMillis+=asset.durationMillis();
            if(expected.equals("AUDIO")){if(asset.durationMillis()==null||asset.durationMillis()<=0)throw VideoTaskException.invalidContract("参考音频需完成时长探测");audioMillis+=asset.durationMillis();}
            if(asset.durationMillis()!=null&&asset.durationMillis()<=0)throw VideoTaskException.invalidContract("参考素材时长不合法");
        }
        if(videoMillis>profile(request.model()).path("maxVideoSeconds").asLong(15)*1000||audioMillis>15000)throw VideoTaskException.invalidContract("参考视频或音频总时长超过型号限制");
        if(request.references().stream().allMatch(r->r.role().equals("reference_audio"))&&!request.references().isEmpty())throw VideoTaskException.invalidContract("参考音频需要同时提供参考图片或视频");
        Long old=repository.findByIdempotencyKey(tenant,user,request.idempotencyKey());
        if(old!=null)return existing(old,request,tenant,user);
        long id=ids.getAsLong();String no="VIDEO-CLOUD-"+id;
        try {
            repository.insertTask(new VideoTaskRepository.TaskRow(id,tenant,user,no,request.model()+" · "+request.capability(),request.capability(),WORKFLOW,"1",request.model(),"QUEUED",request.resolution(),request.seconds(),request.prompt(),mapper.writeValueAsString(Map.of("request",request)),request.idempotencyKey(),dept));
        } catch(DuplicateKeyException e){Long raced=repository.findByIdempotencyKey(tenant,user,request.idempotencyKey());if(raced==null)throw e;return existing(raced,request,tenant,user);}
        catch(Exception e){if(e instanceof RuntimeException r)throw r;throw VideoTaskException.invalidContract("视频任务参数无法保存");}
        event(id,tenant,"CREATED","已创建云端视频任务");return Map.of("taskId",id,"taskNo",no,"status","QUEUED");
    }
    private Map<String,Object> existing(long id,CloudVideoRequest request,String tenant,long user){var task=repository.requireOwnedTask(id,tenant,user);if(!isCloud(task)||!Objects.equals(request,request(task)))throw VideoTaskException.invalidContract("同一幂等键不能用于不同视频参数");return Map.of("taskId",id,"status",task.get("status"),"idempotent",true);}
    public Map<String,Object> execute(long id,String tenant,long user,boolean retry) {
        var task=repository.requireOwnedTask(id,tenant,user);if(!isCloud(task))throw VideoTaskException.invalidContract("任务来源不匹配");
        var request=request(task);
        var status=VideoTaskStatus.valueOf(String.valueOf(task.get("status")));
        if(status==VideoTaskStatus.RUNNING)return Map.of("taskId",id,"status","RUNNING","accepted",false,"outcome","ALREADY_CLAIMED");
        verify(request,user);
        if(retry&&status.isTerminal()&&status!=VideoTaskStatus.SUCCEEDED){
            if(task.get("comfy_prompt_id")!=null||List.of("CLOUD_RESULT_UNKNOWN","ORPHANED_BY_RESTART").contains(String.valueOf(task.get("error_code"))))throw VideoTaskException.invalidContract("供应商可能已接受请求，请核对记录后新建任务，避免重复计费");
            repository.reopen(id,status);status=VideoTaskStatus.QUEUED;
        }
        if(status!=VideoTaskStatus.QUEUED&&status!=VideoTaskStatus.RUNNING)throw VideoTaskException.invalidContract("任务当前不可执行");
        if(repository.transition(id,VideoTaskStatus.QUEUED,VideoTaskStatus.RUNNING,null,null)==0)return Map.of("taskId",id,"status",repository.requireOwnedTask(id,tenant,user).get("status"),"accepted",false,"outcome","ALREADY_CLAIMED");
        try{pool.execute(()->run(id,tenant,user,request));}
        catch(RejectedExecutionException e){repository.transition(id,VideoTaskStatus.RUNNING,VideoTaskStatus.QUEUED,null,null);return Map.of("taskId",id,"status","QUEUED","accepted",false,"outcome","QUEUE_FULL");}
        return Map.of("taskId",id,"status","RUNNING","accepted",true,"outcome","ACCEPTED");
    }
    private void run(long id,String tenant,long user,CloudVideoRequest request) {
        try {
            verification.reserve(request,user,id);
            var remote=client.submit(request,profile(request.model()),ref->referenceUrl(ref,tenant,user));
            repository.markSubmitted(id,remote.id(),1,"BluOcto");event(id,tenant,"SUBMITTED","供应商已接受视频任务，不自动重试付费提交");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.max(60,properties.getTimeoutSeconds()));
            boolean completed=false;
            while(System.nanoTime()<deadline){
                JsonNode state;
                try {state=client.query(remote);} catch(VideoTaskException e) {
                    if(!"CLOUD_QUERY_FAILED".equals(e.getErrorCode()))throw e;
                    Thread.sleep(5000);continue;
                }
                String s=BluOctoVideoClient.state(state);
                if(s.equals("SUCCEEDED")){completed=true;break;}
                if(s.equals("FAILED"))throw new VideoTaskException("CLOUD_RESULT_UNKNOWN","供应商任务未完成，请核对供应商任务记录");
                Thread.sleep(5000);
            }
            if(!completed)throw new VideoTaskException("CLOUD_RESULT_UNKNOWN","供应商任务等待超时，结果未知，请核对记录");
            byte[] bytes=client.download(remote);String key=storage.storeOutput(tenant,user,id,"cloud-"+id+".mp4",bytes,"video/mp4");
            var measured=probe.probe(storage.localPath(key));
            if(!measured.measured()||measured.width()==null||measured.height()==null||measured.durationMillis()==null||measured.fps()==null||measured.fps()<=0||Math.abs(measured.durationMillis()-request.seconds()*1000L)>1000)throw VideoTaskException.outputInvalid("云端视频无法实测或时长与请求不符");
            validateOutput(request,measured.width(),measured.height());
            long asset=ids.getAsLong();repository.insertAsset(new VideoTaskRepository.AssetRow(asset,tenant,user,id,"VIDEO","OUTPUT","cloud-"+id+".mp4",key,"video/mp4",(long)bytes.length,null,measured.width(),measured.height(),measured.durationMillis(),null));
            if(repository.markSucceeded(id,asset,asset,measured.width(),measured.height(),measured.fps(),measured.durationMillis(),false)==0)throw new VideoTaskException("CLOUD_RESULT_UNKNOWN","视频归档状态写入未完成，请核对任务记录");
            verification.passed(request,id);
            event(id,tenant,"SUCCEEDED","云端视频已归档，可在我的任务播放");
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();repository.markFailedIfActive(id,e instanceof VideoTaskException ve?ve.getErrorCode():"CLOUD_RESULT_UNKNOWN",e instanceof VideoTaskException?e.getMessage():"云端视频执行中断，请核对供应商记录");event(id,tenant,"FAILED","云端任务未通过验收，不自动重新生成；请核对任务详情");}
    }
    static void validateOutput(CloudVideoRequest request,int width,int height) {
        int expected=switch(request.resolution()){case "480p"->480;case "720p"->720;case "768p"->768;case "1080p"->1080;case "4k"->2160;default->0;};
        if(expected>0&&Math.min(width,height)!=expected)throw VideoTaskException.outputInvalid("视频分辨率与请求不符");
        if(!request.ratio().equals("adaptive")){String[] r=request.ratio().split(":");double ratio=Double.parseDouble(r[0])/Double.parseDouble(r[1]);if(Math.abs((double)width/height-ratio)>ratio*.02)throw VideoTaskException.outputInvalid("视频比例与请求不符");}
    }
    private CloudVideoRequest request(Map<String,Object> task){try{Object raw=task.get("input_json");JsonNode n=raw instanceof byte[] bytes?mapper.readTree(bytes):mapper.readTree(String.valueOf(raw));if(n.isTextual())n=mapper.readTree(n.asText());return mapper.treeToValue(n.path("request"),CloudVideoRequest.class);}catch(Exception e){throw VideoTaskException.invalidContract("视频云端任务快照无法读取");}}
    private void event(long id,String tenant,String type,String detail){synchronized(this){int seq=repository.listEvents(id,tenant).stream().mapToInt(e->((Number)e.get("sequence")).intValue()).max().orElse(0)+1;repository.appendEvent(ids.getAsLong(),id,tenant,seq,type,detail);}}
    private String referenceUrl(CloudVideoRequest.Reference ref,String tenant,long user) {
        if(!deliveryConfigured())throw VideoTaskException.invalidContract("未配置参考素材读取地址");
        var asset=repository.requireOwnedAsset(ref.assetId(),tenant,user);
        tickets.entrySet().removeIf(e->e.getValue().expires()<System.currentTimeMillis());
        if(tickets.size()>1000)throw VideoTaskException.invalidContract("参考素材链接数量超过限制");
        String ticket=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        tickets.put(ticket,new Ticket(asset.storageKey(),asset.contentType(),System.currentTimeMillis()+7200000));
        return properties.getPublicAssetBaseUrl().replaceAll("/$","")+"/video/cloud/reference/"+ticket;
    }
    public Ticket ticket(String token){var ticket=tickets.get(token);if(ticket==null||ticket.expires()<System.currentTimeMillis())throw VideoTaskException.assetNotFound("参考链接已失效");return ticket;}
    public byte[] referenceContent(Ticket ticket){return storage.read(ticket.key());}
    public void shutdown(){pool.shutdownNow();tickets.clear();}
}
