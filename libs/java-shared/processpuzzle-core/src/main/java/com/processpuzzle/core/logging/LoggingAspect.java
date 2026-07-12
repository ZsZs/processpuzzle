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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Aspect
public class LoggingAspect {

    static final String MDC_CLASS = "logClass";
    static final String MDC_METHOD = "logMethod";
    static final String MDC_ARGS = "logArgs";
    static final String MDC_RESULT = "logResult";
    static final String MDC_CALL_ID = "logCallId";
    static final String MDC_PARENT_CALL_ID = "logParentCallId";
    static final String MDC_DEPTH = "logDepth";

    private static final String INDENT_UNIT = "  ";
    private static final ThreadLocal<Deque<String>> CALL_STACK = ThreadLocal.withInitial(ArrayDeque::new);

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
        Deque<String> stack = CALL_STACK.get();
        String parentCallId = stack.peek();
        String callId = UUID.randomUUID().toString();
        int depth = stack.size();
        String indent = INDENT_UNIT.repeat(depth);
        String argsJson = renderArgs(signature.getParameterNames(), joinPoint.getArgs());

        stack.push(callId);
        try {
            if (entryEnabled) {
                setInvocationMdc(className, methodName, callId, parentCallId, depth);
                MDC.put(MDC_ARGS, argsJson);
                logger.atLevel(level).log("{}→ {}.{}({})", indent, className, methodName, argsJson);
            }

            Object result;
            try {
                result = joinPoint.proceed();
            } catch (Throwable throwable) {
                if (logger.isErrorEnabled()) {
                    setInvocationMdc(className, methodName, callId, parentCallId, depth);
                    logger.atLevel(Level.ERROR)
                            .setCause(throwable)
                            .log("{}✗ {}.{} threw {}", indent, className, methodName, throwable.getClass().getSimpleName());
                }
                throw throwable;
            }

            if (entryEnabled) {
                String resultRendered = renderResult(signature.getReturnType(), result);
                setInvocationMdc(className, methodName, callId, parentCallId, depth);
                MDC.put(MDC_RESULT, resultRendered);
                logger.atLevel(level).log("{}← {}.{} = {}", indent, className, methodName, resultRendered);
            }

            return result;
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                CALL_STACK.remove();
            }
            clearInvocationMdc();
        }
    }

    private void setInvocationMdc(String className, String methodName, String callId, String parentCallId, int depth) {
        MDC.put(MDC_CLASS, className);
        MDC.put(MDC_METHOD, methodName);
        MDC.put(MDC_CALL_ID, callId);
        MDC.put(MDC_DEPTH, Integer.toString(depth));
        if (parentCallId != null) {
            MDC.put(MDC_PARENT_CALL_ID, parentCallId);
        } else {
            MDC.remove(MDC_PARENT_CALL_ID);
        }
    }

    private void clearInvocationMdc() {
        MDC.remove(MDC_CLASS);
        MDC.remove(MDC_METHOD);
        MDC.remove(MDC_ARGS);
        MDC.remove(MDC_RESULT);
        MDC.remove(MDC_CALL_ID);
        MDC.remove(MDC_PARENT_CALL_ID);
        MDC.remove(MDC_DEPTH);
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
