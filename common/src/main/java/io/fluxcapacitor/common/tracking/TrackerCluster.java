/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.common.tracking;

import lombok.Getter;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;

@Value
public class TrackerCluster {

    public static final int[] emptyRange = new int[]{0, 0};

    @Getter
    int segments;
    Map<Tracker, Segment> trackers;
    Map<Tracker, Instant> activeTrackers;

    public TrackerCluster(int segments) {
        this(segments, Collections.emptyMap(), Collections.emptyMap());
    }

    private TrackerCluster(int segments, Map<Tracker, Segment> trackers, Map<Tracker, Instant> activeTrackers) {
        this.segments = segments;
        this.trackers = trackers;
        this.activeTrackers = activeTrackers;
    }

    public TrackerCluster withActiveTracker(Tracker tracker) {
        if (!contains(tracker) || isActive(tracker)) {
            return withWaitingTracker(tracker).withActiveTracker(tracker);
        }
        Map<Tracker, Instant> activeTrackers = new HashMap<>(this.activeTrackers);
        activeTrackers.putIfAbsent(tracker, Instant.now());
        return new TrackerCluster(segments, trackers, activeTrackers);
    }

    public TrackerCluster withWaitingTracker(Tracker tracker) {
        SortedSet<Tracker> trackers = concat(this.trackers.keySet().stream().filter(
                c -> !c.equals(tracker)), Stream.of(tracker)).collect(toCollection(TreeSet::new));
        Map<Tracker, Instant> activeTrackers = new HashMap<>(this.activeTrackers);
        activeTrackers.remove(tracker);
        return recalculate(trackers, activeTrackers);
    }

    public TrackerCluster withoutTracker(Tracker tracker) {
        if (!contains(tracker)) {
            return this;
        }
        SortedSet<Tracker> trackers =
                this.trackers.keySet().stream().filter(c -> !c.equals(tracker)).collect(toCollection(TreeSet::new));
        Map<Tracker, Instant> activeTrackers = new HashMap<>(this.activeTrackers);
        activeTrackers.remove(tracker);
        return recalculate(trackers, activeTrackers);
    }

    public TrackerCluster purgeTrackers(Predicate<Tracker> predicate) {
        TrackerCluster result = this;
        for (Tracker tracker : trackers.keySet()) {
            if (predicate.test(tracker)) {
                result = result.withoutTracker(tracker);
            }
        }
        return result;
    }

    public TrackerCluster purgeCeasedTrackers(Instant threshold) {
        return purgeTrackers(t -> ofNullable(activeTrackers.get(t)).filter(p -> p.isBefore(threshold)).isPresent());
    }

    public Optional<Duration> getProcessingDuration(Tracker tracker) {
        return ofNullable(activeTrackers.get(tracker)).map(t -> Duration.between(t, Instant.now()));
    }

    public int[] getSegment(Tracker tracker) {
        return ofNullable(trackers.get(tracker))
                .map(segment -> {
                    if (tracker.singleTracker()) {
                        return segment.contains(0) && segment.getLength() > 0 ? new int[]{0, segments} : emptyRange;
                    }
                    return segment.asArray();
                }).orElse(null);
    }

    public boolean contains(Tracker tracker) {
        return trackers.containsKey(tracker);
    }

    public boolean isActive(Tracker tracker) {
        return activeTrackers.containsKey(tracker);
    }

    public Set<Tracker> getTrackers() {
        return Collections.unmodifiableSet(trackers.keySet());
    }

    public boolean isEmpty() {
        return trackers.isEmpty();
    }

    private TrackerCluster recalculate(SortedSet<Tracker> trackers, Map<Tracker, Instant> activeTrackers) {
        if (trackers.isEmpty()) {
            return new TrackerCluster(segments);
        }
        Map<Tracker, Segment> constraints = activeTrackers.keySet().stream()
                .filter(c -> !Objects.equals(this.trackers.get(c), new Segment(0, 0)))
                .collect(toMap(identity(), this.trackers::get));

        TreeSet<Integer> grid = createGrid(segments, trackers.size(), constraints.values());

        removeGridPoints(grid, trackers.size(), concat(Stream.of(0, segments), getIntersections(constraints.values())
                .stream()).collect(toSet()));

        List<Segment> trackerSegments = toSegments(grid);

        Map<Tracker, Segment> adjustedConstraints = adjustConstraints(trackerSegments, constraints);
        Map<Tracker, Segment> listeningTrackers = new TreeMap<>(adjustedConstraints);
        List<Segment> remainingSegments = new ArrayList<>(trackerSegments);
        remainingSegments.removeAll(listeningTrackers.values());
        trackers.stream().filter(t -> !adjustedConstraints.containsKey(t)).forEach(t -> listeningTrackers
                .put(t, remainingSegments.isEmpty() ? new Segment(0, 0) : remainingSegments.remove(0)));

        return new TrackerCluster(segments, listeningTrackers, activeTrackers);
    }

    private Map<Tracker, Segment> adjustConstraints(List<Segment> segments, Map<Tracker, Segment> constraints) {
        Map<Tracker, Segment> result = new HashMap<>();
        constraints.forEach(
                ((tracker, segment) -> segments.stream().filter(newSegment -> newSegment.contains(segment.getStart()))
                        .findAny().ifPresent(newSegment -> result.put(tracker, newSegment))));
        return result;
    }

    //create a grid for tracker segments that includes the given constraints

    private static TreeSet<Integer> createGrid(int segments, int trackers, Collection<Segment> constraints) {
        //create evenly spaced grid for Tracker  segments. E.g. size 4 -> [0, 25, 50, 75, 100]
        TreeSet<Integer> result = new TreeSet<>();
        int quotient = segments / trackers, remainder = segments % trackers;
        result.add(0);
        int last = 0;
        for (int i = 1; i <= trackers; i++) {
            result.add(last += trackers - i < remainder ? quotient + 1 : quotient);
        }

        //add constraints to grid. E.g. constraints: [0,33], [40,60] & [80,100] -> [0, 25, 33, 40, 50, 60, 75, 80, 100]
        constraints.forEach(segment -> Collections.addAll(result, segment.getStart(), segment.getEnd()));

        //remove all grid points between constraints. E.g. previous example becomes -> [0, 33, 40, 60, 75, 80, 100]
        result.removeIf(point -> constraints.stream()
                .anyMatch(segment -> point > segment.getStart() && point < segment.getEnd()));

        return result;
    }

    //remove grid points (except if constraints) until the grid size = number of trackers + 1

    private static void removeGridPoints(TreeSet<Integer> grid, int trackers, Set<Integer> constraints) {
        while (grid.size() > trackers + 1) {
            List<Segment> segments = new ArrayList<>();
            Iterator<Integer> iterator = grid.iterator();
            int start = iterator.next();
            while (iterator.hasNext()) {
                int end = iterator.next();
                segments.add(new Segment(start, end));
                start = end;
            }
            segments.stream().sorted(Comparator.comparing(Segment::getLength)).map(Segment::getEnd)
                    .filter(end -> !constraints.contains(end))
                    .findFirst().ifPresent(grid::remove);
        }
    }

    //convert grid to a list of touching segments

    private static List<Segment> toSegments(SortedSet<Integer> grid) {
        List<Segment> result = new ArrayList<>();
        Iterator<Integer> iterator = grid.iterator();
        int next = iterator.next();
        while (iterator.hasNext()) {
            result.add(new Segment(next, next = iterator.next()));
        }
        return result;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static Set<Integer> getIntersections(Collection<Segment> segments) {
        Set<Integer> result = new HashSet<>();
        segments.stream().sorted(Comparator.comparing(Segment::getStart)).reduce((a, b) -> {
            if (a.getEnd() == b.getStart()) {
                result.add(a.getEnd());
            }
            return b;
        });
        return result;
    }

    @Value
    static class Segment {
        int start, end;

        public Segment(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getLength() {
            return end - start;
        }

        public boolean contains(int point) {
            return point >= start && point < end;
        }

        public int[] asArray() {
            return new int[]{start, end};
        }
    }
}
