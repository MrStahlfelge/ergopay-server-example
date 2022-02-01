package org.ergoplatform.ergopay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Service
public class UserSessionService {

    // this is a very basic user session service that holds some data for an anon user, identified
    // simply by a UUID. This is absolutely not how you would do that, it doesn't scale when using
    // multiple backends and the data is destroyed when server has a black out.
    // But we do this here in the example for simplicity

    private final HashMap<String, UserData> userDataMap = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(UserSessionService.class);

    public UserData getUserData(String sessionId) {
        synchronized (userDataMap) {
            UserData userData;
            if (userDataMap.containsKey(sessionId))
                userData = userDataMap.get(sessionId);
            else {
                userData = new UserData();
                userDataMap.put(sessionId, userData);
            }
            userData.setActiveNow();
            return userData;
        }
    }


    @Scheduled(fixedRate = 1000 * 60 * 60)
    private void deleteInactiveUserData() {
        // deletes all user entries inactive for half an hour, runs every hour
        synchronized (userDataMap) {
            Set<Map.Entry<String, UserData>> userSet = userDataMap.entrySet();

            Iterator<Map.Entry<String, UserData>> iterator = userSet.iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, UserData> entry = iterator.next();
                UserData user = entry.getValue();

                if (user.getLastActiveMs() < System.currentTimeMillis() - 1000L * 60 * 30) {
                    logger.info("Removing user data for session " + entry.getKey());
                    iterator.remove();
                }
            }
        }
    }

}
