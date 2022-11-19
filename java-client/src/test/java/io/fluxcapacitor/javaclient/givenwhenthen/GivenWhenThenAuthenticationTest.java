/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.AbstractUserProvider;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.MockUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

public class GivenWhenThenAuthenticationTest {

    private MockUser user = new MockUser("get", "create");
    private final TestFixture testFixture = TestFixture.create(
            DefaultFluxCapacitor.builder().registerUserSupplier(new MockUserProvider()), new MockHandler(), new MockSystemHandler());

    @Test
    void testAuthorizedQuery() {
        testFixture.whenQuery(new Get()).expectResult("succes");
    }

    @Test
    void testAuthorizedQueryAsSystem() {
        user = new MockUser("get", "system");
        testFixture.whenQuery(new Get()).expectResult("hi system");
    }

    @Test
    void testUnauthorizedQuery() {
        user = new MockUser();
        testFixture.whenQuery(new Get()).expectExceptionalResult(UnauthorizedException.class);
    }

    @Test
    void testAuthorizedCommand() {
        testFixture.whenCommand(new Create()).expectSuccessfulResult();
    }

    @Test
    void testUnauthorizedCreate() {
        user = new MockUser();
        testFixture.whenCommand(new Create()).expectExceptionalResult(UnauthorizedException.class);
    }

    @Test
    void testAuthorizedModify() {
        user = new MockUser("modify");
        testFixture.whenCommand(new Update()).expectSuccessfulResult();
    }

    @Test
    void testUnauthorizedModify() {
        testFixture.whenCommand(new Update()).expectExceptionalResult(UnauthorizedException.class);
    }

    @Test
    void testWhenCommandByUser() {
        testFixture.whenCommandByUser(new Update(), new MockUser("modify")).expectSuccessfulResult();
    }

    @Test
    void testAuthorizedModifyAsSystem() {
        user = new MockUser("modify", "system");
        testFixture.whenCommand(new Update()).expectSuccessfulResult();
    }

    @Test
    void testUnauthorizedModifyAsSystemAdmin() {
        user = new MockUser("modify", "system", "admin");
        testFixture.whenCommand(new Update()).expectExceptionalResult(Exception.class);
    }

    @Test
    void testAuthorizedDelete() {
        user = new MockUser("delete");
        testFixture.whenCommand(new Delete()).expectSuccessfulResult();
    }

    @Test
    void testUnauthorizedDelete1() {
        testFixture.whenCommand(new Delete()).expectExceptionalResult(UnauthorizedException.class);
    }

    @Test
    void testUnauthorizedDelete2() {
        user = new MockUser("modify");
        testFixture.whenCommand(new Delete()).expectExceptionalResult(UnauthorizedException.class);
    }

    @Test
    void testUnauthorizedIfForbiddenRoleIsPresent() {
        user = new MockUser("create", "admin");
        testFixture.whenCommand(new Create()).expectExceptionalResult(UnauthorizedException.class);
    }

    @ForbidsRole("system")
    private static class MockHandler {
        @HandleQuery
        String handle(Get query) {
            return "succes";
        }

        @HandleCommand
        void handle(Create command) {
        }

        @HandleCommand
        void handle(Update command) {
        }

        @HandleCommand
        void handle(Delete command) {
        }
    }

    @RequiresRole("system")
    private static class MockSystemHandler {
        @HandleQuery
        String handle(Get query) {
            return "hi system";
        }

        @RequiresRole("!admin")
        @HandleCommand
        void handle(Update command) {
        }
    }

    @Value
    @RequiresRole("get")
    private static class Get {

    }

    @Value
    @RequiresRole({"create", "!admin"})
    private static class Create {

    }

    @Value
    private static class Update implements Modify {

    }

    @Value
    @MockRequiresRole(MockRole.delete)
    private static class Delete implements Modify {

    }

    @MockRequiresRole(MockRole.modify)
    public interface Modify {
    }

    @Target(TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Inherited
    @RequiresRole
    public @interface MockRequiresRole {
        MockRole[] value() default {};
    }

    public enum MockRole {
        modify, delete
    }

    public class MockUserProvider extends AbstractUserProvider {
        public MockUserProvider() {
            super(MockUser.class);
        }

        @Override
        public User getActiveUser() {
            return user;
        }

        @Override
        public User getSystemUser() {
            return user;
        }
    }

}
