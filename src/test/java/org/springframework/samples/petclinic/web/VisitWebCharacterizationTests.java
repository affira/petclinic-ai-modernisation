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
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Characterization tests for the visit pages: add a visit, list visits.
 * <p>
 * Seed data used: pet 7 is Samantha, owned by Jean Coleman (owner 6), with two visits.
 */
class VisitWebCharacterizationTests extends AbstractWebCharacterizationTests {

    private static final String VISIT_FORM = "pets/createOrUpdateVisitForm";

    private List<Map<String, Object>> visitRows(int petId, String description) {
        return this.jdbc.sql("SELECT pet_id, visit_date, description FROM visits WHERE pet_id = ? AND description = ?")
            .params(petId, description).query().listOfRows();
    }

    private int countVisits() {
        return this.jdbc.sql("SELECT COUNT(*) FROM visits").query(Integer.class).single();
    }

    @Nested
    class AddVisit {

        @Test
        void newVisitFormShowsPetAndDefaultsDateToToday() throws Exception {
            MvcResult result = mockMvc.perform(get("/owners/6/pets/7/visits/new"))
                .andExpect(status().isOk())
                .andExpect(view().name(VISIT_FORM))
                .andExpect(model().attribute("visit", allOf(
                    hasProperty("new", is(true)),
                    hasProperty("date", is(LocalDate.now())),
                    hasProperty("pet", hasProperty("name", is("Samantha"))))))
                .andReturn();
            assertForwardedJspExists(result);
        }

        @Test
        void validVisitIsSavedAndRedirectsToOwner() throws Exception {
            mockMvc.perform(post("/owners/6/pets/7/visits/new")
                    .param("date", "2026/01/02")
                    .param("description", "annual checkup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/owners/6"));

            assertThat(visitRows(7, "annual checkup")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("VISIT_DATE", Date.valueOf(LocalDate.of(2026, 1, 2))));
        }

        @Test
        void visitWithoutDateParameterIsSavedWithTodaysDate() throws Exception {
            // Visit's constructor sets date = LocalDate.now()
            mockMvc.perform(post("/owners/6/pets/7/visits/new")
                    .param("description", "no date given"))
                .andExpect(redirectedUrl("/owners/6"));

            assertThat(visitRows(7, "no date given")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("VISIT_DATE", Date.valueOf(LocalDate.now())));
        }

        @Test
        void emptyDescriptionIsRejectedAndNothingIsSaved() throws Exception {
            int before = countVisits();
            mockMvc.perform(post("/owners/6/pets/7/visits/new")
                    .param("date", "2026/01/02")
                    .param("description", ""))
                .andExpect(status().isOk())
                .andExpect(view().name(VISIT_FORM))
                .andExpect(model().attributeErrorCount("visit", 1))
                .andExpect(model().attributeHasFieldErrorCode("visit", "description", "NotEmpty"));

            assertThat(countVisits()).isEqualTo(before);
        }

        @Test
        void isoDateFormatIsAlsoAccepted() throws Exception {
            // Visit.date declares @DateTimeFormat(pattern = "yyyy/MM/dd"), yet an ISO date binds as well
            mockMvc.perform(post("/owners/6/pets/7/visits/new")
                    .param("date", "2026-01-02")
                    .param("description", "iso date"))
                .andExpect(redirectedUrl("/owners/6"));

            assertThat(visitRows(7, "iso date")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("VISIT_DATE", Date.valueOf(LocalDate.of(2026, 1, 2))));
        }

        @Test
        void unparseableDateIsATypeMismatch() throws Exception {
            mockMvc.perform(post("/owners/6/pets/7/visits/new")
                    .param("date", "02/01/2026")
                    .param("description", "annual checkup"))
                .andExpect(view().name(VISIT_FORM))
                .andExpect(model().attributeHasFieldErrorCode("visit", "date", "typeMismatch"));
        }

        @Test
        void ownerIdInUrlIsNotCheckedAgainstThePet() throws Exception {
            // Pet 7 belongs to owner 6, but posting under owner 1 still saves the visit for pet 7,
            // then redirects to owner 1.
            mockMvc.perform(post("/owners/1/pets/7/visits/new")
                    .param("date", "2026/01/02")
                    .param("description", "wrong owner in url"))
                .andExpect(redirectedUrl("/owners/1"));

            assertThat(visitRows(7, "wrong owner in url")).hasSize(1);
        }

        @Test
        void unknownPetShowsGenericErrorPage() throws Exception {
            // JPA em.find returns null for an unknown pet, so VisitController.loadPetWithVisit fails with a
            // NullPointerException, which SimpleMappingExceptionResolver turns into exception.jsp with status 200.
            mockMvc.perform(get("/owners/6/pets/9999/visits/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("exception"))
                .andExpect(model().attribute("exception", instanceOf(NullPointerException.class)));
        }
    }

    @Nested
    class ListVisits {

        @Test
        void visitListPutsPetsVisitsInModelButForwardsToAJspThatDoesNotExist() throws Exception {
            // KNOWN BUG (docs/ARCHITECTURE.md, section 7.4): the controller returns view "visitList",
            // but src/main/webapp/WEB-INF/jsp/visitList.jsp does not exist, so the real server answers 404.
            MvcResult result = mockMvc.perform(get("/owners/6/pets/7/visits"))
                .andExpect(status().isOk())
                .andExpect(view().name("visitList"))
                .andExpect(model().attribute("visits", hasSize(2)))
                .andExpect(forwardedUrl("/WEB-INF/jsp/visitList.jsp"))
                .andReturn();

            assertThat(jspExists(result.getResponse().getForwardedUrl())).isFalse();
        }
    }
}
