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
}
