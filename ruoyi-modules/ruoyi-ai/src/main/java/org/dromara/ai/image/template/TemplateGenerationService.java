package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.ai.image.cloud.ImageCloudService;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.*;

/** 持久化请求账本。所有恢复只查询原任务，不重复调用收费接口。 */
@Service
@ConditionalOnProperty(prefix="image",name={"enabled","template-feed.enabled"},havingValue="true")
public class TemplateGenerationService {
    private final JdbcTemplate jdbc;
    private final TemplateFeedCache cache;
    private final TemplateFeedProperties properties;
    private final ImageCloudService cloud;
    private final ImageTaskRepository tasks;
    public TemplateGenerationService(JdbcTemplate jdbc,TemplateFeedCache cache,TemplateFeedProperties properties,ImageCloudService cloud,ImageTaskRepository tasks) {
        this.jdbc=jdbc; this.cache=cache; this.properties=properties; this.cloud=cloud; this.tasks=tasks;
    }
    public Map<String,Object> submit(String tenant,long user,Long dept,JsonNode body) {
        // 先校验请求形状；不能将客户端传入的绑定字段用于请求或账本哈希。
        String id=body.path("template_id").asText();
        if(!id.matches("[A-Za-z0-9_-]{1,128}")) throw new TemplateFeedException(422,"模板编号无效");
        String client=body.path("client_request_id").asText();
        String hash=TemplateFeedCache.sha(TemplateFeedCache.canonical(body));
        Map<String,Object> prior=findClient(tenant,user,client);
        if(prior!=null) { same(prior,hash); return status(tenant,user,String.valueOf(prior.get("request_id"))); }
        cache.refresh(); JsonNode template=cache.require(id);
        TemplateRequestBuilder.Built built;
        try { built=TemplateRequestBuilder.build(body,template,cache.current().profiles(),properties.getBlockedTerms()); }
        catch(TemplateFeedException e) {
            jdbc.update("INSERT INTO ai_template_validation_audit (tenant_id,user_id,template_id,rules) VALUES (?,?,?,?)",tenant,user,id,
                String.join(",",e.errors.stream().map(x->x.get("rule")).distinct().toList()));
            throw e;
        }
        if(built.revision()!=template.path("revision").asInt()) throw new TemplateFeedException(409,"模板已更新，请重新选择");
        boolean approved=properties.isGenerationEnabled() && properties.getVerifiedProfiles().contains(template.path("binding").path("params_profile").asText());
        boolean validation=properties.getValidationUserIds().contains(user) && !approved;
        if((!approved && !validation) || !cache.published(template)) throw new TemplateFeedException(422,"模板维护中，暂不可生成");
        String request=UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO ai_template_request (request_id,tenant_id,user_id,client_request_id,request_hash,template_id,revision,status) VALUES (?,?,?,?,?,?,?,'pending')",
                request,tenant,user,built.clientId().toString(),built.hash(),id,built.revision());
        } catch(DuplicateKeyException e) { prior=findClient(tenant,user,built.clientId().toString()); if(prior==null) throw e; same(prior,built.hash()); return status(tenant,user,String.valueOf(prior.get("request_id"))); }
        if(validation) {
            jdbc.update("INSERT INTO ai_template_validation_budget (tenant_id,user_id,issued) VALUES (?,?,0) ON DUPLICATE KEY UPDATE issued=issued",tenant,user);
            int reserved=jdbc.update("UPDATE ai_template_validation_budget SET issued=issued+1 WHERE tenant_id=? AND user_id=? AND issued<2",tenant,user);
            if(reserved==0) {
                jdbc.update("UPDATE ai_template_request SET status='failed',error_code='validation_budget',error_message='本轮2次验收额度已用完' WHERE request_id=? AND status='pending'",request);
                return status(tenant,user,request);
            }
        }
        // INSERT 和验收额度已提交才发起任务；后续异常不删除幂等行，不因响应丢失重发。
        try {
            var created=cloud.createTemplate(tenant,user,dept,built.input(),template.path("title").path("zh").asText(),"tpl-"+request);
            long task=((Number)created.get("taskId")).longValue();
            jdbc.update("UPDATE ai_template_request SET task_id=?,updated_at=CURRENT_TIMESTAMP WHERE request_id=? AND status='pending'",task,request);
            String dispatch=cloud.executeTemplate(task,tenant,user);
            if("QUEUE_FULL".equals(dispatch)) {
                jdbc.update("UPDATE ai_template_request SET status='unknown',confirmed_not_submitted=TRUE,error_code='queue_full',error_message='平台队列已满，未调用供应商',updated_at=CURRENT_TIMESTAMP WHERE request_id=? AND status='pending'",request);
                return status(tenant,user,request);
            }
            if(!List.of("ACCEPTED","ALREADY_CLAIMED").contains(dispatch)) throw new IllegalStateException("dispatch declined");
            jdbc.update("UPDATE ai_template_request SET status='submitted',updated_at=CURRENT_TIMESTAMP WHERE request_id=? AND status='pending'",request);
        } catch(Exception e) {
            jdbc.update("UPDATE ai_template_request SET status='unknown',error_code='unknown',error_message='结果待核对，请勿重复生成',updated_at=CURRENT_TIMESTAMP WHERE request_id=? AND status='pending'",request);
        }
        return status(tenant,user,request);
    }
    private static void same(Map<String,Object> row,String hash) { if(!hash.equals(row.get("request_hash"))) throw new TemplateFeedException(409,"同一个请求编号不能用于不同参数"); }
    private Map<String,Object> findClient(String tenant,long user,String client) {
        var rows=jdbc.queryForList("SELECT * FROM ai_template_request WHERE tenant_id=? AND user_id=? AND client_request_id=?",tenant,user,client); return rows.isEmpty()?null:rows.getFirst();
    }
    public Map<String,Object> status(String tenant,long user,String id) {
        var rows=jdbc.queryForList("SELECT * FROM ai_template_request WHERE request_id=? AND tenant_id=? AND user_id=?",id,tenant,user);
        if(rows.isEmpty()) throw new TemplateFeedException(404,"请求不存在");
        Map<String,Object> row=rows.getFirst(); reconcile(row);
        row=jdbc.queryForList("SELECT * FROM ai_template_request WHERE request_id=? AND tenant_id=? AND user_id=?",id,tenant,user).getFirst();
        String status=String.valueOf(row.get("status"));
        Object created=row.get("created_at"); Instant date=created instanceof java.sql.Timestamp ts?ts.toInstant():Instant.now();
        var out=new LinkedHashMap<String,Object>(); out.put("request_id",id); out.put("status",Instant.now().isAfter(date.plusSeconds(86400))?"archived":status); out.put("summary_status",status);
        if(row.get("task_id") instanceof Number task) {
            out.put("task_id",String.valueOf(task.longValue()));
            if("succeeded".equals(status)) out.put("result_urls",tasks.listTaskOutputs(task.longValue(),tenant,user).stream().map(a->properties.getPublicBaseUrl().replace("/templates","")+"/assets/"+a.get("id")+"/content").toList());
        }
        if(row.get("error_code")!=null) out.put("error",Map.of("code",row.get("error_code"),"message",row.getOrDefault("error_message","请核对任务状态")));
        return out;
    }
    private void reconcile(Map<String,Object> row) {
        if(!List.of("pending","submitted","unknown").contains(String.valueOf(row.get("status")))) return;
        String tenant=String.valueOf(row.get("tenant_id")); long user=((Number)row.get("user_id")).longValue(); String request=String.valueOf(row.get("request_id"));
        Long task=row.get("task_id") instanceof Number n?n.longValue():tasks.findByIdempotencyKey(tenant,user,"tpl-"+request);
        if(task==null) {
            jdbc.update("UPDATE ai_template_request SET status='unknown',error_code='unknown',error_message='崩溃窗口待对账',updated_at=CURRENT_TIMESTAMP WHERE request_id=? AND status='pending' AND created_at < ?",request,java.sql.Timestamp.from(Instant.now().minusSeconds(120)));
            return; // 不能证明未到上游，留待人工对账。
        }
        Map<String,Object> t=tasks.requireOwnedTask(task,tenant,user); String next=switch(String.valueOf(t.get("status"))) {
            case "SUCCEEDED" -> "succeeded";
            case "FAILED" -> "unknown".equals(t.get("error_code")) || "CLOUD_RESULT_UNKNOWN".equals(t.get("error_code")) || "ORPHANED_BY_RESTART".equals(t.get("error_code")) ? "unknown":"failed";
            case "RUNNING" -> "submitted"; default -> "unknown";
        };
        if("QUEUED".equals(t.get("status"))) {
            Object confirmed=row.get("confirmed_not_submitted");
            if(Boolean.TRUE.equals(confirmed) || (confirmed instanceof Number n && n.intValue()==1)) return;
            Object created=row.get("created_at");
            if("pending".equals(row.get("status")) && created instanceof java.sql.Timestamp ts && Instant.now().isBefore(ts.toInstant().plusSeconds(120))) return;
        }
        jdbc.update("UPDATE ai_template_request SET task_id=?,status=?,error_code=?,error_message=?,updated_at=CURRENT_TIMESTAMP WHERE request_id=? AND tenant_id=? AND user_id=? AND status=?",
            task,next,List.of("unknown","failed").contains(next)?"unknown".equals(next)?"unknown":"upstream":null,
            "unknown".equals(next)?"供应商结果待核对，请勿重复生成":"failed".equals(next)?"请求被拒绝，请查看任务记录":null,request,tenant,user,row.get("status"));
    }
    /** 仅平台明确未派发的队列拒绝可恢复；上游结果未知需人工对账。 */
    public Map<String,Object> retry(String tenant,long user,String id) {
        Map<String,Object> current=status(tenant,user,id);
        if("archived".equals(current.get("status"))) throw new TemplateFeedException(409,"请求已归档");
        var row=jdbc.queryForList("SELECT template_id,revision FROM ai_template_request WHERE request_id=? AND tenant_id=? AND user_id=?",id,tenant,user).getFirst();
        cache.refresh(); JsonNode template=cache.require(String.valueOf(row.get("template_id")));
        boolean approved=properties.isGenerationEnabled() && properties.getVerifiedProfiles().contains(template.path("binding").path("params_profile").asText());
        if((!approved && !properties.getValidationUserIds().contains(user)) || !cache.published(template) || template.path("revision").asInt()!=((Number)row.get("revision")).intValue()) throw new TemplateFeedException(409,"模板已更新或维护中，不能恢复此请求");
        int claimed=jdbc.update("UPDATE ai_template_request SET status='pending',confirmed_not_submitted=FALSE,updated_at=CURRENT_TIMESTAMP WHERE request_id=? AND tenant_id=? AND user_id=? AND status='unknown' AND confirmed_not_submitted=TRUE",id,tenant,user);
        if(claimed==0) throw new TemplateFeedException(409,"未确认请求从未到达供应商，不能重试；请先对账");
        try {
            String dispatch=cloud.executeTemplate(Long.parseLong(String.valueOf(current.get("task_id"))),tenant,user);
            if("QUEUE_FULL".equals(dispatch)) jdbc.update("UPDATE ai_template_request SET status='unknown',confirmed_not_submitted=TRUE WHERE request_id=? AND status='pending'",id);
            else jdbc.update("UPDATE ai_template_request SET status='submitted',error_code=NULL,error_message=NULL WHERE request_id=? AND status='pending'",id);
        } catch(Exception e) { jdbc.update("UPDATE ai_template_request SET status='unknown',error_code='unknown',error_message='结果待核对' WHERE request_id=? AND status='pending'",id); }
        return status(tenant,user,id);
    }
    @Scheduled(initialDelay=30000,fixedDelay=60000)
    public void recover() {
        try { for(var row:jdbc.queryForList("SELECT * FROM ai_template_request WHERE status IN ('pending','submitted','unknown') ORDER BY updated_at LIMIT 100")) { try { reconcile(row); } catch(Exception ignored) { /* 保留单条未知状态，继续查询其他请求。 */ } } }
        catch(Exception ignored) { /* 未执行迁移时不拖垮其他模块；生成入口将明确失败。 */ }
    }
    public boolean canValidate(String tenant,long user) {
        if(!properties.getValidationUserIds().contains(user)) return false;
        var rows=jdbc.queryForList("SELECT issued FROM ai_template_validation_budget WHERE tenant_id=? AND user_id=?",tenant,user);
        return rows.isEmpty() || ((Number)rows.getFirst().get("issued")).intValue()<2;
    }
}
