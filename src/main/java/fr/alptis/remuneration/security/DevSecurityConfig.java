package fr.alptis.remuneration.security;

import io.smallrye.config.ConfigMapping;

import java.util.List;
import java.util.Map;

/**
 * Configuration of the dev/test users that stand in for the group's single sign-on.
 *
 * <p>Each user carries a password and the directory groups it belongs to (standing in for the
 * directory). Its roles and visible entities are deduced from the {@code role_mapping} table
 * (section 3), not from this configuration.</p>
 */
@ConfigMapping(prefix = "app.security")
public interface DevSecurityConfig {

    /**
     * Returns the declared users, keyed by username.
     *
     * @return a map from username to its definition
     */
    Map<String, DevUser> users();

    /** Definition of a single configured user. */
    interface DevUser {

        /**
         * Returns the user's plain-text password (dev only).
         *
         * @return the password
         */
        String password();

        /**
         * Returns the directory groups the user belongs to.
         *
         * @return the directory group names
         */
        List<String> groups();
    }
}
