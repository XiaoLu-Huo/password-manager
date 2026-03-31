# Password Manager 🔐

一个类似 1Password 的密码管理工具，帮助用户安全地生成、存储和管理各类账户的密码信息。

## 功能特性

- 主密码认证与 MFA（TOTP）双因素验证
- 密码学安全的随机密码生成（CSPRNG）
- AES-256-GCM 加密的凭证存储
- 凭证的增删改查与模糊搜索
- 密码安全性评估与安全报告
- 密码变更历史查询
- 加密 Excel 格式的数据导入/导出
- 自动锁定与会话管理

## 技术栈

| 层级　　　 | 技术　　　　　　　　　　　　　　　　|
| ------------| -------------------------------------|
| 语言　　　 | Java 17　　　　　　　　　　　　　　 |
| 框架　　　 | Spring Boot 3.2　　　　　　　　　　 |
| ORM　　　　| MyBatis-Plus 3.5　　　　　　　　　　|
| 数据库　　 | MySQL 8.0　　　　　　　　　　　　　 |
| 数据库迁移 | Flyway　　　　　　　　　　　　　　　|
| 密码哈希　 | Bouncy Castle (Argon2id)　　　　　　|
| 对称加密　 | JCE (AES-256-GCM)　　　　　　　　　 |
| TOTP　　　 | java-totp　　　　　　　　　　　　　 |
| Excel　　　| EasyExcel + Apache POI　　　　　　　|
| API 文档　 | SpringDoc OpenAPI　　　　　　　　　 |
| 构建工具　 | Gradle 8.x　　　　　　　　　　　　　|
| 测试　　　 | JUnit 5 + jqwik + Mockito + AssertJ |

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0
- Gradle 8.x（已内置 Wrapper）

### 数据库准备

```sql
CREATE DATABASE password_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 配置

编辑 `src/main/resources/application.yml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/password_manager
    username: your_username
    password: your_password
```

### 构建与运行

```bash
# 构建
./gradlew build

# 运行
./gradlew bootRun

# 仅运行测试
./gradlew test
```

启动后访问：
- API 服务：http://localhost:8080
- Swagger UI：http://localhost:8080/swagger-ui.html
- API 文档：http://localhost:8080/api-docs

## 项目结构

```
src/main/java/com/pm/passwordmanager/
├── controller/        # REST 控制器
├── service/           # 业务逻辑接口
│   └── impl/          # 业务逻辑实现
├── mapper/            # MyBatis-Plus Mapper
├── entity/            # 数据库实体
├── dto/
│   ├── request/       # 请求 DTO
│   └── response/      # 响应 DTO
├── config/            # 配置类
├── exception/         # 异常处理
├── enums/             # 枚举类
└── util/              # 工具类（加密引擎等）
```

## 安全架构

采用两层密钥体系：

- **KEK**（Key Encryption Key）：由主密码通过 Argon2id 派生，用于加密/解密 DEK
- **DEK**（Data Encryption Key）：随机生成的 AES-256 密钥，用于加密/解密凭证数据

修改主密码时只需用新 KEK 重新加密 DEK，无需重新加密所有凭证。

## License

MIT
