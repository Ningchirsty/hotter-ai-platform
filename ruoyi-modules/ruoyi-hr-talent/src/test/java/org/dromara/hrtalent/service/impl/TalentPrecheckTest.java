package org.dromara.hrtalent.service.impl;

import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;
import org.dromara.hrtalent.domainservice.TalentDuplicateDomainService;
import org.dromara.hrtalent.enums.DuplicateMatchLevelEnum;
import org.dromara.hrtalent.mapper.DuplicatePrecheckParams;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.support.TalentContactCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人才重复预检分级单元测试（SPEC-P3 §3.1、设计文档 §8.4 / §21.15）。
 *
 * <p>覆盖分级口径与硬约束：</p>
 * <ul>
 *     <li>强匹配：电话哈希或邮箱哈希命中；</li>
 *     <li>中匹配：姓名 +（公司 或 学校 或 简历哈希）；</li>
 *     <li>弱匹配：姓名 + 期望岗位；</li>
 *     <li><b>姓名单独命中不构成任何级别</b>（不得以姓名作为唯一判断条件）；</li>
 *     <li>命中项只回可展示摘要（脱敏电话/邮箱，不含哈希）。</li>
 * </ul>
 *
 * <p><b>说明</b>：Mapper 替身使用 JDK 动态代理手工构造，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class TalentPrecheckTest {

    /**
     * 预检返回的模拟命中行。
     */
    private final List<TalentProfileMapper.PrecheckRow> rows = new ArrayList<>();

    /**
     * 最近一次查询实际使用的参数（用于校验规范化结果）。
     */
    private DuplicatePrecheckParams lastParams;

    /**
     * 被测领域服务。
     */
    private TalentDuplicateDomainService service;

    @BeforeEach
    void setUp() {
        rows.clear();
        lastParams = null;
        service = new TalentDuplicateDomainService(mapperStub());
    }

    /**
     * 构造人才主档 Mapper 的动态代理替身。
     *
     * <p>只实现 {@code selectPrecheckRows}（返回内存行并记录入参），
     * 其余方法返回类型默认值，保证不访问数据库。</p>
     *
     * @return Mapper 替身
     */
    private TalentProfileMapper mapperStub() {
        return (TalentProfileMapper) Proxy.newProxyInstance(
            TalentProfileMapper.class.getClassLoader(),
            new Class<?>[]{TalentProfileMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectPrecheckRows" -> {
                    lastParams = (DuplicatePrecheckParams) args[0];
                    yield new ArrayList<>(rows);
                }
                case "toString" -> "TalentProfileMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 取返回类型的默认值。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (char.class.equals(type)) {
            return (char) 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (double.class.equals(type)) {
            return 0D;
        }
        if (float.class.equals(type)) {
            return 0F;
        }
        return 0;
    }

    /**
     * 构造一条命中行。
     *
     * @param talentId        人才ID
     * @param name            姓名
     * @param phonePlain      电话明文（写入行的 phoneCipher，出参拦截器在真实链路解密）
     * @param emailPlain      邮箱明文
     * @param company         当前公司
     * @param position        期望岗位
     * @param phoneHash       电话哈希
     * @param emailHash       邮箱哈希
     * @return 命中行
     */
    private TalentProfileMapper.PrecheckRow row(Long talentId, String name, String phonePlain, String emailPlain,
                                                String company, String position, String phoneHash, String emailHash) {
        TalentProfileMapper.PrecheckRow row = new TalentProfileMapper.PrecheckRow();
        row.setTalentId(talentId);
        row.setTalentNo("TAL" + talentId);
        row.setName(name);
        row.setPhoneCipher(phonePlain);
        row.setEmailCipher(emailPlain);
        row.setCurrentCompany(company);
        row.setCurrentCity("上海");
        row.setExpectedPosition(position);
        row.setTalentStatus("active");
        row.setOwnerId(1L);
        row.setOwnerDeptId(2L);
        row.setPhoneHash(phoneHash);
        row.setEmailHash(emailHash);
        row.setCreateTime(LocalDateTime.now());
        return row;
    }

    @Test
    @DisplayName("电话哈希命中判为强匹配")
    void shouldClassifyPhoneHitAsStrong() {
        String phoneHash = TalentContactCodec.phoneHash("13800138000");
        rows.add(row(1001L, "张三", "13800138000", "zhangsan@x.com", "甲公司", "后端工程师", phoneHash, null));

        TalentPrecheckVo result = service.precheck("张三", "13800138000", null, null, null, null, null);

        assertTrue(result.isStrongDuplicated());
        assertFalse(result.isMediumDuplicated());
        assertFalse(result.isWeakDuplicated());
        assertTrue(result.isDuplicated());
        assertEquals(1, result.getStrongMatches().size());
        assertEquals(DuplicateMatchLevelEnum.STRONG.getCode(), result.getStrongMatches().get(0).getMatchLevel());
        assertEquals("电话命中", result.getStrongMatches().get(0).getReason());
        assertEquals("138****8000", result.getStrongMatches().get(0).getPhoneMasked());
        assertNotNull(result.getMessage());
    }

    @Test
    @DisplayName("邮箱哈希命中判为强匹配")
    void shouldClassifyEmailHitAsStrong() {
        String emailHash = TalentContactCodec.emailHash("ZhangSan@X.com");
        rows.add(row(1002L, "李四", "13900139000", "zhangsan@x.com", null, null, null, emailHash));

        TalentPrecheckVo result = service.precheck("李四", null, "zhangsan@x.com", null, null, null, null);

        assertTrue(result.isStrongDuplicated());
        assertEquals(1, result.getStrongMatches().size());
        assertEquals("邮箱命中", result.getStrongMatches().get(0).getReason());
    }

    @Test
    @DisplayName("姓名 + 当前公司命中判为中匹配")
    void shouldClassifyNameAndCompanyAsMedium() {
        rows.add(row(1003L, "王五", null, null, "乙公司", "产品经理", null, null));

        TalentPrecheckVo result = service.precheck("王五", null, null, "乙公司", null, null, null);

        assertFalse(result.isStrongDuplicated());
        assertTrue(result.isMediumDuplicated());
        assertEquals(1, result.getMediumMatches().size());
        assertEquals(DuplicateMatchLevelEnum.MEDIUM.getCode(), result.getMediumMatches().get(0).getMatchLevel());
        assertTrue(result.getMediumMatches().get(0).getReason().contains("当前公司命中"));
        assertTrue(service.needManualDispose(result));
    }

    @Test
    @DisplayName("姓名 + 学校命中判为中匹配")
    void shouldClassifyNameAndSchoolAsMedium() {
        rows.add(row(1004L, "赵六", null, null, null, null, null, null));

        TalentPrecheckVo result = service.precheck("赵六", null, null, null, "某某大学", null, null);

        assertTrue(result.isMediumDuplicated());
        assertTrue(result.getMediumMatches().get(0).getReason().contains("学校命中"));
    }

    @Test
    @DisplayName("姓名 + 简历哈希命中判为中匹配")
    void shouldClassifyNameAndResumeHashAsMedium() {
        rows.add(row(1005L, "钱七", null, null, null, null, null, null));

        TalentPrecheckVo result = service.precheck("钱七", null, null, null, null, "a".repeat(64), null);

        assertTrue(result.isMediumDuplicated());
        assertTrue(result.getMediumMatches().get(0).getReason().contains("简历哈希命中"));
    }

    @Test
    @DisplayName("非法简历哈希被忽略，不参与中匹配")
    void shouldIgnoreInvalidResumeHash() {
        rows.add(row(1006L, "孙八", null, null, null, null, null, null));

        TalentPrecheckVo result = service.precheck("孙八", null, null, null, null, "not-a-hash", null);

        assertFalse(result.isDuplicated());
        assertNull(lastParams.resumeHash());
    }

    @Test
    @DisplayName("姓名 + 期望岗位命中判为弱匹配，且不阻塞创建")
    void shouldClassifyNameAndPositionAsWeak() {
        rows.add(row(1007L, "周九", null, null, null, "Java开发", null, null));

        TalentPrecheckVo result = service.precheck("周九", null, null, null, null, null, "Java开发");

        assertFalse(result.isStrongDuplicated());
        assertFalse(result.isMediumDuplicated());
        assertTrue(result.isWeakDuplicated());
        assertEquals(1, result.getWeakMatches().size());
        assertEquals(DuplicateMatchLevelEnum.WEAK.getCode(), result.getWeakMatches().get(0).getMatchLevel());
        assertTrue(result.getWeakMatches().get(0).getReason().contains("期望岗位命中"));
        assertFalse(service.needManualDispose(result));
    }

    @Test
    @DisplayName("仅姓名相同不构成任何级别匹配")
    void shouldNotMatchByNameAlone() {
        rows.add(row(1008L, "吴十", null, null, "丙公司", "测试工程师", null, null));

        TalentPrecheckVo result = service.precheck("吴十", null, null, "丁公司", null, null, "运维工程师");

        assertFalse(result.isDuplicated());
        assertFalse(result.isStrongDuplicated());
        assertFalse(result.isMediumDuplicated());
        assertFalse(result.isWeakDuplicated());
        assertNull(result.getMessage());
        assertFalse(service.needManualDispose(result));
    }

    @Test
    @DisplayName("无任何命中时返回空结果")
    void shouldReturnEmptyWhenNoRows() {
        TalentPrecheckVo result = service.precheck("郑十一", "13700137000", "zheng@x.com", null, null, null, null);

        assertFalse(result.isDuplicated());
        assertTrue(result.getStrongMatches().isEmpty());
        assertTrue(result.getMediumMatches().isEmpty());
        assertTrue(result.getWeakMatches().isEmpty());
        assertEquals(TalentContactCodec.phoneHash("13700137000"), result.getNormalizedPhoneHash());
        assertEquals(TalentContactCodec.emailHash("zheng@x.com"), result.getNormalizedEmailHash());
    }

    @Test
    @DisplayName("电话规范化：11 位手机号补 +86 后哈希一致")
    void shouldNormalizePhoneBeforeHash() {
        assertEquals(TalentContactCodec.phoneHash("+8613800138000"), TalentContactCodec.phoneHash("138-0013-8000"));
        assertEquals(TalentContactCodec.phoneHash("+8613800138000"), TalentContactCodec.phoneHash(" 13800138000 "));
        assertEquals(TalentContactCodec.emailHash("a.b@x.com"), TalentContactCodec.emailHash(" A.B@X.com "));
    }

    @Test
    @DisplayName("命中项只回可展示摘要，不含哈希与密文")
    void shouldReturnOnlySummaryFields() {
        String phoneHash = TalentContactCodec.phoneHash("13800138000");
        rows.add(row(1009L, "冯十二", "13800138000", "feng@x.com", "戊公司", "架构师", phoneHash, null));

        TalentPrecheckVo result = service.precheck("冯十二", "13800138000", "feng@x.com", "戊公司", null, null, "架构师");

        assertTrue(result.isStrongDuplicated());
        TalentPrecheckVo.TalentPrecheckItemVo item = result.getStrongMatches().get(0);
        assertEquals(1009L, item.getTalentId());
        assertEquals("TAL1009", item.getTalentNo());
        assertEquals("138****8000", item.getPhoneMasked());
        assertEquals("f***@x.com", item.getEmailMasked());
        assertFalse(item.getPhoneMasked().contains("13800138000"));
        assertFalse(item.getPhoneMasked().contains(phoneHash));
    }

}
