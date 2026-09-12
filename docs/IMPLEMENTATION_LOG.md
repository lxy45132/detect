# Detect 事件管理后台 — 实施日志

> 本日志逐步记录实施内容、决策依据与验证结果。接口契约以
> `yolo26/docs/superpowers/specs/2026-09-10-event-management-api-design.md` 为准。
> 非侵入约束：Python(yolo26) 端通过 webhook POST 推事件，据响应 `code==0` 判成功，请求头带 `from: Y`；Java 端不得改动此契约。

## 项目概览

- **架构**：pig 风格 Spring Cloud 多模块微服务
- **版本矩阵**：JDK 17 / Spring Boot 3.2.4 / Spring Cloud 2023.0.1 / Spring Cloud Alibaba 2023.0.1.0 / Nacos Server 2.3.2 / MyBatis-Plus 3.5.5(`mybatis-plus-spring-boot3-starter`) / MinIO 8.5.7 / Hutool 5.8.27
- **坐标**：groupId `com.detect`，version `1.0.0`
- **端口规划**：gateway 9999 / auth 8081 / event 8082（原计划网关 8080，因本机 VMware `vmnat` 服务常驻占用 8080，改用 pig 风格 9999）
- **基础设施(docker-compose)**：MySQL 3307(root/root) / Nacos 8848,9848 / Redis 6379 / MinIO 9000,9001(minioadmin/minioadmin)
- **本地环境**：Maven 仓库 `D:\apachemaven\apache-maven-3.9.14\mvn-repo`；JDK `C:\Users\HP\JDK\jdk-17.0.18.8-hotspot`

## 模块结构

```
detect(父 pom)
├─ detect-common(聚合)
│  ├─ detect-common-core       统一响应/异常/常量/@Inner+AOP
│  ├─ detect-common-mybatis    BaseEntity/逻辑删除/自动填充/分页插件
│  ├─ detect-common-security   资源服务器 JWT 验签/SecurityUtils/@Inner 放行
│  └─ detect-common-oss        MinIO 对象存储封装
├─ detect-auth                 认证服务器(签发 JWT)
├─ detect-gateway              网关(路由/跨域/聚合鉴权)   ✅
└─ detect-modules
   └─ detect-event             事件业务(CRUD/规则/统计/导出) 事件域✅(6b) 规则域✅(6c) 处理域✅(6d) 通知✅+字典✅(6e) 联调✅(Step7)
```

## 全局约定

- **统一响应 `R<T>`**：`{code,msg,data}`，成功 `code=0`
- **分页 `PageResult<T>`**：`{records,total,current,size,pages}`
- **逻辑删除**：`del_flag`(0 正常 / 1 删除)，`@TableLogic(value="0",delval="1")`
- **`@Inner` + AOP**：校验请求头 `from: Y`，用于内部免鉴权接口(如 webhook receive)
- **鉴权模型(规格 §2.1)**：前端接口 `/admin/**` 用 JWT Bearer；内部接口 `@Inner` 免鉴权须带 `from: Y`；无 RBAC 矩阵，仅要求"已登录"
- **Token 声明契约**(auth 签发 ↔ common-security 验签对齐)：`iss / sub=username / user_id(Long) / username / authorities(List<String>, 如 ROLE_ADMIN) / iat / exp`
- **自动配置**：common 库模块不加 `@Component`，用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册；Servlet 相关 Bean 加 `@ConditionalOnWebApplication(SERVLET)` 以免 reactive 网关误加载

---

## Step 1-2：基础设施 + 多模块骨架  ✅ 已完成

- docker-compose 起 MySQL/Nacos/Redis/MinIO，全部 healthy
- 建库 `detect_auth` / `detect_event`(`sql/init/01-databases.sql`)
- 父 pom：`dependencyManagement` 导入 SB/SC/SCA BOM + mybatis-plus/minio/hutool + 4 个内部 common 模块；全局 lombok(optional)
- 提交：`a10f910 feat：初始框架`

## Step 3：detect-common 四模块  ✅ 已完成(BUILD SUCCESS，11 单测全绿)

| 模块 | 关键产物 | 单测 |
|---|---|---|
| core | CommonConstants / ResultCode / R / PageResult / BizException / GlobalExceptionHandler / @Inner / InnerAspect / CoreAutoConfiguration | 3 |
| mybatis | BaseEntity(create_time/update_time/del_flag) / MetaObjectHandler 自动填充 / MybatisPlusConfig(分页+乐观锁插件) | 1 |
| security | LoginUser / SecurityUtils / DetectJwtAuthenticationConverter / 401·403 处理器 / PermitAllUrlProperties(扫描 @Inner) / 资源服务器自动配置 | 3 |
| oss | OssProperties / OssUtils(纯函数) / OssTemplate(MinIO 上传下载) / OssAutoConfiguration | 4 |

- **踩坑**：自建子模块 pom 初始为空壳(仅 parent+artifactId)，需逐个补 `dependencies`，否则"程序包不存在"
- **踩坑**：Maven 项目 Java 源码必须置于 `src/main/java/<包路径>` 下，IDE 脚手架残留的 `com/detect/Main.java` 已删除

## Step 4：detect-auth 认证服务器  ✅ 已完成(编译 + 7 单测 + 运行时端到端全绿)

### 架构决策(经用户确认)

1. **用户存储**：DB 表 `detect_auth.sys_user`(非内存/配置文件)
2. **密码授权**：轻量自定义 `POST /oauth/token` 密码端点(不引入完整 Spring Authorization Server)
3. **签名密钥**：持久化 keystore `jwt.jks`(RSA 2048，别名 `detect-jwt`，有效期 3650 天)

### 关键文件

- `AuthApplication`(`@SpringBootApplication` + `@MapperScan`)
- `entity/SysUser`(extends BaseEntity) + `mapper/SysUserMapper`
- `service/DetectUserDetails`(携带 userId 的 UserDetails) + `DetectUserDetailsService`(读 sys_user，角色映射 `ROLE_{role}`)
- `config/JwtKeyProperties`(前缀 `detect.auth.jwt`) + `JwtKeyConfig`(从 JKS 加载 nimbus `RSAKey`，fail-fast)
- `service/JwtService`(`NimbusJwtEncoder` 签发 + `jwkSet()` 仅暴露公钥)
- `web/OAuth2Controller`(`POST /oauth/token`、`GET /oauth2/jwks`，均免鉴权)
- `config/AuthSecurityConfig`(BCrypt + DaoAuthenticationProvider + 无状态 SecurityFilterChain)
- `config/AuthDataInitializer`(启动幂等播种 admin/123456，BCrypt，不硬编码哈希)
- `resources/application.yml`(端口 8081 / MySQL 3307 / jwt 配置，敏感项走环境变量默认值)
- `sql/init/02-auth-schema.sql`(sys_user 建表)

### 验证结果

- **编译 + 单测**：`mvn -pl detect-auth -am clean install` EXIT=0；7 单测全绿(JwtServiceTest 2 / DetectUserDetailsServiceTest 3 / OAuth2ControllerTest 2)
- **打包修复**：父 pom 的 `spring-boot-maven-plugin` 缺 `repackage` execution → 产出瘦 jar(23KB)；补 execution 后 fat jar 34.76MB + `.jar.original`
- **建表**：`docker cp` SQL 进容器 source，`sys_user` 建成
- **运行时**(java -jar 启动，3.96s)：
  - keystore 加载成功；AuthDataInitializer 播种 admin(逻辑删除自动追加 `WHERE del_flag='0'`，自动填充时间戳)
  - `POST /oauth/token`(admin/123456) → 合法 JWT，payload 声明 `{sub:admin, user_id:1, iss:http://localhost:8081, authorities:[ROLE_ADMIN], username:admin, iat, exp}` **完全匹配契约**
  - `GET /oauth2/jwks` → 仅公钥(kty/e/kid/n，无 d)
  - 错误密码 → HTTP 400 + `{"error":"invalid_grant"}`

### 安全备注

- `jwt.jks` 已被 `.gitignore` 排除(密钥不入库)；其他环境需用 keytool 生成或经 CI secret 注入
- keytool 生成命令见本步骤；keystore 密码默认 `detect123456`，生产须经 `JWT_KS_PASSWORD`/`JWT_KEY_PASSWORD` 环境变量覆盖

---

## Step 5：detect-gateway 网关  ✅ 已完成(编译 + 2 单测 + 运行时路由/CORS 全绿)

### 架构决策(经用户确认)

1. **轻量透传**：网关只做 路由 + CORS + 剥离外部 from 头；JWT 验签下沉到 detect-event(复用 common-security)，网关不依赖 jose
2. **Nacos 动态路由**：`lb://detect-auth`、`lb://detect-event`；auth/event 均注册 Nacos(给 auth 回填了 nacos-discovery)
3. **Python 直连 event**：webhook 不经网关；网关剥离外部 `from` 头，杜绝伪造 @Inner

### 关键文件

- `GatewayApplication`(响应式 Spring Cloud Gateway)
- `filter/RemoveFromHeaderGlobalFilter`(GlobalFilter，HIGHEST_PRECEDENCE，剥离 from 头防伪造 @Inner)
- `config/GatewayCorsConfig`(响应式 CorsWebFilter，allowedOriginPattern=* + allowCredentials)
- `application.yml`(端口 **9999** / Nacos discovery / 路由 auth+event / actuator)
- 路由：`/auth/**` → StripPrefix=1 → lb://detect-auth；`/admin/event/**` → StripPrefix=2 → lb://detect-event

### 关键决策与踩坑

- **响应式隔离**：common-core 仅依赖 spring-web(非 webmvc)、servlet-api 为 provided、CoreAutoConfiguration 为 `@ConditionalOnWebApplication(SERVLET)`，故网关复用 common-core(拿 CommonConstants.FROM 契约)不会引入 DispatcherServlet 而启动失败
- **端口冲突**：本机 VMware `vmnat` 服务常驻占用 8080，网关改用 pig 风格 9999
- **Nacos 注册 IP**：Windows 注册为 link-local `169.254.146.191`，本机可达、路由正常；若跨机不可达需 `spring.cloud.inetutils.preferred-networks` 或 `discovery.ip` 固定

### 验证结果

- **编译 + 单测**：`mvn -pl detect-gateway,detect-auth -am clean install` EXIT=0；RemoveFromHeaderGlobalFilterTest 2 单测全绿；fat jar gateway 48.87MB / auth 45.78MB(含 nacos)
- **运行时**(auth+gateway 双起，均注册 Nacos healthy)：
  - `POST 网关:9999/auth/oauth/token`(admin/123456) → HTTP 200 + 合法 JWT(lb://detect-auth 解析 + StripPrefix=1 生效)
  - Nacos 实例列表：detect-auth 1 健康实例(169.254.146.191:8081)
  - CORS 预检 OPTIONS(Origin http://localhost:3000) → 200 + Allow-Origin 回显 + Allow-Credentials true + Max-Age 3600

---

## Step 6a：detect-event 骨架  ✅ 已完成(编译 + fat jar + 启动注册 Nacos + 连库连 Redis)

### 架构决策(经用户确认)

1. **异步规则匹配 = Redis 轻量队列**：receive 入库后 `LPUSH` eventId，后台 `BRPOP` 消费触发规则匹配(6c 实现)，不阻塞 webhook
2. **通知扇出 = OpenFeign 调 auth 列管理员**：规则命中后经 Feign 取全部 ADMIN 用户，逐个写 `sys_notification`(6c 实现)
3. **camera_manage = 最小版 + seed**：文档 §3.2 引用但未给结构，建 `id/device_num(唯一)/device_name/location/status` + BaseEntity 三件套，seed `dev01~dev03`

### 关键文件

- `pom.xml`：4 个 common + web + nacos-discovery + data-redis + openfeign + loadbalancer + hutool + easyexcel + actuator + test；`finalName=detect-event`
- `EventApplication`(`@SpringBootApplication` + `@MapperScan("com.detect.event.mapper")` + `@EnableFeignClients`)
- `application.yml`(端口 8082 / Nacos / detect_event 库 / Redis / 资源服务器 `jwk-set-uri=http://localhost:8081/oauth2/jwks` / MinIO `detect.oss`)
- `sql/init/03-event-schema.sql`(5 表 + camera seed，幂等 `IF NOT EXISTS`/`INSERT IGNORE`)
- 5 实体：`EventRecords`/`AlertRule`/`CameraManage`(extends BaseEntity) + `AlertHandleRecord`/`SysNotification`(无 del_flag，不继承)
- 5 Mapper：均 extends `BaseMapper`
- 5 枚举：`EventTypeEnum`/`TaskTypeEnum`/`HandleStatusEnum`/`PriorityEnum`/`RuleTypeEnum`(带 `nameOf`/`isValid` 静态查名)

### 关键决策与踩坑

- **实体继承边界**：`alert_handle_record`/`sys_notification` 无 del_flag，若继承 BaseEntity 则 `@TableLogic` 会引用不存在的列 → 二者不继承；`SysNotification` 自带 `createTime` 加 `@TableField(fill=INSERT)` 复用公共 MetaObjectHandler
- **JSON 列以 String 承载**：`source_data`/`match_config`/`device_scope`/`time_scope` 存字符串，服务层用 hutool/Jackson 解析，规避 TypeHandler 复杂度
- **传递依赖陷阱**：common-core 的 hutool 为 `optional`、common-security 的 webmvc/jackson 为 `provided`(均不传递) → event 须显式加 web + hutool
- **平台级修复(common-security)**：`PermitAllUrlProperties` 原按类型注入 `RequestMappingHandlerMapping`，event 引入 actuator 后出现第 2 个 `controllerEndpointHandlerMapping` → 同类型 2 bean 歧义启动失败。修复：`@Qualifier("requestMappingHandlerMapping")` 锁定主 MVC 映射(@Inner 控制器注册于此)。auth(无 actuator)/gateway(响应式)不受影响，event 是首个带 actuator 的 Servlet 服务
- **父 pom 开启 `-parameters`**：未继承 spring-boot-starter-parent，显式为 maven-compiler-plugin 配 `<parameters>true</parameters>`，保障 Spring 6 按形参名解析(@PathVariable/@Qualifier)

### 验证结果

- **编译**：`mvn -pl detect-modules/detect-event -am clean install -DskipTests` EXIT=0；fat jar 100.47MB(含 easyexcel+POI)
- **建表**：`docker cp` + 容器内 `mysql < 03-event-schema.sql` EXIT=0；5 表建成，camera_manage seed dev01~dev03
- **运行时**(java -jar 启动，10.456s)：
  - `Tomcat started on port 8082`；`Started EventApplication`
  - Nacos 注册 `detect-event 169.254.146.191:8082 healthy=true`
  - `PermitAllUrlProperties: @Inner 放行 URL: []`(资源服务器自动配置生效，6a 暂无 @Inner 端点)
  - `GET /actuator/health` → `{"status":"UP"}`(聚合含 db/redis/discovery)
  - `HikariPool-1 - Start completed` + `Added connection com.mysql.cj.jdbc.ConnectionImpl` → detect_event 连通

---

## Step 6b：事件域  ✅ 已完成(6b-1 receive + 6b-2 查询/写侧，均运行时端到端验证)

### 6b-1 receive 写路径(Python 对接关键契约)  ✅ 已完成(4 单测 + 运行时端到端)

**契约核对**(读 `api/schema.py` make_record + `api/emitter.py` _post)：
- DTO `EventReceiveDTO` 字段严格对齐 make_record 输出的 22 个 camelCase 字段
- `snapTime` 为 ISO8601 带偏移(如 `...T08:49:50.123+08:00`)→ 归一化东八区 LocalDateTime 存 DATETIME(3)
- `snapImage` **可选**：Python 编码失败会传 null，强校验会致其无限重试+落盘 spool，故不 @NotBlank
- emitter 据 HTTP 2xx **且** body `code==0` 判成功；幂等命中也返回 code=0

**关键文件**：`dto/EventReceiveDTO`(仅 deviceNum/eventType/snapTime/sourceData 硬必填) / `vo/IdVO`(record) / `service/EventRecordService.receive` / `controller/EventRecordController`(`@Inner POST /event-records/receive`) / `test/EventRecordServiceTest`

**关键决策与踩坑**：
- **幂等键含 trackId**：同 device+同 snapTime 可能是同一帧多目标(trackId 不同)，仅用 device+snapTime 会误判；故去重键 = device_num + snap_time + source_data.trackId(trackId 在 JSON 内，先按前两列索引收窄再 Java 侧比对)。people_gathering 无 trackId 则以 null 参与比对
- **幂等短路**：命中重复直接返回已存在 id，**不重复转存 MinIO、不 insert**
- **派生字段以 Java 为准**：deviceName 查 camera_manage 覆盖入参、status 固定 0、handleStatus/priority 取默认(§5.3)

**验证结果**：
- 编译 + 单测：`mvn -pl detect-modules/detect-event clean install` EXIT=0；EventRecordServiceTest 4/4 绿
- 运行时(`@Inner 放行 URL: [/event-records/receive]`)：带 `from:Y` 首投→`{code:0,data:{id:1}}`；同 payload 再投→**同 id:1**(幂等，DB total_rows=1)；无 `from` 头→`{code:403,msg:"内部接口禁止外部访问"}`(InnerAspect 拦截)
- DB 落库核对：device_name=南河湫水闸(camera补全)、snap_time=2026-09-10 08:49:50.123(东八区)、snap_url=…/detect/event/20260911/xxx.jpg(MinIO转存)、status=0/handle_status=0/priority=0、SLAGTRUCK/3/87

### 6b-2 查询侧 + 写侧修正(§4.1.2~§4.1.8，7 端点)  ✅ 已完成(14 单测 + 读写运行时端到端)

**端点**(控制器 `EventRecordController`，除 receive@Inner 外均受资源服务器 `anyRequest().authenticated()` JWT 保护)：
- `GET /page` 分页(current/size/deviceNum/eventType/task/handleStatus/priority/plateNum 模糊/keyword 车牌 OR 设备名/startTime/endTime)，records 含派生 eventTypeName、task(取自 source_data JSON)、格式化 snapTime
- `GET /{id}` 详情：全字段 + sourceData 解析为对象 + hitRule{id,ruleName,ruleType} + handleHistory[]
- `PUT /{id}` 部分修正业务字段(不含 handleStatus，仅传需改字段)；不存在→1001
- `DELETE /{id}` 逻辑删；`DELETE /batch` body{ids:[]}→{deleted:N}
- `GET /statistics` {total,byEventType[],byTask[],byDay[]}；`GET /export` format=xlsx/csv(默认 xlsx)，同步上限 5 万条(超限→4001)

**关键文件**：`dto/`(EventQueryDTO/EventRecordUpdateDTO/BatchDeleteDTO) · `vo/`(EventRecordListVO/EventRecordDetailVO/HitRuleVO/HandleHistoryVO/DeletedVO/EventStatVO(record+3 嵌套)/EventExportRow(EasyExcel)) · `service/EventQueryService`(读侧 342 行) · `service/EventRecordService`(写侧扩 update/delete/batchDelete) · `test/`(EventQueryServiceTest 5 + EventRecordServiceTest +5=9)

**关键决策与踩坑**：
- **CQRS-lite 拆分**：写侧 EventRecordService(receive/update/delete/batchDelete) 与读侧 EventQueryService(page/detail/statistics/export) 分职，控制器注入两者
- **查询侧统一 QueryWrapper(字符串列名) 而非 Lambda**：需支持 source_data 的 MySQL JSON 函数过滤(`JSON_UNQUOTE(JSON_EXTRACT(source_data,'$.task'))`)与聚合投影(select+groupBy)，Lambda 的 select 仅收 SFunction 不支持原生聚合串
- **COUNT 防坑**：手工 selectCount 用「仅过滤」wrapper(不含 ORDER BY)——`SELECT COUNT(*) … ORDER BY` 在 ONLY_FULL_GROUP_BY 下非法；分页 selectPage 内置 count 由 MyBatis-Plus 自动剥离 ORDER BY，不受影响
- **统计聚合**：selectMaps + select 投影 + groupBy，别名用反引号包裹保留字(`COUNT(*) AS \`count\``、`DATE(snap_time) AS \`date\``)；Map key 为列标签(无下划线，不受 mapUnderscoreToCamelCase 影响)
- **部分更新**：`BeanUtil.copyProperties(dto,entity,CopyOptions.setIgnoreNullValue(true))` + updateById(默认 NOT_NULL 策略)，updateTime 由 MetaObjectHandler 自动留痕
- **导出先校验后设头**：先 selectCount(仅过滤)>50000 抛 4001，再设 contentType/Content-Disposition(URLEncoder 编码文件名)，最后 EasyExcel.write(response.getOutputStream())；异常时响应未提交，GlobalExceptionHandler 可回 JSON
- **VO 时间用 String 格式化**：snapTime/handleTime 按 §2.4 `yyyy-MM-dd HH:mm:ss` 输出，避免全局 Jackson 配置；sourceData 用 Object 承载解析后的 Map(解析失败回退原始串)

**🐛 重大踩坑：camera_manage 种子中文双重编码(已修复)**：
- **现象**：读侧运行时中文显示乱码。**字节级确证**：`HEX(device_name)`=`C3A5C28DE28094…`(å + U+008D + em dash)，应为 `南`=`E58D97` → 原始 UTF-8 被按 latin1(cp1252) 解码后再存 utf8mb4(双重编码)
- **根因**：Step 6a 执行 `03-event-schema.sql` 时 mysql 客户端连接字符集非 utf8mb4。**关键区分**：HTTP 响应字节检查证明 `eventTypeName 车辆(E8BDA6E8BE86)` 正确(6b-2 Java+Jackson 中文序列化无误)，`deviceName` 双重编码(代码只是忠实透传 DB 损坏值)——**bug 在种子数据，不在 6b-2 代码**
- **修复**：① `03-event-schema.sql` 加 `SET NAMES utf8mb4;` + camera seed 由 `INSERT IGNORE` 改 `ON DUPLICATE KEY UPDATE`(重跑自愈)；② `docker cp` + `mysql --default-character-set=utf8mb4 < /tmp/03.sql` 重跑；③ `UPDATE event_records e JOIN camera_manage c ON e.device_num=c.device_num SET e.device_name=c.device_name` 同步冗余字段。**验证**：HEX 全部转正确(dev01=E58D97E6B2B3E6B9ABE6B0B4E997B8=南河湫水闸)

**验证结果**：
- 编译 + 单测：`mvn -pl detect-modules/detect-event clean install` EXIT=0；14 单测全绿(EventQueryServiceTest 5 + EventRecordServiceTest 9)；fat jar 100.52MB
- 运行时**读侧**(真实 MySQL)：page total=4 按 snap_time DESC；`?task=license_plate`(JSON_EXTRACT 过滤)total=1；`?plateNum=JINGA`(LIKE)total=1；statistics total=4，byEventType/byTask/byDay 三维聚合排序正确；detail sourceData 解析为对象、hitRule=null、handleHistory 空
- 运行时**写侧**(curl --data-binary @file 规避 PS 引号 bug，DB HEX 确证)：
  - PUT/2 中文部分更新→code=0，DB `plate_num=京A12345`(HEX E4BAAC413132333435)、`vehicle_color=蓝色`(HEX E8939DE889B2) **单次编码正确**、`vehicle_normal_type=TRUCK` **未变**(部分更新成立)
  - PUT/999999→**1001**；无 token GET/page→**401**(JWT 保护)
  - export xlsx→4231B magic `50-4B`(PK/zip)；csv→436B magic `CA-C2-BC`(GBK `事件`，中文 Excel 友好)
  - DELETE/4→code=0；DELETE/batch[2,3]→deleted=2；page after→total=1(仅 id=1)；DB del_flag：id2/3/4=1、id1=0(逻辑删成立)

---

## Step 6c：规则域  ✅ 已完成(拆 6c-1 CRUD+引擎+试跑 / 6c-2 异步队列消费 / 6c-3 通知扇出)

### 6c-1 布控规则 CRUD + 匹配引擎 + 试跑(§4.2.1~§4.2.7，7 端点)  ✅ 已完成(27 单测 + 15 步运行时端到端 + 中文 HEX 确证)

**端点**(控制器 `AlertRuleController`，均受资源服务器 `anyRequest().authenticated()` JWT 保护)：
- `GET /alert-rules/page` 分页(current/size/ruleType/enabled/keyword 规则名模糊)，records 含派生 ruleTypeName、matchConfig 解析为对象
- `GET /alert-rules/{id}` 详情：全字段 + deviceScope/timeScope/matchConfig 解析为对象 + 格式化 createTime/updateTime；不存在→2002
- `POST /alert-rules` 新建：ruleName(必)/ruleType(必,isValid)/matchConfig(必,按 ruleType 校验结构)，默认 priority=1(IMPORTANT)/notifyEnabled=1/enabled=1；响应{id}；配置非法→2001
- `PUT /alert-rules/{id}` 部分更新：仅传需改字段；ruleName 传空串→400；按「生效后 ruleType+matchConfig」组合重校验(防只改其一漏检)
- `DELETE /alert-rules/{id}` 逻辑删(幂等)
- `PUT /alert-rules/{id}/toggle` body{enabled:0|1}；非 0/1→400
- `POST /alert-rules/match-test` body{ruleId,sampleEvent{...}}；响应{matched,reason}(§4.2.7，示例 reason=`crowdNum 12 命中阈值 >10`)；**不落库**(试跑用)

**关键文件**：
- 引擎：`service/RuleMatchInput`(record，归一化事件源，含 `from(EventRecords)` 供 6c-2 复用) · `service/MatchResult`(record，形状即 §4.2.7 响应{matched,reason}，hit/miss 工厂) · `service/RuleMatcher`(@Component 纯函数 204 行，门控 event_type→device_scope→time_scope(支持跨零点)→按 rule_type 核心匹配)
- DTO：`AlertRuleQueryDTO`/`AlertRuleSaveDTO`(matchConfig/deviceScope/timeScope 用 Object 承载)/`ToggleDTO`/`MatchTestDTO`(嵌套 SampleEvent)
- VO：`AlertRuleListVO`/`AlertRuleDetailVO`
- 服务：`service/AlertRuleService`(338 行) · 控制器：`controller/AlertRuleController`(7 端点)
- 测试：`test/RuleMatcherTest`(12) · `test/AlertRuleServiceTest`(15)

**关键决策与踩坑**：
- **匹配引擎纯函数化**：RuleMatcher 不校验 enabled(停用规则仍可试跑)、无副作用；match-test 与 6c-2 队列消费者复用同一引擎("所配即所判")
- **门控顺序**：event_type(大类) → device_scope(生效设备) → time_scope(生效时段，start>end 视为跨零点 `!t.isBefore(start)||!t.isAfter(end)`) → 按 rule_type 核心匹配；任一门控不符即短路 miss 并给出可读 reason
- **CROWD_THRESHOLD 表达式**：正则 `^\s*(>=|<=|==|>|<|=)\s*(-?\d+)\s*$` 解析 op+threshold，switch 比较；试跑 reason 与 §4.2.7 示例逐字对齐(`crowdNum 12 命中阈值 >10`)
- **🐛 hutool `parseObj(Object)` String 陷阱(设计规避)**：`JSONUtil.parseObj(o)` 当 o 运行时是 String 会走 parseObj(Object) 把 String 当 bean(错误)。`AlertRuleService.asJsonObject` 规范化：CharSequence→toString→parseObj(String)；Map/bean→toJsonStr→parseObj(String)，顺带保证嵌套 List→JSONArray。校验(validateMatchConfig)与读取(parseJson)统一走此路径
- **JSON 列以 String 承载**：match_config/device_scope/time_scope 存 String；出参 parseJson 解析为对象直出(JSONObject 实现 Map、JSONArray 实现 List，Jackson 直出)；`[`开头→parseArray 否则 parseObj，异常回退原串
- **校验全在服务层**：不依赖 bean-validation 运行时是否生效；ruleType 非法→2001、matchConfig 结构与 ruleType 不符→2001(PLATE_BLACKLIST/VEHICLE_TYPE/DEVICE_TIME 须非空数组，CROWD_THRESHOLD 须合法表达式)、ruleName 缺失/空串→400

**验证结果**：
- 编译 + 单测：`mvn -pl detect-modules/detect-event test` BUILD SUCCESS；41 单测全绿(RuleMatcher 12 + AlertRuleService 15 + EventQuery 5 + EventRecord 9)；fat jar 100.55MB
- 运行时(15 步冒烟，真实 MySQL + auth JWT)：
  - 建 CROWD_THRESHOLD/PLATE_BLACKLIST 规则→code=0 id=1/2；建配置非法规则(PLATE_BLACKLIST+crowdNum config)→**2001**
  - page total=2；detail rid1 **JSON 列往返**：matchConfig.crowdNum=`>10`、timeScope.start=`08:00`(存 String 出对象)
  - match-test：crowdNum=12→**matched=True**、crowdNum=5→matched=False、eventType 门控(规则限 300 样本 200,crowd=99)→matched=False
  - toggle off→detail enabled=0；update rename→code=0；无 token page→**401**；detail 999999→**2002**；delete rid2→code=0；page after→total=1(逻辑删)
- **中文 HEX 确证**(对比 6b-2 camera_manage 双重编码教训)：id=1 rule_name=`人群聚集预警(已改名)` HEX=`E4BABA·E7BEA4·E8819A·E99B86·E9A284·E8ADA6·28·E5B7B2·E694B9·E5908D·29`(每汉字 3 字节 UTF-8，`(`/`)`=ASCII 28/29，**无双重编码 C3A4…**)；id=2 `车牌黑名单布控` del_flag=1。alert_rule HTTP 写入路径中文单次编码正确

### 6c-2 异步规则匹配队列(Redis LPUSH/BRPOP + 命中落库 §5.3)  ✅ 已完成(12 单测 + 运行时端到端 + 中文 HEX 确证)

**机制**(§4.1.1「入库 → 异步触发布控规则匹配」+ §5.3 自动流转)：
- receive 入库后 `LPUSH detect:event:rule-match:queue eventId`(在 webhook 线程，不阻塞响应)
- `RuleMatchConsumer` 单守护线程 `BRPOP`(2s 超时)取出 eventId → `AlertMatchService.matchEvent`
- matchEvent：selectById(@TableLogic，已删/不存在返 null 跳过) → 载全部 enabled=1 规则(id ASC) → RuleMatcher 逐条匹配 → 取最高 priority 命中(平手取小 id) → 落库

**命中落库**(§5.3)：
- `event_records`：写 `hit_rule_id`=命中规则 id、`priority` 升级=规则 priority(命中后优先级)；`status` 保持 0(未推送)、`handle_status` 保持 0(未处理)——状态流转属 6d，推送属 6c-3
- `alert_handle_record`：自动生成一条系统留痕(handlerName=`系统`、fromStatus=null、toStatus=0、handleRemark=`规则命中：<规则名>（<reason>）`、handleTime=now)
- notify_enabled=1 的规则留 6c-3 扇出通知钩子(当前仅 debug 日志占位)

**关键文件**：`queue/RuleMatchQueue`(Redis List 封装 LPUSH/BRPOP，QUEUE_KEY 常量) · `queue/RuleMatchConsumer`(@PostConstruct 起守护线程 + @PreDestroy 优雅停 + 单条异常隔离) · `service/AlertMatchService`(matchEvent + persistHit) · `EventRecordService.receive`(注入 RuleMatchQueue，替换 TODO(6c) 为 push) · `test/RuleMatchQueueTest`(5) · `test/AlertMatchServiceTest`(7) · `EventRecordServiceTest`(+push/幂等不入队 验证)

**关键决策与踩坑**：
- **at-most-once 轻量队列**：BRPOP 弹出即移除，消费者处理中途宕机则该 eventId 丢失；取舍依据——规格仅要求「异步、不阻塞 webhook」，事件量低可接受(如需 at-least-once 须 BRPOPLPUSH 到「处理中」列表 + ack 回删，本期不做)
- **无读写竞态**：receive 无环绕事务，insert 自动提交后才 LPUSH，消费者 selectById 必见已落库行
- **多命中取舍**：规格未定，采「最高 priority，平手取小 id」——规则按 id ASC 载入 + 严格 `>` 比较，天然保留先到的最小 id，结果确定
- **priority 语义**：event.priority = 命中规则的 alert_rule.priority(「命中后优先级」)；事件默认 0，命中即升级
- **fromStatus=null**：系统留痕是「命中」审计注记而非人工状态流转(0→0 无意义)，故 fromStatus 置 null、toStatus=0(§5.3)
- **消费者线程模型**：单守护线程 BRPOP(2s 超时)循环——超时回到循环顶检查 running 标志，@PreDestroy 置 false + interrupt 即优雅退出；单条异常 catch 后继续下一条，不致命
- **构造器注入新增依赖须同步测试**：EventRecordService 加 RuleMatchQueue 后，EventRecordServiceTest 须补 `@Mock RuleMatchQueue`(否则 @InjectMocks 构造注入传 null，receive 调 push 时 NPE)

**验证结果**：
- 编译 + 单测：`mvn -pl detect-modules/detect-event test` BUILD SUCCESS；**53 单测全绿**(新增 RuleMatchQueue 5 + AlertMatchService 7，其余 41 保持)；重打 fat jar 重启(旧 jar 被运行进程锁，先 Stop-Process 释放再 package)
- 运行时(真实 Redis + MySQL，重启用 rid1 CROWD_THRESHOLD `>10` priority=2 timeScope 08:00-20:00)：
  - 队列 LLEN：投递前 0 → receive 两条(HIT crowdNum=15 得 eventId=5、MISS crowdNum=5 得 eventId=6) → 4s 后 **LLEN=0**(消费者全部取走)
  - **异步解耦确证**(日志线程名)：`[queue] LPUSH eventId=5` 在 webhook 线程 `nio-8082-exec-4`；`[match] 事件 5 命中规则 1` 在消费者线程 `-match-consumer`，晚 84ms
  - DB 命中落库：event 5 → `hit_rule_id=1`、`priority` 0→**2**、status=0、handle_status=0；event 6(未命中) → hit_rule_id=**NULL**、priority=**0**
  - alert_handle_record：仅 event 5 一条系统留痕 from_status=NULL/to_status=0/handler_name=`系统`、handle_remark=`规则命中：人群聚集预警(已改名)（crowdNum 15 命中阈值 >10）`；event 6 **无**记录
  - 6c-3 钩子日志：`[match] 规则 1 notify_enabled=1，事件 5 待 6c-3 扇出通知`
- **中文 HEX 确证**：handler_name=`系统` HEX=`E7B3BB·E7BB9F`(单次编码)；handle_remark 中文规则名 + reason 经 docker mysql utf8mb4 正确回显(控制台乱码仅 Windows codepage 显示假象，DB 字节正确)

### 6c-3 预警通知扇出(Feign→auth @Inner 列管理员 → sys_notification + status 置已推送 §5.3/§4.4)  ✅ 已完成(auth 2 + event 5 单测 + 运行时端到端 + 中文 HEX 确证)

**机制**(§5.3「notify_enabled=1 → 扇出站内通知」+ §4.4 通知域)：
- 6c-2 命中落库后，若规则 `notify_enabled=1` → `NotificationService.notifyRuleHit(record, rule, result)`
- Feign 调 auth `@Inner GET /inner/users/admins`(role=ADMIN 且 enabled=1，id ASC) 取管理员列表
- 每个管理员写一条 `sys_notification`(user_id / title=规则名 / content=设备标识+命中原因 / type=alert / biz_id=eventId / priority=规则priority / read_flag=0，create_time 自动填充)
- ≥1 条写入后 `event_records.status` 0→1(已推送)；无管理员/Feign 失败则不写不置(返 false，**不回滚命中**)

**关键文件**：
- auth 侧：`vo/AdminUserVO`(record，仅 id/username/nickname，不含 password/role) · `web/InnerUserController`(@Inner 列管理员) · `config/AuthSecurityConfig`(permitAll 加 `/inner/**`) · `test/InnerUserControllerTest`(2)
- event 侧：`dto/AdminUser`(@Data，Feign 反序列化目标) · `feign/InnerFeignConfig`(RequestInterceptor 补 `from:Y` 头) · `feign/AuthUserClient`(@FeignClient name=detect-auth，configuration=InnerFeignConfig) · `service/NotificationService`(扇出核心) · `AlertMatchService`(注入 NotificationService，替换 6c-2 TODO 钩子为 notifyRuleHit 调用) · `test/NotificationServiceTest`(5)

**关键决策与踩坑**：
- **@Inner 跨服务鉴权**：event→auth 的 Feign 调用须带 `from:Y` 头过 InnerAspect；采「客户端专属配置」——InnerFeignConfig **刻意不加 @Configuration**(否则被 @ComponentScan 收为全局，污染所有 Feign 客户端)，仅经 `@FeignClient(configuration=...)` 作用于 AuthUserClient
- **auth 无 common-security**：PermitAllUrlProperties(扫 @Inner 自动加白名单)仅资源服务器(event)有；auth 是认证服务器，其 @Inner 端点须**手动**加进 AuthSecurityConfig 的 permitAll(`/inner/**`)，否则被 `anyRequest().authenticated()` 拦为 401
- **通知失败不回滚命中**：notifyRuleHit 内部 try-catch，Feign 异常/无管理员均返 false，命中落库(hit_rule_id/priority/系统留痕)不受影响——通知是「尽力而为」旁路，不应因 auth 抖动丢失预警命中
- **status 语义**：event.status 是「推送状态」(0未推送/1已推送)，仅在 ≥1 条通知成功写入后置 1；与 handle_status(处理状态，属 6d 状态机)正交
- **content 组装**：`设备名(设备编号)：命中原因`(设备名取 record.deviceName，为空则仅编号)，如「南河湫水闸(dev01)：crowdNum 20 命中阈值 >10」；title=规则名
- **VO 最小暴露**：AdminUserVO 仅 id/username/nickname，绝不返回 password/role，遵循内部接口最小权限

**验证结果**：
- 编译 + 单测：`mvn -pl detect-auth,detect-modules/detect-event test` BUILD SUCCESS；**67 单测全绿**(auth 9=新增 InnerUserController 2 + 原 7；event 58=新增 NotificationService 5 + AlertMatchService 补 verify + 原 52)；两模块重打 fat jar 重启(先 Stop-Process 释放 jar 锁)
- 运行时(真实 auth+event+Redis+MySQL，规则 rid1 CROWD_THRESHOLD `>10` priority=2 notify_enabled=1 timeScope 08:00-20:00)：
  - **auth @Inner 端点**：`GET /inner/users/admins` 无 from:Y → `{code:403,msg:内部接口禁止外部访问}`(InnerAspect 拦截)；带 `from:Y` → `{code:0,data:[{id:1,username:admin,nickname:超级管理员}]}`(无 password/role)
  - **完整扇出链路**：`POST /event-records/receive`(crowdNum=20，dev01) → eventId=7 → 4s 后队列 LLEN=0(drain) → event 7 `status` 0→**1**、hit_rule_id=1、priority=2
  - **异步 + 跨服务确证**(日志)：扇出全在 event `-match-consumer` 线程；auth `[inner] 列管理员 1 个` 于 10:45:18.040(Feign 调用)与 event markPushed 10:45:18.061 时刻吻合
  - **sys_notification**：1 条 user_id=1 / type=alert / biz_id=7 / priority=2 / read_flag=0 / create_time 自动填充；alert_handle_record event 7 系统留痕 id=2 from_status=NULL/to_status=0/handler_name=`系统`
- **中文 HEX 确证**：title=`人群聚集预警(已改名)` HEX=`E4BABA·E7BEA4·E8819A·E99B86·E9A284·E8ADA6·28·E5B7B2·E694B9·E5908D·29`(单次编码)；content=`南河湫水闸(dev01)：crowdNum 20 命中阈值 >10` HEX=`E58D97·E6B2B3·E6B9AB·E6B0B4·E997B8·28·6465763031·29·EFBC9A(全角冒号)·63726F77644E756D·20·3230·20·E591BD·E4B8AD·E99888·E580BC·20·3E·3130`(单次编码，全角冒号 EFBC9A 正确)

---

## Step 6d：处理域  ✅ 已完成(拆 6d-1 状态机+处理写路径 / 6d-2 处理查询侧)

### 6d-1 状态机流转(§5.2) + 处理写路径(process/batch-process §4.3.2/§4.3.3)  ✅ 已完成(11 单测 + 8 步运行时端到端 + 中文 HEX 确证)

**状态机**(§5.2 矩阵)：`HandleStatusEnum.canTransition(from,to)` —— 0→{1,3}、1→{2,3}；2/3 终态无出边；对角线(同状态)、越界、null 一律非法。非法流转抛 `3001`(ResultCode.STATUS_TRANSITION_INVALID 已存在，无需新增)。

**处理写路径**：
- `POST /alert-handles/process`(§4.3.2)：校验事件存在(否则 1001) → 校验流转合法(否则 3001) → 更新 `event_records.handle_status` → 写 `alert_handle_record`(fromStatus/toStatus/handlerId/handlerName/handleRemark/handleTime)
- `POST /alert-handles/batch-process`(§4.3.3)：逐条自调用 process，捕获 BizException(1001/3001 均在写库前抛出) → 「非法跳过、合法提交」部分成功，返回 `{processed, skipped[{eventId,reason}]}`
- **处理人取自 JWT**：`SecurityUtils.getUserId()/getUsername()`(common-security)，不接受前端传入(防越权伪造)；handlerName=username(令牌不含 nickname)

**关键文件**：`enums/HandleStatusEnum`(+canTransition) · `dto/HandleProcessDTO`(eventId/toStatus @NotNull + remark) · `dto/BatchHandleDTO`(eventIds @NotEmpty) · `vo/BatchHandleResultVO`(record: processed + skipped[Skipped(eventId,reason)]) · `service/AlertHandleService`(process/batchProcess) · `controller/AlertHandleController`(2 POST) · `test/HandleStatusEnumTest`(6) · `test/AlertHandleServiceTest`(5)

**关键决策与踩坑**：
- **批量事务语义**：batchProcess 与 process 均 @Transactional；batchProcess 内部**自调用** process(不经 Spring 代理→不新开事务→共享本批事务)。校验类异常(1001/3001)在任何写库前抛出，被 catch 后不污染事务，合法条目在批末统一提交——天然实现「部分成功」且规避自调用事务失效陷阱
- **skipped 增强**：规格示例 `skipped:[]` 未定元素结构，本实现回报 `{eventId, reason}`(reason=错误码文案)以便前端提示，属兼容增强
- **toStatus 不加 @Min/@Max**：越界目标(如 9)由 canTransition 判非法→3001(与规格「非法流转返 3001」一致)，不返 400，统一裁决口径
- **mockStatic 测安全上下文**：SecurityUtils 为静态方法，Mockito 5(Boot 3.2.4)默认 inline mock maker 支持 `mockStatic`，无需额外依赖；校验类异常用例在取处理人前抛出，故 1001/3001 用例无需开 mockStatic
- **留痕时间显式写**：AlertHandleRecord 不继承 BaseEntity(无自动填充)，handleTime 由服务层 `LocalDateTime.now()` 显式写入

**验证结果**：
- 编译 + 单测：`mvn -pl detect-modules/detect-event test` BUILD SUCCESS；**69 单测全绿**(新增 HandleStatusEnum 6 + AlertHandleService 5，原 58 保持)；重打 fat jar 重启(暂停期两服务已停，重启 auth PID129788 + event PID136612)
- 运行时(真实 auth+event+MySQL，登录 admin/123456 取 JWT：user_id=1/authorities=ROLE_ADMIN)：
  - **合法流转**：event7 0→1 code=0、1→2 code=0(handle_status 终为 2)
  - **非法流转 3001**：event7 2→1(终态回退)、event5 0→2(跳级) 均 code=3001，msg「状态流转非法：已处理 → 处理中」/「未处理 → 已处理」，零写入
  - **事件不存在 1001**：event999999 → code=1001
  - **批量部分成功**：[1,6,999999]→3 → code=0 `{processed:2, skipped:[{eventId:999999,reason:事件不存在}]}`；event1/6 handle_status→3
  - **JWT 保护**：无 token process → HTTP=401 code=401「未认证」
  - **DB 留痕**：alert_handle_record 新增 4 条(id3~6) handler_id=1/handler_name=admin(取自 JWT)，from/to_status 与流转一致
- **中文 HEX 确证**：handle_remark「受理中，核实」HEX=`E58F97·E79086·E4B8AD·EFBC8C(全角逗号)·E6A0B8·E5AE9E`、「已核实渣土车违规，转执法」HEX=`E5B7B2·E6A0B8·E5AE9E·E6B8A3·E59C9F·E8BDA6·E8BF9D·E8A784·EFBC8C·E8BDAC·E689A7·E6B395`、「批量标记误报」均单次编码正确(handler_name=admin 为 ASCII，来自 JWT username)

### 6d-2 处理查询侧(todo/records/history/statistics §4.3.1/§4.3.4~§4.3.6)  ✅ 已完成(7 单测 + 8 步运行时端到端 + 中文确证 + statistics 逐项吻合 DB)

**四个查询端点**(CQRS-lite 查询侧 `AlertHandleQueryService`，与写侧 `AlertHandleService` 分离)：
- `GET /alert-handles/todo`(§4.3.1)：`handle_status ∈ {0,1}`(未处理/处理中)，按 `priority DESC, snap_time DESC`(紧急置顶)；结构同 §4.1.2 + `hitRuleName`(批量 `selectBatchIds` 解析避免 N+1)；支持 priority/eventType/deviceNum/snap_time 区间过滤
- `GET /alert-handles/records`(§4.3.4)：处理记录分页，按 `handle_time DESC, id DESC`(最新在前)；支持 eventId/handlerId/handle_time 区间过滤
- `GET /alert-handles/{eventId}/history`(§4.3.5)：单事件全部处理记录 `handle_time ASC`(时间正序)，复用 `HandleHistoryVO`；无记录/事件不存在均返空列表(规格未要求 1001)
- `GET /alert-handles/statistics`(§4.3.6)：处理效率统计五项

**关键文件**：`dto/TodoQueryDTO` · `dto/HandleRecordQueryDTO` · `vo/TodoVO`(§4.1.2 字段 + hitRuleName) · `vo/HandleRecordVO`(record) · `vo/HandleStatVO`(record，类注释文档化公式约定) · `service/AlertHandleQueryService`(todo/records/history/statistics + 私有 countEvents/avgHandleMinutes/toTodoVOs) · `controller/AlertHandleController`(+4 GET) · `test/AlertHandleQueryServiceTest`(7)

**关键决策与踩坑**：
- **statistics 公式为本期约定**(规格 §4.3.6 仅给字段名未定口径)：`pendingCount`/`processingCount`=事件 handle_status=0/1 计数(受 deviceNum + snap_time 区间过滤)；`falseRate`=误报(handle_status=3)/总数(0~1 两位小数，总数 0 取 0)；`todayResolved`=今日(handle_time ≥ 当日 00:00) to_status=2 处理记录数(不受 device/区间过滤，"今日"为字段固有语义)；`avgHandleMinutes`=已解决事件 snap_time→解决 handle_time 平均分钟(一位小数)
- **avgHandleMinutes 用 Java 计算避免 join**：先查 to_status=2 处理记录，再按 eventId 批量查事件 snap_time，`Duration.between` 求均值(负值/空样本丢弃)，规避 SQL 跨表聚合
- **todo 映射独立于 EventQueryService**：处理域内聚，少量映射重复可接受；hitRuleName 用 `selectBatchIds`(Set 去重)一次性解析避免 N+1
- **分页参数夹取**：current `Math.max(.,1)`、size `Math.min(Math.max(.,1),200)`(§2.3)；字面单段 `/todo`、`/records`、`/statistics` 与双段 `/{eventId}/history` 无路由冲突

**验证结果**：
- 编译 + 单测：`mvn -pl detect-modules/detect-event test` BUILD SUCCESS；**76 单测全绿**(新增 AlertHandleQueryService 7，原 69 保持)；重打 fat jar(13:06:53)重启 event PID136372(auth PID129788 不动)
- 运行时(真实 auth+event+MySQL，登录 admin/123456 取 JWT token_len=576)：
  - **todo**：仅返 id=5(handle_status=0)，`hitRuleName`="人群聚集预警(已改名)" 批量解析正确，eventTypeName="聚集"/task="people_gathering"/snapTime 格式化；`?priority=2` 过滤生效
  - **records**：total=6，序 id=6,5,4,3,2,1(handle_time DESC, id DESC)；`?eventId=7` 过滤 → 3 条(4,3,2)；handlerName="系统"(留痕)/"admin"(人工) 中文正确
  - **history(7)**：正序 3 条(to_status 0→1→2；系统/admin/admin)，handleRemark 全角标点「规则命中：…（crowdNum 20 命中阈值 >10）」单次编码正确
  - **statistics**：`{pendingCount:1, processingCount:0, todayResolved:1, falseRate:0.5, avgHandleMinutes:110.0}` —— 与 DB 状态推导值**逐项吻合**(pending=id5；ignored=id1,6→falseRate 2/4；todayResolved=id4 to_status=2；avg=event7 snap 11:00:00→handle 12:50:47=110 分钟)
  - **JWT 保护**：无 token todo → HTTP=401 code=401「未认证」

---

## Step 6e：通知 + 分类字典  ✅ 已完成(拆 6e-1 站内通知 / 6e-2 分类字典)

### 6e-1 站内通知 `/notifications`(§4.4，5 端点)  ✅ 已完成(8 单测 + 运行时 11 步 + 中文确证)

**5 端点**(user_id 一律从 JWT 取，用户只能操作自己的通知)：
- `GET /notifications/page`(§4.4.1)：本人通知分页，`create_time DESC, id DESC`；可按 readFlag(0/1)/type(alert/system) 过滤 → `PageResult<NotificationVO{id,title,content,type,bizId,priority,readFlag,createTime}>`
- `GET /notifications/unread-count`(§4.4.2)：本人 read_flag=0 计数 → `{count:N}`(前端红点)
- `PUT /notifications/{id}/read`(§4.4.3)：标记单条已读(写 read_time)；非本人/不存在报 404；已读则幂等直接成功(不覆盖首次 read_time)
- `PUT /notifications/read-all`(§4.4.4)：本人未读批量置已读 → `{read:N}`(本次转已读条数)
- `DELETE /notifications/{id}`(§4.4.5)：物理删(sys_notification 无 del_flag)，ownership-scoped；非本人/不存在报 404

**关键文件**：`dto/NotificationQueryDTO` · `vo/NotificationVO`(record) · `vo/UnreadCountVO`(record:count) · `vo/ReadAllVO`(record:read) · `service/NotificationService`(+page/unreadCount/markRead/markAllRead/delete，与 6c-3 扇出同类) · `controller/NotificationController`(5 端点) · `test/NotificationServiceTest`(+8=13)

**关键决策与踩坑**：
- **并入 NotificationService 而非新建查询服务**：通知域小(1 扇出 + 5 用户侧)、Mapper 已注入，保持域内聚；类文档扩为「扇出 + 用户侧」双职责(不同于 event/handle 大域的 CQRS 拆分)
- **ownership 越权统一 404**：非本人通知返 `NOT_FOUND(404,资源不存在)`而非 403——不泄露「该 id 存在但不属于你」的存在性；ResultCode 已有 404 无需新增码
- **markRead 幂等**：已读(read_flag=1)再标记直接成功、不覆盖首次 read_time；delete/markAllRead 用 ownership-scoped 原子 UPDATE/DELETE(WHERE user_id=当前)
- **markAllRead 返受影响行数**：`update(entity, wrapper)` 返回 int 即本次未读→已读条数，直接作 `{read:N}`

**验证结果**：
- 编译 + 单测：`mvn test` BUILD SUCCESS；NotificationServiceTest **13 绿**(5 扇出 + 8 用户侧：page scope+VO、unread-count、markRead 本人未读/非本人 404/已读幂等、markAllRead 返条数、delete 本人/非本人 404)；用户侧用 mockStatic 桩 SecurityUtils.getUserId
- 运行时(真实 auth+event+MySQL，DB 现存 id=1 user_id=1 read_flag=0)：
  - **page**：1 条(id=1,title=人群聚集预警(已改名),content=南河湫水闸(dev01)：crowdNum 20 命中阈值 >10,type=alert,bizId=7,priority=2,readFlag=0,createTime 格式化) 中文正确
  - **unread-count** {count:1} → **read-all** {read:1} → **unread-count** {count:0}(全部已读生效)；**单条 read**(已读后) code=0 幂等；**page?readFlag=1** 1 条 readFlag=1
  - **delete 999999** HTTP=200 code=404「资源不存在」；**delete 1** code=0 物理删；**page**(删后) total=0；**无 token** page → HTTP=401

### 6e-2 分类字典 `/event-categories`(§4.5，3 端点，只读枚举不建表)  ✅ 已完成(5 单测 + 运行时 4 步)

**3 端点**(字典以枚举为唯一真源，不查库)：
- `GET /event-categories/types`(§4.5.1)：事件大类 → `[{code,name}]`(EventTypeEnum：100人脸/200车辆/300聚集)
- `GET /event-categories/tasks`(§4.5.2)：事件子类，`?eventType` 可选按大类过滤 → `[{code,name,eventType}]`(TaskTypeEnum 6 项)
- `GET /event-categories/enums`(§4.5.3)：一次性全量 → `{eventType,task,handleStatus,priority,ruleType}`(前端启动缓存)

**关键文件**：`vo/EnumItemVO`(record:Object code+String name) · `vo/TaskItemVO`(record:String code+name+Integer eventType) · `vo/EventEnumsVO`(record:5 List) · `service/EventCategoryService`(无依赖，enum.values()→VO) · `controller/EventCategoryController`(3 GET) · `test/EventCategoryServiceTest`(5)

**关键决策与踩坑**：
- **EnumItemVO.code 用 Object**：eventType/handleStatus/priority 为 Integer code(序列化 JSON number)、ruleType 为 String code(序列化 JSON string)，一个 VO 兼容两型且输出与规格逐字一致
- **VO 分离避免 null 污染**：全局 Jackson **序列化 null**(实测 todo 响应含 snapUrl:null)，故 task 用独立 `TaskItemVO`(带 eventType)、其余用 `EnumItemVO`(无 eventType 字段)——否则 types/handleStatus 等会多出 `"eventType":null`
- **字典即枚举**：不建表、不查库，5 个既有枚举(EventType/TaskType/HandleStatus/Priority/RuleType)为唯一真源，前端翻译与下拉共用

**验证结果**：
- 编译 + 单测：`mvn test` BUILD SUCCESS；**89 单测全绿**(EventCategoryServiceTest 5：types 3 项、tasks 全量 6/eventType=200 过滤 4/=300 过滤 1、enums 五类条数 3/6/4/3/4 + 代表项 code·name)
- 运行时：types 3 项中文正确；tasks?eventType=200 → 4 项(vehicle_type/license_plate/plate_unrecognized/ship_plate 均 eventType=200)；enums 五类齐全，**Integer code 输出为数字(100/0)、String code 输出为字符串(PLATE_BLACKLIST)，无 eventType:null 污染**；无 token → HTTP=401

---

## Step 7：端到端联调验证(真实视频 testcar1.mp4)  ✅ 全链路贯通 + 修复 1 个真实 BUG

> 目标：不再手工构造 JSON，改跑**真实 Python 检测管线**(YOLO26 检测 testcar1.mp4 → EventEmitter webhook POST → Java 全链路)，验证「入库→规则命中→通知扇出→前端 JWT 查询」端到端贯通。非侵入契约不变(emitter 带 `from:Y`、据 `code==0` 判成功)。

### 7-1 可行性勘察
- **Python runner**：`Python310`(torch 2.12.0+**cpu** 无 GPU / ultralytics 8.4.66 / opencv 4.11.0)；`uv` 未装、Anaconda base 缺 DL 栈，故选定 Python310
- **视频**：`C:\Users\HP\Desktop\testcar1.mp4`，1920x1020 / 29.76fps / **981 帧 / 33 秒**(短，CPU 可跑完，`--vid_stride 3` 提速)
- **入口**：`main.py --source <video> --preset traffic --webhook-url http://127.0.0.1:8082/event-records/receive --device-id testcar-cam01 --output output/it`(traffic 预设→license_plate+vehicle_type，eventType=200)
- **隔离**：`--output output/it` 写入全新目录，绕开 output/ 下 9/6 陈旧数据(metadata/plates/.relay_cursors/未发 spool)，非破坏、零仓库污染(output/ 已 gitignore)
- **服务**：auth(8081)+event(8082) 在跑；gateway(9999) 未启但不需要(Python 直连 8082 receive @Inner 免鉴权、前端 JWT 查询也直连 8082)
- **DB 现状**：仅 CROWD 规则(id=1,eventType=300)活跃、PLATE_BLACKLIST(id=2)已软删 → car 事件(200)不命中任何现存规则

### 7-2 预建命中规则(保证链路贯通)
- car 事件(200)与现存人群规则(300)门控不符，跑视频前用 JWT `POST /alert-rules` 预建 **DEVICE_TIME 规则 id=3**(`{"deviceNum":["testcar-cam01"]}`,eventType=200,priority=2,notify=1,enabled=1)
- `POST /alert-rules/match-test` 预验证：matched=**True** reason="设备 testcar-cam01 在布控时段内"(不落库、零污染)
- 消费者每次 `selectList(enabled=1)` 动态加载规则 → 新建 id=3 无需重启即生效

### 7-3 跑批 + DB 全链路确证(中文 HEX 验证 UTF-8)
- 跑批 **133.1s** 完成：读 981 帧 / 检测 327 帧(vid_stride=3) / 丢弃 0 / 模型数 3；**车牌识别 1 个唯一车牌 `浙C6B5P8` color=蓝色 conf=0.904**；vehicle_type=none(普通轿车不在启用类别)；**emitter `{'sent':1,'spooled':0,'failed':0,'dropped':0}`**(sent=1 即收到 code==0)
- **event_records id=8**：event_type=200、plate_num HEX=`E6B599433642355038`→浙C6B5P8、**hit_rule_id=3**、**priority=2**(规则升级)、handle_status=0、**status=1(已推送)**、device_num=testcar-cam01、has_snap=1、snap_url=`http://localhost:9000/detect/event/20260912/05d3...5b54.jpg`
- **MinIO 对象物理确认**：容器内 `/data/detect/event/20260912/05d3801bfb5349beb1dab38b8ec95b54.jpg`(xl.meta+分片)；匿名 GET snap_url 返 403(桶私有、非 404→确已上传；生产走预签名/鉴权代理)
- **alert_handle_record id=7**：from=NULL→to=0、handler HEX=`E7B3BBE7BB9F`→**系统**、handle_time=15:12:30
- **sys_notification id=2**：uid=1(admin)、type=alert、biz_id=8、priority=2、read_flag=0；title HEX→**联调-设备布控(testcar)**、content HEX→**testcar-cam01：设备 testcar-cam01 在布控时段内**
- 链路：Python 检测真实车牌 → webhook(sent=1,code==0) → 入库+MinIO 转存 → 异步队列 → 命中规则3 → 升级 priority+系统留痕 → 扇出通知，DB 层逐项 HEX 坐实

### 7-4 前端 JWT 全端点查询
- **event page**：id=8 plateNum=浙C6B5P8、eventTypeName=车辆、task=license_plate、snapUrl、priority=2、hitRuleId=3 ✓
- **todo**：id=8 在待办、**hitRuleName=联调-设备布控(testcar)** 已批量解析(selectBatchIds 防 N+1) ✓
- **history**：系统留痕 handlerName=系统、remark=规则命中：联调-设备布控(testcar)(设备 testcar-cam01 在布控时段内) ✓
- **notification page**：id=2 title/content 中文正确、type=alert、bizId=8；unread count=1 ✓
- handle-statistics、event-categories enums(5类)、401 守卫、真实车牌 PLATE_BLACKLIST match-test(浙C6B5P8 命中 / 京ZZZZZZ 未命中) 均 ✓

### 7-5 🐛 真实数据暴露的 BUG + 修复(TDD)
- **现象**：`GET /event-records/8`(事件详情 §4.1.3)返 **HTTP 500 系统异常**(其余端点均正常)
- **根因**(日志堆栈锁定)：`EventQueryService.parseSourceData()` 用 hutool `JSONUtil.parseObj()` 返回 `cn.hutool.json.JSONObject`；真实 license_plate 事件的 source_data 含 `"trackId":null`/`"charConfidence":null`，hutool 把 JSON null 存为 `cn.hutool.json.JSONNull` **单例**，Jackson 无该类型序列化器 → 响应序列化 `InvalidDefinitionException: No serializer found for class cn.hutool.json.JSONNull (through reference chain: ...EventRecordDetailVO["sourceData"]->JSONObject["trackId"])` → 500
- **为何 6b-2 未暴露**：当时测试事件(people_gathering)的 source_data 无 null 值；真实车牌事件带 null 才触发。**同源潜在雷**：`AlertRuleService.parseJson()`(matchConfig/deviceScope/timeScope 解析进 AlertRuleListVO/DetailVO)同返 hutool JSON，规则配置一旦出现 JSON null 同样 500
- **修复**：两处解析改用静态 Jackson `ObjectMapper.readValue(str, Object.class)` → 产出标准 `LinkedHashMap`/`ArrayList`，JSON null 映射为 **Java null**(Jackson 原生可序列化)；`extractTask`/`RuleMatcher` 等仅取标量(getStr/getLong)的内部解析不受影响、不改
- **TDD**：先加 2 个回归测试(`EventQueryServiceTest.detail_sourceDataWithJsonNulls_isJacksonSerializable` + `AlertRuleServiceTest.detail_jsonColumnsWithNulls_areJacksonSerializable`)，RED 复现 `expected:<null> but was: cn.hutool.json.JSONNull@...`；改 Jackson 后 GREEN，**91 单测全绿**(89+2)BUILD SUCCESS
- **复验**(重打 jar 16:18:22 + 重启 event PID140316)：`GET /event-records/8` → **HTTP 200 code=0**，sourceData=`{"bbox":[],"task":"license_plate","trackId":null,"confidence":0.9037,"plateColor":"蓝色","plateNumber":"浙C6B5P8","hasPlateCrop":false,"charConfidence":null}`(trackId/charConfidence 正确 null、中文正确)，响应体 `contains 'JSONNull'=False`；plateNum HEX 仍=浙C6B5P8、hitRuleId=3、priority=2、handleHistory=1

**关键文件**(改动)：`service/EventQueryService`(parseSourceData→Jackson + 静态 MAPPER) · `service/AlertRuleService`(parseJson→Jackson + 静态 MAPPER) · `test/EventQueryServiceTest`(+1) · `test/AlertRuleServiceTest`(+1)

**结论**：真实视频 testcar1.mp4 驱动的**全链路端到端贯通**(Python 检测→webhook→入库+MinIO→规则命中→系统留痕→通知扇出→前端 JWT 查询)，并借真实数据发现+修复了手工构造 JSON 无法暴露的 hutool JSONNull 序列化 500 缺陷。Step 6(事件/规则/处理/通知/字典全域)+ 联调验证全部完成。

---

## 待办

- Step 6c：规则域 ✅ —— 6c-1(CRUD+引擎+试跑)✅ / 6c-2(异步队列 LPUSH/BRPOP + 命中落库 §5.3)✅ / 6c-3(Feign→auth @Inner 列管理员 → 写 sys_notification + status 置已推送 §5.3/§4.4)✅
- Step 6d：处理域 ✅ —— 6d-1(状态机 canTransition §5.2 + process/batch-process 写路径 §4.3.2/4.3.3)✅ / 6d-2(处理查询侧 todo/records/history/statistics §4.3.1/4.3.4~4.3.6)✅
- Step 6e：通知 + 分类字典 ✅ —— 6e-1(站内通知 /notifications 5 端点 §4.4：page/unread-count/{id}read/read-all/{id}删除，user_id 从 JWT + ownership 越权 404)✅ / 6e-2(分类字典 /event-categories 3 端点 §4.5：types/tasks/enums 只读枚举不建表)✅
- 联调：Python webhook → 入库 → 规则命中 → 通知 → 前端 JWT 查询全链路 ✅ —— 真实视频 testcar1.mp4 跑通(检出真车牌浙C6B5P8)，event id=8 命中规则3→priority升级→系统留痕→扇出通知 id=2，前端 JWT 各端点数据贯通；过程中发现并修复 hutool JSONNull 序列化 500 BUG(见 Step 7)
