package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * MyBatis-Plus 实体元数据初始化测试辅助（P4 C 线单测共用）。
 *
 * <p><b>为什么需要</b>：服务实现大量使用 {@code LambdaQueryWrapper} /
 * {@code LambdaUpdateWrapper} 的方法引用，MyBatis-Plus 需要先注册实体的
 * {@code TableInfo} 与 lambda 缓存；这些元数据平时由 MyBatis 启动时写入，
 * 纯 JUnit（无 Spring、无数据库）环境下必须手工初始化，
 * 否则会抛 {@code MybatisPlus can not find lambda cache for this entity}。</p>
 *
 * <p><b>边界</b>：本类只注册元数据（表名、主键、逻辑删除列），
 * 不建立任何数据库连接，也不改变生产代码行为。</p>
 *
 * @author hr-talent
 */
final class MybatisTableInfoTestSupport {

    /**
     * 工具类不允许实例化。
     */
    private MybatisTableInfoTestSupport() {
    }

    /**
     * 为指定实体注册 MyBatis-Plus 元数据与 lambda 缓存。
     *
     * @param entityTypes 实体类型
     */
    static void init(Class<?>... entityTypes) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entityType : entityTypes) {
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

}
