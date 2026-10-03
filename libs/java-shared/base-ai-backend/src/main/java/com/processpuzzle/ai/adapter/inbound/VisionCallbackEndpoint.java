package com.processpuzzle.ai.adapter.inbound;

import com.processpuzzle.ai.api.AiVisionCallbackApi;
import com.processpuzzle.ai.model.VisionJobNotification;
import com.processpuzzle.ai.usecase.VisionJobs;
import com.processpuzzle.ai.usecase.port.VisionServer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the vision server's job-finished notification. Not a client endpoint: it is authenticated by
 * the per-job {@code X-Callback-Token}, which {@link VisionJobs#notified} checks, and the host
 * application's security chain must let the path through without a user token.
 */
@RestController
public class VisionCallbackEndpoint implements AiVisionCallbackApi {

    private final VisionJobs visionJobs;

    public VisionCallbackEndpoint(VisionJobs visionJobs) {
        this.visionJobs = visionJobs;
    }

    @Override
    public ResponseEntity<Void> receiveVisionJobNotification(String orgKey, String token, VisionJobNotification notification) {
        VisionServer.Status status = VisionServer.Status.valueOf(notification.getStatus().getValue());
        visionJobs.notified(orgKey, token, notification.getJobId(), status);
        return ResponseEntity.noContent().build();
    }
}
