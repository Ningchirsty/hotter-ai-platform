package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.bo.AigModelCreateBo;
import org.dromara.aigov.domain.bo.AigModelGovernanceBo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.helper.AigPermissionHelper;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.invoker.ModelConnectionTester;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 密钥「查看 vs 写入」边界（设计 §5.4 ②）。
 *
 * <p>回归的是走读里点名的那条错：原先 {@code aig:model:secret} 一个权限点同时管读和写，
 * 而菜单名写的是「查看密钥引用」——于是"只授了查看"的人实际能把凭据改成自控 KMS
 * 或他人引用。拆成 {@code :view} / {@code :edit} 之后，写侧必须以 {@code :edit} 为准。</p>
 *
 * <p>本类只覆盖"有 view、没有 edit"这一种最容易被写回的错：只要仍然按 view 判写，
 * 这几个用例就会红。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigModelGovernanceServiceImplSecretBoundaryTest {

    private static final Long MODEL_ID = 9001L;
    private static final Long PROVIDER_ID = 7001L;

    private AigModelViewMapper modelViewMapper;
    private AigModelConfigMapper modelConfigMapper;
    private AigPermissionHelper permissionHelper;
    private AigModelGovernanceMapper modelGovernanceMapper;
    private AigModelSecretCipher secretCipher;
    private AigModelGovernanceServiceImpl service;

    @BeforeEach
    void setUp() {
        modelViewMapper = mock(AigModelViewMapper.class);
        modelConfigMapper = mock(AigModelConfigMapper.class);
        modelGovernanceMapper = mock(AigModelGovernanceMapper.class);
        permissionHelper = mock(AigPermissionHelper.class);
        ModelConnectionTester connectionTester = mock(ModelConnectionTester.class);
        secretCipher = mock(AigModelSecretCipher.class);
        service = new AigModelGovernanceServiceImpl(modelViewMapper, modelConfigMapper, modelGovernanceMapper,
            permissionHelper, connectionTester, secretCipher);
    }

    /**
     * 关键桩：能看脱敏引用（view），但不能写（edit）。
     */
    private void stubViewOnly() {
        when(permissionHelper.canViewModelSecret()).thenReturn(true);
        when(permissionHelper.canEditModelSecret()).thenReturn(false);
    }

    @Test
    @DisplayName("有 view 无 edit：保存治理属性时不得改写密钥引用")
    void saveGovernanceRejectsSecretRefWithoutEditPermission() {
        stubViewOnly();
        AigModelVo existing = new AigModelVo();
        existing.setModelId(MODEL_ID);
        when(modelViewMapper.selectModelById(MODEL_ID)).thenReturn(existing);
        AigModelGovernanceBo bo = new AigModelGovernanceBo();
        bo.setModelId(MODEL_ID);
        bo.setSecretRef("kms://ai/attacker-owned");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveGovernance(bo));

        assertTrue(ex.getMessage().contains("aig:model:secret:edit"),
            "报错必须点名 :edit，否则运维会以为只要补 view 就够了；实际=" + ex.getMessage());
        // 一条都不得落库：把引用指向自控 KMS 属于凭据外流，不能"写一半"
        verifyNoInteractions(modelGovernanceMapper);
    }

    @Test
    @DisplayName("有 view 无 edit：新增模型时不得登记密钥引用")
    void createModelRejectsSecretRefWithoutEditPermission() {
        stubViewOnly();
        when(modelConfigMapper.countByModelKey(anyString())).thenReturn(0);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(1);
        AigModelCreateBo bo = createBo();
        bo.setSecretRef("kms://ai/attacker-owned");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createModel(bo));

        assertTrue(ex.getMessage().contains("aig:model:secret:edit"), "实际=" + ex.getMessage());
        verify(modelConfigMapper, never()).insertModel(any());
    }

    @Test
    @DisplayName("有 view 无 edit：新增模型时不得登记明文密钥（且绝不加密落库）")
    void createModelRejectsPlainKeyWithoutEditPermission() {
        stubViewOnly();
        when(modelConfigMapper.countByModelKey(anyString())).thenReturn(0);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(1);
        AigModelCreateBo bo = createBo();
        bo.setApiKey("sk-should-never-be-stored");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createModel(bo));

        assertTrue(ex.getMessage().contains("aig:model:secret:edit"), "实际=" + ex.getMessage());
        verify(secretCipher, never()).encrypt(anyString());
        verify(modelConfigMapper, never()).insertModel(any());
    }

    private static AigModelCreateBo createBo() {
        AigModelCreateBo bo = new AigModelCreateBo();
        bo.setModelKey("qwen-boundary-test");
        bo.setProviderId(PROVIDER_ID);
        bo.setDeploymentType("LOCAL");
        bo.setDataLevelMax("INTERNAL");
        bo.setLifecycleStatus("CANDIDATE");
        return bo;
    }

}
