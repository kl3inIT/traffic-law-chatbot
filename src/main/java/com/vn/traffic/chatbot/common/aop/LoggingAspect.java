package com.vn.traffic.chatbot.common.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * AOP-based operational logging aspect.
 *
 * <p>Provides two cross-cutting concerns:
 * <ol>
 *   <li>{@code @AfterThrowing} — always-on; logs exceptions at ERROR level for all
 *       Spring beans ({@code @Repository}, {@code @Service}, {@code @RestController})
 *       in the application packages. In the {@code dev} profile, also logs the full
 *       exception message and stack trace.</li>
 *   <li>{@code @Around} — dev-profile-gated; logs method enter/exit at DEBUG level.</li>
 * </ol>
 *
 * <p>Security (T-4.1-01-04): In non-dev profiles only the cause class simple name is
 * logged, not the full exception message, to prevent internal detail disclosure.
 *
 * <p>CRITICAL: The pointcut must NOT cover this aspect class itself to avoid
 * infinite recursion — {@code within(com.vn.traffic.chatbot.common.aop..)} is
 * explicitly excluded.
 */
@Aspect
@Component
public class LoggingAspect {

    private final Environment env;

    public LoggingAspect(Environment env) {
        this.env = env;
    }

    // -----------------------------------------------------------------------
    // Pointcuts
    // -----------------------------------------------------------------------

    /**
     * Pointcut for application domain packages.
     * Uses {@code within(pkg..*)} syntax (trailing {@code *} required by AspectJ 1.9+).
     * Explicitly excludes the {@code common.aop} package to prevent recursion.
     */
    @Pointcut(
        "within(com.vn.traffic.chatbot.ingestion..*) || " +
        "within(com.vn.traffic.chatbot.source..*) || " +
        "within(com.vn.traffic.chatbot.chat..*) || " +
        "within(com.vn.traffic.chatbot.retrieval..*) || " +
        "within(com.vn.traffic.chatbot.parameter..*) || " +
        "within(com.vn.traffic.chatbot.chunk..*)"
    )
    public void applicationPackagePointcut() {
        // pointcut expression
    }

    /**
     * Pointcut for Spring-managed beans (Repository, Service, RestController).
     * Uses {@code within(@Annotation *)} for static type-level annotation matching,
     * which is evaluated at proxy-creation time and works with CGLIB subclass proxies.
     */
    @Pointcut(
        "within(@org.springframework.stereotype.Repository *) || " +
        "within(@org.springframework.stereotype.Service *) || " +
        "within(@org.springframework.web.bind.annotation.RestController *)"
    )
    public void springBeanPointcut() {
        // pointcut expression
    }

    // -----------------------------------------------------------------------
    // Advice
    // -----------------------------------------------------------------------

    /**
     * Logs exceptions thrown from any Spring bean in the application packages.
     * Always-on (no profile guard). In dev profile, logs full message + stack trace.
     * In other profiles, logs only the cause class simple name (T-4.1-01-04).
     */
    @AfterThrowing(pointcut = "applicationPackagePointcut() && springBeanPointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        Logger log = logger(joinPoint);
        String method = joinPoint.getSignature().getName();

        if (env.acceptsProfiles(Profiles.of("dev"))) {
            log.error("Exception in {}() — {}: {}", method,
                    e.getClass().getSimpleName(), e.getMessage(), e);
        } else {
            String causeType = e.getCause() != null
                    ? e.getCause().getClass().getSimpleName()
                    : e.getClass().getSimpleName();
            log.error("Exception in {}() — cause: {}", method, causeType);
        }
    }

    /**
     * Logs method enter and exit at DEBUG level, but ONLY in the {@code dev} profile.
     * Always proceeds with the original invocation regardless of profile.
     */
    @Around("applicationPackagePointcut() && springBeanPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger log = logger(joinPoint);

        if (env.acceptsProfiles(Profiles.of("dev")) && log.isDebugEnabled()) {
            log.debug("Enter: {}()", joinPoint.getSignature().getName());
            try {
                Object result = joinPoint.proceed();
                log.debug("Exit: {}()", joinPoint.getSignature().getName());
                return result;
            } catch (Throwable t) {
                // @AfterThrowing handles the logging; just rethrow
                throw t;
            }
        }

        return joinPoint.proceed();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Logger logger(JoinPoint jp) {
        return LoggerFactory.getLogger(jp.getSignature().getDeclaringTypeName());
    }
}
