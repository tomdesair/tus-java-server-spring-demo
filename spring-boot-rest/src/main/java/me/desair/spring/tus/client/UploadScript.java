package me.desair.spring.tus.client;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusURLMemoryStore;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;

public class UploadScript {

    public static void main(String args[]) throws IOException, ProtocolException {
        if(args == null || args.length != 1) {
            System.err.println("Usage: me.desair.spring.tus.client.UploadScript <path-to-file>");
        }

        // Create a new TusClient instance
        TusClient client = new TusClient();

        // Configure tus HTTP endpoint. This URL will be used for creating new uploads
        // using the Creation extension
        client.setUploadCreationURL(new URL("http://localhost:8080/api/upload"));

        // Enable resumable uploads by storing the upload URL in memory
        client.enableResuming(new TusURLMemoryStore());

        // Open a file using which we will then create a TusUpload. If you do not have
        // a File object, you can manually construct a TusUpload using an InputStream.
        // See the documentation for more information.
        File file = new File(args[0]);
        final TusUpload upload = new TusUpload(file);

        System.out.println("Starting upload...");

        // We wrap our uploading code in the TusExecutor class which will automatically catch
        // exceptions and issue retries with small delays between them and take fully
        // advantage of tus' resumability to offer more reliability.
        // This step is optional but highly recommended.
        TusExecutor executor = new TusExecutor() {
            @Override
            protected void makeAttempt() throws ProtocolException, IOException {
                // First try to resume an upload. If that's not possible we will create a new
                // upload and get a TusUploader in return. This class is responsible for opening
                // a connection to the remote server and doing the uploading.
                TusUploader uploader = client.resumeOrCreateUpload(upload);

                // Alternatively, if your tus server does not support the Creation extension
                // and you obtained an upload URL from another service, you can instruct
                // tus-java-client to upload to a specific URL. Please note that this is usually
                // _not_ necessary and only if the tus server does not support the Creation
                // extension. The Vimeo API would be an example where this method is needed.
                // TusUploader uploader = client.beginOrResumeUploadFromURL(upload, new URL("https://tus.server.net/files/my_file"));

                // Upload the file in chunks of 1KB sizes.
                uploader.setChunkSize(1024);

                // Upload the file as long as data is available. Once the
                // file has been fully uploaded the method will return -1
                do {
                    // Calculate the progress using the total size of the uploading file and
                    // the current offset.
                    long totalBytes = upload.getSize();
                    long bytesUploaded = uploader.getOffset();
                    double progress = (double) bytesUploaded / totalBytes * 100;

                    System.out.printf("Upload at %06.2f%%.\n", progress);
                } while (uploader.uploadChunk() > -1);

                // Allow the HTTP connection to be closed and cleaned up
                uploader.finish();

                System.out.println("Upload finished.");
                System.out.format("Upload available at: %s", uploader.getUploadURL().toString());
            }
        };
        executor.makeAttempts();
    }
}
