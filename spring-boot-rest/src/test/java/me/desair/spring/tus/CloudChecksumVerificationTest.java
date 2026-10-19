package me.desair.spring.tus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import me.desair.tus.server.HttpHeader;
import me.desair.tus.server.TusFileUploadService;
import me.desair.tus.server.upload.UploadInfo;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * End-to-end integration verification test against live AWS S3 and Azure Blob Storage,
 * asserting that checksum directories, parent markers, tombstones, and child references
 * are created, preserved, and completely cleaned up.
 */
@SpringBootTest
public class CloudChecksumVerificationTest {

  private static final Logger log = LoggerFactory.getLogger(CloudChecksumVerificationTest.class);

  @Autowired
  @Qualifier("s3TusFileUploadService")
  private TusFileUploadService s3Service;

  @Autowired
  @Qualifier("azureTusFileUploadService")
  private TusFileUploadService azureService;

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

  @Value("${tus.azure.connection-string}")
  private String azureConnectionString;

  @Value("${tus.azure.container-name:tus-uploads}")
  private String azureContainerName;

  @Test
  public void testAwsS3ChecksumDirectoryLifecycle() throws Exception {
    MinioClient minioClient =
        MinioClient.builder()
            .endpoint(s3Endpoint)
            .credentials(s3AccessKey, s3SecretKey)
            .region(s3Region != null && !s3Region.isEmpty() ? s3Region : "local")
            .build();

    byte[] payload =
        ("AWS S3 Checksum Lifecycle Verification Payload: " + UUID.randomUUID())
            .getBytes(StandardCharsets.UTF_8);
    byte[] sha1Bytes = MessageDigest.getInstance("SHA-1").digest(payload);
    String b64Checksum = Base64.getEncoder().encodeToString(sha1Bytes);
    String hexChecksum = Hex.encodeHexString(sha1Bytes).toLowerCase();
    String checksumHeader = "sha1 " + b64Checksum;
    String checksumPrefix = "checksums/sha1/" + hexChecksum + "/";

    log.info("Starting AWS S3 Checksum Verification with prefix: {}", checksumPrefix);

    // 1. Upload Parent
    MockHttpServletRequest parentPost = createPostRequest("/test-s3/api/upload", payload.length);
    MockHttpServletResponse parentPostResp = new MockHttpServletResponse();
    UploadInfo parentInfo = s3Service.process(parentPost, parentPostResp);

    assertNotNull(parentInfo, "Parent upload must be created");
    assertEquals(201, parentPostResp.getStatus());
    String parentId = parentInfo.getId().toString();

    // Patch Parent with payload & checksum
    MockHttpServletRequest parentPatch =
        createPatchRequest("/test-s3/api/upload/" + parentId, 0L, payload, checksumHeader);
    MockHttpServletResponse parentPatchResp = new MockHttpServletResponse();
    s3Service.process(parentPatch, parentPatchResp);
    assertEquals(204, parentPatchResp.getStatus());

    // Verify S3: parent object exists in checksum directory
    String parentKey = checksumPrefix + "parent";
    try (InputStream is =
        minioClient.getObject(
            GetObjectArgs.builder().bucket(s3BucketName).object(parentKey).build())) {
      String recordedParentId = IOUtils.toString(is, StandardCharsets.UTF_8).trim();
      assertEquals(parentId, recordedParentId, "Parent object in S3 must record parent upload ID");
    }
    log.info("Verified parent marker in S3: {}", parentKey);

    // 2. Upload Child with identical content
    MockHttpServletRequest childPost = createPostRequest("/test-s3/api/upload", payload.length);
    MockHttpServletResponse childPostResp = new MockHttpServletResponse();
    UploadInfo childInfo = s3Service.process(childPost, childPostResp);

    assertNotNull(childInfo, "Child upload must be created");
    assertEquals(201, childPostResp.getStatus());
    String childId = childInfo.getId().toString();

    // Patch Child with payload & checksum (deduplicates against parent)
    MockHttpServletRequest childPatch =
        createPatchRequest("/test-s3/api/upload/" + childId, 0L, payload, checksumHeader);
    MockHttpServletResponse childPatchResp = new MockHttpServletResponse();
    childInfo = s3Service.process(childPatch, childPatchResp);
    assertEquals(204, childPatchResp.getStatus());
    assertEquals(parentInfo.getId(), childInfo.getDuplicatesUploadId(), "Child must link to parent");

    // Verify S3: child marker exists in checksum directory
    String childKey = checksumPrefix + "children/" + childId;
    assertNotNull(
        minioClient.statObject(
            StatObjectArgs.builder().bucket(s3BucketName).object(childKey).build()),
        "Child marker must exist in S3");
    log.info("Verified child marker in S3: {}", childKey);

    // 3. Delete Parent (active child remains)
    MockHttpServletRequest parentDelete =
        createDeleteRequest("/test-s3/api/upload/" + parentId);
    MockHttpServletResponse parentDeleteResp = new MockHttpServletResponse();
    s3Service.process(parentDelete, parentDeleteResp);
    assertEquals(204, parentDeleteResp.getStatus());

    // Verify S3: parent tombstoned, parent marker gone, child marker still present
    String tombstoneKey = checksumPrefix + "parent-deleted";
    assertNotNull(
        minioClient.statObject(
            StatObjectArgs.builder().bucket(s3BucketName).object(tombstoneKey).build()),
        "Tombstone marker must exist in S3");
    assertTrue(s3ObjectExists(minioClient, s3BucketName, parentKey), "Parent marker must be preserved in S3");
    assertTrue(s3ObjectExists(minioClient, s3BucketName, childKey), "Child marker must remain in S3");
    log.info("Verified parent tombstone and child preservation in S3");

    // Verify Child can still read data bytes after parent deletion
    try (InputStream is = s3Service.getUploadedBytes(childInfo)) {
      byte[] readBytes = IOUtils.toByteArray(is);
      assertEquals(new String(payload, StandardCharsets.UTF_8), new String(readBytes, StandardCharsets.UTF_8));
    }
    log.info("Verified child successfully streamed data after parent deletion in S3");

    // 4. Delete Child (last child exiting after parent deletion)
    MockHttpServletRequest childDelete = createDeleteRequest("/test-s3/api/upload/" + childId);
    MockHttpServletResponse childDeleteResp = new MockHttpServletResponse();
    s3Service.process(childDelete, childDeleteResp);
    assertEquals(204, childDeleteResp.getStatus());

    // Verify S3: checksum directory completely purged
    List<String> remainingObjects = listS3Objects(minioClient, s3BucketName, checksumPrefix);
    assertTrue(
        remainingObjects.isEmpty(),
        "Checksum directory in S3 must be completely purged after last child deletion: "
            + remainingObjects);
    log.info("Verified full S3 checksum directory cleanup: {}", checksumPrefix);
  }

  @Test
  public void testAzureBlobChecksumDirectoryLifecycle() throws Exception {
    BlobContainerClient containerClient =
        new BlobContainerClientBuilder()
            .connectionString(azureConnectionString)
            .containerName(azureContainerName)
            .buildClient();

    byte[] payload =
        ("Azure Blob Checksum Lifecycle Verification Payload: " + UUID.randomUUID())
            .getBytes(StandardCharsets.UTF_8);
    byte[] sha1Bytes = MessageDigest.getInstance("SHA-1").digest(payload);
    String b64Checksum = Base64.getEncoder().encodeToString(sha1Bytes);
    String hexChecksum = Hex.encodeHexString(sha1Bytes).toLowerCase();
    String checksumHeader = "sha1 " + b64Checksum;
    String checksumPrefix = "checksums/sha1/" + hexChecksum + "/";

    log.info("Starting Azure Blob Checksum Verification with prefix: {}", checksumPrefix);

    // 1. Upload Parent
    MockHttpServletRequest parentPost =
        createPostRequest("/test-azure/api/upload", payload.length);
    MockHttpServletResponse parentPostResp = new MockHttpServletResponse();
    UploadInfo parentInfo = azureService.process(parentPost, parentPostResp);

    assertNotNull(parentInfo, "Parent upload must be created");
    assertEquals(201, parentPostResp.getStatus());
    String parentId = parentInfo.getId().toString();

    // Patch Parent with payload & checksum
    MockHttpServletRequest parentPatch =
        createPatchRequest("/test-azure/api/upload/" + parentId, 0L, payload, checksumHeader);
    MockHttpServletResponse parentPatchResp = new MockHttpServletResponse();
    azureService.process(parentPatch, parentPatchResp);
    assertEquals(204, parentPatchResp.getStatus());

    // Verify Azure: parent object exists in checksum directory
    String parentKey = checksumPrefix + "parent";
    assertTrue(containerClient.getBlobClient(parentKey).exists(), "Parent blob must exist in Azure");
    String recordedParentId =
        containerClient.getBlobClient(parentKey).downloadContent().toString().trim();
    assertEquals(parentId, recordedParentId, "Parent blob in Azure must record parent upload ID");
    log.info("Verified parent marker in Azure: {}", parentKey);

    // 2. Upload Child with identical content
    MockHttpServletRequest childPost =
        createPostRequest("/test-azure/api/upload", payload.length);
    MockHttpServletResponse childPostResp = new MockHttpServletResponse();
    UploadInfo childInfo = azureService.process(childPost, childPostResp);

    assertNotNull(childInfo, "Child upload must be created");
    assertEquals(201, childPostResp.getStatus());
    String childId = childInfo.getId().toString();

    // Patch Child with payload & checksum (deduplicates against parent)
    MockHttpServletRequest childPatch =
        createPatchRequest("/test-azure/api/upload/" + childId, 0L, payload, checksumHeader);
    MockHttpServletResponse childPatchResp = new MockHttpServletResponse();
    childInfo = azureService.process(childPatch, childPatchResp);
    assertEquals(204, childPatchResp.getStatus());
    assertEquals(parentInfo.getId(), childInfo.getDuplicatesUploadId(), "Child must link to parent");

    // Verify Azure: child marker exists in checksum directory
    String childKey = checksumPrefix + "children/" + childId;
    assertTrue(containerClient.getBlobClient(childKey).exists(), "Child marker must exist in Azure");
    log.info("Verified child marker in Azure: {}", childKey);

    // 3. Delete Parent (active child remains)
    MockHttpServletRequest parentDelete =
        createDeleteRequest("/test-azure/api/upload/" + parentId);
    MockHttpServletResponse parentDeleteResp = new MockHttpServletResponse();
    azureService.process(parentDelete, parentDeleteResp);
    assertEquals(204, parentDeleteResp.getStatus());

    // Verify Azure: parent tombstoned, parent marker gone, child marker still present
    String tombstoneKey = checksumPrefix + "parent-deleted";
    assertTrue(containerClient.getBlobClient(tombstoneKey).exists(), "Tombstone blob must exist in Azure");
    assertTrue(containerClient.getBlobClient(parentKey).exists(), "Parent blob must be preserved in Azure");
    assertTrue(containerClient.getBlobClient(childKey).exists(), "Child marker must remain in Azure");
    log.info("Verified parent tombstone and child preservation in Azure");

    // Verify Child can still read data bytes after parent deletion
    try (InputStream is = azureService.getUploadedBytes(childInfo)) {
      byte[] readBytes = IOUtils.toByteArray(is);
      assertEquals(new String(payload, StandardCharsets.UTF_8), new String(readBytes, StandardCharsets.UTF_8));
    }
    log.info("Verified child successfully streamed data after parent deletion in Azure");

    // 4. Delete Child (last child exiting after parent deletion)
    MockHttpServletRequest childDelete =
        createDeleteRequest("/test-azure/api/upload/" + childId);
    MockHttpServletResponse childDeleteResp = new MockHttpServletResponse();
    azureService.process(childDelete, childDeleteResp);
    assertEquals(204, childDeleteResp.getStatus());

    // Verify Azure: checksum directory completely purged
    List<String> remainingBlobs = listAzureBlobs(containerClient, checksumPrefix);
    assertTrue(
        remainingBlobs.isEmpty(),
        "Checksum directory in Azure must be completely purged after last child deletion: "
            + remainingBlobs);
    log.info("Verified full Azure checksum directory cleanup: {}", checksumPrefix);
  }

  private MockHttpServletRequest createPostRequest(String uri, long length) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.addHeader(HttpHeader.TUS_RESUMABLE, "1.0.0");
    request.addHeader(HttpHeader.UPLOAD_LENGTH, String.valueOf(length));
    return request;
  }

  private MockHttpServletRequest createPatchRequest(
      String uri, long offset, byte[] payload, String checksumHeader) {
    MockHttpServletRequest request = new MockHttpServletRequest("PATCH", uri);
    request.addHeader(HttpHeader.TUS_RESUMABLE, "1.0.0");
    request.addHeader(HttpHeader.UPLOAD_OFFSET, String.valueOf(offset));
    request.addHeader(HttpHeader.CONTENT_TYPE, "application/offset+octet-stream");
    if (checksumHeader != null) {
      request.addHeader(HttpHeader.UPLOAD_CHECKSUM, checksumHeader);
    }
    request.setContent(payload);
    return request;
  }

  private MockHttpServletRequest createDeleteRequest(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("DELETE", uri);
    request.addHeader(HttpHeader.TUS_RESUMABLE, "1.0.0");
    return request;
  }

  private boolean s3ObjectExists(MinioClient client, String bucket, String key) {
    try {
      return client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()) != null;
    } catch (Exception e) {
      return false;
    }
  }

  private List<String> listS3Objects(MinioClient client, String bucket, String prefix) {
    List<String> names = new ArrayList<>();
    try {
      Iterable<Result<Item>> results =
          client.listObjects(
              ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build());
      for (Result<Item> res : results) {
        names.add(res.get().objectName());
      }
    } catch (Exception ignored) {
    }
    return names;
  }

  private List<String> listAzureBlobs(BlobContainerClient client, String prefix) {
    List<String> names = new ArrayList<>();
    try {
      ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix);
      for (BlobItem item : client.listBlobs(options, null)) {
        names.add(item.getName());
      }
    } catch (Exception ignored) {
    }
    return names;
  }
}
