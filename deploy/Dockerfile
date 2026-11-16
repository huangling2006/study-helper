# 多阶段构建：第一阶段用 Maven 编译打包，第二阶段只留 JRE + jar，运行用户非 root。
# 基础镜像：默认走 daocloud 镜像源（国内直连 Docker Hub 会卡住）。
# 能直连的用户可以改成官方地址：
#   --build-arg MAVEN_IMAGE=maven:3.9-eclipse-temurin-21 --build-arg JRE_IMAGE=eclipse-temurin:21-jre

ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre

# ---------- 构建阶段 ----------
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build

# 国内网络用阿里云镜像；删掉这一行就用 Maven 官方源
COPY deploy/maven-settings.xml /root/.m2/settings.xml

# 先只拷 pom，把依赖层单独缓存：改代码不会重新下依赖
COPY pom.xml ./
COPY project-a/pom.xml project-a/pom.xml
RUN mvn -B -q -pl project-a -am dependency:go-offline

COPY project-a/src project-a/src
RUN mvn -B -pl project-a -am clean package -DskipTests \
    && cp project-a/target/project-a-*.jar /build/app.jar

# ---------- 运行阶段 ----------
FROM ${JRE_IMAGE} AS runtime

# 非 root 运行：容器逃逸类漏洞的第一道门槛
RUN groupadd -r app && useradd -r -g app -d /app -s /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

# 堆上限写死在镜像里，compose 里的 environment 可再覆盖
ENV JAVA_TOOL_OPTIONS="-Xmx512m -XX:MaxMetaspaceSize=192m"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
