package org.dromara.creative.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「品牌要求没进提示词」的留痕（内测 S13）。
 *
 * <p><b>为什么值得单独钉</b>：品牌要求分两路进词——必显信息 / 主推卖点进<b>正向</b>，
 * 禁用词进<b>负向</b>。所以"人工写了正向"和"人工写了负向"漏掉的是不同的东西。
 * 第一版把这条提示挂在"正负都人工指定"的分支上，实测漏掉了最常见的那种
 * （只人工写正向）——**界面上什么都没提示，而必显信息确实没进词**。</p>
 *
 * @author creative
 */
class CreativePromptOmissionNotesTest {

    @Test
    @DisplayName("两侧都自动派生：没有额外说明（长度截断那些由 DnaPromptBuilder 负责）")
    void bothDerivedMeansNoNote() {
        assertTrue(CreativeGenerationServiceImpl.promptOmissionNotes(null, null).isEmpty());
        assertTrue(CreativeGenerationServiceImpl.promptOmissionNotes("  ", "").isEmpty(),
            "空白串等同于没填，不该报'人工指定'");
    }

    @Test
    @DisplayName("只人工写了正向：必须提示必显信息/主推卖点没进词（第一版漏掉的就是这一种）")
    void humanPositiveOnly() {
        List<String> notes = CreativeGenerationServiceImpl.promptOmissionNotes("我自己写的提示词", null);
        assertEquals(1, notes.size(), notes.toString());
        assertTrue(notes.get(0).contains("必显信息"), notes.get(0));
        assertTrue(notes.get(0).contains("主推卖点"), notes.get(0));
    }

    @Test
    @DisplayName("只人工写了负向：必须提示禁用词没进词（红线那一路）")
    void humanNegativeOnly() {
        List<String> notes = CreativeGenerationServiceImpl.promptOmissionNotes(null, "我自己写的负向词");
        assertEquals(1, notes.size(), notes.toString());
        assertTrue(notes.get(0).contains("禁用词"), notes.get(0));
    }

    @Test
    @DisplayName("两侧都人工写：两条都要说清（不能合成一句含糊的'品牌要求没生效'）")
    void humanBothSides() {
        List<String> notes = CreativeGenerationServiceImpl.promptOmissionNotes("正向", "负向");
        assertEquals(2, notes.size(), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("必显信息")), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("禁用词")), notes.toString());
    }
}
