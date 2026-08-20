package me.desair.spring.tus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import me.desair.tus.server.TusFileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Spring MVC controller that routes incoming Tus and IETF RUFH file upload requests to the
 * corresponding {@link TusFileUploadService} backend instance (Disk, S3, or Azure Blob).
 */
@Controller
@CrossOrigin(origins = "*")
public class FileUploadController {

  private final TusFileUploadService diskTusFileUploadService;
  private final TusFileUploadService s3TusFileUploadService;
  private final TusFileUploadService azureTusFileUploadService;

  @Autowired
  public FileUploadController(
      @Qualifier("diskTusFileUploadService") TusFileUploadService diskTusFileUploadService,
      @Qualifier("s3TusFileUploadService") TusFileUploadService s3TusFileUploadService,
      @Qualifier("azureTusFileUploadService") TusFileUploadService azureTusFileUploadService) {
    this.diskTusFileUploadService = diskTusFileUploadService;
    this.s3TusFileUploadService = s3TusFileUploadService;
    this.azureTusFileUploadService = azureTusFileUploadService;
  }

  /**
   * Processes file upload requests using the default Disk storage backend.
   *
   * @param servletRequest the HTTP request
   * @param servletResponse the HTTP response
   * @throws IOException if an I/O error occurs during processing
   */
  @RequestMapping(
      value = {"/test/api/upload", "/test/api/upload/**", "/api/upload", "/api/upload/**"},
      method = {
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.HEAD,
        RequestMethod.DELETE,
        RequestMethod.OPTIONS,
        RequestMethod.GET
      })
  public void processDiskUpload(
      final HttpServletRequest servletRequest, final HttpServletResponse servletResponse)
      throws IOException {
    diskTusFileUploadService.process(servletRequest, servletResponse);
    servletResponse.addHeader(
        "Access-Control-Expose-Headers", "Location,Upload-Offset,Upload-Length");
  }

  /**
   * Processes file upload requests using the S3-compatible object storage backend.
   *
   * @param servletRequest the HTTP request
   * @param servletResponse the HTTP response
   * @throws IOException if an I/O error occurs during processing
   */
  @RequestMapping(
      value = {"/test-s3/api/upload", "/test-s3/api/upload/**"},
      method = {
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.HEAD,
        RequestMethod.DELETE,
        RequestMethod.OPTIONS,
        RequestMethod.GET
      })
  public void processS3Upload(
      final HttpServletRequest servletRequest, final HttpServletResponse servletResponse)
      throws IOException {
    s3TusFileUploadService.process(servletRequest, servletResponse);
    servletResponse.addHeader(
        "Access-Control-Expose-Headers", "Location,Upload-Offset,Upload-Length");
  }

  /**
   * Processes file upload requests using the Azure Blob storage backend.
   *
   * @param servletRequest the HTTP request
   * @param servletResponse the HTTP response
   * @throws IOException if an I/O error occurs during processing
   */
  @RequestMapping(
      value = {"/test-azure/api/upload", "/test-azure/api/upload/**"},
      method = {
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.HEAD,
        RequestMethod.DELETE,
        RequestMethod.OPTIONS,
        RequestMethod.GET
      })
  public void processAzureUpload(
      final HttpServletRequest servletRequest, final HttpServletResponse servletResponse)
      throws IOException {
    azureTusFileUploadService.process(servletRequest, servletResponse);
    servletResponse.addHeader(
        "Access-Control-Expose-Headers", "Location,Upload-Offset,Upload-Length");
  }
}
