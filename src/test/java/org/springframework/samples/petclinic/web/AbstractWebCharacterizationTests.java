/*
 * Copyright 2002-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for characterization tests of the web layer.
 * <p>
 * Unlike the existing controller tests (which mock {@code ClinicService}), these tests load the same Spring XML
 * configuration as the running application, split into the same root and servlet contexts as
 * {@code PetclinicInitializer}, with the default {@code jpa} profile, against the H2 test database seeded from
 * {@code db/h2/data.sql}. They pin down <em>current</em> behaviour, including quirks and known bugs, so later
 * refactoring can be checked against it. A test that documents a known bug or quirk says so in its name or comment;
 * don't "fix" such an assertion without deciding to change the behaviour.
 * <p>
 * <strong>No test-managed transaction, on purpose.</strong> In production every service call runs in its own
 * transaction, and the entities the controllers work with are detached. Wrapping a whole test in one transaction keeps
 * them managed, which triggers JPA cascades that never happen for real (for example "Detached entity passed to
 * persist" when editing a pet). So requests here commit for real, and the database is rebuilt from the seed scripts
 * after every test. Assertions on stored data read the database directly with SQL.
 * <p>
 * MockMvc does not render JSPs. Where a view is resolved, tests check the forwarded JSP path and, via
 * {@link #assertForwardedJspExists(MvcResult)}, that the JSP file is actually present in {@code src/main/webapp}.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextHierarchy({
    // Loading all three files into one flat context does not work (the JPA repositories then fail to get their
    // EntityManager), so the hierarchy is needed, not just faithful.
    @ContextConfiguration(name = "root", locations = {
        "classpath:spring/business-config.xml",
        "classpath:spring/tools-config.xml"}),
    @ContextConfiguration(name = "servlet", locations = "classpath:spring/mvc-core-config.xml")
})
@ActiveProfiles("jpa")
@Sql(scripts = {"classpath:db/h2/schema.sql", "classpath:db/h2/data.sql"},
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
abstract class AbstractWebCharacterizationTests {

    private static final Path WEBAPP_ROOT = Paths.get("src/main/webapp");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DataSource dataSource;

    protected MockMvc mockMvc;

    protected JdbcClient jdbc;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        this.jdbc = JdbcClient.create(this.dataSource);
    }

    protected static void assertForwardedJspExists(MvcResult result) {
        String forwardedUrl = result.getResponse().getForwardedUrl();
        assertThat(forwardedUrl).as("forwarded URL").isNotNull();
        assertThat(jspFile(forwardedUrl)).as("JSP file for %s", forwardedUrl).exists();
    }

    protected static Path jspFile(String forwardedUrl) {
        return WEBAPP_ROOT.resolve(forwardedUrl.substring(1));
    }

    protected static boolean jspExists(String forwardedUrl) {
        return Files.exists(jspFile(forwardedUrl));
    }

    protected static int idFromRedirect(MvcResult result, String prefix) {
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(prefix);
        return Integer.parseInt(location.substring(prefix.length()));
    }

    /**
     * All error codes on one field. Use this instead of {@code attributeHasFieldErrorCode} when a field can carry
     * several errors: the order of Bean Validation errors is not guaranteed, and that matcher only checks the first.
     */
    protected static List<String> fieldErrorCodes(MvcResult result, String attribute, String field) {
        BindingResult bindingResult = (BindingResult) result.getModelAndView().getModel()
            .get(BindingResult.MODEL_KEY_PREFIX + attribute);
        assertThat(bindingResult).as("binding result for %s", attribute).isNotNull();
        return bindingResult.getFieldErrors(field).stream().map(FieldError::getCode).toList();
    }
}
