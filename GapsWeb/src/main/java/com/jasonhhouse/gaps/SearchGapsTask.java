/*
 *
 *  Copyright 2020 Jason H House
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.jasonhhouse.gaps;

import com.jasonhhouse.gaps.service.IoService;
import com.jasonhhouse.gaps.service.NotificationService;
import com.jasonhhouse.gaps.service.TmdbService;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.HttpUrl;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

public class SearchGapsTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchGapsTask.class);

    private final GapsService gapsService;
    private final GapsSearch gapsSearch;
    private final TmdbService tmdbService;
    private final IoService ioService;
    private final PlexQuery plexQuery;
    private final GapsUrlGenerator gapsUrlGenerator;
    private final NotificationService notificationService;

    public SearchGapsTask(GapsService gapsService, GapsSearch gapsSearch, TmdbService tmdbService, IoService ioService, PlexQuery plexQuery, GapsUrlGenerator gapsUrlGenerator, NotificationService notificationService) {
        this.gapsService = gapsService;
        this.gapsSearch = gapsSearch;
        this.tmdbService = tmdbService;
        this.ioService = ioService;
        this.plexQuery = plexQuery;
        this.gapsUrlGenerator = gapsUrlGenerator;
        this.notificationService = notificationService;
    }

    @Override
    public void run() {
        LOGGER.info("run()");

        if (CollectionUtils.isEmpty(gapsService.getPlexProperties().getPlexServers())) {
            LOGGER.warn("No Plex Servers Found. Canceling automatic search.");
            return;
        }

        boolean tmdbResult = checkTmdbKey();

        if (tmdbResult) {
            checkPlexServers();

            updatePlexLibraries();

            updateLibraryMovies();

            findRecommendedMovies();
        }
    }

    private boolean checkTmdbKey() {
        LOGGER.info("checkTmdbKey()");

        try {
            String tmdbKey = ioService.readProperties().getMovieDbApiKey();
            Payload payload = tmdbService.testTmdbKey(tmdbKey);

            if (Payload.TMDB_KEY_VALID.getCode() == payload.getCode()) {
                notificationService.tmdbConnectionSuccessful();
                return true;
            } else {
                notificationService.tmdbConnectionFailed(payload.getReason());
                return false;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read properties for TMDB key", e);
            notificationService.tmdbConnectionFailed(e.getMessage());
            return false;
        }
    }

    private void checkPlexServers() {
        LOGGER.info("checkPlexServers()");

        for (PlexServer plexServer : gapsService.getPlexProperties().getPlexServers()) {
            Payload payload = plexQuery.queryPlexServer(plexServer);
            if (payload.getCode() == Payload.PLEX_CONNECTION_SUCCEEDED.getCode()) {
                notificationService.plexServerConnectSuccessful(plexServer);
            } else {
                notificationService.plexServerConnectFailed(plexServer, payload.getReason());
            }
        }
    }

    private void updatePlexLibraries() {
        LOGGER.info("updatePlexLibraries()");
        //Update each Plex Library from each Plex Server
        for (PlexServer plexServer : gapsService.getPlexProperties().getPlexServers()) {
            Payload getLibrariesResults = plexQuery.getLibraries(plexServer);
            if (Payload.PLEX_LIBRARIES_FOUND == getLibrariesResults) {
                LOGGER.info("Plex libraries found for Plex Server {}", plexServer.getFriendlyName());
            } else {
                LOGGER.warn("Plex libraries not found for Plex Server {}", plexServer.getFriendlyName());
            }
        }
    }

    private void updateLibraryMovies() {
        LOGGER.info("updateLibraryMovies()");
        for (PlexServer plexServer : gapsService.getPlexProperties().getPlexServers()) {
            for (PlexLibrary plexLibrary : plexServer.getPlexLibraries()) {
                HttpUrl url = gapsUrlGenerator.generatePlexLibraryUrl(plexServer, plexLibrary);
                try {
                    List<Movie> ownedMovies = plexQuery.findAllPlexMovies(generateOwnedMovieMap(), url);
                    ioService.writeOwnedMoviesToFile(ownedMovies, plexLibrary.getMachineIdentifier(), plexLibrary.getKey());
                    notificationService.plexLibraryScanSuccessful(plexServer, plexLibrary);
                } catch (ResponseStatusException e) {
                    notificationService.plexLibraryScanFailed(plexServer, plexLibrary, e.getMessage());
                }
            }
        }
    }

    private void findRecommendedMovies() {
        LOGGER.info("findRecommendedMovies()");
        for (PlexServer plexServer : gapsService.getPlexProperties().getPlexServers()) {
            for (PlexLibrary plexLibrary : plexServer.getPlexLibraries()) {
                gapsSearch.run(plexLibrary.getMachineIdentifier(), plexLibrary.getKey());
            }
        }
    }

    private Map<Pair<String, Integer>, Movie> generateOwnedMovieMap() {
        Set<Movie> everyMovie = ioService.readMovieIdsFromFile();
        Map<Pair<String, Integer>, Movie> previousMovies = new HashMap<>();

        gapsService
                .getPlexProperties()
                .getPlexServers()
                .forEach(plexServer -> plexServer
                        .getPlexLibraries()
                        .stream()
                        .filter(PlexLibrary::getSelected)
                        .forEach(plexLibrary -> everyMovie.forEach(movie -> previousMovies.put(new Pair<>(movie.getName(), movie.getYear()), movie))));

        return previousMovies;
    }
}