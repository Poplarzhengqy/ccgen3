package org.springframework.config.java.model;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.springframework.beans.factory.support.AbstractBeanDefinition.AUTOWIRE_BY_TYPE;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;
import static org.springframework.config.java.context.BeanVisibility.HIDDEN;
import static org.springframework.config.java.context.BeanVisibility.PUBLIC;
import static org.springframework.config.java.model.AnnotationExtractionUtils.extractClassAnnotation;
import static org.springframework.config.java.model.AnnotationExtractionUtils.extractMethodAnnotation;
import static org.springframework.config.java.model.AutoBeanMethodTests.VALID_AUTOBEAN_METHOD;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.BeanMetadataAttribute;
import org.springframework.beans.TestBean;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.config.java.annotation.Bean;
import org.springframework.config.java.annotation.Configuration;
import org.springframework.config.java.annotation.Primary;
import org.springframework.config.java.context.JavaConfigBeanFactory;
import org.springframework.config.java.naming.MethodNameStrategy;
import org.springframework.config.java.util.DefaultScopes;

/**
 * TODO: clean up duplication between the tests herein
 *
 * @author Chris Beams
 */
public class ConfigurationModelBeanDefinitionReaderTests {

	private ConfigurationModelBeanDefinitionReader renderer;
	private JavaConfigBeanFactory registry;
	private ConfigurationModel model;

	@Before
	public void setUp() {
		registry = createMock(JavaConfigBeanFactory.class);
		renderer = new ConfigurationModelBeanDefinitionReader(registry);
		model = new ConfigurationModel();
	}

	public @Test void render() {
		String configClassName = "com.acme.OrderConfig";
		String beanName = "order";

		// create a simple configuration model
		model.add(new ConfigurationClass(configClassName).add(new BeanMethod(beanName)));
		model.assertIsValid();

		// encode expectations
		expect(registry.getBeanDefinitionCount()).andReturn(0);

		expect(registry.getBeanNamingStrategy()).andReturn(new MethodNameStrategy());

		RootBeanDefinition configBeanDef = new RootBeanDefinition();
		configBeanDef.setBeanClassName(configClassName);
		configBeanDef.addMetadataAttribute(new BeanMetadataAttribute(ConfigurationClass.BEAN_ATTR_NAME, true));
		registry.registerBeanDefinition(configClassName, configBeanDef, PUBLIC);
		expectLastCall();

		BeanDefinition beanDef =
			rootBeanDefinition((String)null)
				.setFactoryBean(configClassName, beanName)
				.getBeanDefinition();
		registry.registerBeanDefinition(beanName, beanDef, HIDDEN);
		expectLastCall();

		expect(registry.getBeanDefinitionCount()).andReturn(2);

		replay(registry);

		// execute assertions and verifications
		assertEquals(2, renderer.loadBeanDefinitions(model));

		verify(registry);
	}

	public @Test void withScope() {
		String configClassName = "com.acme.OrderConfig";
		String beanName = "order";

		// create a simple configuration model
		Bean metadata = extractMethodAnnotation(Bean.class, new MethodAnnotationPrototype() {
			@Bean(scope=DefaultScopes.PROTOTYPE)
			public void targetMethod() { }
		}.getClass());
		model.add(new ConfigurationClass(configClassName).add(new BeanMethod(beanName, metadata)));
		model.assertIsValid();

		// encode expectations
		expect(registry.getBeanDefinitionCount()).andReturn(0);

		expect(registry.getBeanNamingStrategy()).andReturn(new MethodNameStrategy());

		RootBeanDefinition configBeanDef = new RootBeanDefinition();
		configBeanDef.setBeanClassName(configClassName);
		configBeanDef.addMetadataAttribute(new BeanMetadataAttribute(ConfigurationClass.BEAN_ATTR_NAME, true));
		registry.registerBeanDefinition(configClassName, configBeanDef, PUBLIC);
		expectLastCall();


		RootBeanDefinition rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName(configClassName);
		rbd.setFactoryMethodName(beanName);
		rbd.setScope(DefaultScopes.PROTOTYPE);
		registry.registerBeanDefinition(beanName, rbd, HIDDEN);
		expectLastCall();

		expect(registry.getBeanDefinitionCount()).andReturn(2);

		replay(registry);

		// execute assertions and verifications
		assertEquals(2, renderer.loadBeanDefinitions(model));

		verify(registry);
	}

	public @Test void withAutoBean() {
		String configClassName = "com.acme.OrderConfig";
		String beanName = VALID_AUTOBEAN_METHOD.getName();

		// create a simple configuration model
		model.add(new ConfigurationClass(configClassName).add(VALID_AUTOBEAN_METHOD));
		model.assertIsValid();

		// encode expectations
		expect(registry.getBeanDefinitionCount()).andReturn(0);

		RootBeanDefinition configBeanDef = new RootBeanDefinition();
		configBeanDef.setBeanClassName(configClassName);
		configBeanDef.addMetadataAttribute(new BeanMetadataAttribute(ConfigurationClass.BEAN_ATTR_NAME, true));
		registry.registerBeanDefinition(configClassName, configBeanDef, PUBLIC);
		expectLastCall();

		RootBeanDefinition rbd = new RootBeanDefinition();
		rbd.setBeanClassName(TestBean.class.getName());
		rbd.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
		registry.registerBeanDefinition(beanName, rbd, PUBLIC);
		expectLastCall();

		expect(registry.getBeanDefinitionCount()).andReturn(2);

		replay(registry);

		// execute assertions and verifications
		assertEquals(2, renderer.loadBeanDefinitions(model));

		verify(registry);
	}

	public @Test void renderWithPrimaryBean() {
		String configClassName = "com.acme.OrderConfig";
		String beanName = "order";

		// create a simple configuration model
		Bean metadata = extractMethodAnnotation(Bean.class, new MethodAnnotationPrototype() {
			@Bean(primary=Primary.TRUE)
			public void targetMethod() { }
		}.getClass());
		model.add(new ConfigurationClass(configClassName).add(new BeanMethod(beanName, metadata)));
		model.assertIsValid();

		// encode expectations
		expect(registry.getBeanDefinitionCount()).andReturn(0);

		expect(registry.getBeanNamingStrategy()).andReturn(new MethodNameStrategy());

		RootBeanDefinition configBeanDef = new RootBeanDefinition();
		configBeanDef.setBeanClassName(configClassName);
		configBeanDef.addMetadataAttribute(new BeanMetadataAttribute(ConfigurationClass.BEAN_ATTR_NAME, true));
		registry.registerBeanDefinition(configClassName, configBeanDef, PUBLIC);
		expectLastCall();


		RootBeanDefinition rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName(configClassName);
		rbd.setFactoryMethodName(beanName);
		rbd.setPrimary(true);
		registry.registerBeanDefinition(beanName, rbd, HIDDEN);
		expectLastCall();

		expect(registry.getBeanDefinitionCount()).andReturn(2);

		replay(registry);

		// execute assertions and verifications
		assertEquals(2, renderer.loadBeanDefinitions(model));

		verify(registry);
	}

	/*
	 * Tests @Configuration(defaultAutowire=Autowire.BY_TYPE)
	 */
	public @Test void renderWithDefaultAutowireByType() {
		String configClassName = "com.acme.OrderConfig";
		String beanName = "order";

		// create a simple configuration model
		@Configuration(defaultAutowire=Autowire.BY_TYPE)
		class Prototype{ }
		Configuration metadata = extractClassAnnotation(Configuration.class, Prototype.class);

		model.add(new ConfigurationClass(configClassName, metadata).add(new BeanMethod(beanName)));
		model.assertIsValid();

		// encode expectations
		expect(registry.getBeanDefinitionCount()).andReturn(0);

		expect(registry.getBeanNamingStrategy()).andReturn(new MethodNameStrategy());

		// expect the registration of our Configuration class above
		RootBeanDefinition configBeanDef = new RootBeanDefinition();
		configBeanDef.setBeanClassName(configClassName);
		configBeanDef.addMetadataAttribute(new BeanMetadataAttribute(ConfigurationClass.BEAN_ATTR_NAME, true));
		registry.registerBeanDefinition(configClassName, configBeanDef, PUBLIC);
		expectLastCall();

		// expect the registration of the @Bean method above
		RootBeanDefinition rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName(configClassName);
		rbd.setFactoryMethodName(beanName);
		rbd.setAutowireMode(AUTOWIRE_BY_TYPE);
		registry.registerBeanDefinition(beanName, rbd, HIDDEN);
		expectLastCall();

		expect(registry.getBeanDefinitionCount()).andReturn(2);

		replay(registry);

		// execute assertions and verifications
		assertEquals(2, renderer.loadBeanDefinitions(model));

		verify(registry);
	}

	/*
	 * Tests @Bean(autowire=Autowire.BY_TYPE)
	 */
	public @Test void renderWithBeanAutowireByType() {
		String configClassName = "com.acme.OrderConfig";
		String beanName = "order";

		// create a simple configuration model
		Bean metadata = AnnotationExtractionUtils.extractMethodAnnotation(Bean.class, new MethodAnnotationPrototype() {
			@Bean(autowire=Autowire.BY_TYPE)
			public void targetMethod() { }
		}.getClass());

		model.add(new ConfigurationClass(configClassName).add(new BeanMethod(beanName, metadata)));
		model.assertIsValid();

		// encode expectations
		expect(registry.getBeanDefinitionCount()).andReturn(0);

		expect(registry.getBeanNamingStrategy()).andReturn(new MethodNameStrategy());

		// expect the registration of our Configuration class above
		RootBeanDefinition configBeanDef = new RootBeanDefinition();
		configBeanDef.setBeanClassName(configClassName);
		configBeanDef.addMetadataAttribute(new BeanMetadataAttribute(ConfigurationClass.BEAN_ATTR_NAME, true));
		registry.registerBeanDefinition(configClassName, configBeanDef, PUBLIC);
		expectLastCall();

		// expect the registration of the @Bean method above
		RootBeanDefinition rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName(configClassName);
		rbd.setFactoryMethodName(beanName);
		rbd.setAutowireMode(AUTOWIRE_BY_TYPE);
		registry.registerBeanDefinition(beanName, rbd, HIDDEN);
		expectLastCall();

		expect(registry.getBeanDefinitionCount()).andReturn(2);

		replay(registry);

		// execute assertions and verifications
		assertEquals(2, renderer.loadBeanDefinitions(model));

		verify(registry);
	}

	public @Test void renderWithAliases() {
		String configClassName = "com.acme.OrderConfig";
		String beanName = "order";

		// create a simple configuration model
		Bean metadata = extractMethodAnnotation(Bean.class, new MethodAnnotationPrototype() {
			@Bean(aliases = { "tom", "dick", "harry" })
			public void targetMethod() { }
		}.getClass());

		model.add(new ConfigurationClass(configClassName).add(new BeanMethod(beanName, metadata)));
		model.assertIsValid();

		// encode expectations
		expect(registry.getBeanDefinitionCount()).andReturn(0);

		expect(registry.getBeanNamingStrategy()).andReturn(new MethodNameStrategy());

		// expect the registration of our Configuration class above
		RootBeanDefinition configBeanDef = new RootBeanDefinition();
		configBeanDef.setBeanClassName(configClassName);
		configBeanDef.addMetadataAttribute(new BeanMetadataAttribute(ConfigurationClass.BEAN_ATTR_NAME, true));
		registry.registerBeanDefinition(configClassName, configBeanDef, PUBLIC);

		// expect the registration of the @Bean method above
		RootBeanDefinition rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName(configClassName);
		rbd.setFactoryMethodName(beanName);
		registry.registerBeanDefinition(beanName, rbd, HIDDEN);

		// expect registration of aliases
		registry.registerAlias("order", "tom", PUBLIC);
		registry.registerAlias("order", "dick", PUBLIC);
		registry.registerAlias("order", "harry", PUBLIC);

		expect(registry.getBeanDefinitionCount()).andReturn(2);

		// all done with expectations
		replay(registry);

		// execute assertions and verifications
		assertEquals(2, renderer.loadBeanDefinitions(model));

		verify(registry);
	}

}
