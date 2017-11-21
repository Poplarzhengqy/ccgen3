/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.config.java.context;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.config.java.naming.BeanNamingStrategy;
import org.springframework.config.java.naming.MethodNameStrategy;

public class DefaultJavaConfigBeanFactory extends DefaultListableBeanFactory implements JavaConfigBeanFactory {

	/** Defaults to {@link MethodNameStrategy}
	 * @see #setBeanNamingStrategy(BeanNamingStrategy) to override
	 */
	BeanNamingStrategy beanNamingStrategy = new MethodNameStrategy();

	public DefaultJavaConfigBeanFactory(ConfigurableListableBeanFactory externalBeanFactory) {
		super(externalBeanFactory);
		this.copyConfigurationFrom(externalBeanFactory);
	}

	@Override
	public boolean isCurrentlyInCreation(String beanName) {
		if(super.isCurrentlyInCreation(beanName))
			return true;

		if(this.getParentBeanFactory() != null)
			return this.getParentBeanFactory().isCurrentlyInCreation(beanName);

		return super.isCurrentlyInCreation(beanName);
	}

	/**
	 * Overridden to exploit covariant return type
	 */
	@Override
	public DefaultListableBeanFactory getParentBeanFactory() {
		return (DefaultListableBeanFactory) super.getParentBeanFactory();
	}

	public void registerSingleton(String beanName, Object bean, BeanVisibility visibility) {
		switch(visibility) {
			case HIDDEN:
				registerSingleton(beanName, bean);
				break;
			case PUBLIC:
				getParentBeanFactory().registerSingleton(beanName, bean);
				break;
		}
	}

	public void registerBeanDefinition(String beanName, BeanDefinition beanDef, BeanVisibility visibility) {
		switch(visibility) {
			case HIDDEN:
				registerBeanDefinition(beanName, beanDef);
				break;
			case PUBLIC:
				getParentBeanFactory().registerBeanDefinition(beanName, beanDef);
				break;
		}
	}

	public void registerAlias(String beanName, String alias, BeanVisibility visibility) {
		switch(visibility) {
			case HIDDEN:
				registerAlias(beanName, alias);
				break;
			case PUBLIC:
				getParentBeanFactory().registerAlias(beanName, alias);
				break;
		}
	}

	public boolean containsBeanDefinition(String beanName, BeanVisibility visibility) {
		switch(visibility) {
			case HIDDEN:
				return containsBeanDefinition(beanName);
			case PUBLIC:
				return getParentBeanFactory().containsBeanDefinition(beanName);
			default:
				throw new IllegalArgumentException();
		}
	}

	public void setBeanNamingStrategy(BeanNamingStrategy beanNamingStrategy) {
		this.beanNamingStrategy = beanNamingStrategy;
	}

	public BeanNamingStrategy getBeanNamingStrategy() {
		return beanNamingStrategy;
	}

	/*
	@Override
	public String[] getBeanDefinitionNames() {
		LinkedHashSet<String> names = new LinkedHashSet<String>();

		for(String name : super.getBeanDefinitionNames())
			names.add(name);

		if(this.getParentBeanFactory() != null)
			for(String name : this.getParentBeanFactory().getBeanDefinitionNames())
				names.add(name);

		return names.toArray(new String[names.size()]);
	}

	@Override
	public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
		if(containsLocalBean(beanName))
			return super.getBeanDefinition(beanName);

		if(this.getParentBeanFactory() != null)
			return this.getParentBeanFactory().getBeanDefinition(beanName);

		return super.getBeanDefinition(beanName);
	}

	// Need to make special allowances for the BeanFactoryPostProcessors.
	// JavaConfig-specific post processors from the parent should NOT be applied to the child.
	@Override
	public String[] getBeanNamesForType(Class type, boolean includeNonSingletons, boolean allowEagerInit) {
		if(BeanFactoryPostProcessor.class.equals(type))
			return new String[] { };

		return super.getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	*/
}

