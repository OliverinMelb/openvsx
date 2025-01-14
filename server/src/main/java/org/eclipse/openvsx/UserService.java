/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import com.google.common.base.Joiner;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.AccessTokenJson;
import org.eclipse.openvsx.json.NamespaceDetailsJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.security.IdPrincipal;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

import static org.eclipse.openvsx.cache.CacheService.CACHE_NAMESPACE_DETAILS_JSON;
import static org.eclipse.openvsx.util.UrlUtil.createApiUrl;

@Component
public class UserService {

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final StorageUtilService storageUtil;
    private final CacheService cache;
    private final ExtensionValidator validator;
    private final ObservationRegistry observations;

    public UserService(
            EntityManager entityManager,
            RepositoryService repositories,
            StorageUtilService storageUtil,
            CacheService cache,
            ExtensionValidator validator,
            ObservationRegistry observations
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.storageUtil = storageUtil;
        this.cache = cache;
        this.validator = validator;
        this.observations = observations;
    }

    public UserData findLoggedInUser() {
        return Observation.createNotStarted("UserService#findLoggedInUser", observations).observe(() -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                if (authentication.getPrincipal() instanceof IdPrincipal) {
                    var principal = (IdPrincipal) authentication.getPrincipal();
                    return entityManager.find(UserData.class, principal.getId());
                }
            }
            return null;
        });
    }

    @Transactional
    public UserData registerNewUser(OAuth2User oauth2User) {
        var user = new UserData();
        user.setProvider("github");
        user.setAuthId(oauth2User.getName());
        user.setLoginName(oauth2User.getAttribute("login"));
        user.setFullName(oauth2User.getAttribute("name"));
        user.setEmail(oauth2User.getAttribute("email"));
        user.setProviderUrl(oauth2User.getAttribute("html_url"));
        user.setAvatarUrl(oauth2User.getAttribute("avatar_url"));
        entityManager.persist(user);
        return user;
    }

    @Transactional
    public UserData updateExistingUser(UserData user, OAuth2User oauth2User) {
        if ("github".equals(user.getProvider())) {
            var updated = false;
            String loginName = oauth2User.getAttribute("login");
            if (loginName != null && !loginName.equals(user.getLoginName())) {
                user.setLoginName(loginName);
                updated = true;
            }
            String fullName = oauth2User.getAttribute("name");
            if (fullName != null && !fullName.equals(user.getFullName())) {
                user.setFullName(fullName);
                updated = true;
            }
            String email = oauth2User.getAttribute("email");
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                updated = true;
            }
            String providerUrl = oauth2User.getAttribute("html_url");
            if (providerUrl != null && !providerUrl.equals(user.getProviderUrl())) {
                user.setProviderUrl(providerUrl);
                updated = true;
            }
            String avatarUrl = oauth2User.getAttribute("avatar_url");
            if (avatarUrl != null && !avatarUrl.equals(user.getAvatarUrl())) {
                user.setAvatarUrl(avatarUrl);
                updated = true;
            }
            if (updated) {
                cache.evictExtensionJsons(user);
            }
        }
        return user;
    }

    @Transactional
    public PersonalAccessToken useAccessToken(String tokenValue) {
        return Observation.createNotStarted("UserService#useAccessToken", observations).observe(() -> {
            var token = repositories.findAccessToken(tokenValue);
            if (token == null || !token.isActive()) {
                return null;
            }
            token.setAccessedTimestamp(TimeUtil.getCurrentUTC());
            return token;
        });
    }

    public String generateTokenValue() {
        String value;
        do {
            value = UUID.randomUUID().toString();
        } while (repositories.hasAccessToken(value));
        return value;
    }

    public boolean hasPublishPermission(UserData user, Namespace namespace) {
        return Observation.createNotStarted("UserService#hasPublishPermission", observations).observe(() -> {

            if (UserData.ROLE_PRIVILEGED.equals(user.getRole())) {
                // Privileged users can publish to every namespace.
                return true;
            }

            return repositories.canPublishInNamespace(user, namespace);
        });
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson setNamespaceMember(UserData requestingUser, String namespaceName, String provider, String userLogin, String role) {
        var namespace = repositories.findNamespace(namespaceName);
        if (!repositories.isNamespaceOwner(requestingUser, namespace)) {
            throw new ErrorResultException("You must be an owner of this namespace.");
        }
        var targetUser = repositories.findUserByLoginName(provider, userLogin);
        if (targetUser == null) {
            throw new ErrorResultException("User not found: " + provider + "/" + userLogin);
        }

        if (role.equals("remove")) {
            return removeNamespaceMember(namespace, targetUser);
        } else {
            return addNamespaceMember(namespace, targetUser, role);
        }
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson removeNamespaceMember(Namespace namespace, UserData user) throws ErrorResultException {
        var membership = repositories.findMembership(user, namespace);
        if (membership == null) {
            throw new ErrorResultException("User " + user.getLoginName() + " is not a member of " + namespace.getName() + ".");
        }
        entityManager.remove(membership);
        return ResultJson.success("Removed " + user.getLoginName() + " from namespace " + namespace.getName() + ".");
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson addNamespaceMember(Namespace namespace, UserData user, String role) {
        if (!(role.equals(NamespaceMembership.ROLE_OWNER)
                || role.equals(NamespaceMembership.ROLE_CONTRIBUTOR))) {
            throw new ErrorResultException("Invalid role: " + role);
        }
        var membership = repositories.findMembership(user, namespace);
        if (membership != null) {
            if (role.equals(membership.getRole())) {
                throw new ErrorResultException("User " + user.getLoginName() + " already has the role " + role + ".");
            }
            membership.setRole(role);
            return ResultJson.success("Changed role of " + user.getLoginName() + " in " + namespace.getName() + " to " + role + ".");
        }
        membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(user);
        membership.setRole(role);
        entityManager.persist(membership);
        return ResultJson.success("Added " + user.getLoginName() + " as " + role + " of " + namespace.getName() + ".");
    }

    @Transactional(rollbackOn = { ErrorResultException.class, NotFoundException.class })
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key="#details.name")
    public ResultJson updateNamespaceDetails(NamespaceDetailsJson details) {
        var namespace = repositories.findNamespace(details.name);
        if (namespace == null) {
            throw new NotFoundException();
        }

        var issues = validator.validateNamespaceDetails(details);
        if (!issues.isEmpty()) {
            var message = issues.size() == 1
                    ? issues.get(0).toString()
                    : "Multiple issues were found in the extension metadata:\n" + Joiner.on("\n").join(issues);

            throw new ErrorResultException(message);
        }

        if(!Objects.equals(details.displayName, namespace.getDisplayName())) {
            namespace.setDisplayName(details.displayName);
        }
        if(!Objects.equals(details.description, namespace.getDescription())) {
            namespace.setDescription(details.description);
        }
        if(!Objects.equals(details.website, namespace.getWebsite())) {
            namespace.setWebsite(details.website);
        }
        if(!Objects.equals(details.supportLink, namespace.getSupportLink())) {
            namespace.setSupportLink(details.supportLink);
        }
        if(!Objects.equals(details.socialLinks, namespace.getSocialLinks())) {
            namespace.setSocialLinks(details.socialLinks);
        }

        var logo = namespace.getLogoStorageType() != null
                ? storageUtil.getNamespaceLogoLocation(namespace).toString()
                : null;

        if(!Objects.equals(details.logo, logo)) {
            if (details.logoBytes != null && details.logoBytes.length > 0) {
                if (namespace.getLogoStorageType() != null) {
                    storageUtil.removeNamespaceLogo(namespace);
                }

                namespace.setLogoName(details.logo);
                namespace.setLogoBytes(details.logoBytes);
                storeNamespaceLogo(namespace);
            } else if (namespace.getLogoStorageType() != null) {
                storageUtil.removeNamespaceLogo(namespace);
                namespace.setLogoName(null);
                namespace.setLogoBytes(null);
                namespace.setLogoStorageType(null);
            }
        }

        return ResultJson.success("Updated details for namespace " + details.name);
    }

    private void storeNamespaceLogo(Namespace namespace) {
        if (storageUtil.shouldStoreLogoExternally(namespace)) {
            storageUtil.uploadNamespaceLogo(namespace);
            // Don't store the binary content in the DB - it's now stored externally
            namespace.setLogoBytes(null);
        } else {
            namespace.setLogoStorageType(FileResource.STORAGE_DB);
        }
    }
    @Transactional
    public AccessTokenJson createAccessToken(UserData user, String description) {
        return Observation.createNotStarted("UserService#createAccessToken", observations).observe(() -> {
            var token = new PersonalAccessToken();
            token.setUser(user);
            token.setValue(generateTokenValue());
            token.setActive(true);
            token.setCreatedTimestamp(TimeUtil.getCurrentUTC());
            token.setDescription(description);
            entityManager.persist(token);
            var json = token.toAccessTokenJson();
            // Include the token value after creation so the user can copy it
            json.value = token.getValue();
            json.deleteTokenUrl = createApiUrl(UrlUtil.getBaseUrl(), "user", "token", "delete", Long.toString(token.getId()));

            return json;
        });
    }

    @Transactional
    public ResultJson deleteAccessToken(UserData user, long id) {
        var token = repositories.findAccessToken(id);
        if (token == null || !token.isActive()) {
            throw new NotFoundException();
        }

        user = entityManager.merge(user);
        if(!token.getUser().equals(user)) {
            throw new NotFoundException();
        }

        token.setActive(false);
        return ResultJson.success("Deleted access token for user " + user.getLoginName() + ".");
    }
}