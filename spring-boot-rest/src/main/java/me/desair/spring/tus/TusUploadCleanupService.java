package me.desair.spring.tus;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.desair.tus.server.TusFileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service responsible for running expired upload cleanup across all configured storage backends.
 *
 * <p>Executes asynchronously in the background after server startup, and periodically on a daily
 * schedule (every day at 3:30 AM) via Spring's {@link Scheduled} mechanism.
 */
@Service
public class TusUploadCleanupService {

  private static final Logger LOG = LoggerFactory.getLogger(TusUploadCleanupService.class);

  private final List<TusFileUploadService> tusFileUploadServices;

  public TusUploadCleanupService(List<TusFileUploadService> tusFileUploadServices) {
    this.tusFileUploadServices = tusFileUploadServices;
  }

  /**
   * Executes cleanup asynchronously in the background immediately after the server has fully
   * started.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    LOG.info("Server started; triggering background expired upload cleanup task...");
    CompletableFuture.runAsync(this::cleanupExpiredUploads);
  }

  /** Scheduled cleanup executed every day at 3:30 AM. */
  @Scheduled(cron = "${tus.server.cleanup.cron:0 30 3 * * ?}")
  public void scheduledCleanup() {
    LOG.info("Triggering scheduled daily expired upload cleanup task at 3:30 AM...");
    cleanupExpiredUploads();
  }

  /**
   * Iterates through all configured TusFileUploadService beans and triggers cleanup of expired
   * uploads and stale temporary files.
   */
  public void cleanupExpiredUploads() {
    LOG.info(
        "Starting expired upload cleanup across {} storage backend(s)...",
        tusFileUploadServices.size());
    for (TusFileUploadService service : tusFileUploadServices) {
      try {
        service.cleanup();
      } catch (IOException e) {
        LOG.error("Error occurred while cleaning up expired uploads: {}", e.getMessage(), e);
      }
    }
    LOG.info("Expired upload cleanup completed.");
  }
}
