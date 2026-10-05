package org.dromara.content.helper;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.mapper.CpTaskFileMapper;

/**
 * 「事实出处」的结构化校验与拼装（内测 S19 / C7-b）。
 *
 * <p><b>为什么要把自由文本换成"选资料"</b>：C7 只要求填写出处，但自由文本可以被填成
 * {@code -} 或 {@code 见资料}——形式满足、追溯失效。而事实一旦落 {@code CONFIRMED}，
 * 就会随开工包交给设计侧，成为下游唯一能看到的"这条值依据什么"。
 * 现在起，出处必须能指到<b>本任务的一份具体资料</b>（可点开、可核对），
 * 位置（页/行）仍是可选的自由文本——那部分本来就没有稳定结构。</p>
 *
 * <p><b>为什么校验与拼装只放一处</b>：产线里有<b>两个</b>入口会写入人工事实
 * （内容任务页的「手工录入事实」、互动确认卡上的「填写其他值」）。
 * 两处各写一份校验，迟早出现"这边查了文件归属、那边没查"——
 * 那正是内测 C7 时踩过的形状（只改了一个入口 = 等于没改）。</p>
 *
 * @author content
 */
public final class ContentFactOrigin {

    private ContentFactOrigin() {
    }

    /**
     * 校验所选资料属于本任务，并返回它。
     *
     * @param taskId         任务ID
     * @param sourceFileId   所选资料ID
     * @param taskFileMapper 附件 Mapper
     * @param action         本次动作的中文名（如「手工录入事实」「填写其他值」），用于把话说具体
     * @return 附件行
     * @throws ServiceException 未选资料，或所选资料不属于本任务
     */
    public static CpTaskFile requireFile(Long taskId, Long sourceFileId, CpTaskFileMapper taskFileMapper,
                                        String action) {
        if (sourceFileId == null) {
            throw new ServiceException("请选择「事实出处」：这条值是从哪份任务资料里看到的。"
                + "手工录入的值直接标记为已确认，开工包会把出处原样交给设计侧，"
                + "所以出处必须能指到本任务的一份具体资料（不能只写个「见资料」）。");
        }
        CpTaskFile file = taskFileMapper.selectById(sourceFileId);
        if (file == null || !taskId.equals(file.getTaskId())) {
            // 与"确认别人的产物"同一类问题：跨任务引用既可能是误操作，也可能是越权读取
            throw new ServiceException("所选资料不属于本任务（" + action + "），请从本任务的资料列表里选择");
        }
        return file;
    }

    /**
     * 拼装入库用的出处文案：{@code 文件名 · 位置}。
     *
     * <p>结构化信息（{@code sourceFileId}）会单独存字段；这里拼的这份是给**人和下游**看的，
     * 因为开工包里交给设计侧的就是这个字符串——只有位置没有文件名，等于没说清出处。</p>
     *
     * @param file   资料
     * @param detail 位置说明（可空，如「第 3 行」「第 2 页参数表」）
     * @return 形如「产品参数表 V2.xlsx · 第 3 行」
     */
    public static String describe(CpTaskFile file, String detail) {
        String name = StringUtils.blankToDefault(file.getFileName(), "任务资料 #" + file.getFileId());
        return StringUtils.isBlank(detail) ? name : name + " · " + detail.trim();
    }
}
