package com.txscope.core.collector.transaction;


import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * Around advice on @Transactional methods: registers the transaction with TransactionTracker.
 */
@Aspect
public class TransactionTrackingAspect {

    private final TransactionTracker tracker;

    public TransactionTrackingAspect(TransactionTracker tracker) {
        this.tracker = tracker;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || "
            + "@within(org.springframework.transaction.annotation.Transactional)")
    public Object track(ProceedingJoinPoint pjp) throws Throwable {
        String name = methodName(pjp);
        tracker.onTransactionActive(name);
        return pjp.proceed();
    }

    private static String methodName(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        return sig.getDeclaringType().getSimpleName() + "." + sig.getName();
    }
}
