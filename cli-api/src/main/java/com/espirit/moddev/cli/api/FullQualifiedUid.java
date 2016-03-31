/*
 *
 * *********************************************************************
 * fsdevtools
 * %%
 * Copyright (C) 2016 e-Spirit AG
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * *********************************************************************
 *
 */

package com.espirit.moddev.cli.api;


import com.espirit.moddev.cli.api.exceptions.UnknownRootNodeException;
import com.espirit.moddev.cli.api.exceptions.UnregisteredPrefixException;
import de.espirit.firstspirit.access.store.IDProvider;
import de.espirit.firstspirit.access.store.ReferenceType;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The type Full qualified uid.
 */
public class FullQualifiedUid {
    private static final Logger LOGGER = Logger.getLogger(FullQualifiedUid.class);

    public static final String ROOT_NODE_IDENTIFIER = "root";


    private static final Map<String, IDProvider.UidType> storePostfixes = new HashMap();
    static {
        storePostfixes.put("templatestore", IDProvider.UidType.TEMPLATESTORE);
        storePostfixes.put("pagestore", IDProvider.UidType.PAGESTORE);
        storePostfixes.put("contentstore", IDProvider.UidType.CONTENTSTORE);
        storePostfixes.put("sitestore", IDProvider.UidType.SITESTORE_FOLDER);
        storePostfixes.put("mediastore", IDProvider.UidType.MEDIASTORE_FOLDER);
        storePostfixes.put("globalstore", IDProvider.UidType.GLOBALSTORE);
    }
    /**
     * This collection stores a custom mapping from
     * uid prefixes to UidTypes. The collection
     * can be used to add or override prefixes for
     * later usage. It also includes store postfixes,
     * even if those aren't used as prefix.
     */
    private static final Map<String, IDProvider.UidType> customPrefixUidTypeMappings = new HashMap();
    static {
        customPrefixUidTypeMappings.put("page", IDProvider.UidType.PAGESTORE);
        customPrefixUidTypeMappings.put("pagetemplate", IDProvider.UidType.TEMPLATESTORE);

        customPrefixUidTypeMappings.putAll(storePostfixes);
    }


    private static final Pattern DELIMITER = Pattern.compile("\\s*:\\s*");
    private final IDProvider.UidType uidType;
    private final String uid;

    /**
     * Instantiates a new Full qualified uid.
     *
     * @param uidType the uidType
     * @param uid   the uid
     * @throws IllegalArgumentException if uidType or uid is null or blank
     */
    public FullQualifiedUid(final IDProvider.UidType uidType, final String uid) {
        if (uidType == null) {
            throw new IllegalArgumentException("uidType is null.");
        }
        if (StringUtils.isBlank(uid)) {
            throw new IllegalArgumentException("Uid is null or empty.");
        }
        this.uidType = uidType;
        this.uid = uid;
    }

    /**
     * Parse list of full qualified uid strings.
     *
     * @param fullQualifiedUids the full qualified uids
     * @throws IllegalArgumentException if fullQualifiedUids is null
     * or if the prefix/postfix structure is corrupt
     * @throws UnknownRootNodeException if a requested store root does not exist
     *
     * @return the list
     */
    public static List<FullQualifiedUid> parse(final List<String> fullQualifiedUids) {
        if (fullQualifiedUids == null) {
            throw new IllegalArgumentException("fullQualifiedUids is null!");
        }
        if (fullQualifiedUids.isEmpty()) {
            return Collections.emptyList();
        }

        List<FullQualifiedUid> list = new ArrayList<>(fullQualifiedUids.size());
        for (String fullQualifiedUid : fullQualifiedUids) {
            try(Scanner uidScanner = new Scanner(fullQualifiedUid)) {
                uidScanner.useDelimiter(DELIMITER);
                if (uidScanner.hasNext()) {
                    String firstPart = uidScanner.next();
                    if (uidScanner.hasNext()) {
                        String secondPart = uidScanner.next();
                        FullQualifiedUid fqUid;
                        fqUid = getFullQualifiedUid(firstPart, secondPart);
                        list.add(fqUid);
                    } else {
                        throw new IllegalArgumentException("Wrong input format for input string " + firstPart);
                    }
                }
            }
        }
        return list;
    }

    @NotNull
    private static FullQualifiedUid getFullQualifiedUid(String firstPart, String secondPart) {
        FullQualifiedUid fqUid;
        if (firstPart.equals(ROOT_NODE_IDENTIFIER)) {
            try {
                fqUid = new FullQualifiedUid(getUidTypeForPrefix(secondPart.toLowerCase()), ROOT_NODE_IDENTIFIER);
            } catch (UnregisteredPrefixException e) {
                LOGGER.trace(e);
                throw new UnknownRootNodeException("Store root node not known: " + secondPart.toLowerCase());
            }
        } else {
            fqUid = new FullQualifiedUid(getUidTypeForPrefix(firstPart.toLowerCase()), secondPart);
        }
        return fqUid;
    }

    /**
     * Maps a full qualified uid prefix string to a UidType.
     *
     * @param prefix the prefix string the UidType needs to be known
     * @throws UnregisteredPrefixException if the given prefix can not be mapped
     * @throws IllegalArgumentException if the given prefix is null or empty
     * @return the UidType for the prefix
     */
    private static IDProvider.UidType getUidTypeForPrefix(String prefix) {
        if(prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("A prefix must be provided for every uid");
        }

        Map<String, IDProvider.UidType> knownPrefixes = getAllKnownPrefixes();

        if(knownPrefixes.containsKey(prefix)) {
            return knownPrefixes.get(prefix);
        }

        throw new UnregisteredPrefixException("No UidType registered for prefix \"" + prefix + "\""
                                                    + ". Available prefixes are " + knownPrefixes.keySet());
    }

    /**
     * Retrieves all known full qualified uid prefixes and their corresponding UidType.
     * @return a collection of prefixes and UidTypes
     */
    private static Map<String, IDProvider.UidType> getAllKnownPrefixes() {
        Map<String, IDProvider.UidType> result = new HashMap<>();

        for(ReferenceType referenceType : Arrays.asList(ReferenceType.values())) {
            result.put(referenceType.type(), referenceType.getUidType());
        }

        for(Map.Entry<String, IDProvider.UidType> customPrefixUidTypeMapping : customPrefixUidTypeMappings.entrySet()) {
            if(!result.containsKey(customPrefixUidTypeMapping.getKey())) {
                result.put(customPrefixUidTypeMapping.getKey(), customPrefixUidTypeMapping.getValue());
            }
        }

        return result;
    }

    /**
     * Retrieves all FirstSpirit store postfix identifiers that are used as export uids.
     * @return a collection of postfixes and UidTypes
     */
    private static Map<String, IDProvider.UidType> getAllStorePostfixes() {
        return Collections.unmodifiableMap(storePostfixes);
    }

    public static Set<String> getAllKnownPrefixStrings() {
        return Collections.unmodifiableSet(getAllKnownPrefixes().keySet());
    }

    public static Set<String> getAllStorePostfixStrings() {
        return Collections.unmodifiableSet(getAllStorePostfixes().keySet());
    }

    /**
     * Retrieves a uid prefix for the given UidType.
     * @param uidType the UidType to retrieve the prefix for.
     * @return the corresponding prefix
     * @throws IllegalArgumentException if no prefix is registered for the given UidType
     */
    private static String getPrefixForUidType(IDProvider.UidType uidType) {
        Map<String, IDProvider.UidType> knownPrefixes = getAllKnownPrefixes();
        for(Map.Entry<String, IDProvider.UidType> entry : knownPrefixes.entrySet()) {
            if(entry.getValue().equals(uidType)) {
                String prefix = entry.getKey();
                return prefix;
            }
        }
        throw new IllegalArgumentException("No prefix registered for UidType " + uidType.name() + ". Known prefixes are " + getAllKnownPrefixes());
    }

    /**
     * Gets uidType.
     *
     * @return the uidType
     */
    public IDProvider.UidType getUidType() {
        return uidType;
    }

    /**
     * Gets uid.
     *
     * @return the uid
     */
    public String getUid() {
        return uid;
    }

    @Override
    public boolean equals(final Object o) {
        if(o == null || o.getClass() != this.getClass()) {
            return false;
        } else if (this == o) {
            return true;
        } else {
            FullQualifiedUid that = (FullQualifiedUid) o;
            return uidType.equals(that.uidType) && uid.equals(that.uid);
        }
    }

    @Override
    public int hashCode() {
        int result = uidType.hashCode();
        result = 31 * result + uid.hashCode(); //NOSONAR
        return result;
    }

    @Override
    public String toString() {
        if(getUid().equals(ROOT_NODE_IDENTIFIER)) {
            return uid + ":" + getPrefixForUidType(uidType);
        }
        return getPrefixForUidType(uidType) + ":" + uid;
    }
}
