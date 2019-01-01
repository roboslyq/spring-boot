/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.context.event;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

/**
 * {@link SpringApplicationRunListener} to publish {@link SpringApplicationEvent}s.
 * <p>
 * Uses an internal {@link ApplicationEventMulticaster} for the events that are fired
 * before the context is actually refreshed.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 */
public class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {

	private final SpringApplication application;

	private final String[] args;

	private final SimpleApplicationEventMulticaster initialMulticaster;

	/**
	 * 构造函数
	 * @param application
	 * @param args
	 */
	public EventPublishingRunListener(SpringApplication application, String[] args) {
		this.application = application;
		this.args = args;
		this.initialMulticaster = new SimpleApplicationEventMulticaster();
		//从application获取所有的监听器，并且
		for (ApplicationListener<?> listener : application.getListeners()) {
			this.initialMulticaster.addApplicationListener(listener);
		}
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	/**
	 * 监听方法：starting()
	 * 阶段说明：spring应用刚启动
	 * 事件类型：ApplicationStartingEvent
	 */
	public void starting() {
		//关键代码，这里是创建application启动事件`ApplicationStartingEvent`
		this.initialMulticaster.multicastEvent(
				new ApplicationStartingEvent(this.application, this.args));
	}

	@Override
	/**
	 * 监听方法：environmentPrepared(ConfigurableEnvironment environment)
	 * 阶段说明：ConfigurableEnvironment环境已经准备好，允许将其调整
	 * 事件类型：ApplicationEnvironmentPreparedEvent
	 */
	public void environmentPrepared(ConfigurableEnvironment environment) {
		this.initialMulticaster.multicastEvent(new ApplicationEnvironmentPreparedEvent(
				this.application, this.args, environment));
	}

	@Override
	/**
	 * 监听方法：contextPrepared(ConfigurableApplicationContext context)
	 * 阶段说明：ConfigurableApplicationContext context已经准备好，允许将其调整
	 * 事件类型：此处无事件实现
	 */
	public void contextPrepared(ConfigurableApplicationContext context) {

	}

	@Override
	/**
	 * 监听方法：contextLoaded(ConfigurableApplicationContext context)
	 * 阶段说明：ConfigurableApplicationContext已经装载，但仍未启动
	 * 事件类型：ApplicationPreparedEvent
	 */
	public void contextLoaded(ConfigurableApplicationContext context) {
		for (ApplicationListener<?> listener : this.application.getListeners()) {
			if (listener instanceof ApplicationContextAware) {
				((ApplicationContextAware) listener).setApplicationContext(context);
			}
			context.addApplicationListener(listener);
		}
		this.initialMulticaster.multicastEvent(
				new ApplicationPreparedEvent(this.application, this.args, context));
	}

	@Override
	/**
	 * 监听方法：started(ConfigurableApplicationContext context)
	 * 阶段说明：ConfigurableApplicationContext已经启动，此时spring Bean已经初始化完成
	 * 事件类型：ApplicationStartedEvent
	 */
	public void started(ConfigurableApplicationContext context) {
		context.publishEvent(
				new ApplicationStartedEvent(this.application, this.args, context));
	}

	@Override
	/**
	 * 监听方法：running(ConfigurableApplicationContext context)
	 * 阶段说明：spring 应用正在运行
	 * 事件类型：ApplicationReadyEvent
	 */
	public void running(ConfigurableApplicationContext context) {
		context.publishEvent(
				new ApplicationReadyEvent(this.application, this.args, context));
	}

	@Override
	/**
	 * 监听方法：failed(ConfigurableApplicationContext context, Throwable exception)
	 * 阶段说明：spring 应用运行失败
	 * 事件类型：ApplicationFailedEvent
	 */
	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		ApplicationFailedEvent event = new ApplicationFailedEvent(this.application,
				this.args, context, exception);
		if (context != null && context.isActive()) {
			// Listeners have been registered to the application context so we should
			// use it at this point if we can
			context.publishEvent(event);
		}
		else {
			// An inactive context may not have a multicaster so we use our multicaster to
			// call all of the context's listeners instead
			if (context instanceof AbstractApplicationContext) {
				for (ApplicationListener<?> listener : ((AbstractApplicationContext) context)
						.getApplicationListeners()) {
					this.initialMulticaster.addApplicationListener(listener);
				}
			}
			this.initialMulticaster.setErrorHandler(new LoggingErrorHandler());
			this.initialMulticaster.multicastEvent(event);
		}
	}

	private static class LoggingErrorHandler implements ErrorHandler {

		private static Log logger = LogFactory.getLog(EventPublishingRunListener.class);

		@Override
		public void handleError(Throwable throwable) {
			logger.warn("Error calling ApplicationEventListener", throwable);
		}

	}

}
