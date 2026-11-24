package com.devpilot;

import com.devpilot.dao.UserDAO;
import com.devpilot.model.User;
import com.devpilot.service.GitHubAuthService;
import com.devpilot.util.DBConnection;

/** Phase 3 console test: a real GitHub login, saved via JDBC. */
public class Phase3Demo {
    public static void main(String[] args) throws Exception {
        DBConnection.initSchema();
        GitHubAuthService auth = new GitHubAuthService();

        GitHubAuthService.DeviceCode code = auth.requestDeviceCode();
        System.out.println("Open " + code.verificationUri() + " and enter: " + code.userCode());
        System.out.println("Waiting for approval...");

        try {
            User user = new UserDAO().save(auth.fetchUser(auth.waitForToken(code)));
            System.out.println("Logged in and saved: " + user);
        } catch (GitHubAuthService.AuthException e) {
            System.out.println(e.getMessage());
        }
    }
}
