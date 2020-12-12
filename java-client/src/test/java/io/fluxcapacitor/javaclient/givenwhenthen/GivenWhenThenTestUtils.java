package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
import io.fluxcapacitor.javaclient.test.AbstractTestFixture;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

public class GivenWhenThenTestUtils {

    @SafeVarargs
    public static Stream<Arguments> cartesianProduct(Stream<Arguments>... argumentSet) {
        return stream(argumentSet).map(s -> s.collect(toList())).reduce((a, b) -> a.stream()
                .flatMap(ai -> b.stream().map(bi ->
                        concat(stream(ai.get()), stream(bi.get())).collect(toList())).map(Arguments::of))
                .collect(toList())).orElse(emptyList()).stream();
    }

    public static AggregateRepository getRepo(AbstractTestFixture testFixture) {
        return testFixture.getFluxCapacitor().aggregateRepository();
    }
}
