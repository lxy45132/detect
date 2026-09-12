# Detect 事件管理后台

> pig 风格 Spring Cloud 多模块微服务平台，为 **yolo26（Python YOLO26 视频检测管线）** 提供事件接入、布控预警、处理流转、站内通知与统计导出的后端能力。

- **接口契约**：`yolo26/docs/superpowers/specs/2026-09-10-event-management-api-design.md`
- **实施全记录**：[`docs/IMPLEMENTATION_LOG.md`](docs/IMPLEMENTATION_LOG.md)（逐步实现、决策依据、验证结果）
- **坐标**：`groupId=com.detect`，`version=1.0.0`

---

## 1. 系统概览

Python 侧检测管线通过 **webhook** 把事件推给 Java 后端；后端入库、异步匹配布控规则、命中后升级优先级并扇出站内通知；前端通过 **网关 + JWT** 查询与处理预警。

```
┌──────────────────┐   webhook POST  (Header: from: Y)   ┌────────────────────────┐
│  yolo26 (Python) │ ──────────────────────────────────► │   detect-event :8082    │
│  YOLO26 检测管线  │      /event-records/receive          │  @Inner 免鉴权接收 + 转存 │
└──────────────────┘                                      └───────────┬────────────┘
                                                                       │ LPUSH eventId
                                                          Redis 队列    ▼
                                                          ┌────────────────────────┐
                                                          │ RuleMatchConsumer       │
                                                          │ 规则匹配 → 命中落库      │
                                                          └───────────┬────────────┘
                                                       Feign (from:Y) │ 列管理员 + 写通知
                                                                      ▼
┌──────────────┐  JWT Bearer   ┌──────────────┐   lb 路由   ┌────────────────────┐
│  前端 (Web)   │ ────────────► │ gateway:9999 │ ─────────► │ auth:8081 / event  │
└──────────────┘ /admin/event/**└──────────────┘            └────────────────────┘

基础设施 (docker-compose)： MySQL:3307 · Nacos:8848/9848 · Redis:6379 · MinIO:9000/9001
```

**关键链路**（已用真实视频 `testcar1.mp4` 端到端验证贯通，见 IMPLEMENTATION_LOG Step 7）：
`Python 检测车牌 → webhook(code==0) → 入库 + MinIO 转存 → Redis 异步队列 → 命中规则 → 升级 priority + 系统留痕 → Feign 扇出通知 → 前端 JWT 查询`

---

## 2. 技术栈

| 分类 | 选型 | 版本 |
|---|---|---|
| 语言 / 运行时 | Java (JDK) | 17 |
| 应用框架 | Spring Boot | 3.2.4 |
| 微服务 | Spring Cloud | 2023.0.1 |
| 微服务（阿里） | Spring Cloud Alibaba | 2023.0.1.0 |
| 注册中心 | Nacos Server | 2.3.2 |
| 持久层 | MyBatis-Plus (`mybatis-plus-spring-boot3-starter`) | 3.5.5 |
| 对象存储 | MinIO | 8.5.7 |
| 工具库 | Hutool (`hutool-all`) | 5.8.27 |
| 导出 | EasyExcel | 3.3.4 |
| 认证 | Spring Security OAuth2 Resource Server + Nimbus JOSE（自签 JWT） | 随 Boot |
| 数据库 | MySQL | 8.0 |
| 缓存 / 队列 | Redis | 7 |
| 测试 | JUnit 5 + Mockito（inline mock maker，支持 `mockStatic`） | 5.7.0 |

> 未继承 `spring-boot-starter-parent`，改由父 pom 的 `dependencyManagement` 导入 SB/SC/SCA BOM，并显式为 `maven-compiler-plugin` 开启 `-parameters`（保障 Spring 6 按形参名解析 `@PathVariable`/`@Qualifier`）。

---

## 3. 模块结构

```
detect (父 pom)
├─ detect-common                    公共库聚合（自动配置，不含 @Component）
│  ├─ detect-common-core            统一响应 R / 分页 PageResult / 异常 / 常量 / ResultCode / @Inner + AOP
│  ├─ detect-common-mybatis         BaseEntity（create_time/update_time/del_flag）/ 自动填充 / 分页 + 乐观锁插件
│  ├─ detect-common-security        资源服务器 JWT 验签 / SecurityUtils / @Inner 放行 / 401·403 处理器
│  └─ detect-common-oss             MinIO 对象存储封装（OssTemplate / OssUtils）
├─ detect-auth        :8081         认证服务器：POST /oauth/token 签发 JWT、/oauth2/jwks 暴露公钥、@Inner 列管理员
├─ detect-gateway     :9999         响应式网关：Nacos 动态路由 + CORS + 剥离外部 from 头（防伪造 @Inner）
└─ detect-modules
   └─ detect-event    :8082         事件业务：事件 CRUD / 布控规则 / 处理流转 / 站内通知 / 分类字典 / 统计导出
```

| 服务 | 端口 | jar 产物 | 职责 |
|---|---|---|---|
| detect-auth | 8081 | `detect-auth/target/detect-auth-1.0.0.jar` | 认证签发 JWT、JWK Set、内部列管理员 |
| detect-gateway | 9999 | `detect-gateway/target/detect-gateway-1.0.0.jar` | 前端统一入口、路由、跨域 |
| detect-event | 8082 | `detect-modules/detect-event/target/detect-event.jar` | 全部事件业务（webhook 接收 + 查询/处理/通知/字典） |

---

## 4. 核心约定

- **统一响应 `R<T>`**：`{code, msg, data}`，成功 `code=0`。
- **分页 `PageResult<T>`**：`{records, total, current, size, pages}`；`size` 夹取 `[1, 200]`。
- **逻辑删除**：`del_flag`（`0` 正常 / `1` 删除），`@TableLogic(value="0", delval="1")`。`alert_handle_record` / `sys_notification` 无 `del_flag`（物理删/留痕不删）。
- **`@Inner` + AOP**：内部免鉴权接口校验请求头 `from: Y`；缺失则 `403 内部接口禁止外部访问`（HTTP 200 + body `code=403`）。网关会剥离外部传入的 `from` 头，杜绝伪造。
- **鉴权模型**：前端接口经资源服务器 `anyRequest().authenticated()`（JWT Bearer）；无 RBAC 矩阵，仅要求「已登录」。
- **JWT 声明契约**（auth 签发 ↔ common-security 验签对齐）：`iss / sub=username / user_id(Long) / username / authorities(List<String>，如 ROLE_ADMIN) / iat / exp`。**令牌不含 nickname**，故处理人 `handlerName = username`。

**业务错误码（`ResultCode`）**：

| code | 含义 | | code | 含义 |
|---|---|---|---|---|
| 0 | 成功 | | 1001 | 事件不存在 |
| 400 | 参数错误 | | 1002 | 事件已删除 |
| 401 | 未认证 | | 2001 | 规则配置非法 |
| 403 | 无权限 / 内部接口外部访问 | | 2002 | 规则不存在 |
| 404 | 资源不存在 | | 3001 | 状态流转非法 |
| 500 | 系统异常 | | 4001 | 导出数量超上限 |

---

## 5. 环境要求

- **JDK 17**
- **Maven 3.9+**
- **Docker + Docker Compose**（运行 MySQL / Nacos / Redis / MinIO）

---

## 6. 快速开始

### 6.1 启动基础设施

```bash
cd docker
docker compose up -d          # MySQL:3307 · Nacos:8848 · Redis:6379 · MinIO:9000/9001
docker compose ps             # 等待全部 healthy
```

MySQL 首次初始化（空数据卷）时，`docker/docker-compose.yml` 已挂载 `../sql/init` 到 `/docker-entrypoint-initdb.d`，会**按序自动执行**建库建表脚本：

| 脚本 | 作用 |
|---|---|
| `sql/init/01-databases.sql` | 建库 `detect_auth` / `detect_event`（utf8mb4） |
| `sql/init/02-auth-schema.sql` | 建 `sys_user` 表（管理员由应用启动播种，不硬编码密码哈希） |
| `sql/init/03-event-schema.sql` | 建 5 张业务表 + seed 摄像头 `dev01~dev03`（含 `SET NAMES utf8mb4` 防中文双重编码，可重复执行） |

> 若数据卷已存在导致未自动执行，可手动导入：
> `docker exec -i detect-mysql mysql -uroot -proot --default-character-set=utf8mb4 < sql/init/03-event-schema.sql`

### 6.2 生成 JWT 密钥库（首次必须）

`*.jks` 已被 `.gitignore` 排除，**密钥不入库**。auth 启动时从 `classpath:jwt.jks` 加载 RSA 私钥（fail-fast，缺失即启动失败），因此全新克隆后须先生成：

```bash
keytool -genkeypair -alias detect-jwt -keyalg RSA -keysize 2048 -validity 3650 \
  -keystore detect-auth/src/main/resources/jwt.jks \
  -storepass detect123456 -keypass detect123456 \
  -dname "CN=detect, OU=detect, O=detect, L=City, ST=State, C=CN"
```

> 别名须为 `detect-jwt`、密码须与 `JWT_KS_PASSWORD`/`JWT_KEY_PASSWORD`（默认 `detect123456`）一致。生产环境请通过环境变量覆盖密码并经 CI secret 注入 keystore。

### 6.3 构建

```bash
# 项目根目录，一次性构建全部模块（按 reactor 顺序安装 common 到本地仓库）
mvn clean install -DskipTests
```

### 6.4 启动服务

按 **auth → event →（可选）gateway** 顺序启动（每个服务独立进程/终端）：

```bash
# 认证服务器 :8081（启动时幂等播种 admin/123456）
java -jar detect-auth/target/detect-auth-1.0.0.jar

# 事件业务 :8082（连 MySQL/Redis/MinIO，注册 Nacos，起规则匹配消费者线程）
java -jar detect-modules/detect-event/target/detect-event.jar

# 网关 :9999（可选；前端统一入口。Python webhook 与本地调试可直连 8082，不经网关）
java -jar detect-gateway/target/detect-gateway-1.0.0.jar
```

所有连接参数均有默认值（见 §7），本机按默认 docker-compose 部署可**零环境变量直接启动**。

### 6.5 验证

```bash
# 健康检查
curl http://localhost:8082/actuator/health          # {"status":"UP"}

# 登录取 JWT（直连 auth，或经网关 /auth/oauth/token）
curl -X POST http://localhost:8081/oauth/token \
  -d "username=admin&password=123456&grant_type=password"
# → {"access_token":"eyJ...","token_type":"Bearer","expires_in":7200,...}

# 带 JWT 查询事件分页（直连 event）
curl http://localhost:8082/event-records/page \
  -H "Authorization: Bearer <access_token>"

# 经网关查询（StripPrefix=2：/admin/event/event-records/page → /event-records/page）
curl http://localhost:9999/admin/event/event-records/page \
  -H "Authorization: Bearer <access_token>"
```

默认管理员：**admin / 123456**（首次启动由 `AuthDataInitializer` 以 BCrypt 幂等播种）。

---

## 7. 配置项（环境变量）

各服务 `application.yml` 的敏感/环境相关项均支持 `${VAR:默认值}` 覆盖：

| 变量 | 默认值 | 用于 |
|---|---|---|
| `NACOS_HOST` / `NACOS_PORT` | `localhost` / `8848` | auth·gateway·event 注册发现 |
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3307` | auth(`detect_auth`)·event(`detect_event`) |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `root` / `root` | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | event 异步规则匹配队列 |
| `MINIO_ENDPOINT` | `http://localhost:9000` | event 抓拍图转存 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `minioadmin` / `minioadmin` | MinIO 凭证 |
| `MINIO_BUCKET` / `MINIO_PUBLIC_URL` | `detect` / `http://localhost:9000` | 桶名 / 外链前缀 |
| `AUTH_JWKS_URI` | `http://localhost:8081/oauth2/jwks` | event 资源服务器验签公钥来源 |
| `JWT_KS_PASSWORD` / `JWT_KEY_PASSWORD` | `detect123456` | auth keystore / key 密码 |

> 本地私密配置可用 `application-local.yml`（已 gitignore）；生产建议经 Nacos 配置中心或环境变量注入。

---

## 8. API 一览

除标注 **@Inner**（内部免鉴权，须带 `from: Y`）外，业务接口均需 **JWT Bearer**。以下为 **detect-event 直连路径**（经网关时前缀 `/admin/event`，登录前缀 `/auth`）。

### 认证（detect-auth :8081）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/oauth/token` | 免鉴权 | 密码模式签发 JWT（`username/password/grant_type=password`） |
| GET | `/oauth2/jwks` | 免鉴权 | 暴露 JWT 验签公钥（JWK Set，仅公钥） |
| GET | `/inner/users/admins` | **@Inner** | 列全部管理员（供 event 扇出通知；仅返回 id/username/nickname） |

### 事件（`/event-records`）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/event-records/receive` | **@Inner** | Python webhook 入口：入库 + MinIO 转存 + 幂等去重 + 异步触发规则匹配 |
| GET | `/event-records/page` | JWT | 分页（deviceNum/eventType/task/handleStatus/priority/plateNum/keyword/时间区间） |
| GET | `/event-records/{id}` | JWT | 详情（全字段 + sourceData 对象 + 命中规则摘要 + 处理历史） |
| PUT | `/event-records/{id}` | JWT | 部分修正业务字段（不含 handleStatus） |
| DELETE | `/event-records/{id}` | JWT | 逻辑删除 |
| DELETE | `/event-records/batch` | JWT | 批量逻辑删（body `{ids:[]}` → `{deleted:N}`） |
| GET | `/event-records/statistics` | JWT | 分类统计（total + byEventType/byTask/byDay） |
| GET | `/event-records/export` | JWT | 导出 xlsx/csv（`format` 参数，同步上限 5 万条，超限 4001） |

### 布控规则（`/alert-rules`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/alert-rules/page` | 分页（ruleType/enabled/keyword） |
| GET | `/alert-rules/{id}` | 详情（matchConfig/deviceScope/timeScope 解析为对象；不存在 2002） |
| POST | `/alert-rules` | 新建（按 ruleType 校验 matchConfig，非法 2001；默认 priority=1/notify=1/enabled=1） |
| PUT | `/alert-rules/{id}` | 增量更新（按「生效后」ruleType+matchConfig 组合重校验） |
| DELETE | `/alert-rules/{id}` | 逻辑删除（幂等） |
| PUT | `/alert-rules/{id}/toggle` | 启停（body `{enabled:0|1}`） |
| POST | `/alert-rules/match-test` | 试跑（body `{ruleId, sampleEvent}` → `{matched, reason}`，**不落库**） |

规则类型：`PLATE_BLACKLIST`（车牌黑名单）/ `VEHICLE_TYPE`（车辆类型）/ `CROWD_THRESHOLD`（聚集人数阈值，如 `{"crowdNum":">10"}`）/ `DEVICE_TIME`（设备时段，如 `{"deviceNum":["dev01"]}`）。

### 预警处理（`/alert-handles`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/alert-handles/process` | 单条状态流转（校验存在 1001 + 流转合法 3001，写留痕，处理人取自 JWT） |
| POST | `/alert-handles/batch-process` | 批量（「非法跳过、合法提交」→ `{processed, skipped:[{eventId,reason}]}`） |
| GET | `/alert-handles/todo` | 待办（handle_status ∈ {0,1}，priority DESC + snap_time DESC，含 hitRuleName） |
| GET | `/alert-handles/records` | 处理记录分页（handle_time DESC） |
| GET | `/alert-handles/{eventId}/history` | 单事件处理历史（handle_time ASC；无记录返空列表） |
| GET | `/alert-handles/statistics` | 处理效率统计（pendingCount/processingCount/todayResolved/falseRate/avgHandleMinutes） |

**状态机（§5.2）**：`0 未处理 → {1 处理中, 3 误报}`；`1 处理中 → {2 已处理, 3 误报}`；`2/3` 为终态。非法流转返回 3001。

### 站内通知（`/notifications`，user_id 一律取自 JWT，仅能操作本人通知）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/notifications/page` | 本人通知分页（可按 readFlag/type 过滤） |
| GET | `/notifications/unread-count` | 未读计数 `{count:N}`（前端红点） |
| PUT | `/notifications/{id}/read` | 标记单条已读（幂等；非本人/不存在 404） |
| PUT | `/notifications/read-all` | 全部置已读 `{read:N}` |
| DELETE | `/notifications/{id}` | 物理删（ownership-scoped；非本人/不存在 404） |

### 分类字典（`/event-categories`，只读枚举，不建表）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/event-categories/types` | 事件大类 `[{code,name}]`（100 人脸 / 200 车辆 / 300 聚集） |
| GET | `/event-categories/tasks` | 事件子类 `[{code,name,eventType}]`（`?eventType` 可选过滤） |
| GET | `/event-categories/enums` | 一次性全量 `{eventType,task,handleStatus,priority,ruleType}`（前端启动缓存） |

---

## 9. 与 Python（yolo26）集成

**非侵入契约**：Python 端 `api/emitter.py` 通过 webhook POST 推事件，Java 端不得改动此契约。

- **入口**：`POST http://<event-host>:8082/event-records/receive`（直连 event，不经网关）
- **请求头**：`Content-Type: application/json` + `from: Y`（@Inner 免鉴权标识）
- **成功判据**：HTTP `2xx` **且** 响应体 `code == 0`（幂等命中重复也返回 `code=0`）；否则 emitter 退避重试并落盘 spool 补传
- **事件信封字段**：`version / eventId / deviceId / captureTime / eventType / data{}`（`snapImage` base64 **可选**，编码失败会传 null，Java 侧不强校验以免其无限重试）

Python 侧运行示例：

```bash
python main.py --source <video.mp4> --preset traffic \
  --webhook-url http://127.0.0.1:8082/event-records/receive \
  --device-id dev01
```

---

## 10. 关键设计

- **CQRS-lite 读写分离**：写侧 `EventRecordService` / `AlertHandleService` 与查询侧 `EventQueryService` / `AlertHandleQueryService` 分职；查询侧统一用 `QueryWrapper`（字符串列名）以支持 `source_data` 的 MySQL JSON 函数过滤与聚合投影。
- **异步规则匹配（Redis 轻量队列）**：`receive` 入库后 `LPUSH detect:event:rule-match:queue eventId`（不阻塞 webhook）；`RuleMatchConsumer` 单守护线程 `BRPOP`（2s 超时）消费 → `AlertMatchService.matchEvent` 加载全部 `enabled=1` 规则逐条匹配 → 取最高 priority 命中（平手取小 id）落库。采 at-most-once（规格仅要求「异步、不阻塞」）。
- **通知扇出（OpenFeign 跨服务）**：命中且 `notify_enabled=1` → Feign 调 auth `@Inner /inner/users/admins` 取管理员 → 逐个写 `sys_notification` → `event_records.status` 置「已推送」。通知失败不回滚命中（尽力而为旁路）。`InnerFeignConfig` 刻意不加 `@Configuration`，仅经 `@FeignClient(configuration=...)` 作用于单个客户端，避免污染全局。
- **幂等去重**：去重键 = `device_num + snap_time + source_data.trackId`（同帧多目标以 trackId 区分）；命中重复直接返回已存在 id，不重复转存 MinIO、不重复入库。
- **JSON 列以 String 承载**：`source_data / match_config / device_scope / time_scope` 存字符串，服务层解析。**回填到响应 VO 的解析统一用 Jackson `ObjectMapper.readValue(str, Object.class)`**（产出标准 `Map`/`List`，JSON null → Java null），规避 hutool `JSONNull` 单例无 Jackson 序列化器导致的响应 500（详见 IMPLEMENTATION_LOG Step 7-5）。
- **字典即枚举**：分类字典不建表、不查库，5 个枚举（EventType/TaskType/HandleStatus/Priority/RuleType）为唯一真源。

---

## 11. 数据模型

`detect_auth`：

| 表 | 说明 |
|---|---|
| `sys_user` | 系统用户（username 唯一 / BCrypt password / role→`ROLE_x` / enabled / del_flag） |

`detect_event`：

| 表 | 说明 | 关键列 |
|---|---|---|
| `event_records` | 事件主表（Python 推送 + 预警扩展） | device_num / event_type / snap_time / snap_url / source_data(JSON) / plate_num / crowd_num / status / handle_status / priority / hit_rule_id / del_flag |
| `alert_rule` | 布控预警规则 | rule_name / rule_type / event_type / match_config(JSON) / device_scope(JSON) / time_scope(JSON) / priority / notify_enabled / enabled / del_flag |
| `alert_handle_record` | 预警处理记录（无 del_flag，留痕不删） | event_id / from_status / to_status / handler_id / handler_name / handle_remark / handle_time |
| `sys_notification` | 站内通知（无 del_flag，物理删） | user_id / title / content / type(alert·system) / biz_id / priority / read_flag / read_time |
| `camera_manage` | 摄像头设备（最小版，seed dev01~dev03） | device_num(唯一) / device_name / location / status |

---

## 12. 测试

```bash
mvn test                                   # 全量
mvn -pl detect-modules/detect-event test   # 仅 event 模块
```

- **91 单测全绿**（event 模块）：覆盖规则匹配引擎、状态机、异步队列、通知扇出、查询/统计、字典、以及 JSON null 序列化回归。
- 用 Mockito `mockStatic` 桩 `SecurityUtils` 静态方法验证鉴权上下文（Boot 3.2.4 + Mockito 5 默认 inline mock maker，无需额外依赖）。

---

## 13. 常见问题

- **网关端口为何是 9999 而非 8080**：本机 VMware `vmnat` 服务常驻占用 8080，改用 pig 风格 9999。
- **auth 启动即退出**：多为缺 `jwt.jks`（fail-fast）。按 §6.2 生成 keystore，并确认别名/密码与配置一致。
- **Nacos 注册 IP 为 link-local（169.254.x.x）**：Windows 本机可达、路由正常；跨机部署需设 `spring.cloud.inetutils.preferred-networks` 或 `spring.cloud.nacos.discovery.ip` 固定可达 IP。
- **MinIO 抓拍图 URL 匿名访问 403**：桶为私有策略，对象确已上传（403 而非 404）；生产应走预签名 URL 或鉴权代理访问。
- **中文乱码**：DB 经 docker MySQL `utf8mb4` 存储正确；Windows 控制台乱码多为 codepage 显示假象，用 `HEX()` 或 UTF-8 客户端核验字节。
- **事件详情含 null 字段曾返 500**：真实数据的 `source_data` 含 JSON null，历史用 hutool 解析产生 `JSONNull` 单例致 Jackson 序列化失败；已改用 Jackson 解析修复（Step 7-5，含回归测试）。

---

## 14. 目录导航

```
Detect/
├─ detect-common/         公共库（core / mybatis / security / oss）
├─ detect-auth/           认证服务器
├─ detect-gateway/        网关
├─ detect-modules/
│  └─ detect-event/       事件业务（controller / service / dto / vo / entity / mapper / enums / queue / feign）
├─ docker/                docker-compose.yml（MySQL / Nacos / Redis / MinIO）
├─ sql/init/              建库建表 + 种子脚本
├─ docs/                  IMPLEMENTATION_LOG.md（实施全记录）
└─ pom.xml                父 pom（BOM + 版本矩阵 + 构建插件）
```

更多实现细节、决策依据与逐步验证结果，见 [`docs/IMPLEMENTATION_LOG.md`](docs/IMPLEMENTATION_LOG.md)。
