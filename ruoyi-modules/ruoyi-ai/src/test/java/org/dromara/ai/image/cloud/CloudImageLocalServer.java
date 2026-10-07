package org.dromara.ai.image.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.*;
import org.dromara.ai.video.service.LocalFileAssetStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** 仅本机联调入口：复用正式服务与 JDBC/素材存储，不装配生产登录、数据库或 ComfyUI。 */
public final class CloudImageLocalServer implements AutoCloseable {
    private static final String TENANT="local-cloud";
    private static final long USER=1;
    private static final String ORIGIN="http://127.0.0.1:5179";
    private final ObjectMapper mapper=new ObjectMapper();
    private final JdbcImageTaskRepository repository;
    private final JdbcTemplate jdbc;
    private final ImageAssetStore assets;
    private final ImageCloudService cloud;
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService httpWorkers=Executors.newFixedThreadPool(4);
    private final AtomicLong ids;
    private final String secret;
    private final List<String> allowed;

    CloudImageLocalServer(Path data, Path schema, String secret, ImageCloudProperties props, List<String> allowed, int port) throws Exception {
        if (secret.length()<32) throw new IllegalArgumentException("Local session secret is required");
        this.secret=secret; this.allowed=List.copyOf(allowed);
        Files.createDirectories(data);
        var source=new DriverManagerDataSource("jdbc:h2:file:"+data.resolve("tasks").toAbsolutePath().toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        try(var connection=source.getConnection()) { ScriptUtils.executeSqlScript(connection,new FileSystemResource(schema)); }
        jdbc=new JdbcTemplate(source);
        long existing=Math.max(jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM image_task",Long.class),
            Math.max(jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM image_asset",Long.class),jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM image_task_event",Long.class)));
        ids=new AtomicLong(Math.max(existing,System.currentTimeMillis()*1000));
        repository=new JdbcImageTaskRepository(jdbc);
        repository.failAllRunning("ORPHANED_BY_RESTART","本机服务重启，供应商结果未知；请核对供应商记录，不能自动重发");
        assets=new ImageAssetStore(new LocalFileAssetStorage(data.resolve("assets")));
        cloud=new ImageCloudService(props,new BluOctoImageClient(props),repository,assets,ids::incrementAndGet);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/image",this::handle);server.setExecutor(httpWorkers);server.start();
    }
    public static void main(String[] args) throws Exception {
        Path data=Path.of(System.getenv("HOTTER_LOCAL_CLOUD_DATA"));
        Path schema=Path.of(System.getenv("HOTTER_LOCAL_CLOUD_SCHEMA"));
        String secret=Files.readString(Path.of(System.getenv("HOTTER_LOCAL_CLOUD_SESSION_FILE"))).strip();
        var props=new ImageCloudProperties(); props.setEnabled(true);props.setConcurrency(1);
        props.setApiKeyFile(System.getenv("BLUOCTO_API_KEY_FILE"));
        var allowed=new BluOctoImageClient(props).authorizedModels(); // 只读鉴权，未发起生成。
        var app=new CloudImageLocalServer(data,schema,secret,props,allowed,5180);
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        System.out.println("LOCAL_CLOUD_READY http://127.0.0.1:5180; supplier models authorized="+allowed.size());
    }
    int port(){return server.getAddress().getPort();}
    private void handle(HttpExchange ex) {
        try {
            String token=ex.getRequestHeaders().getFirst("X-Hotter-Local-Session");
            if(token==null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8))) { json(ex,403,Map.of("msg","本机联调会话无效"));return; }
            String method=ex.getRequestMethod(),origin=ex.getRequestHeaders().getFirst("Origin");
            if((origin!=null && !ORIGIN.equals(origin)) || (!"GET".equals(method) && !ORIGIN.equals(origin))) { json(ex,403,Map.of("msg","只接受当前本机页面的请求"));return; }
            String path=ex.getRequestURI().getPath();var query=query(ex);
            if("GET".equals(method) && path.equals("/image/cloud/models")) {
                json(ex,200,Map.of("configured",cloud.configured(),"models",allowed,"profiles",CloudImageRequest.profiles(),"capabilities",CloudImageRequest.CAPABILITIES,"verified",allowed.stream().anyMatch(m->CloudImageRequest.verified(m,"T2I"))));return;
            }
            if("GET".equals(method) && path.equals("/image/cloud/check")) { json(ex,200,Map.of("authorizedModels",allowed,"generationVerified",allowed.stream().anyMatch(m->CloudImageRequest.verified(m,"T2I"))));return; }
            if("POST".equals(method) && path.equals("/image/cloud/tasks")) {
                var body=mapper.readTree(read(ex,65536));
                var keys=new HashSet<String>();body.fieldNames().forEachRemaining(keys::add);
                if(!Set.of("model","prompt","capability","referenceAssetIds","maskAssetId","taskName","idempotencyKey","output").containsAll(keys)) throw ImageTaskException.invalidContract("请求包含未知字段");
                if(!allowed.contains(body.path("model").asText())) throw ImageTaskException.invalidContract("当前令牌未开放此型号");
                var request=mapper.treeToValue(((com.fasterxml.jackson.databind.node.ObjectNode)body.deepCopy()).without(List.of("taskName","idempotencyKey")),CloudImageRequest.class);
                json(ex,200,cloud.create(TENANT,USER,null,request,body.path("taskName").asText(""),body.path("idempotencyKey").asText("")));return;
            }
            if(path.equals("/image/assets") && "POST".equals(method)) {
                byte[] content=read(ex,20*1024*1024);var probe=ImageAssetProbe.probeBytes(content);
                if(!probe.measured() || probe.exceedsPixels(16*1024*1024) || !List.of("png","jpeg","jpg","webp").contains(probe.format().toLowerCase(Locale.ROOT))) throw ImageTaskException.invalidContract("素材格式或像素大小无效");
                long id=ids.incrementAndGet();String type="image/"+("jpg".equalsIgnoreCase(probe.format()) ? "jpeg" : probe.format().toLowerCase(Locale.ROOT));
                String name=query.getOrDefault("name","reference.png");if(name.length()>255)throw ImageTaskException.invalidContract("素材名称过长");
                String stored=assets.storeUpload(TENANT,USER,name,content,type);
                repository.insertAsset(new ImageTaskRepository.AssetRow(id,TENANT,USER,null,"IMAGE","UPLOAD",name,stored,type,content.length,null,probe.width(),probe.height(),probe.hasAlpha(),null));
                json(ex,200,Map.of("assetId",id,"contentType",type,"sizeBytes",content.length));return;
            }
            int page=Math.max(1,integer(query,"pageNum",1)),size=Math.max(1,Math.min(100,integer(query,"pageSize",12))),offset=(page-1)*size;
            if("GET".equals(method) && path.equals("/image/tasks")) { json(ex,200,Map.of("rows",repository.listOwnedTasks(TENANT,USER,query.get("status"),query.get("keyword"),offset,size).stream().map(this::view).toList(),"total",repository.countOwnedTasks(TENANT,USER,query.get("status"),query.get("keyword"))));return; }
            if("GET".equals(method) && path.equals("/image/assets")) { json(ex,200,Map.of("rows",repository.listOwnedAssets(TENANT,USER,offset,size).stream().map(this::view).toList(),"total",repository.countOwnedAssets(TENANT,USER)));return; }
            String[] parts=path.split("/");
            if(parts.length>=4 && parts[2].equals("tasks")) {
                long id=Long.parseLong(parts[3]);var task=repository.requireOwnedTask(id,TENANT,USER);
                if("GET".equals(method) && parts.length==4) {var row=view(task);row.put("events",repository.listEvents(id,TENANT).stream().map(this::view).toList());row.put("outputAssets",repository.listTaskOutputs(id,TENANT,USER).stream().map(this::view).toList());json(ex,200,row);return;}
                if("POST".equals(method) && parts.length==5) {
                    if(parts[4].equals("execute") || parts[4].equals("retry")) {
                        String outcome=parts[4].equals("execute")?cloud.execute(id,TENANT,USER):cloud.retry(id,TENANT,USER);
                        json(ex,200,Map.of("taskId",id,"outcome",outcome,"accepted",outcome.equals("ACCEPTED"),"status",repository.requireOwnedTask(id,TENANT,USER).get("status")));return;
                    }
                    if(parts[4].equals("cancel")) {repository.cancelQueued(id,TENANT,USER);json(ex,200,Map.of("taskId",id,"status",repository.requireOwnedTask(id,TENANT,USER).get("status")));return;}
                }
            }
            if(parts.length>=4 && parts[2].equals("assets")) {
                long id=Long.parseLong(parts[3]);var asset=repository.requireOwnedAsset(id,TENANT,USER);
                if("DELETE".equals(method) && parts.length==4){repository.softDeleteAsset(id,TENANT,USER);json(ex,200,Map.of());return;}
                if("GET".equals(method) && parts.length==5 && List.of("content","thumbnail").contains(parts[4])) {
                    byte[] content=parts[4].equals("thumbnail")?assets.thumbnail(asset.storageKey(),asset.contentType()):assets.read(asset.storageKey());
                    String type=parts[4].equals("thumbnail") && content!=null?"image/jpeg":asset.contentType();
                    if(content==null) content=assets.read(asset.storageKey());
                    ex.getResponseHeaders().set("Content-Type",type);ex.getResponseHeaders().set("Cache-Control","no-store");ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");ex.sendResponseHeaders(200,content.length);ex.getResponseBody().write(content);return;
                }
            }
            json(ex,404,Map.of("msg","本机联调未提供该接口"));
        } catch(ImageTaskException e) { try{json(ex,400,Map.of("msg",e.getMessage(),"errorCode",e.getErrorCode()));}catch(Exception ignored){} }
        catch(Exception e) {try{json(ex,400,Map.of("msg","本机请求处理失败，请检查参数或本机服务状态"));}catch(Exception ignored){}} // 禁止回显密钥或供应商响应。
        finally {ex.close();}
    }
    private Map<String,Object> view(Map<String,Object> raw) {
        var output=new LinkedHashMap<String,Object>();
        raw.forEach((key,value)->{String[] segments=key.split("_");StringBuilder name=new StringBuilder(segments[0]);for(int i=1;i<segments.length;i++)name.append(Character.toUpperCase(segments[i].charAt(0))).append(segments[i].substring(1));
            output.put(name.toString(),value instanceof java.sql.Timestamp time?time.toLocalDateTime().toString().replace('T',' '):value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):value);});return output;
    }
    private static int integer(Map<String,String> q,String k,int fallback) {return q.containsKey(k)?Integer.parseInt(q.get(k)):fallback;}
    private static Map<String,String> query(HttpExchange ex) {var q=new HashMap<String,String>();String text=ex.getRequestURI().getRawQuery();if(text!=null)for(String part:text.split("&")){String[] entry=part.split("=",2);q.put(URLDecoder.decode(entry[0],StandardCharsets.UTF_8),entry.length>1?URLDecoder.decode(entry[1],StandardCharsets.UTF_8):"");}return q;}
    private static byte[] read(HttpExchange ex,int limit) throws Exception {byte[] bytes=ex.getRequestBody().readNBytes(limit+1);if(bytes.length>limit)throw ImageTaskException.invalidContract("请求超过大小上限");return bytes;}
    private void json(HttpExchange ex,int status,Object data) throws Exception {byte[] bytes=mapper.writeValueAsBytes(data);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(status,bytes.length);ex.getResponseBody().write(bytes);}
    @Override public void close(){server.stop(0);httpWorkers.shutdownNow();cloud.shutdown();jdbc.execute("SHUTDOWN");}
}
