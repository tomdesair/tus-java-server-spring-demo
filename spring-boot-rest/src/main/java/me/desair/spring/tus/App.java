package me.desair.spring.tus;

import me.desair.tus.server.TusFileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;

@SpringBootApplication
public class App implements ApplicationListener<ContextRefreshedEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(App.class);

  @Value("${spring.profiles.active}")
  protected String springProfilesActive;

  @Value("${tus.server.data.directory}")
  protected String tusDataPath;

  @Value("${server.servlet.context-path:/test}")
  private String servletContextPath;

  @Value("${tus.server.max-upload-size:1073741824}")
  private Long maxUploadSize;

  @Override
  public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
    LOG.info("=======================================");
    LOG.info("App running with active profiles: {}", springProfilesActive);
    LOG.info("Max upload size configured: {} bytes", maxUploadSize);
    LOG.info("=======================================");
  }

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }

  @Bean
  public TusFileUploadService tusFileUploadService() {
    return new TusFileUploadService()
        .withStoragePath(tusDataPath)
        .withUploadUri(servletContextPath + "/api/upload")
        .withMinAppendSize(32L)
        .withMaxUploadSize(maxUploadSize)
        .withThreadLocalCache(true);
  }

  @Bean
  public TomcatServletWebServerFactory tomcatFactory(TusFileUploadService tusFileUploadService) {
    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
    factory.addContextValves(new TusInterimResponseTomcatValve(tusFileUploadService));
    return factory;
  }
}
