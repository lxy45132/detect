package com.detect.event.controller;

import com.detect.common.core.domain.R;
import com.detect.event.dto.BatchHandleDTO;
import com.detect.event.dto.HandleProcessDTO;
import com.detect.event.service.AlertHandleService;
import com.detect.event.vo.BatchHandleResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预警处理接口(接口文档 §4.3)，路径前缀 {@code /alert-handles}(网关 {@code /admin/event} 已被 StripPrefix)。
 *
 * <p>全部为 JWT 前端接口，由 common-security 资源服务器 {@code anyRequest().authenticated()} 保护；
 * 处理人从 JWT 上下文取，不接受前端传入(防越权伪造)。
 * 6d-1 提供写侧 {@code process}/{@code batch-process}；查询侧 {@code todo}/{@code records}/{@code history}/{@code statistics} 见 6d-2。
 */
@RestController
@RequestMapping("/alert-handles")
@RequiredArgsConstructor
public class AlertHandleController {

    private final AlertHandleService alertHandleService;

    /**
     * 处理一条(§4.3.2)：校验流转合法性 → 更新 {@code handle_status} → 写处理记录。
     * 事件不存在报 {@code 1001}，非法流转报 {@code 3001}。
     */
    @PostMapping("/process")
    public R<Void> process(@RequestBody @Valid HandleProcessDTO dto) {
        alertHandleService.process(dto);
        return R.ok();
    }

    /** 批量处理(§4.3.3)：逐条校验，非法跳过并回报 {@code {processed, skipped[]}}。 */
    @PostMapping("/batch-process")
    public R<BatchHandleResultVO> batchProcess(@RequestBody @Valid BatchHandleDTO dto) {
        return R.ok(alertHandleService.batchProcess(dto));
    }
}
