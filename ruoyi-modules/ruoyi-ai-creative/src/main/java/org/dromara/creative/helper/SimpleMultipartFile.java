package org.dromara.creative.helper;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 内存版 {@link MultipartFile}：把已经在内存里的字节当成一次「上传」交给下游服务。
 *
 * <p><b>为什么需要它</b>：内容模块的成品一致性检查入口是
 * {@code run(taskId, referenceFileId, referenceFile, resultFile, remark)}，
 * 成品图参数是 {@code MultipartFile}——它内部会顺带把成品图登记为任务附件
 * （{@code cp_task_file}），这正是视觉工厂需要的「产出登记」。而视觉工厂手里只有
 * 内核素材的字节，也不想为了调一次服务去写临时文件或起一次 HTTP，
 * 因此用这个极薄适配器把字节包成 MultipartFile，<b>复用整条检查与登记链路</b>。</p>
 *
 * <p>注意：{@code spring-test} 的 {@code MockMultipartFile} 只在测试作用域可用，
 * 生产代码不能用它——这是本类存在的直接原因。</p>
 *
 * @author creative
 */
public class SimpleMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    /**
     * @param name             表单字段名（固定 file 即可）
     * @param originalFilename 原始文件名（下游按扩展名推断类型，务必带正确后缀）
     * @param contentType      MIME
     * @param content          字节
     */
    public SimpleMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content == null ? new byte[0] : content;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws java.io.IOException {
        Files.write(dest.toPath(), content);
    }

}
