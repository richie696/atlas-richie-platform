ARG JAVA_IMAGE=azul/zulu-openjdk-debian:25-jre-latest
ARG CLAMAV_IMAGE=clamav/clamav:latest-debian13-slim

# Jib 不能执行 RUN，也不能合并两个基础镜像。这个基础镜像只负责把官方 JRE 和
# 官方 ClamAV 运行时组合起来；业务代码仍由 jib-maven-plugin 分层加入。
FROM ${JAVA_IMAGE} AS java-runtime
FROM ${CLAMAV_IMAGE}

USER root
COPY --from=java-runtime /usr/lib/jvm/zulu25 /opt/java/openjdk

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN java -version \
    && clamd --version \
    && freshclam --version

# 官方 Debian 镜像中的 clamav 用户固定为 1000:1000。Jib 最终镜像也使用该用户，
# clamd、freshclam 和 Java 进程全程不需要 root。
USER 1000:1000
