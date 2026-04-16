---
inclusion: auto
---
# Password Manager Java 后端开发规范

## 1. 技术栈

- Java 17, Spring Boot 3.2.5
- 构建工具：Gradle 8.x
- ORM：MyBatis-Plus 3.5.6
- 数据库：MySQL 8.0，迁移工具：Flyway 9.x
- API 文档：SpringDoc OpenAPI 2.5.0
- 工具库：Lombok 1.18.38, MapStruct 1.5.5
- 加密：Bouncy Castle 1.78.1 (Argon2id), JCE (AES-256-GCM)
- TOTP：java-totp 1.7.1
- Excel：EasyExcel 3.3.4
- 测试：JUnit 5 + Mockito 5.18 + AssertJ + jqwik 1.8.5（属性测试）

## 2. DDD 四层架构

### 2.1 包结构

```
com.pm.passwordmanager/
├── api/                              # 接口层（对外暴露）
│   ├── controller/                   # REST 控制器
│   ├── dto/                          # 请求/响应 DTO
│   │   ├── request/                  # 请求对象 (XxxRequest)
│   │   └── response/                 # 响应对象 (XxxResponse, ApiResponse)
│   └── enums/                        # API 层枚举 (ConflictStrategy, PasswordStrengthLevel)
├── domain/                           # 领域层（核心业务逻辑）
│   ├── model/                        # 充血领域模型（包含业务行为）
│   ├── service/                      # 领域服务接口 + 实现
│   │   └── impl/                     # 领域服务实现
│   └── repository/                   # 仓储接口（面向领域定义）
├── infrastructure/                   # 基础设施层（技术实现）
│   ├── persistence/                  # 持久化相关
│   │   ├── entity/                   # 数据库实体 (XxxEntity)
│   │   ├── mapper/                   # MyBatis-Plus Mapper 接口
│   │   └── repository/              # 仓储实现 (XxxRepositoryImpl)
│   ├── encryption/                   # 加密基础设施
│   │   ├── EncryptionEngine.java     # AES-256-GCM 加密/解密
│   │   ├── Argon2Hasher.java         # Argon2id 哈希
│   │   └── SecureRandomUtil.java     # CSPRNG 工具
│   ├── totp/                         # TOTP 基础设施
│   │   └── TotpUtil.java             # TOTP 工具
│   ├── excel/                        # Excel 基础设施
│   │   └── ExcelEncryptionUtil.java  # Excel 加密/解密工具
│   └── config/                       # 基础设施配置
│       └── MyBatisPlusConfig.java    # MyBatis-Plus 配置
├── config/                           # 应用级配置（跨域、安全等）
├── exception/                        # 异常定义（全局共享）
│   ├── ErrorCode.java                # 错误码枚举
│   ├── BusinessException.java        # 统一业务异常
│   └── GlobalExceptionHandler.java   # 全局异常处理器
└── PasswordManagerApplication.java   # 启动类
```

### 2.2 层间依赖规则

- `api` → `domain`（允许，Controller 调用领域服务）
- `domain` → 不依赖 `api` 和 `infrastructure`（纯业务逻辑，通过仓储接口隔离）
- `infrastructure` → `domain`（实现仓储接口，依赖反转）
- `exception` 包全局共享，各层均可引用

### 2.3 领域划分

本项目按业务能力划分为以下领域：

| 领域 | 领域模型 | 领域服务 | 仓储接口 |
|------|----------|----------|----------|
| 认证 | User | AuthService, SessionService, MfaService | UserRepository, MfaConfigRepository |
| 凭证 | Credential | CredentialService | CredentialRepository |
| 密码生成 | PasswordRule | PasswordGeneratorService | PasswordRuleRepository |
| 密码历史 | PasswordHistory | PasswordHistoryService | PasswordHistoryRepository |
| 安全报告 | （无独立模型） | SecurityReportService | （复用 CredentialRepository） |
| 导入导出 | （无独立模型） | ImportExportService | （复用 CredentialRepository） |
| 密码强度 | （无独立模型） | PasswordStrengthEvaluator | （无） |

## 3. 编码规范

### 3.1 Controller 层

```java
@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
@Tag(name = "凭证管理", description = "凭证 CRUD、搜索与标签筛选接口")
public class CredentialController {

    private final CredentialService credentialService;
    private final UserMapper userMapper;

    @PostMapping
    @Operation(summary = "创建凭证", description = "创建新凭证，支持自动生成密码")
    public ApiResponse<CredentialResponse> create(@Valid @RequestBody CreateCredentialRequest request) {
        Long userId = getCurrentUserId();
        return ApiResponse.success(credentialService.createCredential(userId, request));
    }
}
```

规则：
- 使用 `@RequiredArgsConstructor` 构造器注入，禁止 `@Autowired` 字段注入
- 使用 `@Operation` + `@Tag` 生成 API 文档
- 使用 `@Valid` / `@Validated` 进行参数校验
- Controller 只做参数接收、用户身份获取和结果返回，不包含业务逻辑
- 统一返回 `ApiResponse<T>` 包装

### 3.2 领域模型

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credential {

    private Long id;
    private Long userId;
    private String accountName;
    private String username;
    private byte[] passwordEncrypted;
    private byte[] iv;
    private String url;
    private String notes;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 验证必填字段完整性。 */
    public void validateRequiredFields(String plainPassword) {
        if (isBlank(accountName) || isBlank(username) || isBlank(plainPassword)) {
            throw new BusinessException(ErrorCode.CREDENTIAL_REQUIRED_FIELDS_MISSING);
        }
    }

    /** 检查标签是否包含指定值。 */
    public boolean hasTag(String tag) {
        if (tags == null || tag == null) return false;
        return List.of(tags.split(",")).stream()
                .map(String::trim)
                .anyMatch(t -> t.equalsIgnoreCase(tag));
    }
}
```

规则：
- 领域模型包含业务行为（验证、状态流转等），不是贫血 POJO
- 使用 `@Builder` 构建对象
- 业务规则校验内聚在领域模型中

### 3.3 仓储模式

```java
// domain/repository/ — 接口定义，面向领域模型
public interface CredentialRepository {
    Credential save(Credential credential);
    Optional<Credential> findById(Long id);
    List<Credential> findByUserId(Long userId);
    List<Credential> searchByKeyword(Long userId, String keyword);
    List<Credential> filterByTag(Long userId, String tag);
    void deleteById(Long id);
}

// infrastructure/persistence/repository/ — 实现
@Repository
@RequiredArgsConstructor
public class CredentialRepositoryImpl implements CredentialRepository {

    private final CredentialMapper credentialMapper;

    @Override
    public Optional<Credential> findById(Long id) {
        return Optional.ofNullable(credentialMapper.selectById(id))
                .map(this::toDomain);
    }

    // Entity ↔ Domain Model 转换方法内聚在 RepositoryImpl 中
    private Credential toDomain(CredentialEntity entity) { ... }
    private CredentialEntity toEntity(Credential model) { ... }
}
```

规则：
- 仓储接口定义在 `domain/repository/`，面向领域模型
- 仓储实现在 `infrastructure/persistence/repository/`，负责 Entity ↔ Domain Model 转换
- 转换逻辑简单时直接在 RepositoryImpl 中用私有方法实现，复杂时可抽取 Assembler 类

### 3.4 领域服务

```java
// domain/service/ — 接口
public interface CredentialService {
    CredentialResponse createCredential(Long userId, CreateCredentialRequest request);
    List<CredentialListResponse> listCredentials(Long userId);
    // ...
}

// domain/service/impl/ — 实现
@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {

    private final CredentialRepository credentialRepository;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;

    @Override
    @Transactional
    public CredentialResponse createCredential(Long userId, CreateCredentialRequest request) {
        // 编排领域模型和仓储
    }
}
```

规则：
- 服务接口定义在 `domain/service/`，实现在 `domain/service/impl/`
- 使用 `@Transactional` 管理事务，只在 Service 层标注
- 领域服务编排领域模型、仓储和基础设施组件

### 3.5 数据库实体

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("credential")
public class CredentialEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_name")
    private String accountName;

    // ...
}
```

规则：
- 实体使用 `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`
- 使用 `@TableName` 指定表名（不含 `pm_` 前缀，由 MyBatis-Plus 全局配置 `table-prefix: pm_` 自动添加）
- 使用 `@TableField` 显式映射列名
- 实体是纯数据载体，不包含业务逻辑

### 3.6 MyBatis-Plus Mapper

```java
@Mapper
public interface CredentialMapper extends BaseMapper<CredentialEntity> {
}
```

规则：
- 继承 `BaseMapper<T>`，利用 MyBatis-Plus 内置 CRUD
- 复杂查询使用 `LambdaQueryWrapper` 在仓储实现中构建
- Mapper XML 放在 `classpath:/mapper/` 下（仅在需要复杂 SQL 时使用）

### 3.7 异常处理

```java
// 抛出业务异常
throw new BusinessException(ErrorCode.CREDENTIAL_NOT_FOUND);
throw new BusinessException(ErrorCode.SAME_PASSWORD);
```

规则：
- 错误码集中定义在 `exception/ErrorCode.java`，按模块分段编号
- 通过 `GlobalExceptionHandler`（`@RestControllerAdvice`）统一捕获并返回 `ApiResponse`
- 不在 Controller 中 try-catch 业务异常

### 3.8 命名规范

| 类型 | 后缀 | 位置 | 示例 |
|------|------|------|------|
| 控制器 | Controller | api/controller/ | CredentialController |
| 请求 DTO | Request | api/dto/request/ | CreateCredentialRequest |
| 响应 DTO | Response | api/dto/response/ | CredentialResponse |
| 统一响应 | ApiResponse | api/dto/response/ | ApiResponse\<T\> |
| 领域模型 | 业务名称（无后缀） | domain/model/ | Credential, User |
| 领域服务接口 | Service | domain/service/ | CredentialService |
| 领域服务实现 | ServiceImpl | domain/service/impl/ | CredentialServiceImpl |
| 仓储接口 | Repository | domain/repository/ | CredentialRepository |
| 仓储实现 | RepositoryImpl | infrastructure/persistence/repository/ | CredentialRepositoryImpl |
| 数据库实体 | Entity | infrastructure/persistence/entity/ | CredentialEntity |
| ORM Mapper | Mapper | infrastructure/persistence/mapper/ | CredentialMapper |
| 加密工具 | （按职责命名） | infrastructure/encryption/ | EncryptionEngine, Argon2Hasher |
| 枚举 | （按业务命名） | api/enums/ | ConflictStrategy, PasswordStrengthLevel |

### 3.9 通用编码原则

- 使用 `@RequiredArgsConstructor` + `private final` 进行构造器注入
- 使用 `Optional` 处理可能为 null 的返回值，禁止返回 null 集合（返回空集合）
- 使用 `@Slf4j` 记录日志，按 info/warn/error 分级
- 集合操作优先使用 Stream API
- 禁止在循环中进行数据库查询（N+1 问题）
- 密码和密钥相关数据使用 `byte[]` 传递，避免不必要的 String 转换

## 4. 注释规范

- 注释语言：中文
- 类注释：`/** 类功能描述。 */`
- public 方法注释：

```java
/**
 * 方法功能描述。
 *
 * @param paramName 参数描述
 * @return 返回值描述
 */
```

- 标记注释：`TODO`（待办）/ `FIXME`（缺陷）/ `NOTE`（重要说明）
- 避免无意义注释，复杂业务逻辑和加密流程需详细说明

## 5. 数据库规范

- 表名使用 snake_case，统一前缀 `pm_`（由 MyBatis-Plus `table-prefix` 配置）
- 主键字段 `id`，bigint 自增
- 审计字段：`created_at`, `updated_at`（使用 MySQL `DEFAULT CURRENT_TIMESTAMP` 和 `ON UPDATE CURRENT_TIMESTAMP`）
- 数据库迁移：Flyway，脚本路径 `src/main/resources/db/migration/`，命名 `V{n}__{description}.sql`
- 索引命名：`idx_pm_{表名}_{字段名}`，唯一索引：`uk_pm_{表名}_{字段名}`
- 字符集：`utf8mb4`，引擎：`InnoDB`
- 加密字段使用 `BLOB` 类型存储

## 6. 安全规范

- 主密码使用 Argon2id 哈希存储，不存储明文
- 凭证密码使用 AES-256-GCM 加密，每条凭证独立 IV
- 采用 KEK/DEK 两层密钥架构：KEK 由主密码派生，DEK 随机生成
- DEK 仅在会话期间存于内存，锁定时立即清除
- 使用 `java.security.SecureRandom` 生成所有随机数
- 密码比较使用常量时间算法，防止时序攻击
- SQL 注入防护：使用 MyBatis-Plus 参数化查询，禁止字符串拼接 SQL

## 7. 构建与测试

### 7.1 构建命令

```shell
./gradlew compileJava       # 编译
./gradlew test              # 运行所有测试（JUnit 5 + jqwik）
./gradlew build             # 完整构建
```

### 7.2 测试规范

- 单元测试：JUnit 5 + Mockito + AssertJ
- 属性测试：jqwik 1.8.5，每个属性最少 100 次迭代
- 测试命名：`should_[预期行为]_when_[条件]`
- 属性测试标签：`@Label("Feature: password-manager, Property {N}: {描述}")`
- 测试目录结构镜像源码结构：
  - `src/test/java/com/pm/passwordmanager/util/` — 工具类测试
  - `src/test/java/com/pm/passwordmanager/service/impl/` — 服务层测试（含属性测试）
  - `src/test/java/com/pm/passwordmanager/controller/` — 控制器测试
- 属性测试使用 `@Property(tries = 100)` + `@Provide` 自定义生成器
- 测试引擎配置：`useJUnitPlatform { includeEngines 'junit-jupiter', 'jqwik' }`

## 8. 新增功能开发检查清单

1. 在 `domain/model/` 创建或修改充血领域模型，业务规则内聚在模型中
2. 在 `domain/repository/` 定义仓储接口
3. 在 `domain/service/` 定义领域服务接口，在 `domain/service/impl/` 编写实现
4. 在 `infrastructure/persistence/entity/` 创建数据库实体
5. 在 `infrastructure/persistence/mapper/` 创建 MyBatis-Plus Mapper
6. 在 `infrastructure/persistence/repository/` 实现仓储接口（含 Entity ↔ Domain Model 转换）
7. 在 `api/dto/request/` 和 `api/dto/response/` 定义请求/响应 DTO
8. 在 `api/controller/` 创建 Controller
9. 数据库变更通过 Flyway 迁移脚本管理
10. 错误码在 `exception/ErrorCode.java` 中新增常量
11. 编写属性测试验证核心正确性属性，编写单元测试覆盖边界条件
