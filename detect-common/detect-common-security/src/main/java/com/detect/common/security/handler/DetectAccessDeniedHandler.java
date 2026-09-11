package com.detect.common.security.handler;

import com.detect.common.core.domain.R;
import com.detect.common.core.enums.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 无权限(403)处理：返回统一 R JSON，HTTP 状态 403。
 */
public class DetectAccessDeniedHandler implements AccessDeniedHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(ResultCode.FORBIDDEN.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        R<Void> body = R.failed(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
