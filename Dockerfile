# 基础镜像
FROM openjdk:8-alpine
# 复制主机jar包至镜像内，复制的目录需放置在 Dockerfile 文件同级目录下
ADD target/lioj-code-sandbox-0.0.1-SNAPSHOT.jar app.jar
# 容器启动执行命令
ENTRYPOINT ["java","-jar", "/app.jar" , "--spring.profiles.active=prod"]
# 对外暴露的端口号
EXPOSE  8000
