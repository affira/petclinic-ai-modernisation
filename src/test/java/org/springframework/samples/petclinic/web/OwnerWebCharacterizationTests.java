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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Characterization tests for the owners pages: find, list, show, create and edit.
 */
class OwnerWebCharacterizationTests extends AbstractWebCharacterizationTests {

    private static final String OWNER_FORM = "owners/createOrUpdateOwnerForm";

    private int countOwnersByLastName(String lastName) {
        return this.jdbc.sql("SELECT COUNT(*) FROM owners WHERE last_name = ?")
            .param(lastName).query(Integer.class).single();
    }

    private int countOwnersWithId(int id) {
        return this.jdbc.sql("SELECT COUNT(*) FROM owners WHERE id = ?").param(id).query(Integer.class).single();
    }

    private Map<String, Object> ownerRow(int id) {
        return this.jdbc.sql("SELECT first_name, last_name, address, city, telephone FROM owners WHERE id = ?")
            .param(id).query().singleRow();
    }

    @Nested
    class FindOwners {

        @Test
        void findFormShowsEmptyOwner() throws Exception {
            MvcResult result = mockMvc.perform(get("/owners/find"))
                .andExpect(status().isOk())
                .andExpect(view().name("owners/findOwners"))
                .andExpect(model().attribute("owner", hasProperty("lastName", nullValue())))
                .andExpect(forwardedUrl("/WEB-INF/jsp/owners/findOwners.jsp"))
                .andReturn();
            assertForwardedJspExists(result);
        }

        @Test
        void searchWithoutLastNameParameterListsAllTenOwners() throws Exception {
            MvcResult result = mockMvc.perform(get("/owners"))
                .andExpect(status().isOk())
                .andExpect(view().name("owners/ownersList"))
                .andExpect(model().attribute("selections", hasSize(10)))
                .andReturn();
            assertForwardedJspExists(result);
        }

        @Test
        void searchWithEmptyLastNameListsAllTenOwners() throws Exception {
            mockMvc.perform(get("/owners").param("lastName", ""))
                .andExpect(view().name("owners/ownersList"))
                .andExpect(model().attribute("selections", hasSize(10)));
        }

        @Test
        void searchMatchingSeveralOwnersShowsList() throws Exception {
            mockMvc.perform(get("/owners").param("lastName", "Davis"))
                .andExpect(status().isOk())
                .andExpect(view().name("owners/ownersList"))
                .andExpect(model().attribute("selections", hasSize(2)));
        }

        @Test
        void searchIsAPrefixMatch() throws Exception {
            // "Es" matches Escobito and Estaban
            mockMvc.perform(get("/owners").param("lastName", "Es"))
                .andExpect(view().name("owners/ownersList"))
                .andExpect(model().attribute("selections", hasSize(2)));
        }

        @Test
        void searchMatchingExactlyOneOwnerRedirectsToThatOwner() throws Exception {
            mockMvc.perform(get("/owners").param("lastName", "Franklin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/owners/1"));
        }

        @Test
        void searchIsCaseInsensitive() throws Exception {
            // Comes from the H2 column type VARCHAR_IGNORECASE (db/h2/schema.sql), not from the Java code
            mockMvc.perform(get("/owners").param("lastName", "franklin"))
                .andExpect(redirectedUrl("/owners/1"));
        }

        @Test
        void searchWithNoMatchShowsFindFormWithNotFoundError() throws Exception {
            mockMvc.perform(get("/owners").param("lastName", "Nobody"))
                .andExpect(status().isOk())
                .andExpect(view().name("owners/findOwners"))
                .andExpect(model().attributeHasFieldErrorCode("owner", "lastName", "notFound"));
        }

        @Test
        void percentSignInSearchIsTreatedAsWildcardAndMatchesEveryone() throws Exception {
            mockMvc.perform(get("/owners").param("lastName", "%"))
                .andExpect(view().name("owners/ownersList"))
                .andExpect(model().attribute("selections", hasSize(10)));
        }
    }

    @Nested
    class ShowOwner {

        @Test
        void showsOwnerWithPets() throws Exception {
            MvcResult result = mockMvc.perform(get("/owners/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("owners/ownerDetails"))
                .andExpect(model().attribute("owner", hasProperty("firstName", is("George"))))
                .andExpect(model().attribute("owner", hasProperty("lastName", is("Franklin"))))
                .andExpect(model().attribute("owner", hasProperty("telephone", is("6085551023"))))
                .andExpect(model().attribute("owner", hasProperty("pets", contains(hasProperty("name", is("Leo"))))))
                .andReturn();
            assertForwardedJspExists(result);
        }

        @Test
        void petsAreSortedByName() throws Exception {
            // Jean Coleman (6) has Samantha and Max in the seed data
            mockMvc.perform(get("/owners/6"))
                .andExpect(model().attribute("owner", hasProperty("pets", contains(
                    hasProperty("name", is("Max")), hasProperty("name", is("Samantha"))))));
        }

        @Test
        void unknownOwnerShowsGenericErrorPageWithStatus200() throws Exception {
            // SimpleMappingExceptionResolver (mvc-core-config.xml) renders exception.jsp without an error status
            MvcResult result = mockMvc.perform(get("/owners/9999"))
                .andExpect(status().isOk())
                .andExpect(view().name("exception"))
                .andExpect(model().attribute("exception", instanceOf(EmptyResultDataAccessException.class)))
                .andReturn();
            assertForwardedJspExists(result);
        }
    }

    @Nested
    class CreateOwner {

        @Test
        void creationFormShowsNewOwner() throws Exception {
            MvcResult result = mockMvc.perform(get("/owners/new"))
                .andExpect(status().isOk())
                .andExpect(view().name(OWNER_FORM))
                .andExpect(model().attribute("owner", hasProperty("new", is(true))))
                .andReturn();
            assertForwardedJspExists(result);
        }

        @Test
        void validOwnerIsSavedAndRedirectsToNewOwner() throws Exception {
            MvcResult result = mockMvc.perform(post("/owners/new")
                    .param("firstName", "Ada")
                    .param("lastName", "Characterize")
                    .param("address", "1 Test Street")
                    .param("city", "Testville")
                    .param("telephone", "0123456789"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

            int newId = idFromRedirect(result, "/owners/");
            assertThat(ownerRow(newId)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "FIRST_NAME", "Ada", "LAST_NAME", "Characterize", "ADDRESS", "1 Test Street",
                "CITY", "Testville", "TELEPHONE", "0123456789"));
        }

        @Test
        void allFieldsEmptyRedisplaysFormWithNotEmptyErrors() throws Exception {
            MvcResult result = mockMvc.perform(post("/owners/new")
                    .param("firstName", "")
                    .param("lastName", "")
                    .param("address", "")
                    .param("city", "")
                    .param("telephone", ""))
                .andExpect(status().isOk())
                .andExpect(view().name(OWNER_FORM))
                .andExpect(model().attributeHasFieldErrorCode("owner", "firstName", "NotEmpty"))
                .andExpect(model().attributeHasFieldErrorCode("owner", "lastName", "NotEmpty"))
                .andExpect(model().attributeHasFieldErrorCode("owner", "address", "NotEmpty"))
                .andExpect(model().attributeHasFieldErrorCode("owner", "city", "NotEmpty"))
                .andExpect(model().attributeErrorCount("owner", 6))
                .andReturn();
            // An empty telephone fails both @NotEmpty and @Digits
            assertThat(fieldErrorCodes(result, "owner", "telephone")).containsExactlyInAnyOrder("NotEmpty", "Digits");
            assertThat(countOwnersByLastName("")).isZero();
        }

        @Test
        void nonNumericTelephoneIsRejected() throws Exception {
            mockMvc.perform(post("/owners/new")
                    .param("firstName", "Ada")
                    .param("lastName", "Characterize")
                    .param("address", "1 Test Street")
                    .param("city", "Testville")
                    .param("telephone", "555-1234"))
                .andExpect(view().name(OWNER_FORM))
                .andExpect(model().attributeErrorCount("owner", 1))
                .andExpect(model().attributeHasFieldErrorCode("owner", "telephone", "Digits"));
            assertThat(countOwnersByLastName("Characterize")).isZero();
        }

        @Test
        void telephoneWithElevenSignificantDigitsIsRejected() throws Exception {
            mockMvc.perform(post("/owners/new")
                    .param("firstName", "Ada")
                    .param("lastName", "Characterize")
                    .param("address", "1 Test Street")
                    .param("city", "Testville")
                    .param("telephone", "12345678901"))
                .andExpect(view().name(OWNER_FORM))
                .andExpect(model().attributeHasFieldErrorCode("owner", "telephone", "Digits"));
        }

        @Test
        void telephoneLongerThanTenCharactersIsAcceptedWhenItHasLeadingZeros() throws Exception {
            // Quirk: @Digits(integer = 10) parses the value as a number, so leading zeros don't count.
            // "01234567890" is 11 characters but only 10 significant digits, and it is stored unchanged.
            MvcResult result = mockMvc.perform(post("/owners/new")
                    .param("firstName", "Ada")
                    .param("lastName", "Characterize")
                    .param("address", "1 Test Street")
                    .param("city", "Testville")
                    .param("telephone", "01234567890"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

            int newId = idFromRedirect(result, "/owners/");
            assertThat(ownerRow(newId)).containsEntry("TELEPHONE", "01234567890");
        }

        @Test
        void idSubmittedWithTheFormIsIgnored() throws Exception {
            // OwnerController's @InitBinder disallows binding "id"
            MvcResult result = mockMvc.perform(post("/owners/new")
                    .param("id", "999")
                    .param("firstName", "Ada")
                    .param("lastName", "Characterize")
                    .param("address", "1 Test Street")
                    .param("city", "Testville")
                    .param("telephone", "0123456789"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

            assertThat(idFromRedirect(result, "/owners/")).isNotEqualTo(999);
            assertThat(countOwnersWithId(999)).isZero();
        }
    }

    @Nested
    class EditOwner {

        @Test
        void editFormShowsExistingOwner() throws Exception {
            MvcResult result = mockMvc.perform(get("/owners/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name(OWNER_FORM))
                .andExpect(model().attribute("owner", hasProperty("new", is(false))))
                .andExpect(model().attribute("owner", hasProperty("lastName", is("Franklin"))))
                .andReturn();
            assertForwardedJspExists(result);
        }

        @Test
        void validEditIsSavedAndRedirectsToOwner() throws Exception {
            mockMvc.perform(post("/owners/1/edit")
                    .param("firstName", "George")
                    .param("lastName", "Franklin")
                    .param("address", "110 W. Liberty St.")
                    .param("city", "Middleton")
                    .param("telephone", "6085551023"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/owners/1"));

            assertThat(ownerRow(1)).containsEntry("CITY", "Middleton");
            // Editing the owner must not detach or delete the owner's pets
            assertThat(jdbc.sql("SELECT name FROM pets WHERE owner_id = 1").query(String.class).list())
                .containsExactly("Leo");
        }

        @Test
        void invalidEditRedisplaysFormAndChangesNothing() throws Exception {
            mockMvc.perform(post("/owners/1/edit")
                    .param("firstName", "George")
                    .param("lastName", "Franklin")
                    .param("address", "110 W. Liberty St.")
                    .param("city", "")
                    .param("telephone", "6085551023"))
                .andExpect(status().isOk())
                .andExpect(view().name(OWNER_FORM))
                .andExpect(model().attributeHasFieldErrorCode("owner", "city", "NotEmpty"));

            assertThat(ownerRow(1)).containsEntry("CITY", "Madison");
        }
    }
}
