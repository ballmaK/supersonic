# SuperSonic 快速启动调试指南

## 🚀 一键启动（推荐）

### 方式一：集成服务 + 前端
```bash
# 1. 启动后端服务（Standalone模式）
# 在IDEA中选择：SuperSonic-All-Services-Debug
# 或者手动启动：
# - 后端：SuperSonic-Standalone-Debug
# - 前端：SuperSonic-Frontend-Dev

# 2. 访问应用
# 前端：http://localhost:9000
# 后端API：http://localhost:9080
# H2数据库：http://localhost:9080/h2-console/semantic
```

### 方式二：微服务模式
```bash
# 1. 启动微服务架构
# 在IDEA中选择：SuperSonic-Microservices-Debug
# 包含：
# - Headless服务：http://localhost:9081
# - Chat服务：http://localhost:9082  
# - 前端：http://localhost:9000
```

## 📋 服务端口对照表

| 服务 | 端口 | 主类 | 数据库 |
|------|------|------|--------|
| Standalone | 9080 | StandaloneLauncher | H2 |
| Headless | 9081 | HeadlessLauncher | H2 |
| Chat | 9082 | ChatLauncher | H2 |
| 前端 | 9000 | - | - |

## 🔧 调试配置

### 后端调试
1. **设置断点**：在需要调试的代码行设置断点
2. **启动调试**：选择对应的调试配置，点击调试按钮
3. **查看变量**：在调试窗口中查看变量值和调用栈

### 前端调试
1. **浏览器调试**：打开浏览器开发者工具
2. **网络请求**：在Network标签页查看API请求
3. **控制台日志**：在Console标签页查看日志

## 🗄️ 数据库访问

### H2控制台访问
- **Standalone**: http://localhost:9080/h2-console/semantic
- **Headless**: http://localhost:9081/h2-console/semantic  
- **Chat**: http://localhost:9082/h2-console/chat

### 连接信息
```
用户名：root
密码：
- Standalone/Headless: semantic
- Chat: chat
```

## 📚 API文档

- **Swagger UI**: http://localhost:9080/swagger-ui.html
- **API文档**: http://localhost:9080/v3/api-docs

## ⚡ 快速验证

### 1. 检查服务状态
```bash
# 检查端口是否监听
netstat -an | grep 9080
netstat -an | grep 9000
```

### 2. 测试API接口
```bash
# 测试健康检查
curl http://localhost:9080/actuator/health

# 测试API文档
curl http://localhost:9080/v3/api-docs
```

### 3. 检查前端
```bash
# 访问前端页面
curl http://localhost:9000
```

## 🐛 常见问题

### 问题1：端口被占用
**解决方案**：
- 修改配置文件中的端口号
- 或者停止占用端口的进程

### 问题2：模块找不到
**解决方案**：
- 检查IDEA项目结构
- 重新导入Maven项目
- 检查模块名称是否正确

### 问题3：数据库连接失败
**解决方案**：
- 检查H2数据库是否正常启动
- 检查数据库连接配置
- 查看应用日志

### 问题4：前端启动失败
**解决方案**：
- 检查Node.js版本（建议16+）
- 安装依赖：`cd webapp && pnpm install`
- 先构建SDK：运行 `SuperSonic-Chat-SDK-Build`

## 💡 调试技巧

1. **日志级别**：在application.yaml中设置日志级别为DEBUG
2. **断点调试**：在关键代码处设置断点
3. **数据库调试**：使用H2控制台查看数据
4. **API测试**：使用Swagger UI测试接口
5. **前端调试**：使用浏览器开发者工具

## 📝 开发建议

1. **开发环境**：推荐使用 `SuperSonic-All-Services-Debug`
2. **微服务调试**：使用 `SuperSonic-Microservices-Debug`
3. **数据库调试**：使用H2控制台查看数据状态
4. **API调试**：使用Swagger UI进行接口测试
5. **前端调试**：使用浏览器开发者工具

## 🔄 重启服务

### 后端重启
1. 停止当前运行的服务
2. 重新启动对应的调试配置

### 前端重启
1. 停止前端服务（Ctrl+C）
2. 重新运行 `SuperSonic-Frontend-Dev`

### 完整重启
1. 停止所有服务
2. 清理缓存（可选）
3. 重新启动 `SuperSonic-All-Services-Debug` 