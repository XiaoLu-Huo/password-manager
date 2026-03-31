# 需求文档

## 简介

本文档定义了一个类似 1Password 的密码管理工具平台的需求。该平台旨在帮助用户安全地生成、存储和管理各类账户的密码信息。核心功能包括自动生成高强度密码、安全存储账户凭证、以及灵活地更新和调整密码。

## 术语表

- **Password_Manager（密码管理器）**: 本系统的核心应用，负责密码的生成、存储、检索和管理
- **Vault（密码库）**: 加密存储所有账户凭证信息的安全容器
- **Credential（凭证）**: 一条完整的账户记录，包含账户名称、用户名、密码、关联URL等信息
- **Master_Password（主密码）**: 用户访问密码库的唯一认证密码
- **Password_Generator（密码生成器）**: 根据用户配置的规则自动生成随机密码的模块
- **Password_Strength（密码强度）**: 对密码安全性的评估等级，分为弱、中、强三个级别
- **Encryption_Engine（加密引擎）**: 负责对密码库数据进行加密和解密的模块
- **Password_Rule（密码规则）**: 定义密码生成策略的配置，包括密码长度、字符类型等参数，分为系统默认规则和用户自定义规则
- **MFA（多因素认证）**: 在主密码之外增加额外认证因素以提升账户安全性的机制
- **TOTP（基于时间的一次性密码）**: 一种基于时间生成的动态验证码，用于多因素认证

## 需求

### 需求 1：用户认证与主密码管理

**用户故事：** 作为用户，我希望通过主密码来保护我的密码库，以确保只有我本人能够访问存储的凭证信息。

#### 验收标准

1. WHEN 用户首次使用 Password_Manager, THE Password_Manager SHALL 引导用户创建一个 Master_Password
2. THE Password_Manager SHALL 要求 Master_Password 长度不少于 12 个字符，且包含大写字母、小写字母、数字和特殊字符中的至少三种
3. WHEN 用户输入正确的 Master_Password, THE Password_Manager SHALL 解锁 Vault 并允许用户访问凭证数据
4. WHEN 用户输入错误的 Master_Password, THE Password_Manager SHALL 拒绝访问并显示"主密码错误"提示
5. WHEN 用户连续 5 次输入错误的 Master_Password, THE Password_Manager SHALL 锁定账户 15 分钟
6. THE Encryption_Engine SHALL 使用 AES-256 算法对 Master_Password 的派生密钥进行加密存储
7. THE Password_Manager SHALL 仅在本地存储 Master_Password 的哈希值，不存储明文密码
8. THE Password_Manager SHALL 支持用户启用 MFA 作为 Vault 解锁的额外认证因素
9. WHEN 用户启用 MFA, THE Password_Manager SHALL 引导用户绑定 TOTP 认证器应用，并生成对应的密钥和二维码供用户扫描
10. WHILE MFA 处于启用状态, WHEN 用户输入正确的 Master_Password, THE Password_Manager SHALL 要求用户输入有效的 TOTP 验证码后方可解锁 Vault
11. WHEN 用户输入的 TOTP 验证码无效或已过期, THE Password_Manager SHALL 拒绝解锁并提示"验证码无效，请重新输入"
12. WHEN 用户启用 MFA, THE Password_Manager SHALL 生成一组一次性恢复码并提示用户妥善保存，以便在 TOTP 设备丢失时恢复访问
13. THE Password_Manager SHALL 允许用户在通过完整认证后禁用 MFA

### 需求 2：自动生成密码

**用户故事：** 作为用户，我希望系统能够自动生成高强度的随机密码，以避免使用弱密码或重复密码带来的安全风险。

#### 验收标准

1. WHEN 用户请求生成新密码, THE Password_Generator SHALL 提供"使用默认规则"和"自定义规则"两种模式供用户选择
2. THE Password_Generator SHALL 定义默认 Password_Rule 为：密码长度 16 个字符，包含大写字母、小写字母、数字和特殊字符
3. THE Password_Generator SHALL 要求默认 Password_Rule 生成的密码长度不少于 8 个字符
4. WHEN 用户选择自定义规则, THE Password_Generator SHALL 允许用户配置以下参数：密码长度（8-128 个字符）、是否包含大写字母、是否包含小写字母、是否包含数字、是否包含特殊字符
5. WHEN 用户选择使用默认规则, THE Password_Generator SHALL 直接按照默认 Password_Rule 生成密码
6. THE Password_Manager SHALL 允许用户保存自定义 Password_Rule 以便后续复用
7. WHEN 用户指定的密码长度小于 8 个字符, THE Password_Generator SHALL 提示"密码长度不能少于 8 个字符"并拒绝生成
8. WHEN 用户未选择任何字符类型, THE Password_Generator SHALL 提示"至少选择一种字符类型"并拒绝生成
9. THE Password_Generator SHALL 使用密码学安全的随机数生成器（CSPRNG）生成密码
10. WHEN 密码生成完成, THE Password_Manager SHALL 评估生成密码的 Password_Strength 并向用户展示强度等级

### 需求 3：账户凭证的创建与存储

**用户故事：** 作为用户，我希望能够记录和安全存储各个网站或应用的账户密码信息，以便在需要时快速查找和使用。

#### 验收标准

1. WHEN 用户创建新的 Credential, THE Password_Manager SHALL 要求用户填写以下必填字段：账户名称、用户名、密码
2. THE Password_Manager SHALL 支持以下可选字段：关联 URL、备注、分类标签
3. WHEN 用户保存 Credential, THE Encryption_Engine SHALL 使用 AES-256 算法加密 Credential 的密码字段后存入 Vault，其他字段（账户名称、用户名、URL、备注）以明文存储
4. THE Password_Manager SHALL 在 Credential 创建时自动记录创建时间
5. WHEN 用户创建 Credential 时选择自动生成密码, THE Password_Manager SHALL 调用 Password_Generator 生成密码并自动填入密码字段
6. IF 用户尝试保存缺少必填字段的 Credential, THEN THE Password_Manager SHALL 高亮显示缺失字段并提示"请填写所有必填项"
7. THE Encryption_Engine SHALL 对存储在 Vault 中的所有 Credential 的密码字段进行静态加密

### 需求 4：账户凭证的检索与查看

**用户故事：** 作为用户，我希望能够快速搜索和查看已存储的账户密码信息，以便在登录各类服务时高效使用。

#### 验收标准

1. WHILE Vault 处于解锁状态, THE Password_Manager SHALL 以列表形式展示所有已存储的 Credential 摘要信息（账户名称、用户名、关联 URL）
2. WHEN 用户输入搜索关键词, THE Password_Manager SHALL 在 500 毫秒内返回匹配账户名称、用户名或关联 URL 的 Credential 列表
3. WHEN 用户选择查看某条 Credential 的密码, THE Password_Manager SHALL 默认以掩码形式（"••••••"）显示密码
4. WHEN 用户点击"显示密码"按钮, THE Password_Manager SHALL 明文展示密码内容，并在 30 秒后自动恢复为掩码显示
5. WHEN 用户点击"复制密码"按钮, THE Password_Manager SHALL 将密码复制到系统剪贴板，并在 60 秒后自动清除剪贴板中的密码内容
6. THE Password_Manager SHALL 支持按分类标签筛选 Credential 列表

### 需求 5：密码的更新与调整

**用户故事：** 作为用户，我希望能够方便地更新已存储的账户密码，以便在定期更换密码或密码泄露时快速响应。

#### 验收标准

1. WHEN 用户选择更新某条 Credential 的密码, THE Password_Manager SHALL 允许用户手动输入新密码或调用 Password_Generator 自动生成新密码
2. WHEN 用户确认更新密码, THE Password_Manager SHALL 将旧密码存入该 Credential 的密码历史记录中
3. THE Password_Manager SHALL 为每条 Credential 保留最近 10 条密码历史记录
4. WHEN 用户更新密码, THE Password_Manager SHALL 自动更新该 Credential 的"最后修改时间"字段
5. WHEN 用户输入的新密码与当前密码相同, THE Password_Manager SHALL 提示"新密码不能与当前密码相同"并拒绝更新
6. THE Password_Manager SHALL 允许用户编辑 Credential 的所有字段（账户名称、用户名、密码、关联 URL、备注、分类标签）
7. IF 用户尝试删除某条 Credential, THEN THE Password_Manager SHALL 要求用户二次确认后方可执行删除操作

### 需求 6：密码安全性评估与提醒

**用户故事：** 作为用户，我希望系统能够评估我已存储密码的安全性，并在密码存在风险时提醒我，以便及时采取措施。

#### 验收标准

1. THE Password_Manager SHALL 根据以下规则评估 Password_Strength：长度不足 8 位为"弱"，8-15 位且包含两种以上字符类型为"中"，16 位及以上且包含三种以上字符类型为"强"
2. WHEN 用户打开密码安全报告, THE Password_Manager SHALL 列出所有 Password_Strength 为"弱"的 Credential
3. THE Password_Manager SHALL 检测并标记使用重复密码的 Credential
4. WHEN 某条 Credential 的密码超过 90 天未更新, THE Password_Manager SHALL 在该 Credential 上显示"建议更新密码"提示
5. WHEN 用户查看安全报告, THE Password_Manager SHALL 显示以下统计信息：总凭证数量、弱密码数量、重复密码数量、超期未更新密码数量

### 需求 7：数据导入与导出

**用户故事：** 作为用户，我希望能够导入和导出密码数据，以便从其他密码管理工具迁移数据或备份我的密码库。

#### 验收标准

1. THE Password_Manager SHALL 支持将 Vault 中的 Credential 数据导出为加密保护的 Excel 文件
2. WHEN 用户执行导出操作, THE Password_Manager SHALL 要求用户设置一个 Excel 文件的加密密码
3. WHEN 用户导出 Excel 格式文件, THE Password_Manager SHALL 使用 Excel 原生密码保护机制对文件进行加密
4. WHEN 用户导入 Excel 格式的密码文件, THE Password_Manager SHALL 要求用户输入 Excel 文件密码后方可解析文件内容，并将 Credential 数据导入 Vault
5. IF 导入文件格式不正确, THEN THE Password_Manager SHALL 提示"文件格式不支持，请使用有效的 Excel 文件"
6. WHEN 导入的 Credential 与 Vault 中已有的 Credential 存在账户名称冲突, THE Password_Manager SHALL 提示用户选择"覆盖"、"跳过"或"保留两者"
7. FOR ALL 有效的 Credential 数据, 导出为 Excel 后再导入 SHALL 产生与原始数据等价的 Credential 记录（往返一致性）

### 需求 8：自动锁定与会话管理

**用户故事：** 作为用户，我希望系统在我不活跃时自动锁定密码库，以防止他人在我离开时访问我的密码。

#### 验收标准

1. WHILE Vault 处于解锁状态且用户无操作超过 5 分钟, THE Password_Manager SHALL 自动锁定 Vault
2. THE Password_Manager SHALL 允许用户在设置中自定义自动锁定的超时时间（1-60 分钟）
3. WHEN Vault 被锁定, THE Password_Manager SHALL 清除内存中所有解密后的 Credential 数据
4. WHEN 用户手动点击"锁定"按钮, THE Password_Manager SHALL 立即锁定 Vault
5. WHEN Vault 被锁定后用户尝试访问 Credential, THE Password_Manager SHALL 要求用户重新输入 Master_Password 进行解锁

### 需求 9：密码历史查询

**用户故事：** 作为用户，我希望能够查看某条凭证的密码变更历史记录，以便在需要时回溯之前使用过的密码。

#### 验收标准

1. WHEN 用户查看某条 Credential 的密码历史, THE Password_Manager SHALL 以列表形式展示该凭证的所有历史密码记录，按变更时间倒序排列
2. THE Password_Manager SHALL 为每条密码历史记录显示以下信息：历史密码内容（默认掩码显示）和变更时间
3. WHEN 用户点击某条历史密码的"显示密码"按钮, THE Password_Manager SHALL 明文展示该历史密码，并在 30 秒后自动恢复为掩码显示
4. WHEN 用户点击某条历史密码的"复制密码"按钮, THE Password_Manager SHALL 将该历史密码复制到系统剪贴板，并在 60 秒后自动清除
5. THE Password_Manager SHALL 仅展示最近 10 条密码历史记录（与需求 5.3 保持一致）
6. WHEN 某条 Credential 没有密码变更历史, THE Password_Manager SHALL 显示"暂无密码变更记录"提示
