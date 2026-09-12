package com.detect.event.controller;

import com.detect.common.core.domain.PageResult;
import com.detect.common.core.domain.R;
import com.detect.event.dto.AlertRuleQueryDTO;
import com.detect.event.dto.AlertRuleSaveDTO;
import com.detect.event.dto.MatchTestDTO;
import com.detect.event.dto.ToggleDTO;
import com.detect.event.service.AlertRuleService;
import com.detect.event.service.MatchResult;
import com.detect.event.vo.AlertRuleDetailVO;
import com.detect.event.vo.AlertRuleListVO;
import com.detect.event.vo.IdVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 布控预警规则接口(接口文档 §4.2，共 7 端点)，路径前缀 {@code /alert-rules}(网关 {@code /admin/event} 已 StripPrefix)。
 *
 * <p>全部为 JWT 前端接口，由 common-security 资源服务器 {@code anyRequest().authenticated()} 保护。
 * 字面路径({@code /page}、{@code /match-test})优先级高于 {@code /{id}}，无路由冲突。
 */
@RestController
@RequestMapping("/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    /** 规则分页列表(§4.2.1)：ruleType/enabled 精确 + keyword 规则名模糊，按 id 倒序。 */
    @GetMapping("/page")
    public R<PageResult<AlertRuleListVO>> page(AlertRuleQueryDTO query) {
        return R.ok(alertRuleService.page(query));
    }

    /** 规则详情(§4.2.2)：全字段(含 deviceScope/timeScope/remark)，JSON 列解析直出。 */
    @GetMapping("/{id}")
    public R<AlertRuleDetailVO> detail(@PathVariable Long id) {
        return R.ok(alertRuleService.detail(id));
    }

    /** 新增规则(§4.2.3)：校验 matchConfig↔ruleType(不符报 2001)，返回 {@code {id}}。 */
    @PostMapping
    public R<IdVO> create(@RequestBody AlertRuleSaveDTO dto) {
        return R.ok(new IdVO(alertRuleService.create(dto)));
    }

    /** 修改规则(§4.2.4)：传需改字段，增量更新；不存在报 2002。 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody AlertRuleSaveDTO dto) {
        alertRuleService.update(id, dto);
        return R.ok();
    }

    /** 删除规则(§4.2.5)：逻辑删。 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        alertRuleService.delete(id);
        return R.ok();
    }

    /** 启用/停用(§4.2.6)：body {@code {enabled:0|1}}。 */
    @PutMapping("/{id}/toggle")
    public R<Void> toggle(@PathVariable Long id, @RequestBody ToggleDTO dto) {
        alertRuleService.toggle(id, dto.getEnabled());
        return R.ok();
    }

    /** 规则试跑(§4.2.7)：传事件样本，返回 {@code {matched,reason}}，不落库。 */
    @PostMapping("/match-test")
    public R<MatchResult> matchTest(@RequestBody MatchTestDTO dto) {
        return R.ok(alertRuleService.matchTest(dto));
    }
}
