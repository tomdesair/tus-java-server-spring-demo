package me.desair.spring.tus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import me.desair.tus.server.HttpHeader;
import me.desair.tus.server.TusFileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class FileUploadControllerTest {

  @Autowired
  @Qualifier("diskTusFileUploadService")
  private TusFileUploadService diskTusFileUploadService;

  @Autowired
  @Qualifier("s3TusFileUploadService")
  private TusFileUploadService s3TusFileUploadService;

  @Autowired
  @Qualifier("azureTusFileUploadService")
  private TusFileUploadService azureTusFileUploadService;

  @Autowired private MockMvc mockMvc;

  @Test
  public void contextLoads() {
    assertNotNull(diskTusFileUploadService, "diskTusFileUploadService should be instantiated");
    assertNotNull(s3TusFileUploadService, "s3TusFileUploadService should be instantiated");
    assertNotNull(azureTusFileUploadService, "azureTusFileUploadService should be instantiated");
  }

  @Test
  public void testDiskUploadEndpointOptions() throws Exception {
    // TUS OPTIONS request on disk endpoint should return 204 No Content
    mockMvc
        .perform(options("/test/api/upload").header("Tus-Resumable", "1.0.0"))
        .andExpect(status().isNoContent());
  }

  @Test
  public void testDiskUploadEndpointPostWithoutHeaders() throws Exception {
    // Standard POST without Tus headers rejected with 412 Precondition Failed
    mockMvc.perform(post("/test/api/upload")).andExpect(status().isPreconditionFailed());
  }

  @Test
  public void testLegacyDiskUploadEndpointOptions() throws Exception {
    // Legacy /api/upload alias should also return 204 No Content
    mockMvc
        .perform(options("/api/upload").header("Tus-Resumable", "1.0.0"))
        .andExpect(status().isNoContent());
  }

  @Test
  public void testS3UploadEndpointOptions() throws Exception {
    // TUS OPTIONS request on S3 endpoint should return 204 No Content
    mockMvc
        .perform(options("/test-s3/api/upload").header("Tus-Resumable", "1.0.0"))
        .andExpect(status().isNoContent());
  }

  @Test
  public void testS3UploadEndpointPostWithoutHeaders() throws Exception {
    // Standard POST without Tus headers rejected with 412 Precondition Failed
    mockMvc.perform(post("/test-s3/api/upload")).andExpect(status().isPreconditionFailed());
  }

  @Test
  public void testAzureUploadEndpointOptions() throws Exception {
    // TUS OPTIONS request on Azure endpoint should return 204 No Content
    mockMvc
        .perform(options("/test-azure/api/upload").header("Tus-Resumable", "1.0.0"))
        .andExpect(status().isNoContent());
  }

  @Test
  public void testAzureUploadEndpointPostWithoutHeaders() throws Exception {
    // Standard POST without Tus headers rejected with 412 Precondition Failed
    mockMvc.perform(post("/test-azure/api/upload")).andExpect(status().isPreconditionFailed());
  }

  @Test
  public void testPageRoutesForwarding() throws Exception {
    mockMvc.perform(get("/test")).andExpect(forwardedUrl("/test/index.html"));
    mockMvc.perform(get("/test/")).andExpect(forwardedUrl("/test/index.html"));
    mockMvc.perform(get("/test-s3")).andExpect(forwardedUrl("/test-s3/index.html"));
    mockMvc.perform(get("/test-s3/")).andExpect(forwardedUrl("/test-s3/index.html"));
    mockMvc.perform(get("/test-azure")).andExpect(forwardedUrl("/test-azure/index.html"));
    mockMvc.perform(get("/test-azure/")).andExpect(forwardedUrl("/test-azure/index.html"));
  }

  @Test
  public void testDiskUploadCompletion() throws Exception {
    byte[] content = "Hello Tus Upload Completion!".getBytes();

    // 1. Create upload via MockMvc POST
    MvcResult postResult =
        mockMvc
            .perform(
                post("/test/api/upload")
                    .header(HttpHeader.TUS_RESUMABLE, TusFileUploadService.TUS_API_VERSION)
                    .header(HttpHeader.UPLOAD_LENGTH, String.valueOf(content.length))
                    .header(HttpHeader.UPLOAD_METADATA, "filename dGVzdC50eHQ="))
            .andExpect(status().isCreated())
            .andReturn();

    String location = postResult.getResponse().getHeader(HttpHeader.LOCATION);
    assertNotNull(location, "Location header must be present on upload creation");

    // 2. Upload file content to complete the upload via MockMvc PATCH
    mockMvc
        .perform(
            patch(location)
                .header(HttpHeader.TUS_RESUMABLE, TusFileUploadService.TUS_API_VERSION)
                .header(HttpHeader.UPLOAD_OFFSET, "0")
                .contentType(HttpHeader.CONTENT_TYPE_OFFSET_OCTET_STREAM)
                .content(content))
        .andExpect(status().isNoContent());

    // 3. Retrieve uploaded bytes via tusFileUploadService and verify content
    try (InputStream is = diskTusFileUploadService.getUploadedBytes(location)) {
      assertNotNull(is, "Uploaded byte stream must not be null");
      byte[] retrievedBytes = is.readAllBytes();
      assertArrayEquals(content, retrievedBytes, "Uploaded bytes must match expected content");
    }
  }
}
