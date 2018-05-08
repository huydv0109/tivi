/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.banes.chris.tivi.trakt.calls

import com.uwetrottmann.trakt5.TraktV2
import com.uwetrottmann.trakt5.entities.TrendingShow
import com.uwetrottmann.trakt5.enums.Extended
import me.banes.chris.tivi.ShowFetcher
import me.banes.chris.tivi.calls.PaginatedEntryCallImpl
import me.banes.chris.tivi.data.DatabaseTransactionRunner
import me.banes.chris.tivi.data.daos.TiviShowDao
import me.banes.chris.tivi.data.daos.TrendingDao
import me.banes.chris.tivi.data.entities.TiviShow
import me.banes.chris.tivi.data.entities.TrendingEntry
import me.banes.chris.tivi.data.entities.TrendingListItem
import me.banes.chris.tivi.extensions.fetchBodyWithRetry
import me.banes.chris.tivi.util.AppCoroutineDispatchers
import me.banes.chris.tivi.util.AppRxSchedulers
import javax.inject.Inject

class TrendingCall @Inject constructor(
    databaseTransactionRunner: DatabaseTransactionRunner,
    showDao: TiviShowDao,
    trendingDao: TrendingDao,
    private val showFetcher: ShowFetcher,
    private val trakt: TraktV2,
    schedulers: AppRxSchedulers,
    dispatchers: AppCoroutineDispatchers
) : PaginatedEntryCallImpl<TrendingShow, TrendingEntry, TrendingListItem, TrendingDao>(
        databaseTransactionRunner,
        showDao,
        trendingDao,
        schedulers,
        dispatchers
) {
    override suspend fun networkCall(page: Int): List<TrendingShow> {
        // We add one to the page since Trakt uses a 1-based index whereas we use 0-based
        return trakt.shows().trending(page + 1, pageSize, Extended.NOSEASONS).fetchBodyWithRetry()
    }

    override fun mapToEntry(networkEntity: TrendingShow, show: TiviShow, page: Int): TrendingEntry {
        assert(show.id != null)
        return TrendingEntry(null, show.id!!, page, networkEntity.watchers)
    }

    override suspend fun loadShow(response: TrendingShow): TiviShow {
        return showFetcher.load(response.show.ids.trakt, response.show)
    }
}