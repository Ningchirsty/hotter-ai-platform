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
}
