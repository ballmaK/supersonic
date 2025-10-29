# 🚀 SuperSonic 快速启动指南

## 问题：找不到 pnpm 命令

### 立即解决方案（推荐）

#### 方案1：使用 npm（最稳定）
```bash
# 1. 进入webapp目录
cd webapp

# 2. 安装依赖
npm install

# 3. 构建SDK
cd packages/chat-sdk
npm run build
npm link

# 4. 回到前端目录并链接SDK
cd ../supersonic-fe
npm link ../chat-sdk

# 5. 启动前端开发服务器
npm run start:osdev
```

#### 方案2：使用 npx 运行 pnpm
```bash
# 1. 进入webapp目录
cd webapp

# 2. 使用npx运行pnpm命令
npx pnpm install

# 3. 构建SDK
cd packages/chat-sdk
npx pnpm build
npx pnpm link

# 4. 回到前端目录
cd ../supersonic-fe
npx pnpm link ../chat-sdk

# 5. 启动开发服务器
npx pnpm start:osdev
```

#### 方案3：重新安装 pnpm
```bash
# Windows (PowerShell)
iwr https://get.pnpm.io/install.ps1 -useb | iex

# Linux/macOS
curl -fsSL https://get.pnpm.io/install.sh | sh -

# 或者使用npm安装
npm install -g pnpm

# 重启终端后验证
pnpm --version
```

## 🔧 完整开发环境启动

### 1. 启动后端服务
在IDEA中：
1. 选择运行配置：`SuperSonic-Standalone-Debug`
2. 点击运行按钮
3. 等待服务启动完成
4. 访问：http://localhost:9080

### 2. 启动前端服务
```bash
# 使用npm（推荐）
cd webapp
npm install
cd packages/chat-sdk && npm run build && npm link
cd ../supersonic-fe && npm link ../chat-sdk
npm run start:osdev

# 或者使用npx pnpm
cd webapp
npx pnpm install
cd packages/chat-sdk && npx pnpm build && npx pnpm link
cd ../supersonic-fe && npx pnpm link ../chat-sdk
npx pnpm start:osdev
```

### 3. 验证服务
- **前端**: http://localhost:9000
- **后端API**: http://localhost:9080
- **H2数据库**: http://localhost:9080/h2-console/semantic
- **API文档**: http://localhost:9080/swagger-ui.html

## 📋 快速检查清单

### ✅ 环境检查
- [ ] Node.js 16+ 已安装
- [ ] npm 已安装
- [ ] Java 21 已安装
- [ ] IDEA 已配置调试配置

### ✅ 后端检查
- [ ] 后端服务启动成功
- [ ] 端口9080可访问
- [ ] H2数据库控制台可访问

### ✅ 前端检查
- [ ] 依赖安装完成
- [ ] SDK构建成功
- [ ] 前端服务启动成功
- [ ] 端口9000可访问

## 🐛 常见问题快速解决

### 问题1：端口被占用
```bash
# 检查端口使用情况
netstat -an | grep 9080
netstat -an | grep 9000

# 停止占用进程或修改端口
```

### 问题2：依赖安装失败
```bash
# 清理缓存
npm cache clean --force

# 删除node_modules
rm -rf node_modules
rm -rf packages/*/node_modules

# 重新安装
npm install
```

### 问题3：SDK构建失败
```bash
# 检查TypeScript
npx tsc --noEmit

# 重新构建
cd packages/chat-sdk
npm run build
```

### 问题4：前端启动失败
```bash
# 检查Node.js版本
node --version

# 检查端口
netstat -an | grep 9000

# 尝试不同端口
npm run start:osdev -- --port 9001
```

## 🎯 一键启动脚本

### 创建启动脚本
```bash
# 创建start-dev.sh
cat > start-dev.sh << 'EOF'
#!/bin/bash

echo "🚀 启动SuperSonic开发环境..."

# 检查后端服务
echo "📋 检查后端服务..."
if curl -s http://localhost:9080/actuator/health > /dev/null; then
    echo "✅ 后端服务已启动"
else
    echo "⚠️  后端服务未启动，请在IDEA中启动 SuperSonic-Standalone-Debug"
fi

# 启动前端
echo "🌐 启动前端服务..."
cd webapp

# 检查包管理器
if command -v pnpm &> /dev/null; then
    echo "使用 pnpm"
    PACKAGE_MANAGER="pnpm"
elif command -v yarn &> /dev/null; then
    echo "使用 yarn"
    PACKAGE_MANAGER="yarn"
else
    echo "使用 npm"
    PACKAGE_MANAGER="npm"
fi

# 安装依赖
echo "📦 安装依赖..."
$PACKAGE_MANAGER install

# 构建SDK
echo "🔨 构建SDK..."
cd packages/chat-sdk
$PACKAGE_MANAGER build
$PACKAGE_MANAGER link

# 启动前端
cd ../supersonic-fe
$PACKAGE_MANAGER link ../chat-sdk
echo "🚀 启动前端开发服务器..."
$PACKAGE_MANAGER start:osdev
EOF

# 给脚本执行权限
chmod +x start-dev.sh

# 运行脚本
./start-dev.sh
```

## 📞 获取帮助

如果遇到问题：

1. **检查日志**：
   - 后端日志：IDEA控制台
   - 前端日志：终端输出

2. **验证配置**：
   - 检查端口是否被占用
   - 检查依赖是否正确安装
   - 检查环境变量是否正确

3. **重启服务**：
   - 停止所有服务
   - 清理缓存
   - 重新启动

4. **使用备选方案**：
   - 如果pnpm有问题，使用npm
   - 如果npm有问题，使用yarn
   - 如果前端有问题，检查后端是否正常

## 🎉 成功标志

当您看到以下内容时，说明环境配置成功：

1. **后端服务**：
   ```
   Started StandaloneLauncher in X.XXX seconds
   ```

2. **前端服务**：
   ```
   Local:            http://localhost:9000
   On Your Network:  http://192.168.x.x:9000
   ```

3. **浏览器访问**：
   - 前端页面正常加载
   - 可以正常使用聊天功能
   - API请求正常响应 