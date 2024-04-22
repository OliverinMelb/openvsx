/** ******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.repositories;

import io.micrometer.observation.annotation.Observed;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.QueryRequest;
import org.eclipse.openvsx.json.VersionTargetPlatformsJson;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.openvsx.jooq.Tables.*;

@Component
public class ExtensionVersionJooqRepository {

    @Autowired
    DSLContext dsl;

    public List<ExtensionVersion> findAllActiveByExtensionIdAndTargetPlatform(Collection<Long> extensionIds, String targetPlatform) {
        var query = dsl.select(
                    NAMESPACE.ID,
                    NAMESPACE.NAME,
                    EXTENSION.ID,
                    EXTENSION.NAME,
                    EXTENSION_VERSION.ID,
                    EXTENSION_VERSION.VERSION,
                    EXTENSION_VERSION.TARGET_PLATFORM,
                    EXTENSION_VERSION.PREVIEW,
                    EXTENSION_VERSION.PRE_RELEASE,
                    EXTENSION_VERSION.TIMESTAMP,
                    EXTENSION_VERSION.DISPLAY_NAME,
                    EXTENSION_VERSION.DESCRIPTION,
                    EXTENSION_VERSION.ENGINES,
                    EXTENSION_VERSION.CATEGORIES,
                    EXTENSION_VERSION.TAGS,
                    EXTENSION_VERSION.EXTENSION_KIND,
                    EXTENSION_VERSION.REPOSITORY,
                    EXTENSION_VERSION.SPONSOR_LINK,
                    EXTENSION_VERSION.GALLERY_COLOR,
                    EXTENSION_VERSION.GALLERY_THEME,
                    EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                    EXTENSION_VERSION.DEPENDENCIES,
                    EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                    SIGNATURE_KEY_PAIR.PUBLIC_ID
                )
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .leftJoin(SIGNATURE_KEY_PAIR).on(SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID))
                .where(EXTENSION_VERSION.ACTIVE.eq(true))
                .and(EXTENSION_VERSION.EXTENSION_ID.in(extensionIds));

        if(targetPlatform != null) {
            query = query.and(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        return query.fetch().map(this::toExtensionVersion);
    }

    public Page<String> findActiveVersionStringsSorted(String namespace, String extension, String targetPlatform, Pageable page) {
        var count = DSL.countDistinct(EXTENSION_VERSION.VERSION);
        var totalQuery = dsl.select(count)
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .where(EXTENSION_VERSION.ACTIVE.eq(true))
                .and(NAMESPACE.NAME.equalIgnoreCase(namespace))
                .and(EXTENSION.NAME.equalIgnoreCase(extension));

        if(targetPlatform != null) {
            totalQuery = totalQuery.and(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        var conditions = new ArrayList<Condition>();
        conditions.add(EXTENSION_VERSION.ACTIVE.eq(true));
        conditions.add(NAMESPACE.NAME.equalIgnoreCase(namespace));
        conditions.add(EXTENSION.NAME.equalIgnoreCase(extension));
        if(targetPlatform != null) {
            conditions.add(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        var versions = findVersionStringsSorted(conditions, page);
        var total = totalQuery.fetchOne(count);
        return new PageImpl<>(versions, page, total);
    }

    public Map<Long, List<String>> findActiveVersionStringsSorted(Collection<Long> extensionIds, String targetPlatform, int numberOfRows) {
        if(extensionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        var ids = DSL.values(extensionIds.stream().map(DSL::row).toArray(Row1[]::new)).as("ids", "id");
        var topQuery = dsl.selectQuery();
        topQuery.addSelect(
                EXTENSION_VERSION.EXTENSION_ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.SEMVER_MAJOR,
                EXTENSION_VERSION.SEMVER_MINOR,
                EXTENSION_VERSION.SEMVER_PATCH,
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE
        );
        topQuery.setDistinct(true);
        topQuery.addFrom(EXTENSION_VERSION);
        var conditions = new ArrayList<Condition>();
        conditions.add(EXTENSION_VERSION.EXTENSION_ID.eq(ids.field("id", Long.class)));
        conditions.add(EXTENSION_VERSION.ACTIVE.eq(true));
        if(targetPlatform != null) {
            conditions.add(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        topQuery.addConditions(conditions);
        topQuery.addLimit(numberOfRows);
        topQuery.addOrderBy(
                EXTENSION_VERSION.EXTENSION_ID.asc(),
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                EXTENSION_VERSION.VERSION.asc()
        );

        var top = topQuery.asTable("ev_top");
        return dsl.select(
                        top.field(EXTENSION_VERSION.EXTENSION_ID),
                        top.field(EXTENSION_VERSION.VERSION)
                )
                .from(ids, DSL.lateral(top))
                .stream()
                .map(record -> {
                    var extensionId = record.get(top.field(EXTENSION_VERSION.EXTENSION_ID));
                    var version = record.get(top.field(EXTENSION_VERSION.VERSION));
                    return new AbstractMap.SimpleEntry<>(extensionId, version);
                })
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    public List<ExtensionVersion> findActiveVersionReferencesSorted(Collection<Extension> extensions, int numberOfRows) {
        if(extensions.isEmpty()) {
            return Collections.emptyList();
        }

        var ids = DSL.values(extensions.stream().map(Extension::getId).map(DSL::row).toArray(Row1[]::new)).as("ids", "id");
        var namespaceCol = NAMESPACE.NAME.as("namespace");
        var extensionCol = EXTENSION.NAME.as("extension");
        var top = dsl.select(
                    namespaceCol,
                    extensionCol,
                    EXTENSION_VERSION.ID,
                    EXTENSION_VERSION.EXTENSION_ID,
                    EXTENSION_VERSION.TARGET_PLATFORM,
                    EXTENSION_VERSION.VERSION,
                    EXTENSION_VERSION.ENGINES,
                    SIGNATURE_KEY_PAIR.PUBLIC_ID
                )
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .leftJoin(SIGNATURE_KEY_PAIR).on(SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID))
                .where(EXTENSION_VERSION.EXTENSION_ID.eq(ids.field("id", Long.class)))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .orderBy(
                        EXTENSION_VERSION.EXTENSION_ID.asc(),
                        EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                        EXTENSION_VERSION.SEMVER_MINOR.desc(),
                        EXTENSION_VERSION.SEMVER_PATCH.desc(),
                        EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                        EXTENSION_VERSION.TIMESTAMP.desc()
                )
                .limit(numberOfRows)
                .asTable("ev_top");

        var converter = new ListOfStringConverter();
        return dsl.select(
                        top.field(namespaceCol),
                        top.field(extensionCol),
                        top.field(EXTENSION_VERSION.ID),
                        top.field(EXTENSION_VERSION.EXTENSION_ID),
                        top.field(EXTENSION_VERSION.TARGET_PLATFORM),
                        top.field(EXTENSION_VERSION.VERSION),
                        top.field(EXTENSION_VERSION.ENGINES),
                        top.field(SIGNATURE_KEY_PAIR.PUBLIC_ID)
                )
                .from(ids, DSL.lateral(top))
                .stream()
                .map(record -> {
                    var namespace = new Namespace();
                    namespace.setName(record.get(top.field(namespaceCol)));

                    var extension = new Extension();
                    extension.setId(record.get(top.field(EXTENSION_VERSION.EXTENSION_ID)));
                    extension.setName(record.get(top.field(extensionCol)));
                    extension.setNamespace(namespace);

                    var signatureKeyPair = new SignatureKeyPair();
                    signatureKeyPair.setPublicId(record.get(top.field(SIGNATURE_KEY_PAIR.PUBLIC_ID)));

                    var extVersion = new ExtensionVersion();
                    extVersion.setId(record.get(top.field(EXTENSION_VERSION.ID)));
                    extVersion.setTargetPlatform(record.get(top.field(EXTENSION_VERSION.TARGET_PLATFORM)));
                    extVersion.setVersion(record.get(top.field(EXTENSION_VERSION.VERSION)));
                    extVersion.setEngines(toList(record.get(top.field(EXTENSION_VERSION.ENGINES)), converter));
                    extVersion.setExtension(extension);
                    extVersion.setSignatureKeyPair(signatureKeyPair);
                    return extVersion;
                })
                .collect(Collectors.toList());
    }

    public List<String> findVersionStringsSorted(Long extensionId, String targetPlatform, boolean onlyActive, int numberOfRows) {
        var conditions = new ArrayList<Condition>();
        conditions.add(EXTENSION_VERSION.EXTENSION_ID.eq(extensionId));
        if (targetPlatform != null) {
            conditions.add(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }
        if (onlyActive) {
            conditions.add(EXTENSION_VERSION.ACTIVE.eq(true));
        }

        return findVersionStringsSorted(conditions, Pageable.ofSize(numberOfRows));
    }

    private List<String> findVersionStringsSorted(List<Condition> conditions, Pageable page) {
        var versionsQuery = dsl.selectQuery();
        versionsQuery.setDistinct(true);
        versionsQuery.addSelect(
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.SEMVER_MAJOR,
                EXTENSION_VERSION.SEMVER_MINOR,
                EXTENSION_VERSION.SEMVER_PATCH,
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE
        );

        versionsQuery.addFrom(EXTENSION_VERSION);
        versionsQuery.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        versionsQuery.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        versionsQuery.addConditions(conditions);

        versionsQuery.addOrderBy(
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                EXTENSION_VERSION.VERSION.asc()
        );

        versionsQuery.addLimit(page.getPageSize());
        versionsQuery.addOffset(page.getOffset());
        return versionsQuery.fetch(record -> record.get(EXTENSION_VERSION.VERSION));
    }

    public Page<ExtensionVersion> findActiveVersions(QueryRequest request) {
        var conditions = new ArrayList<Condition>();
        if (!StringUtils.isEmpty(request.namespaceUuid)) {
            conditions.add(NAMESPACE.PUBLIC_ID.eq(request.namespaceUuid));
        }
        if (!StringUtils.isEmpty(request.namespaceName)) {
            conditions.add(DSL.upper(NAMESPACE.NAME).eq(DSL.upper(request.namespaceName)));
        }
        if (!StringUtils.isEmpty(request.extensionUuid)) {
            conditions.add(EXTENSION.PUBLIC_ID.eq(request.extensionUuid));
        }
        if (!StringUtils.isEmpty(request.extensionName)) {
            conditions.add(DSL.upper(EXTENSION.NAME).eq(DSL.upper(request.extensionName)));
        }
        if(request.targetPlatform != null) {
            conditions.add(EXTENSION_VERSION.TARGET_PLATFORM.eq(request.targetPlatform));
        }
        if (!StringUtils.isEmpty(request.extensionVersion)) {
            conditions.add(EXTENSION_VERSION.VERSION.eq(request.extensionVersion));
        }

        var totalCol = "total";
        var totalQuery = dsl.selectQuery();
        totalQuery.addFrom(EXTENSION_VERSION);
        totalQuery.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        totalQuery.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        totalQuery.addConditions(EXTENSION_VERSION.ACTIVE.eq(true));

        var query = findAllActive();
        if(!request.includeAllVersions) {
            var distinctOn = new Field[] {
                    EXTENSION_VERSION.EXTENSION_ID,
                    EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM,
                    EXTENSION_VERSION.TARGET_PLATFORM
            };

            totalQuery.addSelect(DSL.countDistinct(distinctOn).as(totalCol));
            query.addDistinctOn(distinctOn);
            query.addOrderBy(
                    EXTENSION_VERSION.EXTENSION_ID.asc(),
                    EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                    EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                    EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                    EXTENSION_VERSION.SEMVER_MINOR.desc(),
                    EXTENSION_VERSION.SEMVER_PATCH.desc(),
                    EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                    EXTENSION_VERSION.TIMESTAMP.desc()
            );
        } else {
            totalQuery.addSelect(DSL.count().as(totalCol));
            query.addOrderBy(
                    EXTENSION_VERSION.EXTENSION_ID.asc(),
                    EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                    EXTENSION_VERSION.SEMVER_MINOR.desc(),
                    EXTENSION_VERSION.SEMVER_PATCH.desc(),
                    EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                    EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                    EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                    EXTENSION_VERSION.TIMESTAMP.desc()
            );
        }

        totalQuery.addConditions(conditions);
        query.addConditions(conditions);
        query.addOffset(request.offset);
        query.addLimit(request.size);

        var total = totalQuery.fetchOne(totalCol, Integer.class);
        return new PageImpl<>(fetch(query), PageRequest.of(request.offset / request.size, request.size), total);
    }

    public List<ExtensionVersion> findAllActiveByVersionAndExtensionNameAndNamespaceName(String version, String extensionName, String namespaceName) {
        var query = findAllActive();
        query.addConditions(
                EXTENSION_VERSION.VERSION.eq(version),
                DSL.upper(EXTENSION.NAME).eq(DSL.upper(extensionName)),
                DSL.upper(NAMESPACE.NAME).eq(DSL.upper(namespaceName))
        );

        return fetch(query);
    }

    public List<ExtensionVersion> findAllActiveByExtensionNameAndNamespaceName(String targetPlatform, String extensionName, String namespaceName) {
        var query = findAllActive();
        query.addConditions(
                DSL.upper(EXTENSION.NAME).eq(DSL.upper(extensionName)),
                DSL.upper(NAMESPACE.NAME).eq(DSL.upper(namespaceName))
        );

        if(targetPlatform != null) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        return fetch(query);
    }

    public List<ExtensionVersion> findAllActiveByNamespaceName(String targetPlatform, String namespaceName) {
        var query = findAllActive();
        query.addConditions(DSL.upper(NAMESPACE.NAME).eq(DSL.upper(namespaceName)));
        if(targetPlatform != null) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        return fetch(query);
    }

    public List<ExtensionVersion> findAllActiveByExtensionName(String targetPlatform, String extensionName) {
        var query = findAllActive();
        query.addConditions(DSL.upper(EXTENSION.NAME).eq(DSL.upper(extensionName)));
        if(targetPlatform != null) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        return fetch(query);
    }

    private SelectQuery<Record> findAllActive() {
        var query = dsl.selectQuery();
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.PUBLIC_ID,
                NAMESPACE.NAME,
                EXTENSION.ID,
                EXTENSION.PUBLIC_ID,
                EXTENSION.NAME,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                SIGNATURE_KEY_PAIR.PUBLIC_ID
        );
        query.addFrom(EXTENSION_VERSION);
        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addJoin(PERSONAL_ACCESS_TOKEN, JoinType.LEFT_OUTER_JOIN, PERSONAL_ACCESS_TOKEN.ID.eq(EXTENSION_VERSION.PUBLISHED_WITH_ID));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(PERSONAL_ACCESS_TOKEN.USER_DATA));
        query.addJoin(SIGNATURE_KEY_PAIR, JoinType.LEFT_OUTER_JOIN, SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        query.addConditions(EXTENSION_VERSION.ACTIVE.eq(true));
        return query;
    }

    private List<ExtensionVersion> fetch(SelectQuery<Record> query) {
        return query.fetch().map(this::toExtensionVersionFull);
    }

    private ExtensionVersion toExtensionVersionFull(Record record) {
        return toExtensionVersionFull(record, null, null);
    }

    private ExtensionVersion toExtensionVersionFull(
            Record record,
            Extension extension,
            FieldMapper extensionVersionMapper
    ) {
        if(extensionVersionMapper == null) {
            extensionVersionMapper = new DefaultFieldMapper();
        }

        var extVersion = toExtensionVersionCommon(record, extension, extensionVersionMapper);
        extVersion.setLicense(record.get(extensionVersionMapper.map(EXTENSION_VERSION.LICENSE)));
        extVersion.setHomepage(record.get(extensionVersionMapper.map(EXTENSION_VERSION.HOMEPAGE)));
        extVersion.setBugs(record.get(extensionVersionMapper.map(EXTENSION_VERSION.BUGS)));
        extVersion.setMarkdown(record.get(extensionVersionMapper.map(EXTENSION_VERSION.MARKDOWN)));
        extVersion.setQna(record.get(extensionVersionMapper.map(EXTENSION_VERSION.QNA)));

        if(extension == null) {
            var newExtension = extVersion.getExtension();
            newExtension.setPublicId(record.get(EXTENSION.PUBLIC_ID));
            newExtension.setAverageRating(record.get(EXTENSION.AVERAGE_RATING));
            newExtension.setReviewCount(record.get(EXTENSION.REVIEW_COUNT));
            newExtension.setDownloadCount(record.get(EXTENSION.DOWNLOAD_COUNT));
            newExtension.setPublishedDate(record.get(EXTENSION.PUBLISHED_DATE));
            newExtension.setLastUpdatedDate(record.get(EXTENSION.LAST_UPDATED_DATE));

            var newNamespace = newExtension.getNamespace();
            newNamespace.setPublicId(record.get(NAMESPACE.PUBLIC_ID));
        }

        var user = new UserData();
        user.setId(record.get(USER_DATA.ID));
        user.setLoginName(record.get(USER_DATA.LOGIN_NAME));
        user.setFullName(record.get(USER_DATA.FULL_NAME));
        user.setAvatarUrl(record.get(USER_DATA.AVATAR_URL));
        user.setProviderUrl(record.get(USER_DATA.PROVIDER_URL));
        user.setProvider(record.get(USER_DATA.PROVIDER));

        var token = new PersonalAccessToken();
        token.setUser(user);

        extVersion.setPublishedWith(token);
        extVersion.setType(ExtensionVersion.Type.REGULAR);
        return extVersion;
    }

    private ExtensionVersion toExtensionVersion(Record record) {
        var extVersion = toExtensionVersionCommon(record, null, new DefaultFieldMapper());
        extVersion.setType(ExtensionVersion.Type.MINIMAL);
        return extVersion;
    }

    private ExtensionVersion toExtensionVersionCommon(
            Record record,
            Extension extension,
            FieldMapper extensionVersionMapper
    ) {
        var converter = new ListOfStringConverter();

        var extVersion = new ExtensionVersion();
        extVersion.setId(record.get(extensionVersionMapper.map(EXTENSION_VERSION.ID)));
        extVersion.setVersion(record.get(extensionVersionMapper.map(EXTENSION_VERSION.VERSION)));
        extVersion.setTargetPlatform(record.get(extensionVersionMapper.map(EXTENSION_VERSION.TARGET_PLATFORM)));
        extVersion.setPreview(record.get(extensionVersionMapper.map(EXTENSION_VERSION.PREVIEW)));
        extVersion.setPreRelease(record.get(extensionVersionMapper.map(EXTENSION_VERSION.PRE_RELEASE)));
        extVersion.setTimestamp(record.get(extensionVersionMapper.map(EXTENSION_VERSION.TIMESTAMP)));
        extVersion.setDisplayName(record.get(extensionVersionMapper.map(EXTENSION_VERSION.DISPLAY_NAME)));
        extVersion.setDescription(record.get(extensionVersionMapper.map(EXTENSION_VERSION.DESCRIPTION)));
        extVersion.setEngines(toList(record.get(extensionVersionMapper.map(EXTENSION_VERSION.ENGINES)), converter));
        extVersion.setCategories(toList(record.get(extensionVersionMapper.map(EXTENSION_VERSION.CATEGORIES)), converter));
        extVersion.setTags(toList(record.get(extensionVersionMapper.map(EXTENSION_VERSION.TAGS)), converter));
        extVersion.setExtensionKind(toList(record.get(extensionVersionMapper.map(EXTENSION_VERSION.EXTENSION_KIND)), converter));
        extVersion.setRepository(record.get(extensionVersionMapper.map(EXTENSION_VERSION.REPOSITORY)));
        extVersion.setGalleryColor(record.get(extensionVersionMapper.map(EXTENSION_VERSION.GALLERY_COLOR)));
        extVersion.setGalleryTheme(record.get(extensionVersionMapper.map(EXTENSION_VERSION.GALLERY_THEME)));
        extVersion.setLocalizedLanguages(toList(record.get(extensionVersionMapper.map(EXTENSION_VERSION.LOCALIZED_LANGUAGES)), converter));
        extVersion.setDependencies(toList(record.get(extensionVersionMapper.map(EXTENSION_VERSION.DEPENDENCIES)), converter));
        extVersion.setBundledExtensions(toList(record.get(extensionVersionMapper.map(EXTENSION_VERSION.BUNDLED_EXTENSIONS)), converter));
        extVersion.setSponsorLink(record.get(extensionVersionMapper.map(EXTENSION_VERSION.SPONSOR_LINK)));

        if(extension == null) {
            var namespace = new Namespace();
            namespace.setId(record.get(NAMESPACE.ID));
            namespace.setName(record.get(NAMESPACE.NAME));

            extension = new Extension();
            extension.setId(record.get(EXTENSION.ID));
            extension.setName(record.get(EXTENSION.NAME));
            extension.setNamespace(namespace);
        }

        extVersion.setExtension(extension);

        var keyPair = new SignatureKeyPair();
        keyPair.setPublicId(record.get(SIGNATURE_KEY_PAIR.PUBLIC_ID));
        extVersion.setSignatureKeyPair(keyPair);
        return extVersion;
    }

    private List<String> toList(String raw, ListOfStringConverter converter) {
        return converter.convertToEntityAttribute(raw);
    }

    public List<VersionTargetPlatformsJson> findTargetPlatformsGroupedByVersion(Extension extension) {
        var targetPlatforms = DSL.arrayAgg(EXTENSION_VERSION.TARGET_PLATFORM)
                .orderBy(
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc()
                );

        return dsl.select(
                    EXTENSION_VERSION.SEMVER_MAJOR,
                    EXTENSION_VERSION.SEMVER_MINOR,
                    EXTENSION_VERSION.SEMVER_PATCH,
                    EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE,
                    EXTENSION_VERSION.VERSION,
                    targetPlatforms
                )
                .from(EXTENSION_VERSION)
                .where(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()))
                .groupBy(
                        EXTENSION_VERSION.SEMVER_MAJOR,
                        EXTENSION_VERSION.SEMVER_MINOR,
                        EXTENSION_VERSION.SEMVER_PATCH,
                        EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE,
                        EXTENSION_VERSION.VERSION
                )
                .orderBy(
                        EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                        EXTENSION_VERSION.SEMVER_MINOR.desc(),
                        EXTENSION_VERSION.SEMVER_PATCH.desc(),
                        EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                        EXTENSION_VERSION.VERSION.asc()
                )
                .fetch()
                .map(record -> new VersionTargetPlatformsJson(
                        record.get(EXTENSION_VERSION.VERSION),
                        record.get(targetPlatforms)
                ));
    }

    public List<ExtensionVersion> findVersionsForUrls(Extension extension, String targetPlatform, String version) {
        var query = dsl.selectQuery();
        query.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.TARGET_PLATFORM
        );
        query.addFrom(EXTENSION_VERSION);
        query.addConditions(
                EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()),
                EXTENSION_VERSION.VERSION.eq(version)
        );
        if(targetPlatform != null) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        return query.fetch()
                .map((record) -> {
                   var extVersion = new ExtensionVersion();
                   extVersion.setId(record.get(EXTENSION_VERSION.ID));
                   extVersion.setVersion(record.get(EXTENSION_VERSION.VERSION));
                   extVersion.setTargetPlatform(record.get(EXTENSION_VERSION.TARGET_PLATFORM));
                   extVersion.setExtension(extension);
                   return extVersion;
                });
    }

    @Observed
    public ExtensionVersion findLatest(
            Extension extension,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        var query = findLatestQuery(targetPlatform, onlyPreRelease, onlyActive);
        query.addSelect(
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                SIGNATURE_KEY_PAIR.PUBLIC_ID
        );
        query.addJoin(PERSONAL_ACCESS_TOKEN, JoinType.LEFT_OUTER_JOIN, PERSONAL_ACCESS_TOKEN.ID.eq(EXTENSION_VERSION.PUBLISHED_WITH_ID));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(PERSONAL_ACCESS_TOKEN.USER_DATA));
        query.addJoin(SIGNATURE_KEY_PAIR, JoinType.LEFT_OUTER_JOIN, SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        query.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()));
        return query.fetchOne((record) -> toExtensionVersionFull(record, extension, null));
    }

    public List<ExtensionVersion> findLatest(Namespace namespace) {
        var latestQuery = findLatestQuery(null, false, true);
        latestQuery.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                SIGNATURE_KEY_PAIR.PUBLIC_ID
        );
        latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID));
        latestQuery.addJoin(SIGNATURE_KEY_PAIR, JoinType.LEFT_OUTER_JOIN, SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        var latest = latestQuery.asTable();

        var query = dsl.selectQuery();
        query.addSelect(
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                latest.field(EXTENSION_VERSION.ID),
                latest.field(EXTENSION_VERSION.VERSION),
                latest.field(EXTENSION_VERSION.TARGET_PLATFORM),
                latest.field(EXTENSION_VERSION.TIMESTAMP),
                latest.field(EXTENSION_VERSION.DISPLAY_NAME),
                latest.field(EXTENSION_VERSION.DESCRIPTION),
                latest.field(SIGNATURE_KEY_PAIR.PUBLIC_ID)
        );
        query.addFrom(EXTENSION, DSL.lateral(latest));
        query.addConditions(
                EXTENSION.NAMESPACE_ID.eq(namespace.getId()),
                EXTENSION.ACTIVE.eq(true)
        );
        query.addOrderBy(EXTENSION.DOWNLOAD_COUNT.desc());

        return query.fetch(record -> {
            var extension = new Extension();
            extension.setId(record.get(EXTENSION.ID));
            extension.setName(record.get(EXTENSION.NAME));
            extension.setAverageRating(record.get(EXTENSION.AVERAGE_RATING));
            extension.setReviewCount(record.get(EXTENSION.REVIEW_COUNT));
            extension.setDownloadCount(record.get(EXTENSION.DOWNLOAD_COUNT));
            extension.setNamespace(namespace);

            var extVersion = new ExtensionVersion();
            extVersion.setId(record.get(latest.field(EXTENSION_VERSION.ID)));
            extVersion.setVersion(record.get(latest.field(EXTENSION_VERSION.VERSION)));
            extVersion.setTargetPlatform(record.get(latest.field(EXTENSION_VERSION.TARGET_PLATFORM)));
            extVersion.setTimestamp(record.get(latest.field(EXTENSION_VERSION.TIMESTAMP)));
            extVersion.setDisplayName(record.get(latest.field(EXTENSION_VERSION.DISPLAY_NAME)));
            extVersion.setDescription(record.get(latest.field(EXTENSION_VERSION.DESCRIPTION)));
            extVersion.setExtension(extension);

            var keyPair = new SignatureKeyPair();
            keyPair.setPublicId(record.get(latest.field(SIGNATURE_KEY_PAIR.PUBLIC_ID)));
            extVersion.setSignatureKeyPair(keyPair);

            return extVersion;
        });
    }

    public List<ExtensionVersion> findLatest(UserData user) {
        var latestQuery = findLatestQuery(null, false, false);
        latestQuery.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID,
                EXTENSION_VERSION.PUBLISHED_WITH_ID
        );
        latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID));
        var latest = latestQuery.asTable();

        var query = dsl.selectQuery();
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME,
                NAMESPACE.PUBLIC_ID,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION.PUBLIC_ID,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                EXTENSION.ACTIVE,
                latest.field(EXTENSION_VERSION.ID),
                latest.field(EXTENSION_VERSION.VERSION),
                latest.field(EXTENSION_VERSION.TARGET_PLATFORM),
                latest.field(EXTENSION_VERSION.PREVIEW),
                latest.field(EXTENSION_VERSION.PRE_RELEASE),
                latest.field(EXTENSION_VERSION.TIMESTAMP),
                latest.field(EXTENSION_VERSION.DISPLAY_NAME),
                latest.field(EXTENSION_VERSION.DESCRIPTION),
                latest.field(EXTENSION_VERSION.ENGINES),
                latest.field(EXTENSION_VERSION.CATEGORIES),
                latest.field(EXTENSION_VERSION.TAGS),
                latest.field(EXTENSION_VERSION.EXTENSION_KIND),
                latest.field(EXTENSION_VERSION.LICENSE),
                latest.field(EXTENSION_VERSION.HOMEPAGE),
                latest.field(EXTENSION_VERSION.REPOSITORY),
                latest.field(EXTENSION_VERSION.SPONSOR_LINK),
                latest.field(EXTENSION_VERSION.BUGS),
                latest.field(EXTENSION_VERSION.MARKDOWN),
                latest.field(EXTENSION_VERSION.GALLERY_COLOR),
                latest.field(EXTENSION_VERSION.GALLERY_THEME),
                latest.field(EXTENSION_VERSION.LOCALIZED_LANGUAGES),
                latest.field(EXTENSION_VERSION.QNA),
                latest.field(EXTENSION_VERSION.DEPENDENCIES),
                latest.field(EXTENSION_VERSION.BUNDLED_EXTENSIONS),
                SIGNATURE_KEY_PAIR.PUBLIC_ID,
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER
        );
        query.addFrom(NAMESPACE);
        query.addJoin(EXTENSION, EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID));
        query.addJoin(latest, JoinType.CROSS_APPLY, DSL.condition(true));
        query.addJoin(SIGNATURE_KEY_PAIR, JoinType.LEFT_OUTER_JOIN, SIGNATURE_KEY_PAIR.ID.eq(latest.field(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID)));
        query.addJoin(PERSONAL_ACCESS_TOKEN, JoinType.LEFT_OUTER_JOIN, PERSONAL_ACCESS_TOKEN.ID.eq(latest.field(EXTENSION_VERSION.PUBLISHED_WITH_ID)));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(PERSONAL_ACCESS_TOKEN.USER_DATA));
        query.addConditions(PERSONAL_ACCESS_TOKEN.USER_DATA.eq(user.getId()));
        return query.fetch(record -> {
            var extVersion = toExtensionVersionFull(record, null, new TableFieldMapper(latest));
            extVersion.getExtension().getNamespace().setDisplayName(record.get(NAMESPACE.DISPLAY_NAME));
            extVersion.getExtension().setActive(record.get(EXTENSION.ACTIVE));
            return extVersion;
        });
    }

    public ExtensionVersion findLatestForAllUrls(
            Extension extension,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        var query = findLatestQuery(targetPlatform, onlyPreRelease, onlyActive);
        query.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()));
        query.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.PREVIEW
        );
        return query.fetchOne((record) -> {
            if(record == null) {
                return null;
            }

            var extVersion = new ExtensionVersion();
            extVersion.setId(record.get(EXTENSION_VERSION.ID));
            extVersion.setVersion(record.get(EXTENSION_VERSION.VERSION));
            extVersion.setPreview(record.get(EXTENSION_VERSION.PREVIEW));
            extVersion.setExtension(extension);
            return extVersion;
        });
    }

    private SelectQuery<Record> findLatestQuery(
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        var query = dsl.selectQuery();
        query.addFrom(EXTENSION_VERSION);
        if(TargetPlatform.isValid(targetPlatform)) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }
        if(onlyPreRelease) {
            query.addConditions(EXTENSION_VERSION.PRE_RELEASE.eq(true));
        }
        if(onlyActive) {
            query.addConditions(EXTENSION_VERSION.ACTIVE.eq(true));
        }

        query.addOrderBy(
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                EXTENSION_VERSION.TIMESTAMP.desc()
        );
        query.addLimit(1);
        return query;
    }

    public ExtensionVersion find(String namespaceName, String extensionName, String targetPlatform, String version) {
        var onlyPreRelease = VersionAlias.PRE_RELEASE.equals(version);
        var query = findLatestQuery(targetPlatform, onlyPreRelease, true);
        query.addSelect(
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER,
                NAMESPACE.ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME,
                NAMESPACE.PUBLIC_ID,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION.PUBLIC_ID,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                SIGNATURE_KEY_PAIR.PUBLIC_ID
        );
        query.addJoin(PERSONAL_ACCESS_TOKEN, JoinType.LEFT_OUTER_JOIN, PERSONAL_ACCESS_TOKEN.ID.eq(EXTENSION_VERSION.PUBLISHED_WITH_ID));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(PERSONAL_ACCESS_TOKEN.USER_DATA));
        query.addJoin(SIGNATURE_KEY_PAIR, JoinType.LEFT_OUTER_JOIN, SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addConditions(
                EXTENSION.NAME.equalIgnoreCase(extensionName),
                NAMESPACE.NAME.equalIgnoreCase(namespaceName)
        );
        if(!VersionAlias.LATEST.equals(version) && !VersionAlias.PRE_RELEASE.equals(version)) {
            query.addConditions(EXTENSION_VERSION.VERSION.eq(version));
        }

        return query.fetchOne((record) -> {
            var extVersion = toExtensionVersionFull(record);
            extVersion.getExtension().getNamespace().setDisplayName(record.get(NAMESPACE.DISPLAY_NAME));
            return extVersion;
        });
    }

    public List<String> findDistinctTargetPlatforms(Extension extension) {
        return dsl.selectDistinct(EXTENSION_VERSION.TARGET_PLATFORM)
                .from(EXTENSION_VERSION)
                .where(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .fetch(EXTENSION_VERSION.TARGET_PLATFORM);
    }

    private interface FieldMapper {
        <T> Field<T> map(Field<T> field);
    }

    private record TableFieldMapper(Table<Record> table) implements FieldMapper {
        public <T> Field<T> map(Field<T> field) {
            return table.field(field);
        }
    }

    private static class DefaultFieldMapper implements FieldMapper {
        public <T> Field<T> map(Field<T> field) {
            return field;
        }
    }
}
