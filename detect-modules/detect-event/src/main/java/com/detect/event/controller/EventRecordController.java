package com.detect.event.controller;

import com.detect.common.core.annotation.Inner;
import com.detect.common.core.domain.PageResult;
import com.detect.common.core.domain.R;
import com.detect.event.dto.BatchDeleteDTO;
import com.detect.event.dto.EventQueryDTO;
import com.detect.event.dto.EventReceiveDTO;
import com.detect.event.dto.EventRecordUpdateDTO;
import com.detect.event.service.EventQueryService;
import com.detect.event.service.EventRecordService;
import com.detect.event.vo.DeletedVO;
import com.detect.event.vo.EventRecordDetailVO;
import com.detect.event.vo.EventRecordListVO;
import com.detect.event.vo.EventStatVO;
import com.detect.event.vo.IdVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 事件记录接口(接口文档 §4.1，共 8 端点)，路径前缀 {@code /event-records}(网关 {@code /admin/event} 已被 StripPrefix)。
 *
 * <p>{@code receive} 为 {@code @Inner} 内部接口(Python 直连，带 {@code from:Y})；其余 7 个为 JWT 前端接口，
 * 由 common-security 资源服务器 {@code anyRequest().authenticated()} 保护。
 * 字面路径({@code /page}、{@code /statistics}、{@code /export}、{@code /batch})优先级高于 {@code /{id}}，无路由冲突。
 */
@RestController
@RequestMapping("/event-records")
@RequiredArgsConstructor
public class EventRecordController {

    private final EventRecordService eventRecordService;
    private final EventQueryService eventQueryService;

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

    /** 分页查询事件列表(§4.1.2)，默认 snap_time DESC。 */
    @GetMapping("/page")
    public R<PageResult<EventRecordListVO>> page(EventQueryDTO query) {
        return R.ok(eventQueryService.page(query));
    }

    /** 事件详情(§4.1.3)：完整字段 + sourceData 解析对象 + 命中规则摘要 + 处理历史。 */
    @GetMapping("/{id}")
    public R<EventRecordDetailVO> detail(@PathVariable Long id) {
        return R.ok(eventQueryService.detail(id));
    }

    /** 修正事件业务字段(§4.1.4)，不含 handleStatus；不存在报 1001。 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody EventRecordUpdateDTO dto) {
        eventRecordService.update(id, dto);
        return R.ok();
    }

    /** 逻辑删除单条(§4.1.5)。 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        eventRecordService.delete(id);
        return R.ok();
    }

    /** 批量逻辑删除(§4.1.6)，返回 {@code {deleted:N}}。 */
    @DeleteMapping("/batch")
    public R<DeletedVO> batchDelete(@RequestBody @Valid BatchDeleteDTO dto) {
        return R.ok(new DeletedVO(eventRecordService.batchDelete(dto.getIds())));
    }

    /** 分类统计(§4.1.7)：total + byEventType/byTask/byDay。 */
    @GetMapping("/statistics")
    public R<EventStatVO> statistics(@RequestParam(required = false) String startTime,
                                     @RequestParam(required = false) String endTime,
                                     @RequestParam(required = false) String deviceNum) {
        return R.ok(eventQueryService.statistics(startTime, endTime, deviceNum));
    }

    /** 导出(§4.1.8)：xlsx/csv 文件流，同步上限 5 万条(超限报 4001)。 */
    @GetMapping("/export")
    public void export(EventQueryDTO query,
                       @RequestParam(defaultValue = "xlsx") String format,
                       HttpServletResponse response) {
        eventQueryService.export(query, format, response);
    }
}
