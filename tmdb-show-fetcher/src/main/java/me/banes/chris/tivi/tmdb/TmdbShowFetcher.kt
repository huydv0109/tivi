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

package me.banes.chris.tivi.tmdb

import com.uwetrottmann.tmdb2.Tmdb
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
class TmdbShowFetcher @Inject constructor(
    private val showDao: TiviShowDao,
    private val tmdb: Tmdb,
    private val dispatchers: AppCoroutineDispatchers,
    private val entityInserter: EntityInserter
) {
    suspend fun updateShow(tmdbId: Int) {
        return withContext(dispatchers.network) {
            tmdb.tvService().tv(tmdbId).fetchBodyWithRetry()
        }.let { tmdbShow ->
            withContext(dispatchers.database) {
                (showDao.getShowWithTmdbId(tmdbShow.id) ?: TiviShow())
                        .apply {
                            updateProperty(this::tmdbId, tmdbShow.id)
                            updateProperty(this::title, tmdbShow.name)
                            updateProperty(this::summary, tmdbShow.overview)
                            updateProperty(this::tmdbBackdropPath, tmdbShow.backdrop_path)
                            updateProperty(this::tmdbPosterPath, tmdbShow.poster_path)
                            updateProperty(this::homepage, tmdbShow.homepage)
                            lastTmdbUpdate = OffsetDateTime.now()
                        }.let {
                            val id = entityInserter.insertOrUpdate(showDao, it)
                            showDao.getShowWithId(id)
                        }
            }
        }
    }
}
