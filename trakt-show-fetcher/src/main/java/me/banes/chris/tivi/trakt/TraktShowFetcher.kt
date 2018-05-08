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

package me.banes.chris.tivi.trakt

import com.uwetrottmann.trakt5.TraktV2
import com.uwetrottmann.trakt5.entities.Show
import com.uwetrottmann.trakt5.enums.Extended
import kotlinx.coroutines.experimental.withContext
import me.banes.chris.tivi.data.daos.EntityInserter
import me.banes.chris.tivi.data.daos.TiviShowDao
import me.banes.chris.tivi.data.entities.TiviShow
import me.banes.chris.tivi.extensions.fetchBodyWithRetry
import me.banes.chris.tivi.util.AppCoroutineDispatchers
import org.threeten.bp.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktShowFetcher @Inject constructor(
    private val showDao: TiviShowDao,
    private val trakt: TraktV2,
    private val dispatchers: AppCoroutineDispatchers,
    private val entityInserter: EntityInserter
) {
    suspend fun loadShow(traktId: Int, entity: Show? = null): TiviShow {
        val dbShow = withContext(dispatchers.database) { showDao.getShowWithTraktId(traktId) }
        if (dbShow != null) {
            return dbShow
        }

        if (entity != null) {
            return withContext(dispatchers.database) {
                upsertShow(entity)
            }
        }

        return withContext(dispatchers.network) {
            trakt.shows().summary(traktId.toString(), Extended.NOSEASONS).fetchBodyWithRetry()
        }.let {
            withContext(dispatchers.database) {
                upsertShow(it)
            }
        }
    }

    suspend fun updateShow(traktId: Int): TiviShow {
        return withContext(dispatchers.network) {
            trakt.shows().summary(traktId.toString(), Extended.FULL).fetchBodyWithRetry()
        }.let {
            withContext(dispatchers.database) {
                upsertShow(it)
            }
        }
    }

    private fun upsertShow(traktShow: Show): TiviShow {
        return (showDao.getShowWithTraktId(traktShow.ids.trakt) ?: TiviShow())
                .apply {
                    updateProperty(this::traktId, traktShow.ids.trakt)
                    updateProperty(this::tmdbId, traktShow.ids.tmdb)
                    updateProperty(this::title, traktShow.title)
                    updateProperty(this::summary, traktShow.overview)
                    updateProperty(this::homepage, traktShow.homepage)
                    updateProperty(this::rating, traktShow.rating?.toFloat())
                    updateProperty(this::certification, traktShow.certification)
                    updateProperty(this::runtime, traktShow.runtime)
                    updateProperty(this::network, traktShow.network)
                    updateProperty(this::country, traktShow.country)
                    updateProperty(this::_genres, traktShow.genres?.joinToString(","))
                    lastTraktUpdate = OffsetDateTime.now()
                }.let {
                    val id = entityInserter.insertOrUpdate(showDao, it)
                    showDao.getShowWithId(id)!!
                }
    }
}