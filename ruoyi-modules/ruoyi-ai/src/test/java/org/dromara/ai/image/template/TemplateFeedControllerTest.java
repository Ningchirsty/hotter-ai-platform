package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.ai.image.service.ImageInspirationTenantResolver;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TemplateFeedControllerTest {
    @Test void jacksonThreeHttpBodyReachesExistingTreeValidationWithoutLosingVariables() throws Exception {
        var generation=mock(TemplateGenerationService.class);
        var tenants=mock(ImageInspirationTenantResolver.class);
        when(tenants.resolve(7L)).thenReturn("tenant-a");
        when(generation.submit(eq("tenant-a"),eq(7L),eq(9L),any())).thenReturn(Map.of("status","submitted"));
        var mvc=MockMvcBuilders.standaloneSetup(new TemplateFeedController(mock(TemplateFeedCache.class),generation,tenants))
            .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        try(var login=mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(9L);
            mvc.perform(post("/image/templates/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"template_id\":\"tpl_ff359\",\"revision\":2,\"client_request_id\":\"adabf122-3df2-43a0-a763-8f2442cf9dee\",\"variables\":{\"subject\":\"虚构水墨剑客\"}}"))
                .andExpect(status().isAccepted());
            var body=ArgumentCaptor.forClass(JsonNode.class);
            verify(generation).submit(eq("tenant-a"),eq(7L),eq(9L),body.capture());
            assertTrue(body.getValue().path("revision").isInt());
            assertEquals("虚构水墨剑客",body.getValue().path("variables").path("subject").asText());
            assertEquals("adabf122-3df2-43a0-a763-8f2442cf9dee",body.getValue().path("client_request_id").asText());
        }
    }
    @Test void prepareUsesAuthenticatedTenantAndJacksonThreeBodyWithoutSubmittingGeneration() throws Exception {
        var generation=mock(TemplateGenerationService.class);var tenants=mock(ImageInspirationTenantResolver.class);
        when(tenants.resolve(7L)).thenReturn("tenant-a");
        when(generation.prepare(any())).thenReturn(Map.of("model","gpt-image-2.5-sunburst","prompt","test","output",Map.of("n",1)));
        var mvc=MockMvcBuilders.standaloneSetup(new TemplateFeedController(mock(TemplateFeedCache.class),generation,tenants)).setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        try(var login=mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            mvc.perform(post("/image/templates/prepare").contentType(MediaType.APPLICATION_JSON).content("{\"template_id\":\"tpl_ff359\",\"revision\":2,\"variables\":{\"subject\":\"白色花瓶\"}}")) .andExpect(status().isOk());
            var capture=ArgumentCaptor.forClass(JsonNode.class);verify(generation).prepare(capture.capture());verify(tenants).resolve(7L);
            assertEquals("白色花瓶",capture.getValue().path("variables").path("subject").asText());verifyNoMoreInteractions(generation);
        }
    }
    @Test void nonObjectHttpBodyCannotDispatchAnyGeneration() throws Exception {
        var generation=mock(TemplateGenerationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new TemplateFeedController(mock(TemplateFeedCache.class),generation,mock(ImageInspirationTenantResolver.class)))
            .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        mvc.perform(post("/image/templates/generate").contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(generation);
    }
}
