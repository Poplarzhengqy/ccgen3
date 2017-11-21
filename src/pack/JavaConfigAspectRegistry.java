package org.springframework.config.java.model;

import static java.lang.String.format;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.aopalliance.aop.Advice;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.aspectj.annotation.AspectJAdvisorFactory;
import org.springframework.aop.aspectj.annotation.BeanFactoryAspectInstanceFactory;
import org.springframework.aop.aspectj.annotation.MetadataAwareAspectInstanceFactory;
import org.springframework.aop.aspectj.annotation.ReflectiveAspectJAdvisorFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.config.java.annotation.Bean;
import org.springframework.config.java.context.JavaConfigBeanFactory;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.ReflectionUtils.MethodFilter;

/**
 * @author Chris Beams
 */
public class JavaConfigAspectRegistry {

	private static final String BEAN_NAME = JavaConfigAspectRegistry.class.getName();

	private static final Log logger = LogFactory.getLog(JavaConfigAspectRegistry.class);

	private final Map<String, Pointcut> pointcuts = new HashMap<String, Pointcut>();

	private final Map<String, Advice> advices = new HashMap<String, Advice>();

	private final AspectJAdvisorFactory advisorFactory = new ReflectiveAspectJAdvisorFactory();

	private final JavaConfigBeanFactory beanFactory;

	public JavaConfigAspectRegistry(JavaConfigBeanFactory beanFactory) {
		registerSelfWithBeanFactory(beanFactory);
		this.beanFactory = beanFactory;
	}

	private void registerSelfWithBeanFactory(JavaConfigBeanFactory beanFactory) {
		String aspectRegistryBeanName = BEAN_NAME;
		if(beanFactory.containsSingleton(aspectRegistryBeanName))
			throw new IllegalStateException("aspect registry has already been registered with bean factory");
		beanFactory.registerSingleton(aspectRegistryBeanName, this);
	}

	/**
	 * Finds any aspects specified within <var>model</var> and registers associated pointcuts
	 * and advice such that they can be used during runtime processing within enhanced {@link Bean @Bean}
	 * methods.
	 *
	 * @param model
	 * @param beanFactory
	 */
	public void registerAspects(Class<?>[] atAspectClasses) {
		for(Class<?> atAspectClass : atAspectClasses)
			registerAspect(atAspectClass);
	}

	public Object proxyIfAnyPointcutsApply(Object bean, Method method) {
		ProxyFactory pf = new ProxyFactory(bean);

		for(String adviceName : pointcuts.keySet()) {
			Pointcut pc = pointcuts.get(adviceName);
			if(!AopUtils.canApply(pc, bean.getClass()))
				continue;

			Advice advice = advices.get(adviceName);
			DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pc, advice);

			// TODO: [aop] respect Ordering

			pf.addAdvisor(advisor);
		}

		if(pf.getAdvisors().length == 0)
			// no pointcuts apply -> return the unadorned target object
			return bean;

		pf.addAdvice(0, ExposeInvocationInterceptor.INSTANCE);

		Class<?> returnType = method.getReturnType();
		if(returnType.isInterface()) {
			pf.setInterfaces(new Class[] { returnType });
			pf.setProxyTargetClass(false);
		} else {
			pf.setProxyTargetClass(true);
		}

		if(logger.isInfoEnabled())
			logger.info(format("Wrapping object [%s] for @Bean method %s.%s in AOP proxy",
					bean, method.getDeclaringClass().getSimpleName(), method.getName()));
		return pf.getProxy();
	}

	private void registerAspect(final Class<?> atAspectClass) {
		advisorFactory.validate(atAspectClass);

		ReflectionUtils.doWithMethods(atAspectClass,
			new MethodCallback() {
				public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
    				registerPointcutAndAdviceIfMethodIsPointcutAdvisor(atAspectClass, method);
    			}
    		},
    		// exclude all Object.* methods
    		new MethodFilter() {
    			public boolean matches(Method method) {
    				if(method.getDeclaringClass().equals(Object.class))
    					return false;
    				return true;
    			}
    		}
    	);
	}

	private void registerPointcutAndAdviceIfMethodIsPointcutAdvisor(final Class<?> atAspectClass, Method method) {
		String aspectName = getAspectName(atAspectClass);
		MetadataAwareAspectInstanceFactory aif = new BeanFactoryAspectInstanceFactory(beanFactory, aspectName, atAspectClass);
		Advisor pa = advisorFactory.getAdvisor(method, aif, 0, aspectName);
		if (pa != null && (pa instanceof PointcutAdvisor)) {
			String adviceName = method.getName();
			Advice advice = pa.getAdvice();
			// advice may return null in the case of named pointcuts (@Pointcut)
			if (advice != null) {
				Pointcut pointcut = ((PointcutAdvisor) pa).getPointcut();
				pointcuts.put(adviceName, pointcut);
				advices.put(adviceName, advice);
			}
		}
	}

	private String getAspectName(Class<?> atAspectClass) {
		return atAspectClass.getName();
	}

	public static JavaConfigAspectRegistry retrieveFrom(JavaConfigBeanFactory beanFactory) {
		String aspectRegistryBeanName = BEAN_NAME;

		if(!beanFactory.containsBean(aspectRegistryBeanName))
			throw new IllegalStateException("aspect registry bean is not present as expected in bean factory");

		return (JavaConfigAspectRegistry) beanFactory.getBean(aspectRegistryBeanName);
	}

}
