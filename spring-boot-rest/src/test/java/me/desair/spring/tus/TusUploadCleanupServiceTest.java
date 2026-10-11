package me.desair.spring.tus;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;
import me.desair.tus.server.TusFileUploadService;
import org.junit.jupiter.api.Test;

public class TusUploadCleanupServiceTest {

  @Test
  public void testCleanupExpiredUploadsInvokesAllServices() throws Exception {
    TusFileUploadService diskService = mock(TusFileUploadService.class);
    TusFileUploadService s3Service = mock(TusFileUploadService.class);
    TusFileUploadService azureService = mock(TusFileUploadService.class);

    TusUploadCleanupService cleanupService =
        new TusUploadCleanupService(List.of(diskService, s3Service, azureService));

    cleanupService.cleanupExpiredUploads();

    verify(diskService, times(1)).cleanup();
    verify(s3Service, times(1)).cleanup();
    verify(azureService, times(1)).cleanup();
  }

  @Test
  public void testCleanupContinuesWhenOneServiceFails() throws Exception {
    TusFileUploadService failingService = mock(TusFileUploadService.class);
    TusFileUploadService healthyService = mock(TusFileUploadService.class);

    doThrow(new IOException("S3 connection timeout")).when(failingService).cleanup();

    TusUploadCleanupService cleanupService =
        new TusUploadCleanupService(List.of(failingService, healthyService));

    // Cleanup should handle the exception gracefully without throwing
    cleanupService.cleanupExpiredUploads();

    verify(failingService, times(1)).cleanup();
    verify(healthyService, times(1)).cleanup();
  }

  @Test
  public void testScheduledCleanupDelegatesToCleanup() throws Exception {
    TusFileUploadService mockService = mock(TusFileUploadService.class);

    TusUploadCleanupService cleanupService =
        new TusUploadCleanupService(List.of(mockService));

    cleanupService.scheduledCleanup();

    verify(mockService, times(1)).cleanup();
  }
}
