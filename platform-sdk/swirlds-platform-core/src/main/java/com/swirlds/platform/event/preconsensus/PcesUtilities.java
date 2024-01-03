/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.swirlds.platform.event.preconsensus;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for preconsensus events.
 */
public final class PcesUtilities {

    private static final Logger logger = LogManager.getLogger(PcesUtilities.class);

    private PcesUtilities() {}

    /**
     * Compact the generational span of a PCES file.
     *
     * @param originalFile              the file to compact
     * @param previousMaximumGeneration the maximum generation of the previous PCES file, used to prevent using a
     *                                  smaller maximum generation than the previous file.
     * @return the new compacted PCES file.
     */
    @NonNull
    public static PreconsensusEventFile compactPreconsensusEventFile(
            @NonNull final PreconsensusEventFile originalFile, final long previousMaximumGeneration) {

        // Find the maximum generation in the file.
        long maxGeneration = originalFile.getMinimumGeneration();
        try (final IOIterator<GossipEvent> iterator = new PreconsensusEventFileIterator(originalFile, 0)) {

            while (iterator.hasNext()) {
                final GossipEvent next = iterator.next();
                maxGeneration = Math.max(maxGeneration, next.getGeneration());
            }

        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to read file {}", originalFile.getPath(), e);
            return originalFile;
        }

        // Important: do not decrease the maximum generation below the value of the previous file's maximum generation.
        maxGeneration = Math.max(maxGeneration, previousMaximumGeneration);

        if (maxGeneration == originalFile.getMaximumGeneration()) {
            // The file cannot have its span compacted any further.
            logger.info(STARTUP.getMarker(), "No span compaction necessary for {}", originalFile.getPath());
            return originalFile;
        }

        // Now, compact the generational span of the file using the newly discovered maximum generation.
        final PreconsensusEventFile newFile = originalFile.buildFileWithCompressedSpan(maxGeneration);
        try {
            Files.move(originalFile.getPath(), newFile.getPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to compact span of file {}", originalFile.getPath(), e);
            return originalFile;
        }

        logger.info(
                STARTUP.getMarker(),
                "Span compaction completed for {}, new maximum generation is {}",
                originalFile.getPath(),
                maxGeneration);

        return newFile;
    }

    /**
     * Parse a file into a PreConsensusEventFile wrapper object.
     *
     * @param path the path to the file
     * @return the wrapper object, or null if the file can't be parsed
     */
    @Nullable
    public static PreconsensusEventFile parseFile(@NonNull final Path path) {
        try {
            return PreconsensusEventFile.of(path);
        } catch (final IOException exception) {
            // ignore any file that can't be parsed
            logger.warn(EXCEPTION.getMarker(), "Failed to parse file: {}", path, exception);
            return null;
        }
    }

    /**
     * Compact all PCES files within a directory tree.
     *
     * @param rootPath the root of the directory tree
     */
    public static void compactPreconsensusEventFiles(@NonNull final Path rootPath) {
        final List<PreconsensusEventFile> files = new ArrayList<>();
        try (final Stream<Path> fileStream = Files.walk(rootPath)) {
            fileStream
                    .filter(f -> !Files.isDirectory(f))
                    .filter(f -> f.toString().endsWith(PreconsensusEventFile.EVENT_FILE_EXTENSION))
                    .map(PcesUtilities::parseFile)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEachOrdered(files::add);
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to walk directory tree {}", rootPath, e);
        }

        long previousMaximumGeneration = 0;
        for (final PreconsensusEventFile file : files) {
            final PreconsensusEventFile compactedFile = compactPreconsensusEventFile(file, previousMaximumGeneration);
            previousMaximumGeneration = compactedFile.getMaximumGeneration();
        }
    }

    /**
     * Perform sanity checks on the properties of the next file in the sequence, to ensure that we maintain various
     * invariants.
     *
     * @param permitGaps                if gaps are permitted in sequence number
     * @param previousSequenceNumber    the sequence number of the previous file
     * @param previousMinimumGeneration the minimum generation of the previous file
     * @param previousMaximumGeneration the maximum generation of the previous file
     * @param previousOrigin            the origin round of the previous file
     * @param previousTimestamp         the timestamp of the previous file
     * @param descriptor                the descriptor of the next file
     * @throws IllegalStateException if any of the required invariants are violated by the next file
     */
    public static void fileSanityChecks(
            final boolean permitGaps,
            final long previousSequenceNumber,
            final long previousMinimumGeneration,
            final long previousMaximumGeneration,
            final long previousOrigin,
            @NonNull final Instant previousTimestamp,
            @NonNull final PreconsensusEventFile descriptor) {

        // Sequence number should always monotonically increase
        if (!permitGaps && previousSequenceNumber + 1 != descriptor.getSequenceNumber()) {
            throw new IllegalStateException("Gap in preconsensus event files detected! Previous sequence number was "
                    + previousSequenceNumber + ", next sequence number is "
                    + descriptor.getSequenceNumber());
        }

        // Minimum generation may never decrease
        if (descriptor.getMinimumGeneration() < previousMinimumGeneration) {
            throw new IllegalStateException("Minimum generation must never decrease, file " + descriptor.getPath()
                    + " has a minimum generation that is less than the previous minimum generation of "
                    + previousMinimumGeneration);
        }

        // Maximum generation may never decrease
        if (descriptor.getMaximumGeneration() < previousMaximumGeneration) {
            throw new IllegalStateException("Maximum generation must never decrease, file " + descriptor.getPath()
                    + " has a maximum generation that is less than the previous maximum generation of "
                    + previousMaximumGeneration);
        }

        // Timestamp must never decrease
        if (descriptor.getTimestamp().isBefore(previousTimestamp)) {
            throw new IllegalStateException("Timestamp must never decrease, file " + descriptor.getPath()
                    + " has a timestamp that is less than the previous timestamp of "
                    + previousTimestamp);
        }

        // Origin round must never decrease
        if (descriptor.getOrigin() < previousOrigin) {
            throw new IllegalStateException("Origin round must never decrease, file " + descriptor.getPath()
                    + " has an origin round that is less than the previous origin round of "
                    + previousOrigin);
        }
    }

    /**
     * Get the directory where event files are stored. If that directory doesn't exist, create it.
     *
     * @param platformContext the platform context for this node
     * @param selfId          the ID of this node
     * @return the directory where event files are stored
     * @throws IOException if an error occurs while creating the directory
     */
    @NonNull
    public static Path getDatabaseDirectory(
            @NonNull final PlatformContext platformContext, @NonNull final NodeId selfId) throws IOException {

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);

        final Path savedStateDirectory = stateConfig.savedStateDirectory();
        final Path databaseDirectory = savedStateDirectory
                .resolve(preconsensusEventStreamConfig.databaseDirectory())
                .resolve(Long.toString(selfId.id()));

        if (!Files.exists(databaseDirectory)) {
            Files.createDirectories(databaseDirectory);
        }

        return databaseDirectory;
    }

    /**
     * Get the initial origin round for the PCES files. This is the origin round of the first file that is compatible
     * with the starting round, or the starting round itself if no file has an original that is compatible with the
     * starting round.
     *
     * @param files         the files that have been read from disk
     * @param startingRound the round the system is starting from
     * @return the initial origin round
     */
    public static long getInitialOrigin(@NonNull final PcesFileTracker files, final long startingRound) {
        final int firstRelevantFileIndex = files.getFirstRelevantFileIndex(startingRound);
        if (firstRelevantFileIndex >= 0) {
            // if there is a file with an origin that is compatible with the starting round, use that origin
            return files.getFile(firstRelevantFileIndex).getOrigin();
        } else {
            // if there is no file with an origin that is compatible with the starting round, use the starting round
            // itself as the origin.
            return startingRound;
        }
    }
}