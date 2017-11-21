package org.springframework.config.java.context;

import static java.lang.String.format;
import static org.springframework.config.java.context.BeanVisibility.visibilityOf;
import static org.springframework.config.java.core.ScopedProxyMethodProcessor.resolveHiddenScopedProxyBeanName;
import static org.springframework.config.java.util.DefaultScopes.SINGLETON;
import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.StringUtils.hasLength;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.proxy.NoOp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.config.java.annotation.AutoBean;
import org.springframework.config.java.annotation.Bean;
import org.springframework.config.java.annotation.ExternalBean;
import org.springframework.config.java.annotation.ExternalValue;
import org.springframework.config.java.annotation.ResourceBundles;
import org.springframework.config.java.annotation.aop.ScopedProxy;
import org.springframework.config.java.model.JavaConfigAspectRegistry;
import org.springframework.config.java.valuesource.ValueResolutionException;
import org.springframework.config.java.valuesource.ValueSource;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class CglibConfigurationEnhancer implements ConfigurationEnhancer {
	private static final Log log = LogFactory.getLog(CglibConfigurationEnhancer.class);
	private final JavaConfigBeanFactory beanFactory;

	public CglibConfigurationEnhancer(JavaConfigBeanFactory beanFactory) {
		notNull(beanFactory, "beanFactory must be non-null");
		this.beanFactory = beanFactory;
	}

	public String enhance(String configClassName) {
		if(log.isInfoEnabled())
			log.info("Enhancing " + configClassName);

		Class<?> configClass = loadClassFromName(configClassName);

		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(configClass);
		enhancer.setUseFactory(false);
		enhancer.setCallbackFilter(new CallbackFilter() {
			public int accept(Method candidateMethod) {
				if(findAnnotation(candidateMethod, Bean.class) != null)
					return 1;
				if(findAnnotation(candidateMethod, ExternalBean.class) != null)
					return 2;
				if(findAnnotation(candidateMethod, AutoBean.class) != null)
					return 3;
				if(findAnnotation(candidateMethod, ExternalValue.class) != null)
					return 4;
				return 0;
			}
		});
		enhancer.setCallbackTypes(
			new Class<?>[] {
    			NoOp.class,
    			BeanMethodInterceptor.class,
    			ExternalBeanMethodInterceptor.class,
    			AutoBeanMethodInterceptor.class,
    			ExternalValueMethodInterceptor.class
			});

		Class<?> enhancedSubclass = enhancer.createClass();

		Enhancer.registerCallbacks(enhancedSubclass,
			new Callback[] {
				NoOp.INSTANCE,
				new BeanMethodInterceptor(beanFactory),
				new ExternalBeanMethodInterceptor(beanFactory),
				new AutoBeanMethodInterceptor(beanFactory),
				new ExternalValueMethodInterceptor(beanFactory)
			});

		if(log.isInfoEnabled())
			log.info(format("Successfully enhanced %s; enhanced class name is: %s",
							  configClassName, enhancedSubclass.getName()));

		return enhancedSubclass.getName();
	}

	private Class<?> loadClassFromName(String configClassName) {
		try {
			return Class.forName(configClassName);
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalArgumentException("class must be loadable", ex);
		}
	}

	static class ExternalBeanMethodInterceptor implements MethodInterceptor {
		private final BeanFactory beanFactory;

		public ExternalBeanMethodInterceptor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		public Object intercept(Object o, Method m, Object[] args, MethodProxy mp) throws Throwable {
			final String name;

			ExternalBean extBean = AnnotationUtils.findAnnotation(m, ExternalBean.class);
			Assert.notNull(extBean, "ExternalBean methods must be annotated with @ExternalBean");

			String alternateName = extBean.value();
			if(StringUtils.hasLength(alternateName))
				name = alternateName;
			else
				name = m.getName();

			return beanFactory.getBean(name);
		}
	}

	static class ExternalValueMethodInterceptor implements MethodInterceptor {
		private final BeanFactory beanFactory;
		private ValueSource valueSource;

		public ExternalValueMethodInterceptor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		public Object intercept(Object o, Method m, Object[] args, MethodProxy mp) throws Throwable {
			ExternalValue metadata = AnnotationUtils.findAnnotation(m, ExternalValue.class);
			Assert.notNull(metadata, "ExternalValue methods must be annotated with @ExternalValue");

    		String name = metadata.value();
    		if (!hasLength(name)) {
    			// no explicit name provided -> use method name
    			// TODO: {naming strategy} plug in naming strategy
    			name = m.getName();
    			// Strip property name if needed
    			if (name.startsWith("get") && Character.isUpperCase(name.charAt(3)))
    				name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
    		}

			Class<?> requiredType = m.getReturnType();

			if(valueSource == null)
				initializeValueSource(m);

			try {
				return valueSource.resolve(name, requiredType);
			}
			catch(ValueResolutionException ex) {
				// value was not found in properties -> default to the body of the method (if any exists)
				if(Modifier.isAbstract(m.getModifiers()))
					throw ex; // cannot call super implementation if it's abstract.

				return mp.invokeSuper(o, args);
			}
		}

		/**
		 * Lazily initializes value source by retrieving it from the beanFactory. This design
		 * allows this method interceptor to be wired up by {@link CglibConfigurationEnhancer}
		 * eagerly and then only cause a failure if a user actually tries to access an
		 * {@link ExternalValue @ExternalValue} method without having provided a
		 * {@link ResourceBundles @ResourceBundles} annotation.
		 */
		private void initializeValueSource(Method m) {
    		if(beanFactory.containsBean("valueSource")) {
    			valueSource = (ValueSource) beanFactory.getBean("valueSource");
    		}
    		else {
    			String className = m.getDeclaringClass().getSimpleName();
    			String methodName = m.getName();
    			throw new IllegalStateException(format("No ValueSource bean could be found in " +
    					"beanFactory while trying to resolve @ExternalValue method %s.%s. " +
    					"Perhaps no @ResourceBundles annotation was provided on %s?",
    					className, methodName, className));
    		}
		}
	}

	static class AutoBeanMethodInterceptor implements MethodInterceptor {
		private final BeanFactory beanFactory;

		public AutoBeanMethodInterceptor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		public Object intercept(Object o, Method m, Object[] args, MethodProxy mp) throws Throwable {
			final String name;

			AutoBean metadata = AnnotationUtils.findAnnotation(m, AutoBean.class);
			Assert.notNull(metadata, "AutoBean methods must be annotated with @AutoBean");

			name = m.getName();

			return beanFactory.getBean(name);
		}
	}

	/**
	 * Intercepts calls to {@link Bean @Bean} methods delegating to
	 * {@link #intercept(Object, Method, Object[], MethodProxy) intercept()} in order
	 * to ensure proper bean functionality: singleton, AOP proxying, etc.
	 *
	 * @author Chris Beams
	 */
	static class BeanMethodInterceptor implements MethodInterceptor {
		private static final Log log = LogFactory.getLog(BeanMethodInterceptor.class);
		private final JavaConfigAspectRegistry aspectRegistry;
		private final JavaConfigBeanFactory beanFactory;

		public BeanMethodInterceptor(JavaConfigBeanFactory beanFactory) {
			this.aspectRegistry = JavaConfigAspectRegistry.retrieveFrom(beanFactory);
			this.beanFactory = beanFactory;
		}

		/**
		 * Enhances a {@link Bean @Bean} method to check the supplied BeanFactory for the existence
		 * of this bean object
		 */
		public Object intercept(Object o, Method m, Object[] args, MethodProxy mp) throws Throwable {
			// by default the bean will be named according to the name of the method
			// TODO: incorporate BeanNamingStrategy here
			String beanName = m.getName();

			boolean isScopedProxy = (AnnotationUtils.findAnnotation(m, ScopedProxy.class) != null);
			String scopedBeanName = resolveHiddenScopedProxyBeanName(beanName);
			if(isScopedProxy && beanFactory.isCurrentlyInCreation(scopedBeanName))
				beanName = scopedBeanName;

			if(factoryContainsBean(beanName)) {
				// we have an already existing cached instance of this bean -> retrieve it
				Object cachedBean = beanFactory.getBean(beanName);
				if(log.isInfoEnabled())
					log.info(format("Returning cached singleton object [%s] for @Bean method %s.%s",
							cachedBean, m.getDeclaringClass().getSimpleName(), m.getName()));
				return cachedBean;
			}

			// no instance exists yet -> create a new one
			Object bean = mp.invokeSuper(o, args);

			bean = aspectRegistry.proxyIfAnyPointcutsApply(bean, m);

			Bean metadata = AnnotationUtils.findAnnotation(m, Bean.class);
			if(metadata.scope().equals(SINGLETON)) {
				BeanVisibility visibility = visibilityOf(m.getModifiers());
				if(log.isInfoEnabled())
					log.info(format("Registering new %s singleton object [%s] for @Bean method %s.%s",
						visibility, bean, m.getDeclaringClass().getSimpleName(), m.getName()));

				beanFactory.registerSingleton(beanName, bean, visibility);
			}

			return bean;
		}

		/**
		 * Check the beanFactory to see whether the bean named <var>beanName</var> already exists.
		 * Accounts for the fact that the requested bean may be "in creation", i.e.: we're in the
		 * middle of servicing the initial request for this bean.  From JavaConfig's perspective,
		 * this means that the bean does not actually yet exist, and that it is now our job to create
		 * it for the first time by executing the logic in the corresponding Bean method.
		 *
		 * @param beanName name of bean to check for
		 * @return true if <var>beanName</var> already exists in beanFactory
		 */
		private boolean factoryContainsBean(String beanName) {
			// TODO: stopgap check; see todos above, this type wrangling needs to get worked out better
			Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory,
					"beanFactory must be of type ConfigurableListableBeanFactory. Actual type: " +
					beanFactory.getClass().getSimpleName());

			return beanFactory.containsBean(beanName)
				&& !beanFactory.isCurrentlyInCreation(beanName);
		}

	}

}
