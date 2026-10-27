# 签名密钥目录

把正式 release keystore 放在这里，例如 `colorskip-release.jks`。

- `.jks` / `.keystore` 已被 `.gitignore` 忽略，**不要提交**。
- `keystore.properties` 也已被忽略；从根目录的
  [`keystore.properties.example`](../keystore.properties.example) 复制后填写。
- 仓库不提供任何真实密钥或密码。

GitHub Actions 默认只构建 debug 包，不会读取这里的密钥。
