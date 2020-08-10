package io.fluxcapacitor.javaclient.test;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.function.Predicate;

public class GivenWhenThenUtils {
    public static <T> Matcher<T> toMatcher(Predicate<T> predicate) {
        return new TypeSafeMatcher<T>() {
            @Override
            protected boolean matchesSafely(T t) {
                return predicate.test(t);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Predicate matcher");
            }
        };
    }
}
