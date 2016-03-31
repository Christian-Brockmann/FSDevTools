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

package com.espirit.moddev.cli.results;

import de.espirit.firstspirit.access.database.BasicEntityInfo;
import de.espirit.firstspirit.access.store.BasicElementInfo;
import de.espirit.firstspirit.store.access.nexport.operations.ImportOperation;

import java.util.List;
import java.util.Set;

public class ImportResult extends SimpleResult<ImportOperation.Result> {
    public ImportResult(ImportOperation.Result result) {
        super(result);
    }

    public ImportResult(Exception exception) {
        super(exception);
    }

    @Override
    public void log() {
        if (isError()) {
            LOGGER.error("Import operation not successful", exception);
        } else {
            LOGGER.info("Import operation successful");

            logUpdateElements(get().getUpdatedElements(), "updated elements");
            logUpdateElements(get().getCreatedElements(), "created elements");
            logUpdateElements(get().getDeletedElements(), "deleted elements");
            logUpdateElements(get().getMovedElements(), "moved elements");
            logCreatedElements(get().getCreatedEntities(), "created entities");
            logUpdateElements(get().getLostAndFoundElements(), "lost and found elements");
            logProblems(get().getProblems(), "problems");

            Object[] args = {Integer.valueOf(get().getUpdatedElements().size()),
                    Integer.valueOf(get().getCreatedElements().size()),
                    Integer.valueOf(get().getDeletedElements().size())};

            LOGGER.info("Import done.\n\t"
                    + "updated elements: {}\n\t"
                    + "created elements: {}\n\t"
                    + "deleted elements: {}", args);
        }
    }

    private void logProblems(List<ImportOperation.Problem> problems, String state) {
        LOGGER.info(state + ": " + problems.size());
        for (ImportOperation.Problem problem : problems) {
            LOGGER.debug(problem.getMessage());
        }
    }

    private void logCreatedElements(Set<BasicEntityInfo> createdEntities, String state) {
        LOGGER.info(state + ": " + createdEntities.size());
        for (BasicEntityInfo info : createdEntities) {
            LOGGER.debug("Gid: " + info.getGid() + " EntityType: " + info.getEntityType());
        }
    }

    /**
     * log info messages.
     *
     * @param handle represents the current element that was imported
     * @param state  is used for the log message ("updated", "created" and "deleted" etc.)
     */
    public void logUpdateElements(final Set<BasicElementInfo> handle, final String state) {
        LOGGER.info(state + ": " + handle.size());
        for (BasicElementInfo _handle : handle) {
            LOGGER.debug("Uid: " + _handle.getUid() + " NodeId: " + _handle.getNodeId());
        }
    }
}
