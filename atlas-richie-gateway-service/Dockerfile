FROM ghcr.io/graalvm/jdk-community:25

# 作者信息
MAINTAINER richie696 Docker springboot "richie696@icloud.com"

# 修改为阿里云的源地址 && 安装需要的软件，解决时区问题
RUN echo "https://mirrors.aliyun.com/alpine/latest-stable/main/" > /etc/apk/repositories && \
    echo "https://mirrors.aliyun.com/alpine/latest-stable/community/" >> /etc/apk/repositories && \
    apk --update curl bash tzdata && \
    rm -rf /var/cache/apk/* \

# 修改镜像为东八区时间
ENV TZ Asia/Shanghai

# 传递Maven打包的jar包名称
ARG JAR_FILE

# 将jar包复制到容器中
ADD target/${JAR_FILE} app.jar

# 暴露容器端口
EXPOSE 9500

# 固定不变的启动命令
ENTRYPOINT ["nohup", "java", "-jar", "/app.jar"]

# JAVA_OPTS 需要自定义时放开，内容请自行修改，最后的“&”符号是为了让容器后台运行请不要删除
# CMD ["--profile=production","--server.port=8081","&"]
