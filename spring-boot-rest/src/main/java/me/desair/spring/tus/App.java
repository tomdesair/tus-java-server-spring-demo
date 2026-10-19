package me.desair.spring.tus;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import java.util.List;
import me.desair.tus.server.TusFileUploadService;
import me.desair.tus.server.UploadCompletionListener;
import me.desair.tus.server.upload.azure.AzureBlobLockingService;
import me.desair.tus.server.upload.azure.AzureBlobStorageService;
import me.desair.tus.server.upload.s3.S3LockingService;
import me.desair.tus.server.upload.s3.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class App implements ApplicationListener<ContextRefreshedEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(App.class);

  private UploadCompletionListener createUploadCompletionListener(String storageBackend) {
    return (uploadInfo, service) -> {
      LOG.info(
          "[Upload Listener] [{}] Upload completed - ID: {}, File Name: {}, File Size: {} bytes, Checksum: {} ({}), Duplicates: {}",
          storageBackend,
          uploadInfo.getId(),
          uploadInfo.getFileName(),
          uploadInfo.getLength(),
          uploadInfo.getChecksum(),
          uploadInfo.getChecksumAlgorithm(),
          uploadInfo.getDuplicatesUploadId());
    };
  }

  @Value("${spring.profiles.active:dev}")
  protected String springProfilesActive;

  @Value("${tus.server.data.directory:${java.io.tmpdir}/tus}")
  protected String tusDataPath;

  @Value("${tus.server.max-upload-size:1073741824}")
  private Long maxUploadSize;

  @Value("${tus.server.max-append-size:65536}")
  private Long maxAppendSize;

  @Value("${tus.server.upload-expiration-period:86400000}")
  private Long uploadExpirationPeriod;

  // S3 Configuration Properties
  @Value("${tus.s3.endpoint:http://localhost:9000}")
  private String s3Endpoint;

  @Value("${tus.s3.bucket-name:tus-uploads}")
  private String s3BucketName;

  @Value("${tus.s3.access-key:minioadmin}")
  private String s3AccessKey;

  @Value("${tus.s3.secret-key:minioadmin}")
  private String s3SecretKey;

  @Value("${tus.s3.region:eu-central-1}")
  private String s3Region;

  @Value("${tus.s3.locking.jitter-min-ms:20}")
  private long s3JitterMinMs;

  @Value("${tus.s3.locking.jitter-max-ms:60}")
  private long s3JitterMaxMs;

  // Azure Blob Configuration Properties
  @Value(
      "${tus.azure.connection-string:DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;}")
  private String azureConnectionString;

  @Value("${tus.azure.container-name:tus-uploads}")
  private String azureContainerName;

  @Override
  public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
    LOG.info("=================================================================");
    LOG.info("App running with active profiles: {}", springProfilesActive);
    LOG.info("Max upload size configured: {} bytes", maxUploadSize);
    LOG.info("Max append size configured: {} bytes", maxAppendSize);
    LOG.info("Upload expiration period configured: {} ms", uploadExpirationPeriod);
    LOG.info("Storage backend endpoints:");
    LOG.info("  - Disk Storage (Default):   http://localhost:8080/test/       -> /test/api/upload");
    LOG.info(
        "  - S3 Object Storage:       http://localhost:8080/test-s3/    -> /test-s3/api/upload");
    LOG.info(
        "  - Azure Blob Storage:      http://localhost:8080/test-azure/ -> /test-azure/api/upload");
    LOG.info("=================================================================");
  }

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }

  /**
   * Default TusFileUploadService bean configured with DiskStorageService and
   * LeaseFileLockingService.
   *
   * @return Disk-backed TusFileUploadService instance
   */
  @Bean(name = {"tusFileUploadService", "diskTusFileUploadService"})
  @Primary
  public TusFileUploadService diskTusFileUploadService() {
    return new TusFileUploadService()
        .withStoragePath(tusDataPath)
        .withUploadUri("/test/api/upload")
        .withUploadCompletionListener(createUploadCompletionListener("Disk Storage"))
        .withMinAppendSize(32L)
        .withMaxAppendSize(maxAppendSize)
        .withMaxUploadSize(maxUploadSize)
        .withUploadExpirationPeriod(uploadExpirationPeriod)
        .withThreadLocalCache(true)
        .withUploadDeduplication(true)
        .withDownloadFeature();
  }

  /**
   * S3-compatible TusFileUploadService bean configured with S3StorageService and S3LockingService.
   *
   * @return S3-backed TusFileUploadService instance
   */
  @Bean(name = "s3TusFileUploadService")
  public TusFileUploadService s3TusFileUploadService() {
    S3StorageService s3StorageService =
        new S3StorageService(s3Endpoint, s3Region, s3AccessKey, s3SecretKey, s3BucketName);
    S3LockingService s3LockingService =
        new S3LockingService(s3Endpoint, s3Region, s3AccessKey, s3SecretKey, s3BucketName)
            .withJitter(s3JitterMinMs, s3JitterMaxMs);

    return new TusFileUploadService()
        .withUploadUri("/test-s3/api/upload")
        .withUploadStorageService(s3StorageService)
        .withUploadLockingService(s3LockingService)
        .withUploadCompletionListener(createUploadCompletionListener("S3 Storage"))
        .withMinAppendSize(32L)
        .withMaxUploadSize(maxUploadSize)
        .withUploadExpirationPeriod(uploadExpirationPeriod)
        .withThreadLocalCache(true)
        .withUploadDeduplication(true)
        .withDownloadFeature();
  }

  /**
   * Azure Blob Storage TusFileUploadService bean configured with AzureBlobStorageService and
   * AzureBlobLockingService.
   *
   * @return Azure-backed TusFileUploadService instance
   */
  @Bean(name = "azureTusFileUploadService")
  public TusFileUploadService azureTusFileUploadService() {
    BlobContainerClient containerClient =
        new BlobContainerClientBuilder()
            .connectionString(azureConnectionString)
            .containerName(azureContainerName)
            .buildClient();
    LOG.info("Azure BlobContainerUrl: {}", containerClient.getBlobContainerUrl());
    LOG.info("Azure container exists: {}", containerClient.exists());

    AzureBlobStorageService azureStorageService = new AzureBlobStorageService(containerClient);
    AzureBlobLockingService azureLockingService = new AzureBlobLockingService(containerClient);

    return new TusFileUploadService()
        .withUploadUri("/test-azure/api/upload")
        .withUploadStorageService(azureStorageService)
        .withUploadLockingService(azureLockingService)
        .withUploadCompletionListener(createUploadCompletionListener("Azure Blob Storage"))
        .withMinAppendSize(32L)
        .withMaxUploadSize(maxUploadSize)
        .withUploadExpirationPeriod(uploadExpirationPeriod)
        .withThreadLocalCache(true)
        .withUploadDeduplication(true)
        .withDownloadFeature();
  }

  @Bean
  public TomcatServletWebServerFactory tomcatFactory(
      @Qualifier("diskTusFileUploadService") TusFileUploadService diskService,
      @Qualifier("s3TusFileUploadService") TusFileUploadService s3Service,
      @Qualifier("azureTusFileUploadService") TusFileUploadService azureService) {
    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
    factory.addContextValves(
        new TusInterimResponseTomcatValve(List.of(diskService, s3Service, azureService)));
    return factory;
  }
}
