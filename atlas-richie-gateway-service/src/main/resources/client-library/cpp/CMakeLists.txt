cmake_minimum_required(VERSION 3.10)
project(HttpClient)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找依赖库
find_package(OpenSSL REQUIRED)
find_package(CURL REQUIRED)

# 包含目录
include_directories(${OPENSSL_INCLUDE_DIR})

# 源文件
set(SOURCES
    http_client.cpp
    url.h
    ecc_crypto.cpp
    duplicate_submit.cpp
    curl_wrapper.cpp
)

# 头文件
set(HEADERS
    http_client.h
    url.h
    ecc_crypto.h
    duplicate_submit.h
    curl_wrapper.h
)

# 创建库
add_library(httpclient STATIC ${SOURCES} ${HEADERS})

# 链接库
target_link_libraries(httpclient
    ${OPENSSL_LIBRARIES}
    ${CURL_LIBRARIES}
)

# 示例程序
add_executable(httpclient_example example/main.cpp)
target_link_libraries(httpclient_example httpclient)

# 安装
install(TARGETS httpclient httpclient_example
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)

install(FILES ${HEADERS} DESTINATION include/httpclient)

