package org.dromara.ai.image.template;

import java.util.List;
import java.util.Map;

/** 只携带预定义错误，不保存带凭据的上游 URL 或原始输入。 */
public class TemplateFeedException extends RuntimeException {
    final int status;
    final List<Map<String,String>> errors;
    public TemplateFeedException(int status, String message) { this(status, message, List.of()); }
    public TemplateFeedException(int status, String message, List<Map<String,String>> errors) {
        super(message); this.status = status; this.errors = List.copyOf(errors);
    }
}
