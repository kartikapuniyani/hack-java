package hack_java.hack_java.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import hack_java.hack_java.config.AppProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Service
@Slf4j
public class SignedUrlService {
    private AppProperties.GCPBucketProperties bucketProperties;

    /**
     * Generates a signed URL for a file in a Google Cloud Storage bucket.
     *
     * @param bucketName The name of the bucket.
     * @param objectName The name of the object (file) in the bucket.
     * @param durationInMinutes The duration (in minutes) for which the signed URL is valid.
     * @return The signed URL as a String.
     */
    public String generateSignedUrl(String bucketName, String objectName, int durationInMinutes) {
        try {
            // Decode the base64 encoded service account key
            byte[] decodedKey = Base64.getDecoder().decode(bucketProperties.getBucketKeyFilePath());

            // Initialize GCP storage client with the decoded key
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new ByteArrayInputStream(decodedKey));

            Storage storage = StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .build()
                    .getService();

            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

            // Generate the signed URL
            URL signedUrl = storage.signUrl(
                    blobInfo,
                    durationInMinutes,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature() // Use V4 signing
            );
            return signedUrl.toString();
        } catch (Exception e) {
            log.error("error in generateSignedUrl : {}",e.getMessage());
            return "";
        }
    }


    /**
     * Generates a preSigned URL for a file in a Google Cloud Storage bucket.
     *
     * @param bucketName The name of the bucket.
     * @param objectName The name of the object (file) in the bucket.
     * @param durationInMinutes The duration (in minutes) for which the signed URL is valid.
     * @return The signed URL as a String.
     */
    public String generatePreSignedUrl(String bucketName, String objectName, int durationInMinutes) {
        try {
            // Decode the base64 encoded service account key
            byte[] decodedKey = Base64.getDecoder().decode(bucketProperties.getBucketKeyFilePath());

            // Initialize GCP storage client with the decoded key
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new ByteArrayInputStream(decodedKey));

            Storage storage = StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .build()
                    .getService();

            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("video/mp4") // Set appropriate content type
                    .build();

            // Generate the signed URL
            URL signedUrl = storage.signUrl(
                    blobInfo,
                    durationInMinutes,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature(),
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withContentType() // Include content type in the signed URL
            );
            return signedUrl.toString();
        } catch (Exception e) {
            log.error("error in generateSignedUrl : {}",e.getMessage());
            return "";
        }
    }


}
