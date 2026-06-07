package com.richie.component.mongodb.core;

import com.richie.component.mongodb.exception.MongodbException;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Function;

public final class LambdaMeta {

    public static SerializedLambda extract(Function<?, ?> lambda) {
        try {
            Method writeReplace = lambda.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            return (SerializedLambda) writeReplace.invoke(lambda);
        } catch (Exception e) {
            throw new MongodbException("Failed to extract lambda: " + e.getMessage(), e);
        }
    }

    public static String resolveFieldName(Function<?, ?> lambda) {
        SerializedLambda serializedLambda = extract(lambda);
        String methodName = serializedLambda.getImplMethodName();
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return methodName;
    }

    private LambdaMeta() {
    }
}
