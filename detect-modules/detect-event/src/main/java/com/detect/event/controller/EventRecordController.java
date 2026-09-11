package com.detect.event.controller;

import com.detect.common.core.annotation.Inner;
import com.detect.common.core.domain.R;
import com.detect.event.dto.EventReceiveDTO;
import com.detect.event.service.EventRecordService;
import com.detect.event.vo.IdVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 事件记录接口(接口文档 §4.1)，路径前缀 {@code /event-records}(网关 {@code /admin/event} 已被 StripPrefix)。
 * 当前实现 receive 写路径；查询侧端点在 6b-2 追加。
 */
@RestController
@RequestMapping("/event-records")
@RequiredArgsConstructor
public class EventRecordController {

    private final EventRecordService eventRecordService;

    /**
     * 接收 Python 推送的单条事件(§4.1.1)。
     *
     * <p>{@code @Inner}：免 JWT，须带请求头 {@code from: Y}(InnerAspect 校验)；Python 直连本服务，不经网关。
     * 成功返回 {@code {code:0, data:{id}}}，Python {@code emitter._post} 据 code==0 判投递成功。
     */
    @Inner
    @PostMapping("/receive")
    public R<IdVO> receive(@RequestBody @Valid EventReceiveDTO dto) {
        Long id = eventRecordService.receive(dto);
        return R.ok(new IdVO(id));
    }
}
