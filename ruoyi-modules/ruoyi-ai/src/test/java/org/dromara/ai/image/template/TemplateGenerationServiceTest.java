package org.dromara.ai.image.template;

import org.dromara.ai.image.cloud.ImageCloudService;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateGenerationServiceTest {
    JdbcTemplate jdbc; TemplateFeedCache cache; TemplateFeedProperties properties; ImageCloudService cloud; ImageTaskRepository tasks; TemplateGenerationService service;
    com.fasterxml.jackson.databind.node.ObjectNode body;
    @BeforeEach void setup() throws Exception {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:tpl"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE ai_template_request (request_id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(20),user_id BIGINT,client_request_id VARCHAR(36),request_hash VARCHAR(64),template_id VARCHAR(128),revision INT,task_id BIGINT,status VARCHAR(16),confirmed_not_submitted BOOLEAN DEFAULT FALSE,error_code VARCHAR(64),error_message VARCHAR(255),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(tenant_id,user_id,client_request_id))");
        jdbc.execute("CREATE TABLE ai_template_validation_audit (tenant_id VARCHAR(20),user_id BIGINT,template_id VARCHAR(128),rules VARCHAR(1024))");
        jdbc.execute("CREATE TABLE ai_template_validation_budget (tenant_id VARCHAR(20),user_id BIGINT,issued INT,PRIMARY KEY(tenant_id,user_id))");
        cache=mock(TemplateFeedCache.class); properties=new TemplateFeedProperties(); properties.setGenerationEnabled(true); properties.setVerifiedProfiles(List.of("img-square-hd","img-portrait-916")); cloud=mock(ImageCloudService.class); tasks=mock(ImageTaskRepository.class);
        var f=TemplateFeedCache.JSON.readTree(getClass().getResourceAsStream("/template-feed/full-4.json")); var t=f.path("templates").get(0);
        when(cache.require(t.path("id").asText())).thenReturn(t); when(cache.current()).thenReturn(new TemplateFeedCache.Snapshot(4,Map.of(),Map.of(),TemplateFeedCache.nodes(f.path("profiles")),java.time.Instant.now(),"")); when(cache.published(t)).thenReturn(true);
        when(cloud.createTemplate(anyString(),anyLong(),any(),any(),anyString(),anyString())).thenReturn(Map.of("taskId",123L)); when(cloud.executeTemplate(anyLong(),anyString(),anyLong())).thenReturn("ACCEPTED"); when(tasks.requireOwnedTask(123L,"a",7L)).thenReturn(Map.of("status","RUNNING"));
        body=TemplateFeedCache.JSON.createObjectNode(); body.put("template_id",t.path("id").asText()); body.put("revision",1); body.put("client_request_id",UUID.randomUUID().toString()); body.putObject("variables");
        service=new TemplateGenerationService(jdbc,cache,properties,cloud,tasks);
    }
    @Test void prepareReturnsOnlyEditableDraftEvenWhenDirectTemplateGenerationIsDisabled() throws Exception {
        properties.setGenerationEnabled(false); properties.setVerifiedProfiles(List.of());
        body.remove("client_request_id");
        var template=(com.fasterxml.jackson.databind.node.ObjectNode)cache.require(body.path("template_id").asText()).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)template.path("prompt")).put("en","Detailed creative scene ".repeat(90));
        when(cache.require(body.path("template_id").asText())).thenReturn(template);when(cache.published(template)).thenReturn(true);
        var result=service.prepare(body);
        assertEquals(Set.of("templateId","revision","model","capability","prompt","referenceAssetIds","output"),result.keySet());
        assertEquals("gpt-image-2.5-sunburst",result.get("model"));assertEquals("T2I",result.get("capability"));
        assertEquals(template.path("prompt").path("en").asText(),result.get("prompt"));assertTrue(((String)result.get("prompt")).length()>1000);
        assertEquals(List.of(),result.get("referenceAssetIds"));assertFalse(body.has("client_request_id"));
        for(String table:List.of("ai_template_request","ai_template_validation_audit","ai_template_validation_budget")) assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
        verifyNoInteractions(cloud,tasks);
    }
    @Test void prepareRejectsStaleMaintenanceAndUndeclaredVariablesWithoutAnyTaskOrBudget() {
        body.remove("client_request_id");body.put("revision",2);
        assertEquals(409,assertThrows(TemplateFeedException.class,()->service.prepare(body)).status);
        body.put("revision",1);when(cache.published(any())).thenReturn(false);
        assertEquals(422,assertThrows(TemplateFeedException.class,()->service.prepare(body)).status);
        when(cache.published(any())).thenReturn(true);((com.fasterxml.jackson.databind.node.ObjectNode)body.path("variables")).put("injected","ignored");
        assertEquals("undeclared_variable",assertThrows(TemplateFeedException.class,()->service.prepare(body)).errors.getFirst().get("rule"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_template_request",Integer.class));verifyNoInteractions(cloud,tasks);
    }
    @Test void prepareSubstitutesOnlyDeclaredVariablesAndRejectsMissingRequiredText() {
        body.remove("client_request_id");
        var template=(com.fasterxml.jackson.databind.node.ObjectNode)cache.require(body.path("template_id").asText()).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)template.path("prompt")).put("en","Photograph of {{subject}} in soft light");
        template.putArray("variables").addObject().put("key","subject").put("type","text").put("required",true).put("max_len",100);
        when(cache.require(body.path("template_id").asText())).thenReturn(template);when(cache.published(template)).thenReturn(true);
        assertEquals("required",assertThrows(TemplateFeedException.class,()->service.prepare(body)).errors.getFirst().get("rule"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)body.path("variables")).put("subject","白色花瓶");
        assertEquals("Photograph of 白色花瓶 in soft light",service.prepare(body).get("prompt"));verifyNoInteractions(cloud,tasks);
    }
    @Test void concurrentResponseLossReplaysUseOneDurableRequestAndOneDispatch() throws Exception {
        try(var executor=Executors.newFixedThreadPool(6)) {
            var futures=new ArrayList<Future<Map<String,Object>>>(); for(int i=0;i<6;i++) futures.add(executor.submit(()->service.submit("a",7L,null,body.deepCopy())));
            var ids=new HashSet<Object>(); for(var f:futures) ids.add(f.get(20,TimeUnit.SECONDS).get("request_id")); assertEquals(1,ids.size());
        }
        verify(cloud,times(1)).createTemplate(anyString(),anyLong(),any(),any(),anyString(),anyString()); verify(cloud,times(1)).executeTemplate(123L,"a",7L);
        var restarted=new TemplateGenerationService(jdbc,cache,properties,cloud,tasks); restarted.submit("a",7L,null,body); verify(cloud,times(1)).executeTemplate(123L,"a",7L);
    }
    @Test void conflictingUuidFailsAndOtherOwnerCannotReadOrRetry() {
        var r=service.submit("a",7L,null,body); body.put("revision",2); assertEquals(409,assertThrows(TemplateFeedException.class,()->service.submit("a",7L,null,body)).status);
        String id=String.valueOf(r.get("request_id")); assertEquals(404,assertThrows(TemplateFeedException.class,()->service.status("b",7L,id)).status); assertEquals(404,assertThrows(TemplateFeedException.class,()->service.retry("a",8L,id)).status);
    }
    @Test void crashWindowAndUnknownTaskNeverAutomaticallyResubmit() {
        var r=service.submit("a",7L,null,body); String id=String.valueOf(r.get("request_id"));
        jdbc.update("UPDATE ai_template_request SET status='pending',task_id=NULL WHERE request_id=?",id);
        when(tasks.findByIdempotencyKey("a",7L,"tpl-"+id)).thenReturn(123L); when(tasks.requireOwnedTask(123L,"a",7L)).thenReturn(Map.of("status","FAILED","error_code","CLOUD_RESULT_UNKNOWN"));
        service.recover(); assertEquals("unknown",service.status("a",7L,id).get("status")); assertEquals(409,assertThrows(TemplateFeedException.class,()->service.retry("a",7L,id)).status);
        verify(cloud,times(1)).executeTemplate(anyLong(),anyString(),anyLong());
    }
    @Test void explicitRejectionIsTerminalAndNeverRetried() {
        var r=service.submit("a",7L,null,body); when(tasks.requireOwnedTask(123L,"a",7L)).thenReturn(Map.of("status","FAILED","error_code","CLOUD_HTTP_REJECTED")); String id=String.valueOf(r.get("request_id"));
        assertEquals("failed",service.status("a",7L,id).get("status")); assertEquals(409,assertThrows(TemplateFeedException.class,()->service.retry("a",7L,id)).status); verify(cloud,times(1)).executeTemplate(anyLong(),anyString(),anyLong());
    }
    @Test void offlineAndStaleRevisionCannotCreateAnyPaidTask() {
        when(cache.published(any())).thenReturn(false); assertEquals(422,assertThrows(TemplateFeedException.class,()->service.submit("a",7L,null,body)).status);
        when(cache.published(any())).thenReturn(true); body.put("revision",2); assertEquals(409,assertThrows(TemplateFeedException.class,()->service.submit("a",7L,null,body)).status); verifyNoInteractions(cloud);
    }
    @Test void knownPlatformQueueRejectionRetainsItsEvidenceAndCanRetryOnce() {
        when(cloud.executeTemplate(anyLong(),anyString(),anyLong())).thenReturn("QUEUE_FULL","ACCEPTED"); when(tasks.requireOwnedTask(123L,"a",7L)).thenReturn(Map.of("status","QUEUED"));
        var r=service.submit("a",7L,null,body); String id=String.valueOf(r.get("request_id")); assertEquals("unknown",r.get("status")); assertEquals("queue_full",((Map<?,?>)r.get("error")).get("code"));
        service.retry("a",7L,id); assertEquals(409,assertThrows(TemplateFeedException.class,()->service.retry("a",7L,id)).status);
        verify(cloud,times(2)).executeTemplate(123L,"a",7L); verify(cloud,times(1)).createTemplate(anyString(),anyLong(),any(),any(),anyString(),anyString());
    }
    @Test void invalidVariablesAuditOnlyRulesAndCannotCreateATask() {
        ((com.fasterxml.jackson.databind.node.ObjectNode)body.path("variables")).put("injected","secret-text");
        assertThrows(TemplateFeedException.class,()->service.submit("a",7L,null,body));
        String rules=jdbc.queryForObject("SELECT rules FROM ai_template_validation_audit",String.class); assertEquals("undeclared_variable",rules); assertFalse(rules.contains("secret-text")); verifyNoInteractions(cloud);
    }
    @Test void paidValidationBudgetSurvivesRestartAndNeverExceedsTwoDispatches() {
        properties.setGenerationEnabled(false); properties.setValidationUserIds(List.of(7L));
        for(int i=0;i<3;i++) { body.put("client_request_id",UUID.randomUUID().toString()); service.submit("a",7L,null,body); }
        verify(cloud,times(2)).createTemplate(anyString(),anyLong(),any(),any(),anyString(),anyString());
        assertEquals(2,jdbc.queryForObject("SELECT issued FROM ai_template_validation_budget",Integer.class));
        var restarted=new TemplateGenerationService(jdbc,cache,properties,cloud,tasks); assertFalse(restarted.canValidate("a",7L));
    }

    private String queuedAfterTemplateUpdate() throws Exception {
        properties.setGenerationEnabled(false);properties.setValidationUserIds(List.of(7L));
        when(cloud.executeTemplate(anyLong(),anyString(),anyLong())).thenReturn("QUEUE_FULL","ACCEPTED");
        when(tasks.requireOwnedTask(123L,"a",7L)).thenReturn(Map.of("status","QUEUED"));
        var result=service.submit("a",7L,null,body);String request=String.valueOf(result.get("request_id"));
        var captured=org.mockito.ArgumentCaptor.forClass(org.dromara.ai.image.cloud.CloudImageRequest.class);
        verify(cloud).createTemplate(anyString(),anyLong(),any(),captured.capture(),anyString(),anyString());
        String snapshot=TemplateFeedCache.JSON.writeValueAsString(Map.of("source","cloud","provider","bluocto","request",captured.getValue()));
        when(tasks.requireOwnedTask(123L,"a",7L)).thenReturn(Map.of("status","QUEUED","idempotency_key","tpl-"+request,"input_json",snapshot));
        var latest=cache.require(body.path("template_id").asText()).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)latest).put("revision",2);
        ((com.fasterxml.jackson.databind.node.ObjectNode)latest.path("prompt")).put("en","New prompt must not replace the original task");
        when(cache.require(body.path("template_id").asText())).thenReturn(latest);when(cache.published(latest)).thenReturn(true);
        return request;
    }
    @Test void updatedTemplateRestoresFrozenTaskWithoutNewBudgetOrRevision() throws Exception {
        String request=queuedAfterTemplateUpdate();service.retry("a",7L,request);
        verify(cloud,times(1)).createTemplate(anyString(),anyLong(),any(),any(),anyString(),anyString());
        verify(cloud,times(2)).executeTemplate(123L,"a",7L);
        assertEquals(1,jdbc.queryForObject("SELECT issued FROM ai_template_validation_budget",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT revision FROM ai_template_request",Integer.class));
        assertEquals(409,assertThrows(TemplateFeedException.class,()->service.retry("a",7L,request)).status);
    }
    @Test void changedBindingOrMismatchedTaskIdentityCannotRestore() throws Exception {
        String request=queuedAfterTemplateUpdate();
        var latest=cache.require(body.path("template_id").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode)latest.path("binding")).put("params_profile","img-portrait-916");
        assertEquals(409,assertThrows(TemplateFeedException.class,()->service.retry("a",7L,request)).status);
        ((com.fasterxml.jackson.databind.node.ObjectNode)latest.path("binding")).put("params_profile","img-square-hd");
        var task=new HashMap<>(tasks.requireOwnedTask(123L,"a",7L));task.put("idempotency_key","tpl-other");when(tasks.requireOwnedTask(123L,"a",7L)).thenReturn(task);
        assertEquals(409,assertThrows(TemplateFeedException.class,()->service.retry("a",7L,request)).status);
        verify(cloud,times(1)).executeTemplate(anyLong(),anyString(),anyLong());
    }
}
