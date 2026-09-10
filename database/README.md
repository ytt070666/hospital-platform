# 数据库迁移

当前 Flyway migration 随后端 `hospital-bootstrap` 模块打包，路径为 `src/main/resources/db/migration`，以确保部署 JAR 可自带真实版本化结构。这里保留数据库文档入口；禁止在此目录或生产库中执行未经 Flyway 管理的手工 DDL。
