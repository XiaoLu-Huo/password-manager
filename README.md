# Password Manager 🔐

一个类似 1Password 的密码管理工具，帮助用户安全地生成、存储和管理各类账户的密码信息。

## 功能特性

- 主密码认证与 MFA（TOTP）双因素验证
- 密码学安全的随机密码生成（CSPRNG）
- AES-256-GCM 加密的凭证存储
- 凭证的增删改查与模糊搜索
- 标签分类与筛选
- 密码安全性评估与安全报告（弱密码 / 重复密码 / 超期未更新）
- 密码变更历史查询与明文查看
- 加密 Excel 格式的数据导入/导出（支持冲突策略）
- 自定义密码生成规则管理
- 自动锁定与会话管理（可配置超时时间）
- 账户锁定保护（连续失败自动锁定）

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2 |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0 |
| 数据库迁移 | Flyway |
| 密码哈希 | Bouncy Castle (Argon2id) |
| 对称加密 | JCE (AES-256-GCM) |
| TOTP | java-totp |
| Excel | EasyExcel + Apache POI |
| 对象映射 | MapStruct |
| API 文档 | SpringDoc OpenAPI |
| 构建工具 | Gradle 8.x |
| 测试 | JUnit 5 + jqwik (PBT) + Mockito + AssertJ |

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0
- Gradle 8.x（已内置 Wrapper）

### 数据库准备（Colima + Docker）

```bash
# 启动 Colima
colima start

# 启动 MySQL 8.0 容器
docker run -d \
  --name password-manager-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=password_manager \
  -e MYSQL_CHARSET=utf8mb4 \
  -v pm-mysql-data:/var/lib/mysql \
  mysql:8.0 \
  --character_set_server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

容器启动后数据库 `password_manager` 会自动创建，连接信息：

| 参数 | 值 |
|------|----|
| Host | localhost |
| Port | 3306 |
| 用户名 | root |
| 密码 | root |
| 数据库 | password_manager |

常用命令：

```bash
# 查看容器状态
docker ps

# 停止 / 启动
docker stop password-manager-mysql
docker start password-manager-mysql

# 连接数据库（需安装 mysql-client: brew install mysql-client）
mysql -h 127.0.0.1 -u root -proot password_manager
```

### 配置

默认配置已对应上述 Docker 环境，如需修改请编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/password_manager
    username: root
    password: root
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
