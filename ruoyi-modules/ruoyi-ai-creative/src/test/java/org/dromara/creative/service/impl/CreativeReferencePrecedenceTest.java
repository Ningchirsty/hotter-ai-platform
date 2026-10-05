package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出图参考图的优先级（内测 S4 的收口）。
 *
 * <p><b>原缺陷</b>：兜底是"取最新一张图片附件"，不看 {@code source_type}。
 * 而 {@code source_type} 是"这张图是干什么用的"的唯一声明，{@code createTime} 只说明"谁后传的"——
 * 拿后者当判据，等于让上传顺序决定出图输入：后传的一张产品图会把参考图顶掉，
 * 而用户看到的是"我明明选了参考图"。</p>
 *
 * <p>钉住三档优先级与两个反例方向：<b>显式指定必须被尊重</b>（指定了不存在的要报错，
 * 不能悄悄换一张）、<b>有 REFERENCE 时不许被更新的普通图片顶掉</b>、
 * <b>一张 REFERENCE 都没有时仍要能出图</b>（兜底回退到最新图片，不能因为"没标注角色"就拒绝）。</p>
 *
 * @author creative
 */
class CreativeReferencePrecedenceTest {

    private static final long TASK_ID = 42L;

    private static CpTaskFileVo file(long id, String kind, String sourceType, int minute) {
        CpTaskFileVo vo = new CpTaskFileVo();
        vo.setFileId(id);
        vo.setFileKind(kind);
        vo.setSourceType(sourceType);
        vo.setFileName("f" + id);
        vo.setCreateTime(LocalDateTime.of(2026, 10, 1, 10, minute));
        return vo;
    }

    @Test
    @DisplayName("显式指定优先：选中哪张就用哪张（哪怕它不是最新的）")
    void explicitSelectionWins() {
        CpTaskFileVo older = file(1L, "IMAGE", "UPLOAD", 1);
        CpTaskFileVo newer = file(2L, "IMAGE", "REFERENCE", 9);

        CpTaskFileVo picked = CreativeGenerationServiceImpl.resolveReference(
            TASK_ID, 1L, List.of(older, newer));

        assertEquals(1L, picked.getFileId(), "显式指定被忽略 = 内测 S4 的原形");
    }

    @Test
    @DisplayName("显式指定了不存在的附件：报错，而不是悄悄换一张")
    void explicitSelectionMustExist() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> CreativeGenerationServiceImpl.resolveReference(TASK_ID, 999L,
                List.of(file(1L, "IMAGE", "REFERENCE", 1))));
        assertTrue(ex.getMessage().contains("999"), ex.getMessage());
    }

    @Test
    @DisplayName("未指定时优先「被登记为参考图」的附件，即使有更新的普通图片")
    void markedReferenceBeatsNewerPlainImage() {
        CpTaskFileVo markedReference = file(1L, "IMAGE", "REFERENCE", 1);
        CpTaskFileVo newerProductImage = file(2L, "IMAGE", "PRODUCT", 9);
        CpTaskFileVo newerUpload = file(3L, "IMAGE", "UPLOAD", 8);

        CpTaskFileVo picked = CreativeGenerationServiceImpl.resolveReference(
            TASK_ID, null, List.of(markedReference, newerProductImage, newerUpload));

        assertEquals(1L, picked.getFileId(),
            "后传的产品图把参考图顶掉了——这正是上传顺序决定出图输入的缺陷");
    }

    @Test
    @DisplayName("没有登记过参考图：回退到最新一张图片（不能因为没标注就不给用）")
    void fallsBackToNewestImage() {
        CpTaskFileVo older = file(1L, "IMAGE", "UPLOAD", 1);
        CpTaskFileVo newer = file(2L, "IMAGE", null, 5);

        CpTaskFileVo picked = CreativeGenerationServiceImpl.resolveReference(
            TASK_ID, null, List.of(older, newer));

        assertEquals(2L, picked.getFileId());
    }

    @Test
    @DisplayName("非图片附件不参与挑选；一张图都没有时明确报错")
    void nonImagesAreIgnoredAndEmptyFails() {
        CpTaskFileVo pdf = file(1L, "PDF", "UPLOAD", 9);
        assertThrows(ServiceException.class,
            () -> CreativeGenerationServiceImpl.resolveReference(TASK_ID, null, List.of(pdf)));
    }
}
