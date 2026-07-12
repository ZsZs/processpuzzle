package com.processpuzzle.core.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Aspect
public class LoggingAspect {

    static final String MDC_CLASS = "logClass";
    static final String MDC_METHOD = "logMethod";
    static final String MDC_ARGS = "logArgs";
    static final String MDC_RESULT = "logResult";

    private final ObjectMapper objectMapper;

    public LoggingAspect() {
        this(new ObjectMapper());
    }

    public LoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(logMethod)")
    public Object aroundMethod(ProceedingJoinPoint joinPoint, LogMethod logMethod) throws Throwable {
        return logInvocation(joinPoint, logMethod.level());
    }

    @Around("execution(public * *(..)) && @within(logClass)")
    public Object aroundClass(ProceedingJoinPoint joinPoint, LogClass logClass) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        if (method.isAnnotationPresent(LogMethod.class)) {
            return joinPoint.proceed();
        }
        return logInvocation(joinPoint, logClass.level());
    }

    private Object logInvocation(ProceedingJoinPoint joinPoint, Level level) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> declaringType = signature.getDeclaringType();
        Logger logger = LoggerFactory.getLogger(declaringType);

        boolean entryEnabled = logger.isEnabledForLevel(level);
        if (!entryEnabled && !logger.isErrorEnabled()) {
            return joinPoint.proceed();
        }

        String className = declaringType.getSimpleName();
        String methodName = signature.getName();

        try {
            if (entryEnabled) {
                MDC.put(MDC_CLASS, className);
                MDC.put(MDC_METHOD, methodName);
                MDC.put(MDC_ARGS, renderArgs(signature.getParameterNames(), joinPoint.getArgs()));
                logger.atLevel(level).log("method entry");
            }

            Object result;
            try {
                result = joinPoint.proceed();
            } catch (Throwable throwable) {
                if (logger.isErrorEnabled()) {
                    MDC.put(MDC_CLASS, className);
                    MDC.put(MDC_METHOD, methodName);
                    logger.atLevel(Level.ERROR).setCause(throwable).log("method threw");
                }
                throw throwable;
            }

            if (entryEnabled) {
                MDC.put(MDC_CLASS, className);
                MDC.put(MDC_METHOD, methodName);
                MDC.put(MDC_RESULT, renderResult(signature.getReturnType(), result));
                logger.atLevel(level).log("method exit");
            }

            return result;
        } finally {
            MDC.remove(MDC_CLASS);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_ARGS);
            MDC.remove(MDC_RESULT);
        }
    }

    private String renderArgs(String[] names, Object[] values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            String key = (names != null && i < names.length && names[i] != null) ? names[i] : "arg" + i;
            map.put(key, values[i]);
        }
        return toJson(map);
    }

    private String renderResult(Class<?> returnType, Object result) {
        if (returnType == void.class || returnType == Void.class) {
            return "void";
        }
        return toJson(result);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return Objects.toString(value);
        }
    }
}
