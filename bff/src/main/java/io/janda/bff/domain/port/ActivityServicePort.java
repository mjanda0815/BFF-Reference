package io.janda.bff.domain.port;

import io.janda.bff.domain.model.ActivityEvent;
import java.util.List;
import reactor.core.publisher.Mono;

/** Port for retrieving recent activity from a downstream activity service. */
public interface ActivityServicePort {

  Mono<List<ActivityEvent>> getRecentActivity(String userId, String accessToken);
}
