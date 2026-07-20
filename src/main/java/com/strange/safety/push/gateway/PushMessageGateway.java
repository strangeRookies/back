package com.strange.safety.push.gateway;

import java.util.List;

public interface PushMessageGateway {

    List<PushSendResult> send(List<String> tokens, PushMessagePayload payload) throws Exception;
}
