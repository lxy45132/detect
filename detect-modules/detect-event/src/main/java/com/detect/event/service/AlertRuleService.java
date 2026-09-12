package com.detect.event.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.detect.common.core.constant.CommonConstants;
import com.detect.common.core.domain.PageResult;
import com.detect.common.core.enums.ResultCode;
import com.detect.common.core.exception.BizException;
import com.detect.event.dto.AlertRuleQueryDTO;
import com.detect.event.dto.AlertRuleSaveDTO;
import com.detect.event.dto.MatchTestDTO;
import com.detect.event.entity.AlertRule;
import com.detect.event.enums.PriorityEnum;
import com.detect.event.enums.RuleTypeEnum;
import com.detect.event.mapper.AlertRuleMapper;
import com.detect.event.vo.AlertRuleDetailVO;
import com.detect.event.vo.AlertRuleListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 布控预警规则服务(接口文档 §4.2)：分页/详情/新增/修改/删除/启停/试跑。
 *
 * <p>{@code matchConfig}/{@code deviceScope}/{@code timeScope} 为 JSON 列，实体以 String 承载：
 * 入库前用 {@link JSONUtil#toJsonStr} 序列化，出库后解析为对象/数组直出(§4.2.1/4.2.2 响应即对象形态)。
 *
 * <p><b>校验</b>：新增/修改均校验 matchConfig 与 ruleType 是否匹配(不符→2001)。修改为「传需改字段」的增量更新，
 * 用<b>生效后</b>的 ruleType+matchConfig 组合校验(dto 未传的取库中现值)，避免只改其一时漏检。
 * 校验一律在服务层执行，不依赖 bean-validation 是否在运行时生效。
 *
 * <p><b>试跑</b>(§4.2.7)复用 {@link RuleMatcher}，与 6c-2 队列消费者同一套判定逻辑，保证「所配即所判」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    /** 时间输出格式(§2.4 东八区) */
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern(CommonConstants.DATETIME_PATTERN);
    /** 试跑样本 snapTime 格式(§4.2.7 示例 yyyy-MM-dd HH:mm:ss) */
    private static final DateTimeFormatter SAMPLE_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 每页条数上限(§2.3) */
    private static final long MAX_PAGE_SIZE = 200L;
    /** crowdNum 阈值表达式(与 {@link RuleMatcher} 一致)，保存前校验用 */
    private static final Pattern CROWD_EXPR = Pattern.compile("^\\s*(>=|<=|==|>|<|=)\\s*(-?\\d+)\\s*$");

    private final AlertRuleMapper alertRuleMapper;
    private final RuleMatcher ruleMatcher;

    /** 分页(§4.2.1)：按 id 倒序；ruleType/enabled 精确、keyword 规则名模糊；size 夹取 [1,200]。 */
    public PageResult<AlertRuleListVO> page(AlertRuleQueryDTO q) {
        long current = Math.max(q.getCurrent(), 1L);
        long size = Math.min(Math.max(q.getSize(), 1L), MAX_PAGE_SIZE);
        QueryWrapper<AlertRule> w = new QueryWrapper<>();
        w.eq(StringUtils.hasText(q.getRuleType()), "rule_type", q.getRuleType());
        w.eq(q.getEnabled() != null, "enabled", q.getEnabled());
        w.like(StringUtils.hasText(q.getKeyword()), "rule_name", q.getKeyword());
        w.orderByDesc("id");
        Page<AlertRule> page = alertRuleMapper.selectPage(new Page<>(current, size), w);
        List<AlertRuleListVO> vos = page.getRecords().stream().map(this::toListVO).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 详情(§4.2.2)：全字段，JSON 列解析直出。 @throws BizException 2002 规则不存在 */
    public AlertRuleDetailVO detail(Long id) {
        return toDetailVO(loadOrThrow(id));
    }

    /**
     * 新增(§4.2.3)：校验必填 + matchConfig↔ruleType；priority/notifyEnabled/enabled 缺省取 1。
     *
     * @return 新规则 id
     * @throws BizException 400 ruleName 缺失 / 2001 规则类型或配置非法
     */
    public Long create(AlertRuleSaveDTO dto) {
        if (!StringUtils.hasText(dto.getRuleName())) {
            throw new BizException(ResultCode.BAD_REQUEST, "ruleName 必填");
        }
        if (!RuleTypeEnum.isValid(dto.getRuleType())) {
            throw new BizException(ResultCode.RULE_CONFIG_INVALID, "规则类型非法：" + dto.getRuleType());
        }
        if (dto.getMatchConfig() == null) {
            throw new BizException(ResultCode.RULE_CONFIG_INVALID, "matchConfig 必填");
        }
        validateMatchConfig(dto.getRuleType(), dto.getMatchConfig());

        AlertRule e = new AlertRule();
        applyDto(e, dto);
        if (e.getPriority() == null) {
            e.setPriority(PriorityEnum.IMPORTANT.getCode());   // 默认 1(§4.2.3)
        }
        if (e.getNotifyEnabled() == null) {
            e.setNotifyEnabled(1);
        }
        if (e.getEnabled() == null) {
            e.setEnabled(1);
        }
        alertRuleMapper.insert(e);
        log.info("[rule-create] id={} name={} type={}", e.getId(), e.getRuleName(), e.getRuleType());
        return e.getId();
    }

    /**
     * 修改(§4.2.4)：传需修改字段，非空增量更新(updateById 的 NOT_NULL 策略再次确保 null 不进 SET)。
     * 用「生效后 ruleType+matchConfig」重新校验。
     *
     * @throws BizException 2002 规则不存在 / 2001 配置非法 / 400 ruleName 传空串
     */
    public void update(Long id, AlertRuleSaveDTO dto) {
        AlertRule existing = loadOrThrow(id);
        String effType = dto.getRuleType() != null ? dto.getRuleType() : existing.getRuleType();
        if (!RuleTypeEnum.isValid(effType)) {
            throw new BizException(ResultCode.RULE_CONFIG_INVALID, "规则类型非法：" + effType);
        }
        Object effConfig = dto.getMatchConfig() != null ? dto.getMatchConfig() : existing.getMatchConfig();
        validateMatchConfig(effType, effConfig);
        if (dto.getRuleName() != null && !StringUtils.hasText(dto.getRuleName())) {
            throw new BizException(ResultCode.BAD_REQUEST, "ruleName 不能为空串");
        }
        AlertRule e = new AlertRule();
        e.setId(id);
        applyDto(e, dto);
        alertRuleMapper.updateById(e);
        log.info("[rule-update] id={}", id);
    }

    /** 删除(§4.2.5)：逻辑删(@TableLogic 转 UPDATE del_flag=1)。幂等：不存在/已删亦返回成功。 */
    public void delete(Long id) {
        alertRuleMapper.deleteById(id);
        log.info("[rule-delete] id={}", id);
    }

    /**
     * 启用/停用(§4.2.6)。
     *
     * @throws BizException 400 enabled 非 0·1 / 2002 规则不存在
     */
    public void toggle(Long id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new BizException(ResultCode.BAD_REQUEST, "enabled 须为 0 或 1");
        }
        loadOrThrow(id);
        AlertRule e = new AlertRule();
        e.setId(id);
        e.setEnabled(enabled);
        alertRuleMapper.updateById(e);
        log.info("[rule-toggle] id={} enabled={}", id, enabled);
    }

    /**
     * 试跑(§4.2.7)：加载规则 + 样本归一化 → 复用 {@link RuleMatcher}，<b>不落库</b>。
     *
     * @throws BizException 400 ruleId/sampleEvent 缺失 / 2002 规则不存在
     */
    public MatchResult matchTest(MatchTestDTO dto) {
        if (dto.getRuleId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "ruleId 必填");
        }
        if (dto.getSampleEvent() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "sampleEvent 必填");
        }
        AlertRule rule = loadOrThrow(dto.getRuleId());
        MatchTestDTO.SampleEvent s = dto.getSampleEvent();
        RuleMatchInput in = new RuleMatchInput(s.getEventType(), s.getDeviceNum(), s.getPlateNum(),
                s.getVehicleType(), s.getVehicleNormalType(), s.getCrowdNum(), parseSampleTime(s.getSnapTime()));
        MatchResult res = ruleMatcher.match(rule, in);
        log.info("[rule-match-test] ruleId={} matched={} reason={}", dto.getRuleId(), res.matched(), res.reason());
        return res;
    }

    // ---------------- 内部 ----------------

    private AlertRule loadOrThrow(Long id) {
        AlertRule r = alertRuleMapper.selectById(id);
        if (r == null) {
            throw new BizException(ResultCode.RULE_NOT_FOUND);
        }
        return r;
    }

    /** 校验 matchConfig 与 ruleType 是否匹配(§4.2.3 错误 2001)。 */
    private void validateMatchConfig(String ruleType, Object matchConfig) {
        JSONObject cfg;
        try {
            cfg = asJsonObject(matchConfig);
        } catch (Exception e) {
            throw new BizException(ResultCode.RULE_CONFIG_INVALID, "matchConfig 须为 JSON 对象");
        }
        if (cfg == null) {
            throw new BizException(ResultCode.RULE_CONFIG_INVALID, "matchConfig 须为 JSON 对象");
        }
        switch (RuleTypeEnum.valueOf(ruleType)) {
            case PLATE_BLACKLIST -> requireNonEmptyArray(cfg, "plateNum", ruleType);
            case VEHICLE_TYPE -> requireNonEmptyArray(cfg, "vehicleNormalType", ruleType);
            case DEVICE_TIME -> requireNonEmptyArray(cfg, "deviceNum", ruleType);
            case CROWD_THRESHOLD -> requireCrowdExpr(cfg, ruleType);
        }
    }

    private void requireNonEmptyArray(JSONObject cfg, String key, String ruleType) {
        Object node = cfg.get(key);
        if (!(node instanceof JSONArray ja) || ja.isEmpty()) {
            throw new BizException(ResultCode.RULE_CONFIG_INVALID,
                    ruleType + " 的 matchConfig 需含非空数组 " + key);
        }
    }

    private void requireCrowdExpr(JSONObject cfg, String ruleType) {
        String expr = cfg.getStr("crowdNum");
        if (expr == null || !CROWD_EXPR.matcher(expr).matches()) {
            throw new BizException(ResultCode.RULE_CONFIG_INVALID,
                    ruleType + " 的 matchConfig.crowdNum 需为阈值表达式(如 \">10\"、\">=5\")");
        }
    }

    /**
     * 将 matchConfig 归一为 hutool {@link JSONObject}。
     * 关键：Map/bean 先经 {@code toJsonStr} 转串再 {@code parseObj(String)}，
     * 既避免 {@code parseObj(Object)} 把 String 当 bean 的陷阱，又保证嵌套 List 规范为 JSONArray。
     */
    private JSONObject asJsonObject(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof JSONObject jo) {
            return jo;
        }
        String json = (o instanceof CharSequence cs) ? cs.toString() : JSONUtil.toJsonStr(o);
        return JSONUtil.parseObj(json);
    }

    /** 将 DTO 非空字段映射到实体；JSON 字段序列化为字符串存储。 */
    private void applyDto(AlertRule e, AlertRuleSaveDTO dto) {
        if (dto.getRuleName() != null) {
            e.setRuleName(dto.getRuleName());
        }
        if (dto.getRuleType() != null) {
            e.setRuleType(dto.getRuleType());
        }
        if (dto.getEventType() != null) {
            e.setEventType(dto.getEventType());
        }
        if (dto.getMatchConfig() != null) {
            e.setMatchConfig(JSONUtil.toJsonStr(dto.getMatchConfig()));
        }
        if (dto.getPriority() != null) {
            e.setPriority(dto.getPriority());
        }
        if (dto.getNotifyEnabled() != null) {
            e.setNotifyEnabled(dto.getNotifyEnabled());
        }
        if (dto.getDeviceScope() != null) {
            e.setDeviceScope(JSONUtil.toJsonStr(dto.getDeviceScope()));
        }
        if (dto.getTimeScope() != null) {
            e.setTimeScope(JSONUtil.toJsonStr(dto.getTimeScope()));
        }
        if (dto.getEnabled() != null) {
            e.setEnabled(dto.getEnabled());
        }
        if (dto.getRemark() != null) {
            e.setRemark(dto.getRemark());
        }
    }

    private AlertRuleListVO toListVO(AlertRule r) {
        AlertRuleListVO vo = new AlertRuleListVO();
        vo.setId(r.getId());
        vo.setRuleName(r.getRuleName());
        vo.setRuleType(r.getRuleType());
        vo.setRuleTypeName(RuleTypeEnum.nameOf(r.getRuleType()));
        vo.setEventType(r.getEventType());
        vo.setMatchConfig(parseJson(r.getMatchConfig()));
        vo.setPriority(r.getPriority());
        vo.setNotifyEnabled(r.getNotifyEnabled());
        vo.setEnabled(r.getEnabled());
        return vo;
    }

    private AlertRuleDetailVO toDetailVO(AlertRule r) {
        AlertRuleDetailVO vo = new AlertRuleDetailVO();
        vo.setId(r.getId());
        vo.setRuleName(r.getRuleName());
        vo.setRuleType(r.getRuleType());
        vo.setRuleTypeName(RuleTypeEnum.nameOf(r.getRuleType()));
        vo.setEventType(r.getEventType());
        vo.setMatchConfig(parseJson(r.getMatchConfig()));
        vo.setPriority(r.getPriority());
        vo.setNotifyEnabled(r.getNotifyEnabled());
        vo.setDeviceScope(parseJson(r.getDeviceScope()));
        vo.setTimeScope(parseJson(r.getTimeScope()));
        vo.setEnabled(r.getEnabled());
        vo.setRemark(r.getRemark());
        vo.setCreateBy(r.getCreateBy());
        vo.setCreateTime(formatTime(r.getCreateTime()));
        vo.setUpdateTime(formatTime(r.getUpdateTime()));
        return vo;
    }

    /** JSON 串解析为对象/数组直出；空返回 null，非法回退原始串。 */
    private Object parseJson(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            String t = s.trim();
            return t.startsWith("[") ? JSONUtil.parseArray(t) : JSONUtil.parseObj(t);
        } catch (Exception e) {
            return s;
        }
    }

    private String formatTime(LocalDateTime t) {
        return t == null ? null : t.format(DT);
    }

    private LocalDateTime parseSampleTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.trim(), SAMPLE_DT);
        } catch (Exception e) {
            log.warn("[rule-match-test] snapTime 解析失败(时段门控将放行): {}", s);
            return null;
        }
    }
}
