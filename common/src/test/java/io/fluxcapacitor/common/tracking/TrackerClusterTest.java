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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TrackerClusterTest {

    private final int maxSegment = 100;
    private TrackerCluster subject = new TrackerCluster(maxSegment);

    @Test
    public void testSingleClientCoversAllSegments() {
        Tracker client = asTracker("client");
        subject = subject.withWaitingTracker(client);
        assertArrayEquals(new int[]{0, maxSegment}, subject.getSegment(client));
        subject = subject.withActiveTracker(client);
        assertArrayEquals(new int[]{0, maxSegment}, subject.getSegment(client));
    }

    private Tracker asTracker(String name) {
        return SimpleTracker.builder().consumerName(name).build();
    }

    @Test
    public void testEqualSegmentsWithMultipleClients() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2"), client3 = asTracker("client3"), client4 = asTracker("client4");
        subject = subject.withWaitingTracker(client1).withWaitingTracker(client2).withWaitingTracker(client3)
                .withWaitingTracker(client4);
        assertArrayEquals(new int[]{0, 25}, subject.getSegment(client1));
        assertArrayEquals(new int[]{25, 50}, subject.getSegment(client2));
        assertArrayEquals(new int[]{50, 75}, subject.getSegment(client3));
        assertArrayEquals(new int[]{75, maxSegment}, subject.getSegment(client4));
        assertAllSegmentsCovered(subject);
    }

    @Test
    public void testOddNumberOfClients() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2"), client3 = asTracker("client3");
        subject = subject.withWaitingTracker(client1).withWaitingTracker(client2).withWaitingTracker(client3);
        assertAllSegmentsCovered(subject);
    }

    @Test
    public void testSegmentOfProcessingClientIsFixed() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2"), client3 = asTracker("client3"), client4 = asTracker("client4"), client5 = asTracker("client5");
        subject = subject.withWaitingTracker(client1).withWaitingTracker(client2).withWaitingTracker(client3)
                .withWaitingTracker(client4).withWaitingTracker(client5);
        subject = subject.withActiveTracker(client5).withoutTracker(client1);
        assertAllSegmentsCovered(subject);
    }

    @Test
    public void testAddingClientsInReverseAlphabeticalOrder() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2");
        subject = subject.withWaitingTracker(client2).withWaitingTracker(client1);
        assertArrayEquals(new int[]{0, 50}, subject.getSegment(client1));
        assertArrayEquals(new int[]{50, maxSegment}, subject.getSegment(client2));

        subject = subject.withActiveTracker(client1);
        assertArrayEquals(new int[]{0, 50}, subject.getSegment(client1));
        assertArrayEquals(new int[]{50, maxSegment}, subject.getSegment(client2));

        subject = subject.withWaitingTracker(client1);
        assertArrayEquals(new int[]{0, 50}, subject.getSegment(client1));
        assertArrayEquals(new int[]{50, maxSegment}, subject.getSegment(client2));
        assertAllSegmentsCovered(subject);
    }

    @Test
    public void testAllIsWellWhenProcessingClientListensAgain() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2"), client3 = asTracker("client3"), client4 = asTracker("client4"), client5 = asTracker("client5");
        subject = subject.withWaitingTracker(client1).withWaitingTracker(client2).withWaitingTracker(client3)
                .withWaitingTracker(client4).withWaitingTracker(client5);
        subject = subject.withActiveTracker(client5).withoutTracker(client1).withWaitingTracker(client5)
                .withWaitingTracker(client1);
        assertEquals(5, subject.getTrackers().size());
        assertAllSegmentsCovered(subject);
    }

    @Test
    public void testAllIsWellWhenAllClientsAreProcessingAndClientIsAdded() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2"), client3 = asTracker("client3"), client4 = asTracker("client4"), client5 = asTracker("client5");
        subject = subject.withWaitingTracker(client1).withWaitingTracker(client2).withWaitingTracker(client3)
                .withWaitingTracker(client4);
        subject = subject.withActiveTracker(client1);
        subject = subject.withActiveTracker(client2).withActiveTracker(client3).withActiveTracker(client4);
        subject = subject.withWaitingTracker(client5);
        assertEquals(5, subject.getTrackers().size());
        assertAllSegmentsCovered(subject);
        assertArrayEquals(new int[]{0, 0}, subject.getSegment(client5));
    }

    @Test
    public void testAllIsWellWhenAllClientsAreProcessingAndClientIsAdded_reverseAlphabeticalOrder() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2");
        subject = subject.withWaitingTracker(client2).withActiveTracker(client2);
        subject = subject.withWaitingTracker(client1);
        assertArrayEquals(new int[]{0, maxSegment}, subject.getSegment(client2));
        assertArrayEquals(new int[]{0, 0}, subject.getSegment(client1));
    }

    @Test
    public void testSegmentsAreRedistributedWhenAllClientsProcessingAndExistingClientComesBack() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2");
        subject = subject.withWaitingTracker(client1).withActiveTracker(client1).withWaitingTracker(client2)
                .withActiveTracker(client2);
        assertArrayEquals(new int[]{0, maxSegment}, subject.getSegment(client1));
        assertArrayEquals(new int[]{0, 0}, subject.getSegment(client2));
        subject = subject.withWaitingTracker(client1);
        assertArrayEquals(new int[]{0, 50}, subject.getSegment(client1));
        assertArrayEquals(new int[]{50, maxSegment}, subject.getSegment(client2));
    }

    @Test
    public void testPurgingCeasedTrackers() {
        Tracker client1 = asTracker("client1"), client2 = asTracker("client2");
        subject = subject.withWaitingTracker(client1).withWaitingTracker(client2)
                .withActiveTracker(client2);
        assertSame(subject, subject.purgeCeasedTrackers(Instant.now().minusSeconds(1)));
        subject = subject.purgeCeasedTrackers(Instant.now().plusSeconds(1));
        assertEquals(singleton(client1), subject.getTrackers());
    }

    private void assertAllSegmentsCovered(TrackerCluster subject) {
        if (subject.getTrackers().isEmpty()) {
            return;
        }
        List<int[]> segments = new ArrayList<>();
        subject.getTrackers().forEach(client -> segments.add(subject.getSegment(client)));
        segments.sort(Comparator.<int[]>comparingInt(segment -> segment[0]).thenComparingInt(segment -> segment[1]));
        Iterator<int[]> iterator = segments.iterator();
        int[] next = iterator.next();
        assertEquals(0, next[0]);
        while (iterator.hasNext()) {
            assertEquals(next[1], (next = iterator.next())[0]);
        }
        assertEquals(maxSegment, next[1]);
    }
}