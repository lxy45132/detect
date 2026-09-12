package com.detect.event.service;

import com.detect.event.enums.EventTypeEnum;
import com.detect.event.enums.HandleStatusEnum;
import com.detect.event.enums.PriorityEnum;
import com.detect.event.enums.RuleTypeEnum;
import com.detect.event.enums.TaskTypeEnum;
import com.detect.event.vo.EnumItemVO;
import com.detect.event.vo.EventEnumsVO;
import com.detect.event.vo.TaskItemVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 分类字典服务(接口文档 §4.5)：只读枚举聚合，<b>不查库</b>(字典以枚举为唯一真源)。
 *
 * <p>无依赖注入——纯函数式映射 {@code enum.values() → VO}，供前端下拉/翻译缓存。
 */
@Service
public class EventCategoryService {

    /** 事件大类(§4.5.1)：人脸/车辆/聚集。 */
    public List<EnumItemVO> types() {
        return Arrays.stream(EventTypeEnum.values())
                .map(e -> new EnumItemVO(e.getCode(), e.getName()))
                .toList();
    }

    /** 事件子类(§4.5.2)：{@code eventType} 非空时按大类过滤，否则返回全部。 */
    public List<TaskItemVO> tasks(Integer eventType) {
        return Arrays.stream(TaskTypeEnum.values())
                .filter(t -> eventType == null || t.getEventType() == eventType)
                .map(t -> new TaskItemVO(t.getCode(), t.getName(), t.getEventType()))
                .toList();
    }

    /** 全量枚举(§4.5.3)：一次性返回五类，前端启动加载缓存。 */
    public EventEnumsVO enums() {
        return new EventEnumsVO(types(), tasks(null), handleStatuses(), priorities(), ruleTypes());
    }

    private List<EnumItemVO> handleStatuses() {
        return Arrays.stream(HandleStatusEnum.values())
                .map(h -> new EnumItemVO(h.getCode(), h.getName()))
                .toList();
    }

    private List<EnumItemVO> priorities() {
        return Arrays.stream(PriorityEnum.values())
                .map(p -> new EnumItemVO(p.getCode(), p.getName()))
                .toList();
    }

    private List<EnumItemVO> ruleTypes() {
        return Arrays.stream(RuleTypeEnum.values())
                .map(r -> new EnumItemVO(r.getCode(), r.getName()))
                .toList();
    }
}
