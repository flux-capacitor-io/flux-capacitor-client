package io.fluxcapacitor.javaclient.givenwhenthen;


import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest
@MethodSource("getParameters")
public @interface TestWithParameters {
}