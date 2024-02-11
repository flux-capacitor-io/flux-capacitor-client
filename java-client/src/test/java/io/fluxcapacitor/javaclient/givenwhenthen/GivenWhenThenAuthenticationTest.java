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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.givenwhenthen.forbidsadmin.ForbidsAdminViaPackage;
import io.fluxcapacitor.javaclient.givenwhenthen.requiresadmin.RequiresAdminViaPackage;
import io.fluxcapacitor.javaclient.givenwhenthen.requiresadmin.subpackage.RequiresAdminViaParentPackage;
import io.fluxcapacitor.javaclient.givenwhenthen.requiresuser.RequiresAdminOverride;
import io.fluxcapacitor.javaclient.givenwhenthen.requiresuser.RequiresUserViaPackage;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.AbstractUserProvider;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsAnyRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.MockUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresAnyRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.Value;
import org.junit.jupiter.api.Nested;
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
            DefaultFluxCapacitor.builder().registerUserProvider(new MockUserProvider()), new MockHandler(),
            new RefdataHandler(), new MockSystemHandler());

    @Nested
    class PackageAnnotation {
        @Test
        void requiresAdmin() {
            testFixture.whenQuery(new RequiresAdminViaPackage()).expectExceptionalResult(UnauthorizedException.class);
        }

        @Test
        void requiresAdminSuccess() {
            user = new MockUser("admin");
            testFixture.whenQuery(new RequiresAdminViaPackage()).expectResult("success");
        }

        @Test
        void requiresUser() {
            user = null;
            testFixture.whenQuery(new RequiresUserViaPackage()).expectExceptionalResult(UnauthenticatedException.class);
        }

        @Test
        void requiresUserSuccess() {
            testFixture.whenQuery(new RequiresUserViaPackage()).expectResult("success");
        }

        @Test
        void requiresAdminOverride() {
            testFixture.whenQuery(new RequiresAdminOverride()).expectExceptionalResult(UnauthorizedException.class);
        }

        @Test
        void requiresAdminOverrideSuccess() {
            user = new MockUser("admin");
            testFixture.whenQuery(new RequiresAdminOverride()).expectResult("success");
        }

        @Test
        void forbidsAdminSuccess() {
            testFixture.whenQuery(new ForbidsAdminViaPackage()).expectResult("success");
        }

        @Test
        void forbidsAdmin() {
            user = new MockUser("admin");
            testFixture.whenQuery(new ForbidsAdminViaPackage()).expectExceptionalResult(UnauthorizedException.class);
        }

        @Nested
        class ViaParent {
            @Test
            void requiresAdminViaParent() {
                testFixture.whenQuery(new RequiresAdminViaParentPackage()).expectExceptionalResult(UnauthorizedException.class);
            }

            @Test
            void requiresAdminSuccessViaParent() {
                user = new MockUser("admin");
                testFixture.whenQuery(new RequiresAdminViaParentPackage()).expectResult("success");
            }
        }
    }

    @Test
    void testQueryThatRequiresNoAuthentication() {
        user = null;
        testFixture.whenQuery(new RequiresNoAuthentication()).expectResult("success");
    }

    @Test
    void testQueryThatRequiresNoRoles() {
        user = new MockUser();
        testFixture.whenQuery(new RequiresAuthentication()).expectResult("success");
    }

    @Test
    void testQueryThatRequiresUserButNoRolesFailsWithoutUser() {
        user = null;
        testFixture.whenQuery(new RequiresAuthentication()).expectExceptionalResult(UnauthenticatedException.class);
    }

    @Test
    void testAuthorizedQuery() {
        testFixture.whenQuery(new Get()).expectResult("success");
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
    void testUnauthorizedDeleteSelf() {
        testFixture.whenCommand(new DeleteSelf()).expectExceptionalResult(UnauthorizedException.class);
    }

    @Test
    void testUnauthorizedIfForbiddenRoleIsPresent() {
        user = new MockUser("create", "admin");
        testFixture.whenCommand(new Create()).expectExceptionalResult(UnauthorizedException.class);
    }
    
    private static class RefdataHandler {
        @HandleQuery
        String handle(RequiresNoAuthentication query) {
            return "success";
        }

        @HandleQuery
        String handle(RequiresAuthentication query) {
            return "success";
        }
    }

    @ForbidsAnyRole("system")
    private static class MockHandler {
        @HandleQuery
        String handle(Get query) {
            return "success";
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

    @RequiresAnyRole("system")
    private static class MockSystemHandler {
        @HandleQuery
        String handle(Get query) {
            return "hi system";
        }

        @RequiresAnyRole("!admin")
        @HandleCommand
        void handle(Update command) {
        }
    }

    @Value
    private static class RequiresNoAuthentication {
    }

    @Value
    @RequiresUser
    private static class RequiresAuthentication {
    }

    @Value
    @RequiresAnyRole("get")
    private static class Get {

    }

    @Value
    @RequiresAnyRole({"create", "!admin"})
    private static class Create {

    }

    @Value
    private static class Update implements Modify {

    }

    @Value
    @MockRequiresRole(MockRole.delete)
    private static class Delete implements Modify {

    }

    @Value
    @MockRequiresRole(MockRole.delete)
    private static class DeleteSelf {
        @HandleCommand
        void handle() {
        }
    }

    @MockRequiresRole(MockRole.modify)
    public interface Modify {
    }

    @Target(TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Inherited
    @RequiresAnyRole
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
