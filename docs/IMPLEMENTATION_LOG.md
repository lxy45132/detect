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
   └─ detect-event             事件业务(CRUD/规则/统计/导出) 事件域✅(6b)
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

## 待办

- Step 6c：规则域(CRUD + 引擎 + Redis 队列消费 + Feign 扇出通知 + auth @Inner 列管理员)
- Step 6d：处理域(状态机流转 + 处理记录留痕)
- Step 6e：通知(5) + 分类字典(3)
- 联调：Python webhook → 入库 → 前端 JWT 查询全链路
