package org.dromara.aigov.task.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.bo.AigTaskMirrorQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorSourceVo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorVo;
import org.dromara.aigov.task.service.IAigTaskMirrorProvider;
import org.dromara.aigov.task.service.IAigTaskMirrorService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 存量只读镜像的注册与分发。
 *
 * <p><b>为什么注册期就校验重复来源</b>：两个模块声明同一个 {@code source} 时，
 * 「查这个来源得到谁的数据」取决于 Spring 的注入顺序——它会时好时坏，
 * 而且换一次启动顺序就换一个结果。这种不确定性必须在<b>启动期</b>以明确的异常暴露，
 * 而不是等到某天有人发现列表里的数字对不上。</p>
 *
 * <p><b>为什么来源不存在时报错要列出全部已知来源</b>：调用方拼错一个 code 是常态，
 * 报错只说「来源不存在」会让人去翻代码；列出可选值则一句话自解释。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
public class AigTaskMirrorServiceImpl implements IAigTaskMirrorService {

    /**
     * 来源编码 → 提供者。
     *
     * <p>用 {@link LinkedHashMap} 保持注册顺序只是为了让日志稳定；
     * 对外暴露的清单会再按编码显式排序。</p>
     */
    private final Map<String, IAigTaskMirrorProvider> providers;

    /**
     * 构造：收集全部提供者并校验来源唯一。
     *
     * @param providers Spring 注入的全部镜像提供者（可为空——没有任何存量来源也能正常启动）
     */
    public AigTaskMirrorServiceImpl(List<IAigTaskMirrorProvider> providers) {
        List<IAigTaskMirrorProvider> list = providers == null ? List.of() : providers;
        Map<String, IAigTaskMirrorProvider> map = new LinkedHashMap<>();
        for (IAigTaskMirrorProvider provider : list) {
            String source = provider.source();
            if (StringUtils.isBlank(source)) {
                throw new IllegalStateException(
                    "镜像提供者 " + provider.getClass().getName() + " 未声明来源编码（source() 返回空）");
            }
            IAigTaskMirrorProvider previous = map.putIfAbsent(source, provider);
            if (previous != null) {
                throw new IllegalStateException("镜像来源编码重复：" + source + " 同时被 "
                    + previous.getClass().getName() + " 与 " + provider.getClass().getName()
                    + " 声明；必须改成不同的来源编码，否则「查这个来源得到谁的数据」取决于启动顺序");
            }
        }
        this.providers = map;
        log.info("只读镜像来源注册完成，共 {} 个：{}", map.size(), map.keySet());
    }

    @Override
    public List<AigTaskMirrorSourceVo> listSources() {
        return providers.values().stream()
            .map(provider -> new AigTaskMirrorSourceVo(provider.source(), provider.label(), provider.description()))
            .sorted(Comparator.comparing(AigTaskMirrorSourceVo::getSource))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public PageResult<AigTaskMirrorVo> queryPage(AigTaskMirrorQueryBo bo, PageQuery pageQuery) {
        if (bo == null || StringUtils.isBlank(bo.getSource())) {
            throw new ServiceException("镜像来源不能为空（不提供跨来源合并分页——各来源分页语义不同，"
                + "合成一页会让页码与总数都失真）");
        }
        return require(bo.getSource()).queryPage(bo, pageQuery);
    }

    @Override
    public AigTaskMirrorVo getDetail(String source, String refId) {
        if (StringUtils.isBlank(source)) {
            throw new ServiceException("镜像来源不能为空");
        }
        if (StringUtils.isBlank(refId)) {
            throw new ServiceException("镜像行ID不能为空");
        }
        return require(source).getDetail(refId);
    }

    /**
     * 取来源对应的提供者，不存在时给出可选值。
     *
     * @param source 来源编码
     * @return 提供者
     */
    private IAigTaskMirrorProvider require(String source) {
        IAigTaskMirrorProvider provider = providers.get(source);
        if (provider == null) {
            throw new ServiceException("未知的镜像来源：" + source + "；当前可用的来源为 "
                + (providers.isEmpty() ? "（无，说明没有任何业务域注册镜像提供者）" : String.join("、", providers.keySet())));
        }
        return provider;
    }

}
