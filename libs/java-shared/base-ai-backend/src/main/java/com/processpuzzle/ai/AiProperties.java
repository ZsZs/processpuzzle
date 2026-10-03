package com.processpuzzle.ai;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the AI feature, under {@code processpuzzle.ai}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "processpuzzle.ai")
public class AiProperties {

    private Media media = new Media();
    private VisionServer visionServer = new VisionServer();
    private Poller poller = new Poller();

    @Getter
    @Setter
    public static class Media {
        /** How long a client has to PUT an upload before the slot is discarded. */
        private Duration uploadExpiry = Duration.ofMinutes(30);
        /** Validity of the signed URLs handed to browsers. */
        private Duration readUrlExpiry = Duration.ofHours(1);
        /** Validity of the signed URLs handed to the vision server: queue wait plus processing. */
        private Duration visionUrlExpiry = Duration.ofHours(6);
        private long maxPhotoBytes = 40L * 1024 * 1024;
        private long maxVideoBytes = 4L * 1024 * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class VisionServer {
        /** e.g. {@code http://vision-server:8000/v1}. Unset means no vision server: enrollment stays PENDING. */
        private String baseUrl;
        private String serviceToken;
        /**
         * This backend's API root as the vision server reaches it on the infrastructure network, e.g.
         * {@code http://testbed-backend:8080/api/v1}; the notification path is appended to it.
         */
        private String callbackBaseUrl;
        /** The application stack this backend serves, for the vision server's logs and fair queuing. */
        private String stack = "processpuzzle";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(60);
    }

    @Getter
    @Setter
    public static class Poller {
        private boolean enabled = true;
        private Duration interval = Duration.ofMinutes(1);
        /** A ticket untouched this long is chased: resubmitted, or its job state fetched. */
        private Duration overdueAfter = Duration.ofMinutes(10);
        /** Submissions after which a ticket that never reached the vision server is failed. */
        private int maxSubmitAttempts = 20;
    }
}
