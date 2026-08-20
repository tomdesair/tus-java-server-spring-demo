# tus-java-server-spring-demo

A reference Spring Boot demo demonstrating asynchronous, resumable file uploads powered by the [tus-java-server](https://github.com/tomdesair/tus-java-server/) library and the modern [Uppy](https://uppy.io/) file uploader (Uppy v5).

This demo showcases dual protocol compatibility—supporting both the **Tus 1.0.0** protocol and the **IETF Resumable Uploads for HTTP (RUFH)** specification—across three distinct storage backends:
1. **Local File System & Shared Disk** (`DiskStorageService` & `LeaseFileLockingService`)
2. **S3-Compatible Object Storage** (`S3StorageService` & `S3LockingService` via MinIO Java SDK)
3. **Azure Blob Storage** (`AzureBlobStorageService` & `AzureBlobLockingService` via Azure SDK)

---

## Quick Start

### 1. Build and Run

To build the project and launch the Spring Boot application, run:

```bash
git clone https://github.com/tomdesair/tus-java-server-spring-demo.git
cd tus-java-server-spring-demo
mvn clean package
java -jar spring-boot-rest/target/spring-boot-rest-0.0.1-SNAPSHOT.jar
```

### 2. Access the Test Pages

Open your browser and navigate to any of the test pages:

| Test Page URL | Storage Backend | Upload Endpoint | Default Configuration |
|---|---|---|---|
| **[http://localhost:8080/test/](http://localhost:8080/test/)** | **Disk Storage (Default)** | `/test/api/upload` | Local temp directory (`${java.io.tmpdir}/tus`) |
| **[http://localhost:8080/test-s3/](http://localhost:8080/test-s3/)** | **S3 Object Storage** | `/test-s3/api/upload` | Local MinIO (`http://localhost:9000`, bucket `tus-uploads`) |
| **[http://localhost:8080/test-azure/](http://localhost:8080/test-azure/)** | **Azure Blob Storage** | `/test-azure/api/upload` | Azurite Emulator (`http://127.0.0.1:10000`, container `tus-uploads`) |
| **[http://localhost:8080/](http://localhost:8080/)** | **Demo Home / Landing** | &mdash; | Index overview linking to all three test pages |

---

## How to Test Each Storage Backend

### 1. Testing Default Disk Storage

Disk storage works out-of-the-box with **zero external dependencies**:
1. Start the application: `java -jar spring-boot-rest/target/spring-boot-rest-0.0.1-SNAPSHOT.jar`
2. Visit **http://localhost:8080/test/**
3. Select and upload files of any size using the Uppy Dashboard.
4. Uploaded files and lock files will be stored locally under `${java.io.tmpdir}/tus`.

---

### 2. Testing S3 Object Storage (AWS S3 or Local MinIO)

You can test S3 storage locally using [MinIO](https://min.io/) in Docker:

```bash
# 1. Start MinIO in Docker
docker run -d --name minio \
  -p 9000:9000 -p 9001:9001 \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  minio/minio server /data --console-address ":9001"

# 2. Create the target bucket (via MinIO client or MinIO web console at http://localhost:9001)
docker exec -it minio mc alias set myminio http://localhost:9000 minioadmin minioadmin
docker exec -it minio mc mb myminio/tus-uploads
```

Configure `application.properties` (pre-configured for local MinIO by default):
```properties
tus.s3.endpoint=http://localhost:9000
tus.s3.bucket-name=tus-uploads
tus.s3.access-key=minioadmin
tus.s3.secret-key=minioadmin
tus.s3.region=us-east-1
```

Visit **http://localhost:8080/test-s3/** to upload files directly to S3/MinIO.

---

### 3. Testing Azure Blob Storage (Azure Cloud or Local Azurite)

You can test Azure Blob Storage locally using Microsoft's official [Azurite emulator](https://github.com/Azure/Azurite) in Docker:

```bash
# 1. Start Azurite in Docker
docker run -d --name azurite \
  -p 10000:10000 -p 10001:10001 -p 10002:10002 \
  mcr.microsoft.com/azure-storage/azurite

# 2. Create the target blob container
docker run --rm --net=host mcr.microsoft.com/azure-cli az storage container create \
  --name tus-uploads \
  --connection-string "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
```

Configure `application.properties` (pre-configured for local Azurite by default):
```properties
tus.azure.connection-string=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;
tus.azure.container-name=tus-uploads
```

For real Azure Cloud storage, replace the connection string with your account connection string from the Azure Portal (**Storage Account &rarr; Access Keys &rarr; Connection string**).

Visit **http://localhost:8080/test-azure/** to upload files directly to Azure Blob Storage.

---

## Project Structure & Architecture

* **`spring-boot-rest`**: The Spring Boot backend application:
  * [`App.java`](spring-boot-rest/src/main/java/me/desair/spring/tus/App.java): Configures three `TusFileUploadService` Spring beans (`diskTusFileUploadService`, `s3TusFileUploadService`, `azureTusFileUploadService`) and registers the Tomcat interim response valve.
  * [`FileUploadController.java`](spring-boot-rest/src/main/java/me/desair/spring/tus/FileUploadController.java): Routes incoming tus requests (`POST`, `PATCH`, `HEAD`, `DELETE`, `OPTIONS`, `GET`) to their respective storage backend service.
  * [`TusInterimResponseTomcatValve.java`](spring-boot-rest/src/main/java/me/desair/spring/tus/TusInterimResponseTomcatValve.java): A custom Tomcat `Valve` that enables native emission of **HTTP 104 Interim Responses** before servlet execution.
  * [`application.properties`](spring-boot-rest/src/main/resources/application.properties): Fully documented configuration file for all storage backend settings.
  * [`src/main/resources/public/`](spring-boot-rest/src/main/resources/public/): Static HTML test pages (`/test/`, `/test-s3/`, `/test-azure/`, and root `/`).

* **`uppy-file-upload`**: Webpack frontend module bundling Uppy v5:
  * [`uppy-fileupload.js`](uppy-file-upload/app/uppy-fileupload.js): Configures Uppy Dashboard and Tus plugins, dynamically resolving the upload endpoint from each HTML page's `data-endpoint` attribute.
  * Stylesheets imported from `@uppy/core/css/style.min.css` and `@uppy/dashboard/css/style.min.css`.

---

## Production Considerations

When adapting this demo architecture for production environments, consider the following best practices:

### 1. Request Caching with `ThreadLocalCachedStorageAndLockingService`
During a single HTTP request (e.g. `PATCH` or `HEAD`), the tus protocol engine checks upload info, validates offsets, and appends chunk bytes. Enabling `.withThreadLocalCache(true)` (as configured in `App.java`) caches metadata within the thread for the duration of that request, significantly reducing roundtrips to S3/Azure and optimizing performance.

### 2. Multi-Replica Clustered Deployments & Distributed Locking
* **Shared Disk / NFS**: `LeaseFileLockingService` provides distributed locking across multiple app containers mounted to shared NFSv4, SMB, or AWS EFS volumes without requiring Redis or an external database.
* **S3**: `S3LockingService` uses lightweight S3 object locks (`.lock`) with TTL heartbeats to coordinate uploads across clustered replicas.
* **Azure**: `AzureBlobLockingService` uses native 30-second renewable Azure Blob Leases for distributed concurrency control.

### 3. HTTP 104 Interim Responses
In standard Servlet/Tomcat environments, preliminary `1xx` responses (such as `104 Resumption`) are emitted using `TusInterimResponseTomcatValve`. In enterprise environments behind reverse proxies (Nginx, HAProxy, Envoy, Cloudflare), interim responses can alternatively be terminated or generated directly at the API Gateway layer.

### 4. Upload Expiration & Cleanup
Configure upload expiration via `.withUploadExpirationPeriod(Long)` and schedule a regular background task (e.g. using Spring's `@Scheduled`) to invoke `tusFileUploadService.cleanup()`. This removes stale chunks, expired uploads, and orphaned lock files from storage automatically.

### 5. Backend File Processing
Once an upload is completed, retrieve file bytes and metadata via:
```java
InputStream fileStream = tusFileUploadService.getUploadedBytes(uploadUrl);
UploadInfo info = tusFileUploadService.getUploadInfo(uploadUrl);
// Process or move the file to long-term storage...
tusFileUploadService.deleteUpload(uploadUrl);
```