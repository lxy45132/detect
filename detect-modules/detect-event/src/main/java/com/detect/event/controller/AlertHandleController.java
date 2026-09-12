package com.detect.event.controller;

import com.detect.common.core.domain.PageResult;
import com.detect.common.core.domain.R;
import com.detect.event.dto.BatchHandleDTO;
import com.detect.event.dto.HandleProcessDTO;
import com.detect.event.dto.HandleRecordQueryDTO;
import com.detect.event.dto.TodoQueryDTO;
import com.detect.event.service.AlertHandleQueryService;
import com.detect.event.service.AlertHandleService;
import com.detect.event.vo.BatchHandleResultVO;
import com.detect.event.vo.HandleHistoryVO;
import com.detect.event.vo.HandleRecordVO;
import com.detect.event.vo.HandleStatVO;
import com.detect.event.vo.TodoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预警处理接口(接口文档 §4.3)，路径前缀 {@code /alert-handles}(网关 {@code /admin/event} 已被 StripPrefix)。
 *
 * <p>全部为 JWT 前端接口，由 common-security 资源服务器 {@code anyRequest().authenticated()} 保护；
 * 处理人从 JWT 上下文取，不接受前端传入(防越权伪造)。
 * 写侧 {@code process}/{@code batch-process}(§4.3.2/§4.3.3)；查询侧 {@code todo}/{@code records}/{@code history}/{@code statistics}(§4.3.1/§4.3.4~§4.3.6)。
 *
 * <p>字面路径({@code /todo}、{@code /records}、{@code /statistics})与 {@code /{eventId}/history}(双段)无路由冲突。
 */
@RestController
@RequestMapping("/alert-handles")
@RequiredArgsConstructor
public class AlertHandleController {

    private final AlertHandleService alertHandleService;
    private final AlertHandleQueryService alertHandleQueryService;

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

    /** 待办列表(§4.3.1)：{@code handle_status ∈ {0,1}}，按 {@code priority DESC, snap_time DESC} 排序。 */
    @GetMapping("/todo")
    public R<PageResult<TodoVO>> todo(TodoQueryDTO query) {
        return R.ok(alertHandleQueryService.todo(query));
    }

    /** 处理记录分页(§4.3.4)：按 {@code handle_time DESC} 排序。 */
    @GetMapping("/records")
    public R<PageResult<HandleRecordVO>> records(HandleRecordQueryDTO query) {
        return R.ok(alertHandleQueryService.records(query));
    }

    /** 事件处理历史(§4.3.5)：该事件全部处理记录，时间正序。 */
    @GetMapping("/{eventId}/history")
    public R<List<HandleHistoryVO>> history(@PathVariable Long eventId) {
        return R.ok(alertHandleQueryService.history(eventId));
    }

    /** 处理效率统计(§4.3.6)。 */
    @GetMapping("/statistics")
    public R<HandleStatVO> statistics(@RequestParam(required = false) String startTime,
                                      @RequestParam(required = false) String endTime,
                                      @RequestParam(required = false) String deviceNum) {
        return R.ok(alertHandleQueryService.statistics(startTime, endTime, deviceNum));
    }
}
