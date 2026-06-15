package ar.com.logistics.reference.controller;

import ar.com.logistics.reference.dto.ProvinceResponse;
import ar.com.logistics.reference.dto.ProvinceResponse.ProvincesResponse;
import ar.com.logistics.tenant.reference.Province;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated reference-data endpoints. PR3 ships the
 * provinces catalog (24 AR codes, alphabetical). Rate-limit
 * budgets and authentication requirements do NOT apply to
 * {@code /api/v1/reference/*} in v1.
 *
 * <p>The 1-hour {@code Cache-Control: public, max-age=3600} header
 * is a hint for upstream caches and lets the
 * {@code ProvincesService} on the frontend avoid round-trips on
 * repeated renders. The DB is still the source of truth.
 */
@RestController
@RequestMapping("/api/v1/reference")
public class ReferenceController {

    private static final Duration CACHE_MAX_AGE = Duration.ofHours(1);

    @GetMapping("/provinces")
    public ResponseEntity<ProvincesResponse> provinces() {
        List<ProvinceResponse> data =
                Province.all().stream().map(ProvinceResponse::from).toList();
        CacheControl cc = CacheControl.maxAge(CACHE_MAX_AGE).cachePublic();
        return ResponseEntity.ok().cacheControl(cc).body(new ProvincesResponse(data));
    }
}
