package org.dromara.ai.image.cloud;

import org.dromara.ai.image.exception.ImageTaskException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CloudImageOutputValidationTest {
    private final List<Map<String,Object>> evidence=List.of(Map.of("model","gpt-image-2.5-flare","capability","EDIT","output",Map.of("size","1536x1024","n",2,"quality","medium","outputFormat","jpeg"),"verifiedFields",List.of("size","n")));
    private CloudImageRequest request(String model,String mode,CloudImageOutput output) {
        return new CloudImageRequest(model,"test",mode,List.of(),null,output);
    }
    @Test void independentPassedValuesCanBeCombinedWithoutChangingOtherFields() {
        assertDoesNotThrow(()->CloudImageOutputValidation.requireVerified(request("gpt-image-2.5-flare","EDIT",new CloudImageOutput("1536x1024",2,null,null)),evidence));
        assertDoesNotThrow(()->CloudImageOutputValidation.requireVerified(request("gpt-image-2.5-flare","EDIT",new CloudImageOutput(null,2,null,null)),evidence));
    }
    @Test void acceptedButUnprovenQualityAndIgnoredFormatAreNotPromoted() {
        assertThrows(ImageTaskException.class,()->CloudImageOutputValidation.requireVerified(request("gpt-image-2.5-flare","EDIT",new CloudImageOutput(null,1,null,"jpeg")),evidence));
        assertThrows(ImageTaskException.class,()->CloudImageOutputValidation.requireVerified(request("gpt-image-2.5-flare","EDIT",new CloudImageOutput(null,1,"medium",null)),evidence));
    }
    @Test void evidenceDoesNotCrossModelOrCapability() {
        assertThrows(ImageTaskException.class,()->CloudImageOutputValidation.requireVerified(request("gpt-image-2.5-sunburst","EDIT",new CloudImageOutput(null,2,null,null)),evidence));
        assertThrows(ImageTaskException.class,()->CloudImageOutputValidation.requireVerified(request("gpt-image-2.5-flare","T2I",new CloudImageOutput(null,2,null,null)),evidence));
    }
    @Test void runtimeRegistryUsesMeasuredReceiptsAndKeepsDefaultCompatibility() {
        assertDoesNotThrow(()->CloudImageOutputValidation.requireVerified(request("qwen-image-3.0","T2I",new CloudImageOutput("1024x1024",1,null,null))));
        assertThrows(ImageTaskException.class,()->CloudImageOutputValidation.requireVerified(request("wan2.7-image","EDIT",new CloudImageOutput("1536x1024",2,null,null))));
        assertDoesNotThrow(()->CloudImageOutputValidation.requireVerified(request("gpt-image-2.5-sunburst","T2I",CloudImageOutput.DEFAULT)));
    }
}
