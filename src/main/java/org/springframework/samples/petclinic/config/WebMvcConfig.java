/*
 * Copyright 2002-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.format.FormatterRegistry;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.samples.petclinic.model.Vets;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.web.PetTypeFormatter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;
import org.springframework.web.servlet.view.JstlView;
import org.springframework.web.servlet.view.xml.MarshallingView;

/**
 * Spring MVC configuration for the DispatcherServlet's application context.
 * <p>
 * Replaces the former {@code spring/mvc-core-config.xml} and {@code spring/mvc-view-config.xml}: Spring Framework 7
 * deprecates the {@code mvc:} XML namespace. Each part below says which XML element it replaces. The root context
 * (services, persistence) is still configured in XML, see {@code PetclinicInitializer}.
 */
@Configuration
@EnableWebMvc // <mvc:annotation-driven/>
@ComponentScan("org.springframework.samples.petclinic.web") // <context:component-scan base-package="...web"/>
public class WebMvcConfig implements WebMvcConfigurer {

    private final ClinicService clinicService;

    public WebMvcConfig(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    /** {@code conversion-service="conversionService"} with its {@code PetTypeFormatter}. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new PetTypeFormatter(this.clinicService));
    }

    /**
     * {@code <mvc:resources mapping="/resources/**" location="/resources/"/>} for the files in
     * src/main/webapp/resources, and {@code <mvc:resources mapping="/webjars/**" .../>} so Javascript and CSS
     * libraries can be declared as Maven dependencies (Bootstrap, Font Awesome...).
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    /**
     * Keeps the content negotiation behaviour of the former XML configuration. Without this, Java config registers
     * default file-extension mappings ("xml", "json", ...) that let the ContentNegotiatingViewResolver pick the
     * 'vets/vetList.xml' view for {@code GET /vets} with {@code Accept: application/xml} (200 with XML). The XML
     * configuration answered 406 there. Verified by comparing 56 HTTP responses before and after the switch and pinned
     * by {@code ContentNegotiationCharacterizationTests}. Why the XML namespace behaved differently inside Spring was
     * not investigated further.
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.replaceMediaTypes(Map.of());
    }

    /** {@code <mvc:view-controller path="/" view-name="welcome"/>} */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("welcome");
    }

    /**
     * {@code <mvc:default-servlet-handler/>}: serve static resources (*.html, ...) from src/main/webapp/. Required when
     * both servlet-mapping is '/' and static resources need to be served.
     */
    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    /**
     * {@code <mvc:view-resolvers>}: a ContentNegotiatingViewResolver (with {@code use-not-acceptable="true"} and a
     * default JstlView) that delegates to a BeanNameViewResolver and to the JSP InternalResourceViewResolver.
     */
    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        JstlView defaultView = new JstlView();
        defaultView.setUrl("");
        registry.enableContentNegotiation(true, defaultView);
        registry.beanName();
        registry.jsp("/WEB-INF/jsp/", ".jsp");
    }

    /** Message source for this context, loaded from localized "messages_xx" files in src/main/resources. */
    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/messages");
        return messageSource;
    }

    /**
     * Resolves exceptions to the 'exception' view (WEB-INF/jsp/exception.jsp). Declared as a bean, as in the XML, so
     * the DispatcherServlet uses it after Spring MVC's own exception resolvers.
     */
    @Bean
    public SimpleMappingExceptionResolver simpleMappingExceptionResolver() {
        SimpleMappingExceptionResolver resolver = new SimpleMappingExceptionResolver();
        resolver.setDefaultErrorView("exception");
        // needed otherwise exceptions won't be logged anywhere
        resolver.setWarnLogCategory("warn");
        return resolver;
    }

    /** Renders an XML view. Used by the BeanNameViewResolver. */
    @Bean(name = "vets/vetList.xml")
    public MarshallingView vetsXmlView() {
        return new MarshallingView(marshaller());
    }

    /** {@code <oxm:jaxb2-marshaller>}: Object-XML mapping declared using annotations inside 'Vets'. */
    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(Vets.class);
        return marshaller;
    }
}
