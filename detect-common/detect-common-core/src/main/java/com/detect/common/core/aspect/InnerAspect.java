package com.detect.common.core.aspect;

import com.detect.common.core.annotation.Inner;
import com.detect.common.core.constant.CommonConstants;
import com.detect.common.core.enums.ResultCode;
import com.detect.common.core.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link Inner} 校验切面：拦截标注方法，校验请求头 from=Y，否则拒绝。
 * 不加 @Component，由 CoreAutoConfiguration @Import 注册，避免 reactive 网关误加载。
 */
@Aspect
public class InnerAspect {

    @Before("@annotation(inner)")
    public void check(Inner inner) {
        if (!inner.value()) {
            return;
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BizException(ResultCode.FORBIDDEN, "内部接口禁止访问");
        }
        HttpServletRequest request = attrs.getRequest();
        if (!CommonConstants.FROM_IN.equals(request.getHeader(CommonConstants.FROM))) {
            throw new BizException(ResultCode.FORBIDDEN, "内部接口禁止外部访问");
        }
    }
}
