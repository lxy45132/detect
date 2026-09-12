package com.detect.event.service;

import com.detect.event.vo.EnumItemVO;
import com.detect.event.vo.EventEnumsVO;
import com.detect.event.vo.TaskItemVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EventCategoryService} 单测(6e-2)：纯枚举聚合，无 DB/Mock。
 * 校验大类 3 项、子类全量 6 项与按 eventType 过滤、全量枚举五类条数与代表项 code/name。
 */
class EventCategoryServiceTest {

    private final EventCategoryService service = new EventCategoryService();

    @Test
    void types_returnsThreeEventTypes() {
        List<EnumItemVO> types = service.types();
        assertEquals(3, types.size());
        assertEquals(100, types.get(0).code());
        assertEquals("人脸", types.get(0).name());
        assertEquals(300, types.get(2).code());
        assertEquals("聚集", types.get(2).name());
    }

    @Test
    void tasks_noFilter_returnsAllSix() {
        assertEquals(6, service.tasks(null).size());
    }

    @Test
    void tasks_filterByEventType200_returnsVehicleSubset() {
        List<TaskItemVO> vehicleTasks = service.tasks(200);
        // vehicle_type / license_plate / plate_unrecognized / ship_plate
        assertEquals(4, vehicleTasks.size());
        assertTrue(vehicleTasks.stream().allMatch(t -> t.eventType() == 200));
        assertTrue(vehicleTasks.stream()
                .anyMatch(t -> "license_plate".equals(t.code()) && "车牌识别".equals(t.name())));
    }

    @Test
    void tasks_filterByEventType300_returnsCrowdOnly() {
        List<TaskItemVO> crowdTasks = service.tasks(300);
        assertEquals(1, crowdTasks.size());
        assertEquals("people_gathering", crowdTasks.get(0).code());
    }

    @Test
    void enums_aggregatesAllFiveCategories() {
        EventEnumsVO enums = service.enums();
        assertEquals(3, enums.eventType().size());
        assertEquals(6, enums.task().size());
        assertEquals(4, enums.handleStatus().size());
        assertEquals(3, enums.priority().size());
        assertEquals(4, enums.ruleType().size());
        // 抽查代表项
        assertEquals(0, enums.handleStatus().get(0).code());
        assertEquals("未处理", enums.handleStatus().get(0).name());
        assertEquals(2, enums.priority().get(2).code());
        assertEquals("紧急", enums.priority().get(2).name());
        assertEquals("PLATE_BLACKLIST", enums.ruleType().get(0).code());
        assertEquals("车牌黑名单", enums.ruleType().get(0).name());
    }
}
