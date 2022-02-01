package org.ergoplatform.ergopay;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
public class FrontEndSampleController {
    // this class processes all calls from the example frontend

    private final UserSessionService sessionService;

    public FrontEndSampleController(UserSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/getUserAddress/{sessionId}")
    public String getUserAddress(@PathVariable String sessionId) {
        UserData userData = sessionService.getUserData(sessionId);

        return (userData.p2pkAddress != null) ? userData.p2pkAddress : "";
    }
}