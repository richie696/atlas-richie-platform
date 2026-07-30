package cn.richie696.antivirus.api;

/** 服务统一响应；任务不存在、已过期或无权查询均返回 failure(null)。 */
public record ApiResponse<T>(boolean success, T data, String msg) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "ok");
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
