package cn.richie696.component.chunking;

/** 外部语义边界服务无法生成可靠结果时抛出的异常。 */
public final class SemanticBoundaryException extends RuntimeException {
    public SemanticBoundaryException(String message) {
        super(message);
    }

    public SemanticBoundaryException(String message, Throwable cause) {
        super(message, cause);
    }
}
