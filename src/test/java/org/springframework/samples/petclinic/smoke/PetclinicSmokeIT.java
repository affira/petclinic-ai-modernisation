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
package org.springframework.samples.petclinic.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Smoke tests against the packaged WAR running in a real servlet container.
 * <p>
 * Maven starts the WAR in Jetty in the {@code pre-integration-test} phase and stops it afterwards (see the
 * {@code jetty-maven-plugin} executions in {@code pom.xml}); Failsafe runs this class in between. Unlike the MockMvc
 * tests, these requests go through the container and the JSPs are really rendered, so they catch broken views, missing
 * static resources and container incompatibilities. They check that pages work, not every detail of behaviour (the
 * characterization tests do that).
 * <p>
 * The server is started once per build with fresh seed data, and these tests do write to it, so each write uses its
 * own unique names.
 */
class PetclinicSmokeIT {

    private static final String BASE_URL = System.getProperty("petclinic.baseUrl", "http://localhost:9967");

    /**
     * Text that only appears when something went wrong: the app's error page, Jetty's own error page, or a JSP or
     * servlet failure. (Not plain "Exception": the menu's tooltip on every page contains "RuntimeException".)
     */
    private static final String[] ERROR_MARKERS =
        {"Something happened...", "HTTP ERROR", "JasperException", "ServletException"};

    private static final Pattern LOCAL_ASSET =
        Pattern.compile("<(?:link|script|img)\\b[^>]*?\\b(?:href|src)=\"(/[^\"]*)\"");

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return this.client.send(HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, Map<String, String> form) throws IOException, InterruptedException {
        String body = form.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        return this.client.send(HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** GETs a page, checks it rendered without errors, checks that its CSS/JS/images load, and returns the HTML. */
    private String page(String path, String... expectedTexts) throws IOException, InterruptedException {
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).as("status of %s", path).isEqualTo(200);
        String html = response.body();
        assertThat(html).as("page %s", path).contains(expectedTexts).doesNotContain(ERROR_MARKERS);
        assertLocalAssetsLoad(path, html);
        return html;
    }

    private void assertLocalAssetsLoad(String path, String html) throws IOException, InterruptedException {
        Set<String> assets = new LinkedHashSet<>();
        Matcher matcher = LOCAL_ASSET.matcher(html);
        while (matcher.find()) {
            assets.add(matcher.group(1));
        }
        assertThat(assets).as("assets referenced by %s", path).isNotEmpty();
        for (String asset : assets) {
            assertThat(get(asset).statusCode()).as("asset %s referenced by %s", asset, path).isEqualTo(200);
        }
    }

    private static String location(HttpResponse<?> response) {
        return response.headers().firstValue("Location").map(l -> URI.create(l).getPath()).orElse(null);
    }

    @Test
    void welcomePage() throws Exception {
        page("/", "Welcome");
    }

    @Test
    void findOwnersPage() throws Exception {
        page("/owners/find", "Find Owners", "Find Owner");
    }

    @Test
    void ownersListShowsAllOwners() throws Exception {
        page("/owners?lastName=", "ownersTable", "George Franklin", "Carlos Estaban");
    }

    @Test
    void ownerDetailsShowsPetsAndVisits() throws Exception {
        page("/owners/6", "Owner Information", "Jean Coleman", "Pets and Visits", "Samantha", "Max", "spayed",
            "rabies shot");
    }

    @Test
    void ownerForms() throws Exception {
        page("/owners/new", "Add Owner");
        page("/owners/1/edit", "Update Owner", "Franklin");
    }

    @Test
    void createOwnerThenShowIt() throws Exception {
        HttpResponse<String> created = postForm("/owners/new", Map.of(
            "firstName", "Smoke", "lastName", "Owner" + System.nanoTime(), "address", "1 Test Street",
            "city", "Testville", "telephone", "0123456789"));
        assertThat(created.statusCode()).isEqualTo(302);
        String ownerPath = location(created);
        assertThat(ownerPath).matches("/owners/\\d+");
        page(ownerPath, "Owner Information", "Testville");
    }

    @Test
    void invalidOwnerRedisplaysFormWithMessages() throws Exception {
        HttpResponse<String> response = postForm("/owners/new", Map.of(
            "firstName", "", "lastName", "X", "address", "a", "city", "b", "telephone", "abc"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Add Owner", "must not be empty", "numeric value out of bounds");
    }

    @Test
    void petForms() throws Exception {
        page("/owners/1/pets/new", "Add Pet", "hamster");
        page("/owners/1/pets/1/edit", "Update Pet", "Leo");
    }

    @Test
    void addPetThenShowOwner() throws Exception {
        String name = "Smokey" + System.nanoTime();
        HttpResponse<String> created = postForm("/owners/2/pets/new", Map.of(
            "name", name, "birthDate", "2020/01/15", "type", "dog"));
        assertThat(created.statusCode()).isEqualTo(302);
        assertThat(location(created)).isEqualTo("/owners/2");
        page("/owners/2", name);
    }

    @Test
    void visitForm() throws Exception {
        page("/owners/6/pets/7/visits/new", "Add Visit", "Samantha", "spayed");
    }

    @Test
    void addVisitThenShowOwner() throws Exception {
        String description = "smoke visit " + System.nanoTime();
        HttpResponse<String> created = postForm("/owners/6/pets/8/visits/new", Map.of(
            "date", "2026/01/02", "description", description));
        assertThat(created.statusCode()).isEqualTo(302);
        assertThat(location(created)).isEqualTo("/owners/6");
        page("/owners/6", description);
    }

    @Test
    void vetsPageAndFeeds() throws Exception {
        page("/vets", "Veterinarians", "vetsTable", "James Carter");

        HttpResponse<String> json = get("/vets.json");
        assertThat(json.statusCode()).isEqualTo(200);
        assertThat(json.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("application/json"));
        assertThat(json.body()).contains("\"vetList\"", "Carter");

        HttpResponse<String> xml = get("/vets.xml");
        assertThat(xml.statusCode()).isEqualTo(200);
        assertThat(xml.body()).contains("<vets>", "Carter");
    }

    @Test
    void errorPageIsRenderedWithStatus200() throws Exception {
        // Current behaviour (docs/ARCHITECTURE.md 7.4): SimpleMappingExceptionResolver sets no error status
        HttpResponse<String> response = get("/oups");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Something happened...");
    }

    @Test
    void visitListReturns404BecauseItsJspIsMissing() throws Exception {
        // KNOWN BUG (docs/ARCHITECTURE.md 7.4): VisitController.showVisits returns view "visitList", which has no JSP
        assertThat(get("/owners/6/pets/7/visits").statusCode()).isEqualTo(404);
    }
}
