package com.processpuzzle.ai.adapter.outbound;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.ai.usecase.port.VisionServerUnavailableException;
import com.processpuzzle.ai.vision.api.VisionApi;
import com.processpuzzle.ai.vision.model.DetectionSettings;
import com.processpuzzle.ai.vision.model.EnrollmentJobRequest;
import com.processpuzzle.ai.vision.model.EnrollmentJobResult;
import com.processpuzzle.ai.vision.model.EnrollmentPhotoResult;
import com.processpuzzle.ai.vision.model.MediaRef;
import com.processpuzzle.ai.vision.model.OcrSettings;
import com.processpuzzle.ai.vision.model.Requester;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * {@link VisionServer} over HTTP, through the {@code @HttpExchange} client generated from
 * vision-server-api.yaml. This module's own adapter rather than the composition root's: the vision
 * server is infrastructure, not another feature, so no compile edge to a feature library is at stake.
 *
 * <p>Created by {@link com.processpuzzle.ai.AiConfiguration} only when
 * {@code processpuzzle.ai.vision-server.base-url} is set.
 */
public class HttpVisionServer implements VisionServer {

    static final String NOTIFICATION_PATH = "/organizations/%s/vision-notifications";

    private final VisionApi api;
    private final AiProperties.VisionServer settings;

    public HttpVisionServer(AiProperties properties) {
        this(createClient(properties.getVisionServer()), properties.getVisionServer());
    }

    HttpVisionServer(VisionApi api, AiProperties.VisionServer settings) {
        this.api = api;
        this.settings = settings;
    }

    @Override
    public void submitEnrollment(EnrollmentRequest request) {
        EnrollmentJobRequest body = new EnrollmentJobRequest()
                .jobId(request.jobId())
                .requester(new Requester().stack(settings.getStack()).orgKey(request.orgKey()))
                .callbackUrl(URI.create(callbackUrl(request.orgKey())))
                .callbackToken(request.callbackToken())
                .detection(new DetectionSettings().detectorClass(request.detectorClass()))
                .ocr(request.readIdentifier() ? new OcrSettings().identifierPattern(request.identifierPattern()) : null)
                .photos(request.photos().stream()
                        .map(media -> new MediaRef().mediaId(media.mediaId()).url(URI.create(media.url())))
                        .toList());
        call(() -> api.submitEnrollmentJob(body));
    }

    @Override
    public Optional<JobState> job(UUID jobId) {
        try {
            var job = call(() -> api.getVisionJob(jobId)).getBody();
            return Optional.ofNullable(job).map(found -> new JobState(Status.valueOf(found.getStatus().getValue()), found.getFailureReason()));
        } catch (NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public EnrollmentOutcome enrollmentResult(UUID jobId) {
        EnrollmentJobResult result;
        try {
            result = call(() -> api.getEnrollmentJobResult(jobId)).getBody();
        } catch (NotFound e) {
            throw new VisionServerUnavailableException("the result of vision job " + jobId + " is gone");
        }
        if (result == null || result.getPhotos() == null) {
            throw new VisionServerUnavailableException("the vision server returned no result for job " + jobId);
        }
        return new EnrollmentOutcome(result.getPhotos().stream().map(HttpVisionServer::toOutcome).toList());
    }

    @Override
    public void discard(UUID jobId) {
        try {
            call(() -> api.deleteVisionJob(jobId));
        } catch (NotFound e) {
            // already gone
        }
    }

    String callbackUrl(String orgKey) {
        String base = settings.getCallbackBaseUrl();
        if (base == null || base.isBlank()) {
            throw new VisionServerUnavailableException("processpuzzle.ai.vision-server.callback-base-url is not set");
        }
        return base.replaceAll("/+$", "") + NOTIFICATION_PATH.formatted(orgKey);
    }

    private static PhotoOutcome toOutcome(EnrollmentPhotoResult photo) {
        return new PhotoOutcome(
                photo.getMediaId(),
                EnrollmentPhotoStatus.valueOf(photo.getStatus().getValue()),
                photo.getCrop() == null ? null : photo.getCrop().getData(),
                photo.getEmbedding() == null ? null : photo.getEmbedding().getModel(),
                photo.getEmbedding() == null ? null : photo.getEmbedding().getVector(),
                photo.getIdentifier() == null ? null : photo.getIdentifier().getText(),
                photo.getFailureReason());
    }

    /** A 404 is an answer and is surfaced as {@link NotFound}; every other failure is "unavailable". */
    private static <T> T call(Supplier<T> exchange) {
        try {
            return exchange.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new NotFound();
            }
            throw new VisionServerUnavailableException("the vision server refused the call: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new VisionServerUnavailableException("the vision server could not be reached: " + e.getMessage(), e);
        }
    }

    private static VisionApi createClient(AiProperties.VisionServer settings) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(settings.getConnectTimeout());
        requestFactory.setReadTimeout(settings.getReadTimeout());
        RestClient client = RestClient.builder()
                .baseUrl(settings.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + settings.getServiceToken())
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(VisionApi.class);
    }

    private static final class NotFound extends RuntimeException {
        NotFound() {
            super(null, null, false, false);
        }
    }
}
