# 智能学情诊断学习助手 (Study Helper)

基于 JDK 21 + Spring Boot 3.5.5 开发的后端刷题与错题管理系统。当前处于 W1 阶段（用户模块与鉴权链路已交付）。

## 🛠️ 技术栈
- 核心框架：Spring Boot 3.5.5
- 运行环境：JDK 21 (开启虚拟线程)
- 数据库：MySQL 8.0 (Docker 部署，宿主机端口 3307)
- 缓存：Redis 7.2 (Docker 部署)
- ORM：MyBatis-Plus 3.5.17
- 鉴权：JWT + 拦截器 + Redis
- 接口文档：Knife4j (OpenAPI 3)

## 🚀 快速启动
1. 确保本地已安装 Docker。
2. 进入项目根目录，启动依赖环境：
   ```bash
   cd deploy
   docker compose up -d