# 实现计划：密码管理器（Password Manager）

## 概述

按照从基础设施到核心功能、从后端到前端的顺序，逐步实现密码管理器的完整功能。后端使用 Java 17 + Spring Boot 3 + MySQL 8.0 + MyBatis-Plus + Gradle 构建，前端使用 Electron + React + TypeScript，浏览器插件使用 Chrome Extension Manifest V3。

## 任务

- [-] 1. 搭建后端项目基础设施
  - [x] 1.1 初始化 Spring Boot 3 + Gradle 项目结构
    - 创建 Gradle 项目，配置 `build.gradle` 引入 Spring Boot 3、MyBatis-Plus、Bouncy Castle、jqwik、JUnit 5、Mockito、Lombok、MapStruct、SpringDoc OpenAPI、EasyExcel、java-totp 等依赖
    - 创建包结构：controller、service、service/impl、mapper、entity、dto/request、dto/response、config、exception、enums、util
    - 配置 `application.yml`（MySQL 数据源、MyBatis-Plus、服务端口等）
    - _需求: 全局基础设施_

  - [x] 1.2 创建 Flyway 数据库迁移脚本
    - 编写 `V1__init_schema.sql`，包含 `pm_user`、`pm_credential`、`pm_password_history`、`pm_password_rule`、`pm_mfa_config` 五张表的 DDL
    - 包含索引定义
    - _需求: 全局数据模型_

  - [x] 1.3 实现统一响应包装与全局异常处理
    - 创建 `ApiResponse<T>` 统一响应类
    - 创建 `ErrorCode` 错误码枚举（包含所有错误码定义）
    - 创建 `BusinessException` 业务异常类
    - 创建 `GlobalExceptionHandler` 全局异常处理器
    - _需求: 全局错误处理_

  - [x] 1.4 创建数据库实体与 MyBatis-Plus Mapper
    - 创建 `UserEntity`、`CredentialEntity`、`PasswordHistoryEntity`、`PasswordRuleEntity`、`MfaConfigEntity`
    - 创建对应的 Mapper 接口：`UserMapper`、`CredentialMapper`、`PasswordHistoryMapper`、`PasswordRuleMapper`、`MfaConfigMapper`
    - 配置 `MyBatisPlusConfig`（分页插件等）
    - _需求: 全局数据模型_

  - [x] 1.5 创建所有 DTO（Request/Response）类
    - 创建 request 包下所有请求 DTO：`CreateMasterPasswordRequest`、`UnlockVaultRequest`、`VerifyTotpRequest`、`EnableMfaRequest`、`GeneratePasswordRequest`、`CreateCredentialRequest`、`UpdateCredentialRequest`、`SearchCredentialRequest`、`ImportRequest`、`ExportRequest`、`UpdateSettingsRequest`
    - 创建 response 包下所有响应 DTO：`UnlockResultResponse`、`MfaSetupResponse`、`GeneratedPasswordResponse`、`CredentialResponse`、`CredentialListResponse`、`SecurityReportResponse`、`PasswordStrengthResponse`、`PasswordHistoryResponse`、`ExportResultResponse`
    - 创建枚举类：`ConflictStrategy`、`PasswordStrengthLevel`
    - _需求: 全局接口定义_

- [ ] 2. 实现加密引擎与安全工具
  - [x] 2.1 实现 Argon2id 哈希工具 (`Argon2Hasher`)
    - 使用 Bouncy Castle 实现 Argon2id 哈希、验证和密钥派生
    - 实现 `hash()`、`verify()`、`deriveKey()`、`generateSalt()` 方法
    - _需求: 1.6, 1.7_

  - [x] 2.2 编写 Argon2Hasher 属性测试
    - **Property 2: 认证正确性（往返属性）**
    - 验证：对任意密码进行哈希后，使用相同密码验证应成功，使用不同密码验证应失败
    - **验证需求: 1.3, 1.4**

  - [x] 2.3 实现 AES-256-GCM 加密引擎 (`EncryptionEngine`)
    - 实现 `encrypt()`、`decrypt()`、`generateDek()`、`generateIv()` 方法
    - 使用 JCE 的 AES/GCM/NoPadding 模式，IV 长度 96-bit
    - _需求: 1.6, 3.3, 3.7_

  - [x] 2.4 编写凭证加密往返属性测试
    - **Property 8: 凭证加密往返**
    - 验证：对任意数据使用 DEK 加密后再解密，应得到原始数据
    - **验证需求: 3.3**

  - [x] 2.5 实现密码强度评估器 (`PasswordStrengthEvaluator`)
    - 实现强度评估规则：长度 < 8 → 弱；8-15 且 ≥ 2 种字符类型 → 中；≥ 16 且 ≥ 3 种字符类型 → 强
    - _需求: 6.1_

  - [x] 2.6 编写密码强度评估属性测试
    - **Property 13: 密码强度评估规则**
    - 验证：对任意密码字符串，评估结果应符合长度和字符类型的组合规则
    - **验证需求: 6.1**

  - [x] 2.7 实现 TOTP 工具 (`TotpUtil`)
    - 使用 java-totp 库实现 TOTP 密钥生成、二维码 URI 生成、验证码验证
    - _需求: 1.9, 1.10, 1.11_

  - [x] 2.8 实现 CSPRNG 工具 (`SecureRandomUtil`)
    - 基于 `java.security.SecureRandom` 封装密码学安全随机数生成
    - _需求: 2.9_

- [x] 3. 检查点 - 确保加密引擎和工具类测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [ ] 4. 实现用户认证与主密码管理
  - [x] 4.1 实现 `AuthService` 及 `AuthServiceImpl`
    - 实现首次创建主密码流程：验证复杂度 → 生成 salt → Argon2id 哈希 → 生成 DEK → 用 KEK 加密 DEK → 存储
    - 实现解锁密码库流程：检查锁定状态 → 验证密码 → 派生 KEK → 解密 DEK → 存入会话
    - 实现连续失败锁定逻辑（5 次失败锁定 15 分钟）
    - _需求: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

  - [x] 4.2 编写主密码复杂度验证属性测试
    - **Property 1: 主密码复杂度验证**
    - 验证：主密码长度 ≥ 12 且包含至少三种字符类型时通过，否则拒绝
    - **验证需求: 1.2**

  - [x] 4.3 编写连续失败锁定属性测试
    - **Property 3: 连续失败锁定**
    - 验证：连续 5 次错误密码后账户锁定 15 分钟
    - **验证需求: 1.5**

  - [x] 4.4 实现 `MfaService` 及 `MfaServiceImpl`
    - 实现启用 MFA：生成 TOTP 密钥、二维码、恢复码
    - 实现 TOTP 验证逻辑
    - 实现禁用 MFA
    - _需求: 1.8, 1.9, 1.10, 1.11, 1.12, 1.13_

  - [x] 4.5 编写 MFA 双因素强制属性测试
    - **Property 4: MFA 双因素强制**
    - 验证：启用 MFA 后，仅主密码不足以解锁，需同时提供有效 TOTP 码
    - **验证需求: 1.10, 1.11**

  - [x] 4.6 实现 `SessionService` 及 `SessionServiceImpl`
    - 管理内存中的 DEK 会话
    - 实现自动锁定超时检测
    - 实现锁定时清除内存中所有解密数据
    - _需求: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 4.7 编写自动锁定超时范围验证属性测试
    - **Property 18: 自动锁定超时范围验证**
    - 验证：1-60 分钟范围内接受，超出范围拒绝
    - **验证需求: 8.2**

  - [x] 4.8 编写密码库锁定后会话清除属性测试
    - **Property 19: 密码库锁定后会话清除**
    - 验证：锁定后 DEK 被清除，后续凭证访问被拒绝
    - **验证需求: 8.3, 8.5**

  - [x] 4.9 实现 `AuthController`
    - 实现 `POST /api/auth/setup`、`POST /api/auth/unlock`、`POST /api/auth/verify-totp`、`POST /api/auth/lock`、`POST /api/auth/mfa/enable`、`POST /api/auth/mfa/disable` 端点
    - _需求: 1.1 ~ 1.13_

- [x] 5. 检查点 - 确保认证模块测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [ ] 6. 实现密码生成器
  - [x] 6.1 实现 `PasswordGeneratorService` 及 `PasswordGeneratorServiceImpl`
    - 实现默认规则生成（16 位，含大小写字母、数字、特殊字符）
    - 实现自定义规则生成（长度 8-128，可配置字符类型）
    - 使用 `SecureRandom` 生成密码，确保每种启用类型至少包含一个字符
    - 实现密码规则的保存和查询
    - 实现输入验证：长度 < 8 拒绝、未选字符类型拒绝
    - _需求: 2.1 ~ 2.10_

  - [x] 6.2 编写密码生成器遵循配置规则属性测试
    - **Property 5: 密码生成器遵循配置规则**
    - 验证：生成密码长度等于配置长度，仅包含启用的字符类型，且每种类型至少一个
    - **验证需求: 2.4**

  - [x] 6.3 编写密码规则保存往返属性测试
    - **Property 6: 密码规则保存往返**
    - 验证：保存自定义规则后查询应返回等价配置
    - **验证需求: 2.6**

  - [x] 6.4 实现 `PasswordGenController`
    - 实现 `POST /api/password-generator/generate`、`POST /api/password-generator/evaluate`、密码规则 CRUD 端点
    - _需求: 2.1 ~ 2.10_

- [ ] 7. 实现凭证 CRUD 与搜索
  - [x] 7.1 实现 `CredentialService` 及 `CredentialServiceImpl`
    - 实现凭证创建：必填字段验证、密码加密存储、自动记录创建时间、支持自动生成密码
    - 实现凭证列表查询（摘要信息）
    - 实现凭证搜索（按账户名称、用户名、URL 模糊匹配，500ms 内返回）
    - 实现按标签筛选
    - 实现凭证详情查看（密码解密）
    - 实现凭证更新（含密码更新时记录历史、新旧密码不同验证、更新修改时间）
    - 实现凭证删除（二次确认由前端处理）
    - _需求: 3.1 ~ 3.7, 4.1 ~ 4.6, 5.1 ~ 5.7_

  - [x] 7.2 编写凭证必填字段验证属性测试
    - **Property 7: 凭证必填字段验证**
    - 验证：账户名称、用户名、密码均非空时创建成功，缺少任一则失败
    - **验证需求: 3.1, 3.6**

  - [x] 7.3 编写搜索结果正确性属性测试
    - **Property 9: 搜索结果正确性**
    - 验证：搜索结果包含所有匹配凭证，不包含不匹配凭证
    - **验证需求: 4.2**

  - [x] 7.4 编写标签筛选正确性属性测试
    - **Property 10: 标签筛选正确性**
    - 验证：按标签筛选结果仅包含带有该标签的凭证
    - **验证需求: 4.6**

  - [x] 7.5 编写新旧密码不同验证属性测试
    - **Property 12: 新旧密码不同验证**
    - 验证：新密码与当前密码相同时更新被拒绝
    - **验证需求: 5.5**

  - [x] 7.6 实现 `CredentialController`
    - 实现 `POST /api/credentials`、`GET /api/credentials`、`GET /api/credentials/{id}`、`PUT /api/credentials/{id}`、`DELETE /api/credentials/{id}`、`GET /api/credentials/search` 端点
    - _需求: 3.1 ~ 3.7, 4.1 ~ 4.6, 5.1 ~ 5.7_

- [ ] 8. 实现密码历史查询
  - [x] 8.1 实现 `PasswordHistoryService` 及 `PasswordHistoryServiceImpl`
    - 实现获取凭证密码历史（最近 10 条，按时间倒序）
    - 实现历史密码明文查看（解密后返回，前端 30 秒后恢复掩码）
    - 实现密码变更记录（由 CredentialService 调用，超过 10 条删除最早记录）
    - _需求: 5.2, 5.3, 9.1 ~ 9.6_

  - [x] 8.2 编写密码更新记录历史属性测试
    - **Property 11: 密码更新记录历史并保持上限**
    - 验证：旧密码被记录，历史不超过 10 条，修改时间被更新
    - **验证需求: 5.2, 5.3, 5.4, 9.5**

  - [x] 8.3 编写密码历史排序与完整性属性测试
    - **Property 20: 密码历史排序与完整性**
    - 验证：历史记录按变更时间降序排列，每条包含掩码密码和变更时间
    - **验证需求: 9.1, 9.2**

  - [x] 8.4 实现 `PasswordHistoryController`
    - 实现 `GET /api/credentials/{id}/password-history`、`POST /api/credentials/{id}/password-history/{historyId}/reveal` 端点
    - _需求: 9.1 ~ 9.6_

- [ ] 9. 实现安全报告
  - [ ] 9.1 实现 `SecurityReportService` 及 `SecurityReportServiceImpl`
    - 实现弱密码检测（解密后评估强度）
    - 实现重复密码检测（比较解密后的密码）
    - 实现超期未更新检测（最后修改时间超过 90 天）
    - 实现安全报告统计（总凭证数、弱密码数、重复密码数、超期数）
    - _需求: 6.1 ~ 6.5_

  - [ ] 9.2 编写安全报告统计一致性属性测试
    - **Property 14: 安全报告统计一致性**
    - 验证：弱密码列表、重复密码列表、超期列表与统计数字一致
    - **验证需求: 6.2, 6.3, 6.4, 6.5**

  - [ ] 9.3 实现 `SecurityReportController`
    - 实现 `GET /api/security-report`、`GET /api/security-report/weak`、`GET /api/security-report/duplicate`、`GET /api/security-report/expired` 端点
    - _需求: 6.1 ~ 6.5_

- [ ] 10. 实现数据导入导出
  - [ ] 10.1 实现 `ImportExportService` 及 `ImportExportServiceImpl`
    - 实现导出：查询凭证 → 解密 → EasyExcel 写入 → Apache POI 加密保护
    - 实现导入：POI 解密 → EasyExcel 读取 → 冲突策略处理（覆盖/跳过/保留两者）→ 加密存储
    - 实现 `ExcelEncryptionUtil` 工具类
    - _需求: 7.1 ~ 7.7_

  - [ ] 10.2 编写导入需要正确密码属性测试
    - **Property 15: 导入需要正确密码**
    - 验证：错误密码导入失败，正确密码导入成功
    - **验证需求: 7.4**

  - [ ] 10.3 编写导入冲突策略属性测试
    - **Property 16: 导入冲突策略**
    - 验证：覆盖替换旧数据、跳过保留旧数据、保留两者同时存在
    - **验证需求: 7.6**

  - [ ] 10.4 编写 Excel 导出/导入往返一致性属性测试
    - **Property 17: Excel 导出/导入往返一致性**
    - 验证：导出后再导入应产生与原始数据等价的凭证记录
    - **验证需求: 7.7**

  - [ ] 10.5 实现 `ImportExportController`
    - 实现 `POST /api/import-export/export`、`POST /api/import-export/import` 端点
    - _需求: 7.1 ~ 7.7_

- [ ] 11. 实现设置模块
  - [ ] 11.1 实现 `SettingsController`
    - 实现 `GET /api/settings`、`PUT /api/settings` 端点
    - 支持自动锁定超时时间配置（1-60 分钟）
    - _需求: 8.2_

- [ ] 12. 检查点 - 确保后端所有模块测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [ ] 13. 搭建 Electron 桌面应用前端
  - [ ] 13.1 初始化 Electron + React + TypeScript 项目
    - 创建 Electron 项目结构（main、renderer、preload）
    - 配置 TypeScript、Webpack/Vite 打包
    - 创建 `preload.ts` 安全桥接脚本
    - 创建 `api-client.ts` HTTP API 客户端封装
    - _需求: 全局前端基础设施_

  - [ ] 13.2 实现认证相关页面
    - 实现 `SetupPage.tsx`（首次设置主密码）
    - 实现 `UnlockPage.tsx`（解锁页面，含 TOTP 输入）
    - 实现 `PasswordInput.tsx` 组件（掩码/明文切换）
    - _需求: 1.1, 1.3, 1.4, 1.5, 1.10_

  - [ ] 13.3 实现凭证管理页面
    - 实现 `VaultPage.tsx`（凭证列表主页，含搜索和标签筛选）
    - 实现 `CredentialDetailPage.tsx`（凭证详情/编辑，含密码掩码显示、30 秒自动恢复、复制密码 60 秒清除）
    - 实现 `CredentialCard.tsx`、`SearchBar.tsx`、`TagFilter.tsx`、`ConfirmDialog.tsx` 组件
    - _需求: 3.1 ~ 3.7, 4.1 ~ 4.6, 5.1 ~ 5.7_

  - [ ] 13.4 实现密码生成器页面
    - 实现 `PasswordGeneratorPage.tsx`（默认规则/自定义规则切换，密码强度指示器）
    - 实现 `StrengthIndicator.tsx` 组件
    - _需求: 2.1 ~ 2.10_

  - [ ] 13.5 实现密码历史、安全报告、导入导出、设置页面
    - 实现 `PasswordHistoryPage.tsx`（历史密码列表，掩码显示，30 秒自动恢复）
    - 实现 `SecurityReportPage.tsx`（安全报告统计与详情列表）
    - 实现 `ImportExportPage.tsx`（Excel 导入导出，冲突策略选择）
    - 实现 `SettingsPage.tsx`（自动锁定超时配置、MFA 管理）
    - _需求: 6.1 ~ 6.5, 7.1 ~ 7.7, 8.2, 9.1 ~ 9.6_

  - [ ] 13.6 实现 Electron 主进程功能
    - 实现 `clipboard-manager.ts`（剪贴板管理，60 秒自动清除）
    - 实现 `auto-lock.ts`（用户无操作自动锁定检测）
    - 实现 `ipc-handlers.ts`（IPC 消息处理）
    - 实现 `useAutoLock.ts` 和 `useClipboard.ts` Hooks
    - _需求: 4.5, 8.1, 8.3, 8.4_

- [ ] 14. 搭建 Chrome Extension 浏览器插件
  - [ ] 14.1 初始化 Chrome Extension Manifest V3 项目
    - 创建 `manifest.json` 配置
    - 创建 `service-worker.ts`（后台服务，API 请求代理）
    - 创建 `api-client.ts`（与后端通信）
    - _需求: 全局浏览器插件基础设施_

  - [ ] 14.2 实现插件核心功能
    - 实现 `content-script.ts`（检测页面登录表单，提取 URL）
    - 实现 `Popup.tsx`（弹出窗口主界面）
    - 实现 `QuickSearch.tsx`（快速搜索凭证）
    - 实现 `AutoFillPrompt.tsx`（自动填充提示）
    - 实现 Content Script → Service Worker → 后端 API 的完整通信链路
    - _需求: 4.2（搜索匹配 URL）_

- [ ] 15. 集成联调与最终检查点
  - [ ] 15.1 前后端集成联调
    - 配置 `CorsConfig.java` 跨域设置
    - 配置 `SecurityConfig.java` 安全配置（会话拦截器）
    - 验证 Electron 应用与后端 API 的完整通信
    - 验证 Chrome Extension 与后端 API 的完整通信
    - _需求: 全局集成_

  - [ ] 15.2 编写后端集成测试
    - 使用 Spring Boot Test + H2 内存数据库编写关键流程集成测试
    - 覆盖：主密码设置→解锁→凭证 CRUD→密码历史→安全报告→导入导出 完整流程
    - _需求: 全局集成验证_

- [ ] 16. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 开发
- 每个任务引用了具体的需求编号以确保可追溯性
- 检查点任务用于阶段性验证，确保增量开发的正确性
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界条件
- 后端实现优先于前端，确保 API 就绪后再开发 UI
