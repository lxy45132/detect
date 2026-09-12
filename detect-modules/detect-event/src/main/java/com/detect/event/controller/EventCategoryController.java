package com.detect.event.controller;

import com.detect.common.core.domain.R;
import com.detect.event.service.EventCategoryService;
import com.detect.event.vo.EnumItemVO;
import com.detect.event.vo.EventEnumsVO;
import com.detect.event.vo.TaskItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类字典接口(接口文档 §4.5)，路径前缀 {@code /event-categories}。只读枚举，不建表。
 *
 * <p>全部为 JWT 前端接口，供前端下拉选项与 code→中文名翻译；{@code /enums} 一次性返回全部以便启动缓存。
 */
@RestController
@RequestMapping("/event-categories")
@RequiredArgsConstructor
public class EventCategoryController {

    private final EventCategoryService eventCategoryService;

    /** 事件大类(§4.5.1)。 */
    @GetMapping("/types")
    public R<List<EnumItemVO>> types() {
        return R.ok(eventCategoryService.types());
    }

    /** 事件子类(§4.5.2)：{@code eventType} 可选，按大类过滤。 */
    @GetMapping("/tasks")
    public R<List<TaskItemVO>> tasks(@RequestParam(required = false) Integer eventType) {
        return R.ok(eventCategoryService.tasks(eventType));
    }

    /** 全部枚举(§4.5.3)：前端启动加载缓存。 */
    @GetMapping("/enums")
    public R<EventEnumsVO> enums() {
        return R.ok(eventCategoryService.enums());
    }
}
