---
inclusion:manual/auto
---
# [项目名称] Java 后端开发规范

> 使用说明：将此模板复制到新项目的 `.kiro/steering/` 目录下，替换所有 `[占位符]` 为项目实际值，

> 删除不适用的章节，将 `inclusion` 改为 `auto` 即可生效。

> 带有 `<!-- 可选 -->` 标记的章节可根据项目实际情况决定是否保留。

## 1. 技术栈

- Java [17/21], Spring Boot [x.x.x]
- 构建工具：[Gradle x.x / Maven x.x]
- ORM：[MyBatis Plus x.x / JPA / MyBatis x.x]
- 数据库：[MySQL 8.0 / PostgreSQL x.x]，迁移工具：[Flyway / Liquibase]
- 缓存：[Redisson x.x / Spring Data Redis]，本地缓存：[Caffeine / Guava Cache]
- API 文档：[SpringDoc OpenAPI x.x / Swagger x.x]
- 工具库：Lombok [x.x], MapStruct [x.x]

<!-- 可选：微服务项目填写，单体项目删除此章节 -->

## 2. 微服务架构

### 2.1 服务清单

| 服务 | 职责 |

|------|------|

| [service-name] | [职责描述] |

| gateway | API 网关 |

### 2.2 注册中心与配置中心

- [Nacos x.x / Eureka / Consul / Kubernetes Service Discovery]

### 2.3 服务间通信

- 同步调用：[OpenFeign / RestTemplate / WebClient]
- 异步消息：[RocketMQ / Kafka / RabbitMQ]，集成方式：[Spring Cloud Stream / 原生 SDK]

### 2.4 共享库

-`commons`：[基础工具类、分页模型、异常基类等]

-`spring-commons`：[Spring 相关工具、Feign 配置、切面等]

-`xxx-service-api`：各服务对外暴露的接口和 DTO

### 2.5 任务调度 `<!-- 可选 -->`

- [XXL-Job / Spring Scheduler / Quartz]

## 3. 分层架构

<!-- 根据项目实际架构选择：DDD 四层 或 经典三层，删除不适用的方案 -->

### 方案 A：DDD 四层架构

```

com.[company].[project].[module]/

├── api/                          # 接口层（对外暴露）

│   ├── controller/               # REST 控制器

│   ├── dto/                      # 请求/响应 DTO

│   │   ├── request/              # 请求对象 (XxxRequest)

│   │   └── response/             # 响应对象 (XxxResponse)

│   ├── enums/                    # API 层枚举

│   └── mapper/                   # MapStruct DTO 映射接口

├── domain/                       # 领域层（核心业务逻辑）

│   ├── model/                    # 充血领域模型（包含业务行为）

│   ├── service/                  # 领域服务

│   ├── repository/               # 仓储接口（面向领域定义）

│   ├── command/                  # 命令对象

│   └── event/                    # 领域事件

├── infrastructure/               # 基础设施层（技术实现）

│   ├── repository/               # 仓储实现

│   ├── entity/                   # 数据库实体

│   ├── mapper/                   # ORM Mapper 接口

│   ├── assembler/                # Entity ↔ Domain Model 转换器

│   ├── client/                   # 外部服务调用

│   ├── listener/                 # 事件监听器

│   ├── message/                  # 消息处理

│   ├── job/                      # 定时任务

│   └── config/                   # 基础设施配置

├── config/                       # 应用配置

├── exception/                    # 异常定义

└── util/                         # 工具类

```

层间依赖规则：

- api → domain（允许）
- domain → 不依赖任何其他层（纯业务逻辑）
- infrastructure → domain（实现仓储接口，依赖反转）

### 方案 B：经典三层架构

```

com.[company].[project].[module]/

├── controller/                   # 控制器层

├── service/                      # 业务逻辑层

│   └── impl/                     # 服务实现

├── repository/ 或 dao/           # 数据访问层

├── entity/                       # 数据库实体

├── dto/                          # 数据传输对象

├── config/                       # 配置类

├── exception/                    # 异常定义

└── util/                         # 工具类

```

## 4. 编码规范

### 4.1 Controller 层

```java

@RestController

@RequestMapping("[resources]")

@RequiredArgsConstructor

@Tag(name = "[服务名称]")

publicclass XxxController {


privatefinalXxxServicexxxService;


    @Operation(summary ="[操作描述]")

    @PostMapping

publicPageResponse<XxxResponse> queryXxx(@Valid @RequestBodyXxxRequestrequest) {

returnxxxService.queryXxx(request);

    }

}

```

- 使用 `@RequiredArgsConstructor` 构造器注入，禁止 `@Autowired` 字段注入
- 使用 `@Operation` + `@Tag` 生成 API 文档
- 使用 `@Valid` / `@Validated` 进行参数校验
- Controller 只做参数接收和结果返回，不包含业务逻辑

### 4.2 领域模型（DDD 项目适用）

```java

@Getter

@SuperBuilder

@NoArgsConstructor

publicclass XxxModel {

privateLongid;

privateStringname;


    /** 业务行为封装在领域对象中. */

publicvoidchangeStatus(StatusnewStatus) {

// 状态校验与流转逻辑

    }

}

```

- 领域模型包含业务行为，不是贫血 POJO
- 使用 `@SuperBuilder` 支持继承链的 Builder 模式
- 聚合根负责管理子对象的一致性

### 4.3 仓储模式（DDD 项目适用）

```java

// domain/repository/ — 接口定义，面向领域模型

publicinterface XxxRepository {

Optional<Xxx> findById(Longid);

Xxxsave(Xxxxxx);

}


// infrastructure/repository/ — 实现，负责 Entity ↔ Domain Model 转换

@Repository

@RequiredArgsConstructor

publicclass XxxRepositoryImpl implements XxxRepository {

privatefinalXxxMapperxxxMapper;

privatefinalXxxAssemblerassembler;


    @Override

publicOptional<Xxx> findById(Longid) {

returnOptional.ofNullable(xxxMapper.selectById(id)).map(assembler::toDomain);

    }

}

```

### 4.4 数据库实体

<!-- MyBatis Plus 方案 -->

```java

@Getter

@Setter

@Builder

@NoArgsConstructor

@AllArgsConstructor

@TableName("[table_name]")

publicclass XxxEntity {

    @TableId(type =IdType.AUTO)

privateLongid;


    @TableField("[column_name]")

privateStringfieldName;

}

```

<!-- JPA 方案（二选一） -->

```java

@Entity

@Table(name = "[table_name]")

@Getter

@Setter

@NoArgsConstructor

publicclass XxxEntity {

    @Id

    @GeneratedValue(strategy =GenerationType.IDENTITY)

privateLongid;


    @Column(name ="[column_name]")

privateStringfieldName;

}

```

### 4.5 DTO 映射（MapStruct）

```java

@Mapper(componentModel = "spring")

publicinterface XxxDtoMapper {

XxxResponsetoResponse(XxxModelmodel);

XxxModeltoDomain(XxxRequestrequest);

}

```

### 4.6 领域事件（DDD 项目适用） `<!-- 可选 -->`

```java

publicclass XxxCreatedEvent {

privatefinalLongid;


publicstaticXxxCreatedEventof(Xxxxxx) {

returnnewXxxCreatedEvent(xxx.getId());

    }

}

```

- 提供静态工厂方法创建事件
- 事件监听器放在 `infrastructure/listener/`

### 4.7 异常处理

```java

// 统一业务异常

thrownewBusinessException(ErrorCode.NOT_FOUND, "资源未找到");

thrownewBusinessException(ErrorCode.INVALID_REQUEST, "参数无效", extraData);

```

- 错误码集中定义在 `exception/ErrorCode.java`
- 通过全局异常处理器 `@RestControllerAdvice` 统一返回格式
- 领域特定异常可单独定义，继承统一基类

### 4.8 命名规范

| 类型 | 后缀 | 位置 | 示例 |

|------|------|------|------|

| 控制器 | Controller | [api/]controller/ | OrderController |

| 请求 DTO | Request | [api/]dto/request/ | CreateOrderRequest |

| 响应 DTO | Response | [api/]dto/response/ | OrderDetailResponse |

| 领域模型 | 业务名称（无后缀） | domain/model/ | Order, Product |

| 服务类 | Service | [domain/]service/ | OrderService |

| 服务实现 | ServiceImpl | service/impl/ | OrderServiceImpl（三层架构） |

| 仓储接口 | Repository | [domain/]repository/ | OrderRepository |

| 仓储实现 | RepositoryImpl | infrastructure/repository/ | OrderRepositoryImpl |

| 数据库实体 | Entity | [infrastructure/]entity/ | OrderEntity |

| ORM Mapper | Mapper | [infrastructure/]mapper/ | OrderMapper |

| 对象转换器 | Assembler / DtoMapper | assembler/ 或 mapper/ | OrderAssembler |

| 领域事件 | Event | domain/event/ | OrderCreatedEvent |

| 定时任务 | Job / Handler | [infrastructure/]job/ | DataSyncJob |

| Feign 客户端 | Client | [infrastructure/]client/ | UserServiceClient |

### 4.9 通用编码原则

- 使用 `final` 修饰不可变字段和局部变量
- 使用 `Optional` 处理可能为 null 的返回值，禁止返回 null 集合（返回空集合）
- 使用 `@Slf4j` 记录日志，按 info/warn/error 分级
- 使用 `@Transactional` 管理事务，只在 Service 层标注，避免在 Controller 层使用
- 集合操作优先使用 Stream API
- 禁止在循环中进行数据库查询（N+1 问题）

## 5. 注释规范

- 注释语言：[中文 / 英文]
- 类注释：`/** 类功能描述. */`
- 字段注释：`/** 字段描述. */`
- public 方法注释：

```java

/**

* 方法功能描述.

* @param paramName 参数描述

* @return 返回值描述

* @throws XxxException 异常描述

*/

```

- 标记注释：`TODO`（待办）/ `FIXME`（缺陷）/ `NOTE`（重要说明）
- 避免无意义注释，复杂逻辑需详细说明

## 6. 数据库规范

- 表名使用 snake_case
- 主键字段 `id`，[bigint 自增 / UUID / 雪花算法]
- 审计字段：`created_by`, `created_at`, `updated_by`, `updated_at`
- 逻辑删除：[enable_flag (tinyint 1/0) / is_deleted (boolean) / deleted_at (datetime)]
- 数据库迁移：[Flyway / Liquibase]，脚本路径 `src/main/resources/db/migration/`
- 索引命名：`idx_[表名]_[字段名]`，唯一索引：`uk_[表名]_[字段名]`
- 禁止使用 `SELECT *`，明确指定查询字段
- 大表查询必须走索引，避免全表扫描

## 7. 服务间调用规范 `<!-- 可选：微服务项目填写 -->`

### 7.1 Feign 接口定义

```java

@FeignClient(name = "[service-name]", path = "/[base-path]")

publicinterface XxxServiceApi {

    @PostMapping("/[endpoint]")

ApiResponse<XxxDto> getXxx(@RequestBodyXxxRequestrequest);

}

```

### 7.2 依赖版本管理

- 各服务 api 模块版本在 [dependency-management.gradle / pom.xml parent] 中统一管理
- 版本变量定义在 [gradle.properties / pom.xml properties]

### 7.3 调用原则

- 服务间调用必须设置超时和重试策略
- 关键调用添加熔断降级（[Resilience4j / Sentinel]）
- 跨服务数据一致性通过 [Saga / 本地消息表 / 事务消息] 保证

## 8. 安全规范

- 敏感配置（数据库密码、API Key）通过 [环境变量 / 配置中心 / Vault] 管理，禁止硬编码
- API 认证：[JWT / OAuth2 / Session]
- 接口权限控制：[Spring Security / 自定义注解 + AOP]
- SQL 注入防护：使用参数化查询，禁止字符串拼接 SQL
- XSS 防护：输入参数校验和输出编码
- 敏感数据（手机号、身份证等）日志脱敏

## 9. 构建与质量

### 9.1 构建命令

<!-- Gradle 方案 -->

```shell

./gradlewcheck# 运行所有检查

./gradlewtest# 单元测试

./gradlewintegrationTest# 集成测试

./gradlewspotlessApply# 自动格式化

```

<!-- Maven 方案（二选一） -->

```shell

mvnverify# 运行所有检查

mvntest# 单元测试

mvnfailsafe:integration-test# 集成测试

```

### 9.2 质量门禁

- 静态分析：[PMD / SpotBugs / SonarQube]
- 代码风格：[Checkstyle / Spotless]
- 代码覆盖率：[Jacoco]，最低覆盖率 [xx]%
- Git Hooks：提交前自动检查

### 9.3 测试规范

- 单元测试：JUnit 5 + Mockito + AssertJ
- 集成测试：[Testcontainers / H2 / MariaDB4j] 嵌入式数据库
- 测试命名：`should_[预期行为]_when_[条件]`
- 测试结构：Given-When-Then 或 Arrange-Act-Assert

## 10. Git 提交规范

格式：`[commitType]: [TICKET-ID] commitMessage`

commitType 可选值：

-`feat`：新功能

-`fix`：修复缺陷

-`refactor`：重构（不改变行为）

-`docs`：文档变更

-`test`：测试相关

-`chore`：构建/工具变更

-`style`：代码格式（不影响逻辑）

示例：

-`feat: [PROJ-123] add order export functionality`

-`fix: [PROJ-456] fix null pointer in payment callback`

## 11. 新增功能开发检查清单

<!-- DDD 架构版本 -->

1. 在 `domain/model/` 创建或修改充血领域模型，业务逻辑内聚在模型中
2. 在 `domain/repository/` 定义仓储接口
3. 在 `domain/service/` 编写领域服务，编排领域模型和仓储
4. 在 `infrastructure/entity/` 创建数据库实体
5. 在 `infrastructure/mapper/` 创建 ORM Mapper
6. 在 `infrastructure/assembler/` 创建 Entity ↔ Domain Model 转换器
7. 在 `infrastructure/repository/` 实现仓储接口
8. 在 `api/dto/` 定义请求/响应 DTO
9. 在 `api/mapper/` 创建 MapStruct DTO 映射
10. 在 `api/controller/` 创建 Controller
11. 如需异步处理，在 `domain/event/` 定义领域事件，在 `infrastructure/listener/` 实现监听
12. 如需定时任务，在 `infrastructure/job/` 创建任务 Handler
13. 如需调用其他服务，引入对应 `xxx-service-api` 依赖
14. 数据库变更通过迁移脚本管理
15. 错误码在 `exception/ErrorCode.java` 中新增常量
16. 编写单元测试，确保核心逻辑覆盖

<!-- 三层架构版本（二选一）

1. 在 `entity/` 创建数据库实体

2. 在 `repository/` 或 `dao/` 创建数据访问接口

3. 在 `service/` 定义服务接口，在 `service/impl/` 编写实现

4. 在 `dto/` 定义请求/响应 DTO

5. 在 `controller/` 创建 Controller

6. 数据库变更通过迁移脚本管理

7. 错误码在 `exception/ErrorCode.java` 中新增常量

8. 编写单元测试

-->
