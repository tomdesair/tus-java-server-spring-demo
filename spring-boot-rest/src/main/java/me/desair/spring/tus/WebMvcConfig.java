package me.desair.spring.tus;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private static final String[] CLASSPATH_RESOURCE_LOCATIONS = {"classpath:/public/"};

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    if (!registry.hasMappingForPattern("/webjars/**")) {
      registry
          .addResourceHandler("/webjars/**")
          .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
    if (!registry.hasMappingForPattern("/**")) {
      registry.addResourceHandler("/**").addResourceLocations(CLASSPATH_RESOURCE_LOCATIONS);
    }
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/test").setViewName("forward:/test/index.html");
    registry.addViewController("/test/").setViewName("forward:/test/index.html");
    registry.addViewController("/test-s3").setViewName("forward:/test-s3/index.html");
    registry.addViewController("/test-s3/").setViewName("forward:/test-s3/index.html");
    registry.addViewController("/test-azure").setViewName("forward:/test-azure/index.html");
    registry.addViewController("/test-azure/").setViewName("forward:/test-azure/index.html");
  }
}
