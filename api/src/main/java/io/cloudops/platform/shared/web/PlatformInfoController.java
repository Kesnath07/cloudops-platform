package io.cloudops.platform.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated build metadata. The deployment pipeline polls this endpoint through
 * CloudFront to confirm that the release it just rolled out is the one serving traffic.
 */
@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Platform")
public final class PlatformInfoController {

    private final String version;
    private final String release;

    public PlatformInfoController(ObjectProvider<BuildProperties> buildProperties,
                                  @Value("${cloudops.release:local}") String release) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "unknown";
        this.release = release;
    }

    @GetMapping("/info")
    @Operation(summary = "Build version and release identifier of the running API")
    public PlatformInfo info() {
        return new PlatformInfo("cloudops-api", version, release);
    }

    public record PlatformInfo(String service, String version, String release) {
    }
}
