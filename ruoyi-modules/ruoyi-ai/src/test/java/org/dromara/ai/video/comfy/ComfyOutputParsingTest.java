package org.dromara.ai.video.comfy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ComfyUI 输出解析的回归测试。
 *
 * <p>用例形状取自 2026-09-16 在 A100 上的真实执行结果：
 * {@code SaveVideo} 节点把成片放在 <b>images</b> 数组里、字段为 filename，
 * 而当时的解析只读 videos 数组，导致「ComfyUI 执行完成但没有产出视频」的误判。</p>
 */
class ComfyOutputParsingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<ComfyOutput> parse(String historyJson) throws Exception {
        // 构造一个 client 只为调用纯解析方法；不发起任何网络请求。
        HttpComfyClient client = new HttpComfyClient("http://192.168.2.223:8188", MAPPER, false);
        JsonNode history = MAPPER.readTree(historyJson);
        return client.extractOutputs(history.path("prompt-id-1").path("outputs"));
    }

    @Test
    @DisplayName("回归：SaveVideo 放在 images 数组里的 mp4 必须被识别为成片")
    void detectsVideoInImagesArray() throws Exception {
        String history = """
            {"prompt-id-1": {"outputs": {
              "9":  {"text": ["24.0"]},
              "10": {"text": ["124"]},
              "7":  {"images": [{"filename": "MiniMaxH3_Director_fl2v_native_1080p_turbo_bf16_5s_00013_.mp4",
                                 "subfolder": "video", "type": "output"}], "animated": [true]},
              "8":  {"text": ["director log"]}
            }}}
            """;
        List<ComfyOutput> outputs = parse(history);
        assertEquals(1, outputs.size(), "必须恰好识别出 1 个成片");
        ComfyOutput out = outputs.get(0);
        assertEquals("MiniMaxH3_Director_fl2v_native_1080p_turbo_bf16_5s_00013_.mp4", out.fileName());
        assertEquals("video", out.subfolder(), "subfolder 必须保留，否则 /view 取不到文件");
        assertEquals("output", out.type());
    }

    @Test
    @DisplayName("videos 数组形态同样支持")
    void detectsVideoInVideosArray() throws Exception {
        String history = """
            {"prompt-id-1": {"outputs": {
              "7": {"videos": [{"filename": "out.mp4", "subfolder": "", "type": "output",
                                "width": 1920, "height": 1080, "fps": 24.0,
                                "duration_ms": 5166, "size_bytes": 123456}]}
            }}}
            """;
        List<ComfyOutput> outputs = parse(history);
        assertEquals(1, outputs.size());
        assertEquals(5166L, outputs.get(0).durationMillis());
        assertTrue(outputs.get(0).is1080p());
    }

    @Test
    @DisplayName("非视频输出（如预览 PNG）不得被当作成片")
    void ignoresNonVideoImages() throws Exception {
        String history = """
            {"prompt-id-1": {"outputs": {
              "7": {"images": [{"filename": "preview_00001_.png", "subfolder": "", "type": "temp"}]}
            }}}
            """;
        assertTrue(parse(history).isEmpty(), "PNG 预览不应被识别为成片");
    }

    @Test
    @DisplayName("node 的 filename 为对象形态时也要兼容")
    void handlesObjectFilename() throws Exception {
        String history = """
            {"prompt-id-1": {"outputs": {
              "7": {"images": [{"filename": {"filename": "nested.mp4", "subfolder": "video"},
                                "subfolder": "video", "type": "output"}]}
            }}}
            """;
        List<ComfyOutput> outputs = parse(history);
        assertEquals(1, outputs.size());
        assertEquals("nested.mp4", outputs.get(0).fileName());
    }

    @Test
    @DisplayName("没有输出的成功执行返回空列表（不伪装成成功）")
    void emptyOutputsStayEmpty() throws Exception {
        assertTrue(parse("{\"prompt-id-1\": {\"outputs\": {}}}").isEmpty());
    }

    private String describeFailure(String messagesJson) throws Exception {
        HttpComfyClient client = new HttpComfyClient("http://192.168.2.223:8188", MAPPER, false);
        return client.describeExecutionFailure(MAPPER.readTree(messagesJson));
    }

    @Test
    @DisplayName("回归：节点执行异常必须报出节点与异常信息，而不是笼统的「执行失败」")
    void executionErrorCarriesNodeAndException() throws Exception {
        // 形状取自 ComfyUI 0.35 的 status.messages 实际结构。
        String message = describeFailure("""
            [["execution_start", {"prompt_id": "p1"}],
             ["execution_error", {"prompt_id": "p1", "node_id": "5",
                "node_type": "MiniMaxH3Director",
                "exception_type": "torch.OutOfMemoryError",
                "exception_message": "CUDA out of memory. Tried to allocate 2.00 GiB"}]]
            """);
        assertTrue(message.contains("MiniMaxH3Director"), "应含节点类型，实际：" + message);
        assertTrue(message.contains("#5"), "应含节点 id，实际：" + message);
        assertTrue(message.contains("OutOfMemoryError"), "应含异常类型，实际：" + message);
        assertTrue(message.contains("out of memory"), "应含异常信息，实际：" + message);
    }

    @Test
    @DisplayName("实测场景：被中断（显存不足常见形态）要提示节点与可能原因")
    void interruptedExecutionPointsAtNode() throws Exception {
        // 这是 2026-09-17 生产上真实发生的形态：A100 空闲显存仅 14% 时，
        // H3 在 MiniMaxH3Director 节点被中断，此前只记「ComfyUI 执行失败」，
        // 无法判断是显存问题。
        String message = describeFailure("""
            [["execution_start", {"prompt_id": "8fe3732a"}],
             ["execution_cached", {"nodes": ["1", "2"]}],
             ["execution_interrupted", {"prompt_id": "8fe3732a", "node_id": "5",
                "node_type": "MiniMaxH3Director", "executed": []}]]
            """);
        assertTrue(message.contains("MiniMaxH3Director"), "应含节点类型，实际：" + message);
        assertTrue(message.contains("#5"), "应含节点 id，实际：" + message);
        assertTrue(message.contains("显存"), "应提示显存等可能原因，实际：" + message);
    }

    @Test
    @DisplayName("messages 缺失或形状异常时降级为通用文案，不抛异常也不返回空")
    void failureDescriptionDegradesGracefully() throws Exception {
        assertEquals("ComfyUI 执行失败", describeFailure("[]"));
        assertEquals("ComfyUI 执行失败", describeFailure("[[\"execution_start\", {}]]"));
        HttpComfyClient client = new HttpComfyClient("http://192.168.2.223:8188", MAPPER, false);
        assertEquals("ComfyUI 执行失败", client.describeExecutionFailure(null));
    }
}
