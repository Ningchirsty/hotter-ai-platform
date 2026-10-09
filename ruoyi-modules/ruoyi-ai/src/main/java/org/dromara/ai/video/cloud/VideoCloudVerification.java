package org.dromara.ai.video.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.exception.VideoTaskException;
import java.nio.file.*;
import java.util.*;

/** 成片验收记录及一次性付费额度，先原子落盘再调用供应商。 */
public class VideoCloudVerification {
    public record Attempt(long taskId, String variant) { }
    public record State(Map<String, Attempt> attempts, Set<String> verified) { }
    private final VideoCloudProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private State state = new State(new LinkedHashMap<>(), new LinkedHashSet<>());
    public VideoCloudVerification(VideoCloudProperties properties) {
        this.properties = properties;
        String file = properties.getVerificationFile();
        if (file != null && !file.isBlank() && Files.exists(Path.of(file))) {
            try { state = mapper.readValue(Files.readAllBytes(Path.of(file)), State.class); }
            catch (Exception e) { throw new IllegalStateException("云端视频验收记录无法读取，禁止重新计算付费额度", e); }
        }
    }
    public static String variant(CloudVideoRequest r) {
        return String.join("|", r.model(), r.capability(), String.valueOf(r.seconds()), r.resolution(), r.ratio(), String.valueOf(Boolean.TRUE.equals(r.generateAudio())));
    }
    private boolean advancedUntested(CloudVideoRequest r) {
        return r.seed() != null || (r.negativePrompt() != null && !r.negativePrompt().isBlank());
    }
    public synchronized boolean verified(CloudVideoRequest r) {
        return !advancedUntested(r) && verifiedVariants().contains(variant(r));
    }
    public synchronized Set<String> verifiedVariants() {
        Set<String> result = new LinkedHashSet<>(state.verified());
        if (properties.getVerifiedVariants() != null) result.addAll(properties.getVerifiedVariants());
        return Set.copyOf(result);
    }
    public synchronized List<String> candidates(long user) {
        if (!validationActor(user) || remaining() == 0 || properties.getValidationVariants() == null) return List.of();
        return properties.getValidationVariants().stream().filter(v -> !state.attempts().containsKey(v.split("\\|", -1)[0])).toList();
    }
    private boolean validationActor(long user) {
        return properties.getValidationUserIds() != null && properties.getValidationUserIds().contains(user)
            && properties.getVerificationFile() != null && !properties.getVerificationFile().isBlank();
    }
    public synchronized int remaining() { return Math.max(0, properties.getValidationLimit() - state.attempts().size()); }
    public synchronized void requireAllowed(CloudVideoRequest request, long user) {
        if (!verified(request) && (advancedUntested(request) || !candidates(user).contains(variant(request))))
            throw new VideoTaskException("CLOUD_UNVERIFIED", "当前创作能力与输出参数组合尚未通过成片验收");
    }
    /** 恢复已有远端任务只允许已验收组合或属于原验收操作者的原任务。 */
    public synchronized void requireRecovery(CloudVideoRequest request, long user, long taskId) {
        Attempt attempt = state.attempts().get(request.model());
        if (advancedUntested(request) || (!verified(request) &&
            (!validationActor(user) || attempt == null || attempt.taskId() != taskId || !attempt.variant().equals(variant(request)))))
            throw new VideoTaskException("CLOUD_UNVERIFIED", "该任务不具备恢复成片的验收记录");
    }
    /** 与正常已验收生成隔离；同一型号在本批次最多一次。 */
    public synchronized void reserve(CloudVideoRequest request, long user, long taskId) {
        if (verified(request)) return;
        requireAllowed(request, user);
        Map<String, Attempt> attempts = new LinkedHashMap<>(state.attempts());
        attempts.put(request.model(), new Attempt(taskId, variant(request)));
        persist(new State(attempts, new LinkedHashSet<>(state.verified())));
    }
    public synchronized void passed(CloudVideoRequest request, long taskId) {
        Attempt attempt = state.attempts().get(request.model());
        if (attempt == null || attempt.taskId() != taskId || !attempt.variant().equals(variant(request))) return;
        Set<String> verified = new LinkedHashSet<>(state.verified());
        verified.add(variant(request));
        persist(new State(new LinkedHashMap<>(state.attempts()), verified));
    }
    private void persist(State next) {
        try {
            Path file = Path.of(properties.getVerificationFile()).toAbsolutePath();
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "video-verification-", ".json");
            try {
                try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    var buffer=java.nio.ByteBuffer.wrap(mapper.writeValueAsBytes(next));
                    while(buffer.hasRemaining())channel.write(buffer);
                    channel.force(true);
                }
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                state = next;
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception e) { throw new VideoTaskException("CLOUD_VERIFICATION_STORE_FAILED", "验收额度无法安全保存，未继续调用供应商"); }
    }
}
