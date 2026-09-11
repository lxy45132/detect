package com.detect.common.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisPlusConfigTest {

    @Test
    void interceptorShouldContainPaginationAndOptimisticLocker() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();
        List<InnerInterceptor> inners = interceptor.getInterceptors();
        assertTrue(inners.stream().anyMatch(i -> i instanceof PaginationInnerInterceptor),
                "应包含分页插件");
        assertTrue(inners.stream().anyMatch(i -> i instanceof OptimisticLockerInnerInterceptor),
                "应包含乐观锁插件");
    }
}
