package com.processpuzzle.core.logging;

import com.processpuzzle.core.exception.UnhandledExceptionHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.util.List;

/**
 * Tells {@link LoggingAspect} whether an exception leaving a logged method is one the API answers on
 * purpose — a duplicate key, an unknown id, a rejected draft — rather than a fault.
 *
 * <p>The question is put to the {@code @RestControllerAdvice} classes themselves instead of a list kept
 * here: an exception some advice other than {@link UnhandledExceptionHandler} claims for the method's
 * class is an answered outcome, and anything else reaches the catch-all as a 500. An exception that
 * declares its own 4xx status through {@link ErrorResponse} counts too, as the catch-all treats it.
 *
 * <p>Without this the aspect logged every conflict at {@code ERROR} with a full stack trace — every
 * seeded definition on every restart, and every 404 a client provoked — burying the real faults.
 */
public class HandledExceptionClassifier {

    /** {@code null} outside a Spring context, where only a self-declared 4xx counts as handled. */
    private final ApplicationContext context;
    private List<Advice> advices;

    public HandledExceptionClassifier(ApplicationContext context) {
        this.context = context;
    }

    public boolean isHandled(Class<?> declaringType, Throwable throwable) {
        if (throwable instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            return true;
        }
        for (Advice advice : advices()) {
            if (advice.bean().isApplicableToBeanType(declaringType)
                    && advice.resolver().resolveMethodByThrowable(throwable) != null) {
                return true;
            }
        }
        return false;
    }

    /** Looked up on first use rather than at construction: the advices are not all registered that early. */
    private synchronized List<Advice> advices() {
        List<Advice> resolved = advices;
        if (resolved == null) {
            if (context == null) {
                return List.of();
            }
            resolved = ControllerAdviceBean.findAnnotatedBeans(context).stream()
                    .filter(bean -> bean.getBeanType() != null)
                    .filter(bean -> !UnhandledExceptionHandler.class.isAssignableFrom(bean.getBeanType()))
                    .map(bean -> new Advice(bean, new ExceptionHandlerMethodResolver(bean.getBeanType())))
                    .toList();
            advices = resolved;
        }
        return resolved;
    }

    private record Advice(ControllerAdviceBean bean, ExceptionHandlerMethodResolver resolver) {
    }
}
