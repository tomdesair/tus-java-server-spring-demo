package me.desair.spring.tus;

import me.desair.tus.server.TusFileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;

@SpringBootApplication
public class App implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    @Value("${spring.profiles.active}")
    protected String springProfilesActive;

    @Value("${tus.server.data.directory}")
    protected String tusDataPath;

    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        LOG.info("=======================================");
        LOG.info("App running with active profiles: {}", springProfilesActive);
        LOG.info("=======================================");
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(App.class, args);
    }

    @Bean
    public TusFileUploadService tusFileUploadService() {
        return new TusFileUploadService()
                .withStoragePath(tusDataPath)
                .withDownloadFeature()
                .withUploadURI(servletContextPath + "/api/upload")
                .withThreadLocalCache(true);
    }

}
