package com.edu360.api.platformops.entitlement;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;

/**
 * I-07: makes every tenant permission check also check the module entitlement, without changing
 * PermissionEvaluatorService itself. Every tenant endpoint calls
 * PermissionEvaluatorService.authorize(principal, ACTION_CODE, ...) before doing anything, so
 * wrapping that one method covers them all: when the action's module is switched off for the
 * principal's tenant the call fails with 403 MODULE_NOT_ENABLED before the permission decision,
 * the handler body never runs and nothing is saved.
 *
 * Only authorize(...) is wrapped. can(...) answers a question; the entry point it guards always
 * ends in authorize(...) or is itself guarded by one.
 */
@Component
public class ModuleEntitlementEnforcer implements BeanPostProcessor {

    private final ObjectProvider<ModuleEntitlementService> entitlementService;

    public ModuleEntitlementEnforcer(ObjectProvider<ModuleEntitlementService> entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> target = AopUtils.getTargetClass(bean);
        if (target == null || !PermissionEvaluatorService.class.isAssignableFrom(target)) {
            return bean;
        }
        MethodInterceptor check = entitlementCheck(entitlementService);
        if (bean instanceof Advised advised) {
            advised.addAdvice(0, check);
            return bean;
        }
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        factory.addAdvice(check);
        return factory.getProxy();
    }

    static MethodInterceptor entitlementCheck(ObjectProvider<ModuleEntitlementService> service) {
        return invocation -> {
            Object[] args = invocation.getArguments();
            if ("authorize".equals(invocation.getMethod().getName())
                    && args.length >= 2
                    && args[0] instanceof RbacPrincipal principal
                    && args[1] instanceof String actionCode) {
                service.getObject().requireEnabled(principal.tenantId(), actionCode);
            }
            return invocation.proceed();
        };
    }
}
