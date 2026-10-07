package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.bo.AigModelSecretBatchBo;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.aigov.helper.AigPermissionHelper;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.invoker.ModelConnectionTester;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 「同一供应商共用一把 Key」批量录入的行为锁定测试。
 *
 * <p><b>为什么必须单独测这一段</b>：批量的风险不在「少写了几行」这类显性失败，
 * 而在两处静默错误：</p>
 * <ul>
 *     <li><b>加密次数</b>：如果实现被改成「每行各加密一次」，功能上看不出任何差别，
 *         但同一段明文会有 N 段不同的密文（一旦将来换成随机 IV 就会立刻不同）。
 *         本测试直接锁定「encrypt 恰好调用一次」。</li>
 *     <li><b>作用范围越界</b>：请求里混进别家供应商的模型 ID 时，若只做「按 ID 更新」，
 *         一次误传就会把 B 家的凭据覆盖成 A 家的 Key，而 B 家直到调用失败才暴露。
 *         本测试锁定「整批拒绝且一行都不写」。</li>
 * </ul>
 *
 * <p>测试为纯 Mockito：不加载 Spring 上下文，也不碰真实数据库。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigModelGovernanceServiceImplBatchSecretTest {

    /**
     * 被批量覆盖的供应商（其下 3 个模型共用一把 Key）。
     */
    private static final Long PROVIDER_ID = 1001L;

    /**
     * 该供应商下已登记的模型ID（含一个停用模型——停用也应一起写，否则重新启用时带旧 Key）。
     */
    private static final List<Long> OWNED_IDS = List.of(11L, 12L, 13L);

    /**
     * 另一家供应商的模型ID：用于验证越界拒绝。
     */
    private static final Long FOREIGN_ID = 99L;

    private static final String PLAIN_KEY = "sk-plain-batch-secret";
    private static final String CIPHER_KEY = "CIPHER-BASE64";

    private AigModelConfigMapper modelConfigMapper;
    private AigPermissionHelper permissionHelper;
    private AigModelSecretCipher secretCipher;
    private AigModelGovernanceServiceImpl service;

    @BeforeEach
    void setUp() {
        AigModelViewMapper modelViewMapper = mock(AigModelViewMapper.class);
        modelConfigMapper = mock(AigModelConfigMapper.class);
        AigModelGovernanceMapper modelGovernanceMapper = mock(AigModelGovernanceMapper.class);
        permissionHelper = mock(AigPermissionHelper.class);
        ModelConnectionTester connectionTester = mock(ModelConnectionTester.class);
        secretCipher = mock(AigModelSecretCipher.class);
        // 构造顺序即字段声明顺序（Lombok @RequiredArgsConstructor）
        service = new AigModelGovernanceServiceImpl(modelViewMapper, modelConfigMapper, modelGovernanceMapper,
            permissionHelper, connectionTester, secretCipher);
    }

    /**
     * 放行权限、放行存在性校验，并给出该供应商名下的模型ID。
     */
    private void stubProviderWithModels() {
        when(permissionHelper.canViewModelSecret()).thenReturn(true);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(1);
        when(modelConfigMapper.selectModelIdsByProvider(PROVIDER_ID)).thenReturn(OWNED_IDS);
        when(secretCipher.encrypt(PLAIN_KEY)).thenReturn(CIPHER_KEY);
    }

    private static AigModelSecretBatchBo bo(Long providerId, List<Long> modelIds) {
        AigModelSecretBatchBo bo = new AigModelSecretBatchBo();
        bo.setProviderId(providerId);
        bo.setModelIds(modelIds);
        return bo;
    }

    @Test
    @DisplayName("modelIds 为空：作用于该供应商下全部模型，且明文只加密一次")
    @SuppressWarnings("unchecked")
    void appliesToAllModelsOfProviderWithSingleEncryption() {
        stubProviderWithModels();
        when(modelConfigMapper.updateModelApiKeyBatch(OWNED_IDS, CIPHER_KEY)).thenReturn(3);
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, null);
        bo.setApiKey(PLAIN_KEY);

        int rows = service.applyProviderSecret(bo);

        assertEquals(3, rows, "应返回实际影响行数");
        // 加密恰好一次：这是「共享一把 Key」的实现承诺，不是偶然
        verify(secretCipher, times(1)).encrypt(PLAIN_KEY);
        // 所有目标行写入同一段密文——而不是各写各的
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(modelConfigMapper).updateModelApiKeyBatch(idsCaptor.capture(), eq(CIPHER_KEY));
        assertEquals(OWNED_IDS, idsCaptor.getValue(), "空 modelIds 必须展开为该供应商下的全部模型");
    }

    @Test
    @DisplayName("写入的是密文：落库参数绝不能等于明文")
    void writesCipherTextNeverPlainText() {
        stubProviderWithModels();
        when(modelConfigMapper.updateModelApiKeyBatch(anyList(), any())).thenReturn(3);
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, null);
        bo.setApiKey(PLAIN_KEY);

        service.applyProviderSecret(bo);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(modelConfigMapper).updateModelApiKeyBatch(anyList(), keyCaptor.capture());
        assertEquals(CIPHER_KEY, keyCaptor.getValue());
        assertNotEquals(PLAIN_KEY, keyCaptor.getValue(), "明文绝不能落库");
    }

    @Test
    @DisplayName("显式 modelIds：只作用于列出的子集")
    void respectsExplicitSubset() {
        stubProviderWithModels();
        List<Long> subset = List.of(11L, 13L);
        when(modelConfigMapper.updateModelApiKeyBatch(subset, CIPHER_KEY)).thenReturn(2);
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, subset);
        bo.setApiKey(PLAIN_KEY);

        int rows = service.applyProviderSecret(bo);

        assertEquals(2, rows);
        verify(modelConfigMapper).updateModelApiKeyBatch(subset, CIPHER_KEY);
        verify(secretCipher, times(1)).encrypt(PLAIN_KEY);
    }

    @Test
    @DisplayName("modelIds 含别家供应商的模型：整批拒绝，一行都不写")
    void rejectsForeignModelIdsAsWholeBatch() {
        when(permissionHelper.canViewModelSecret()).thenReturn(true);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(1);
        when(modelConfigMapper.selectModelIdsByProvider(PROVIDER_ID)).thenReturn(OWNED_IDS);
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, List.of(11L, FOREIGN_ID));
        bo.setApiKey(PLAIN_KEY);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.applyProviderSecret(bo));

        assertTrue(ex.getMessage().contains(String.valueOf(FOREIGN_ID)),
            "报错必须点名越界的模型ID，便于定位；实际=" + ex.getMessage());
        // 关键：不是「跳过越界项照常写剩下的」——那会留下半批已改的库
        verify(modelConfigMapper, never()).updateModelApiKeyBatch(anyList(), any());
        verify(secretCipher, never()).encrypt(any());
    }

    @Test
    @DisplayName("clearKey=true：写入 null 清除，且完全不加密")
    void clearKeyWritesNullAndNeverEncrypts() {
        when(permissionHelper.canViewModelSecret()).thenReturn(true);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(1);
        when(modelConfigMapper.selectModelIdsByProvider(PROVIDER_ID)).thenReturn(OWNED_IDS);
        when(modelConfigMapper.updateModelApiKeyBatch(OWNED_IDS, null)).thenReturn(3);
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, null);
        bo.setClearKey(true);
        // 明确带上明文：clearKey=true 时必须被忽略，而不是「顺带写进去」
        bo.setApiKey(PLAIN_KEY);

        int rows = service.applyProviderSecret(bo);

        assertEquals(3, rows);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(modelConfigMapper).updateModelApiKeyBatch(eq(OWNED_IDS), keyCaptor.capture());
        assertNull(keyCaptor.getValue(), "清除语义必须写 null，不能是空串或残留密文");
        verify(secretCipher, never()).encrypt(any());
    }

    @Test
    @DisplayName("非 clear 且密钥为空：拒绝而非静默清空")
    void rejectsBlankKeyInsteadOfSilentlyClearing() {
        when(permissionHelper.canViewModelSecret()).thenReturn(true);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(1);
        when(modelConfigMapper.selectModelIdsByProvider(PROVIDER_ID)).thenReturn(OWNED_IDS);
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, null);
        bo.setApiKey("   ");

        assertThrows(ServiceException.class, () -> service.applyProviderSecret(bo));

        // 这是最容易造成事故的路径：一次「忘了填」就抹掉 7 个模型的凭据
        verify(modelConfigMapper, never()).updateModelApiKeyBatch(anyList(), any());
    }

    @Test
    @DisplayName("无 aig:model:secret 权限：拒绝且不触库")
    void rejectsWithoutSecretPermission() {
        when(permissionHelper.canViewModelSecret()).thenReturn(false);
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, null);
        bo.setApiKey(PLAIN_KEY);

        assertThrows(ServiceException.class, () -> service.applyProviderSecret(bo));

        // 批量入口覆盖面比单个模型大，更不能靠控制器注解就算数
        verifyNoInteractions(modelConfigMapper);
        verify(secretCipher, never()).encrypt(any());
    }

    @Test
    @DisplayName("供应商下没有任何模型：拒绝而不是「成功写了 0 行」")
    void rejectsWhenProviderHasNoModels() {
        when(permissionHelper.canViewModelSecret()).thenReturn(true);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(1);
        when(modelConfigMapper.selectModelIdsByProvider(PROVIDER_ID)).thenReturn(List.of());
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, null);
        bo.setApiKey(PLAIN_KEY);

        assertThrows(ServiceException.class, () -> service.applyProviderSecret(bo));

        // 返回 0 行会让界面显示「成功」，管理员误以为密钥已配置
        verify(modelConfigMapper, never()).updateModelApiKeyBatch(anyList(), any());
    }

    @Test
    @DisplayName("供应商不存在：拒绝且不触库")
    void rejectsMissingProvider() {
        when(permissionHelper.canViewModelSecret()).thenReturn(true);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(0);
        AigModelSecretBatchBo bo = bo(PROVIDER_ID, null);
        bo.setApiKey(PLAIN_KEY);

        assertThrows(ServiceException.class, () -> service.applyProviderSecret(bo));

        verify(modelConfigMapper, never()).selectModelIdsByProvider(any());
        verify(modelConfigMapper, never()).updateModelApiKeyBatch(anyList(), any());
    }

    @Test
    @DisplayName("供应商ID为空：拒绝且不触库")
    void rejectsNullProviderId() {
        assertThrows(ServiceException.class, () -> service.applyProviderSecret(bo(null, null)));

        verifyNoInteractions(modelConfigMapper);
        verify(permissionHelper, never()).canViewModelSecret();
    }

}
