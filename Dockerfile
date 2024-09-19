## 基础镜像
#FROM openjdk:8-jdk-alpine
#
## 指定工作目录
#WORKDIR /app
#
## 将 jar 包添加到工作目录，比如 target/yuoj-backend-user-service-0.0.1-SNAPSHOT.jar
#ADD target/lioj-code-sandbox-0.0.1-SNAPSHOT.jar .
#
## 暴露端口
#EXPOSE 8000
#
## 启动命令
#ENTRYPOINT ["java","-jar","/app/lioj-code-sandbox-0.0.1-SNAPSHOT.jar"
#,"--spring.profiles.active=prod"]


# 基础镜像
FROM maven:3.8.1-jdk-8-slim as builder

# 指定工作目录
WORKDIR /app

# 添加源码文件
COPY pom.xml .
COPY src ./src

# 构建 jar 包，跳过测试
RUN mvn package -DskipTests

# 启动命令
ENTRYPOINT ["java","-jar","/app/target/lioj-code-sandbox-0.0.1-SNAPSHOT.jar","--spring.profiles.active=prod"]
