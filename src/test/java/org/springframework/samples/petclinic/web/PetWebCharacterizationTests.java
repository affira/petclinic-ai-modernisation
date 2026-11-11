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
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Characterization tests for the pet pages: create and edit a pet for an owner.
 */
class PetWebCharacterizationTests extends AbstractWebCharacterizationTests {

    private static final String PET_FORM = "pets/createOrUpdatePetForm";

    private List<Map<String, Object>> petRows(int ownerId, String name) {
        return this.jdbc.sql("SELECT id, name, birth_date, type_id, owner_id FROM pets WHERE owner_id = ? AND name = ?")
            .params(ownerId, name).query().listOfRows();
    }

    @Nested
    class CreatePet {

        @Test
        void creationFormShowsNewPetForOwnerAndPetTypesSortedByName() throws Exception {
            MvcResult result = mockMvc.perform(get("/owners/1/pets/new"))
                .andExpect(status().isOk())
                .andExpect(view().name(PET_FORM))
                .andExpect(model().attribute("pet", hasProperty("new", is(true))))
                .andExpect(model().attribute("owner", hasProperty("lastName", is("Franklin"))))
                .andExpect(model().attribute("types", contains(
                    hasProperty("name", is("bird")), hasProperty("name", is("cat")), hasProperty("name", is("dog")),
                    hasProperty("name", is("hamster")), hasProperty("name", is("lizard")), hasProperty("name", is("snake")))))
                .andReturn();
            assertForwardedJspExists(result);
        }

        @Test
        void validPetIsSavedAndRedirectsToOwner() throws Exception {
            mockMvc.perform(post("/owners/1/pets/new")
                    .param("name", "Rex")
                    .param("birthDate", "2020/01/15")
                    .param("type", "dog"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/owners/1"));

            List<Map<String, Object>> rows = petRows(1, "Rex");
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0))
                .containsEntry("TYPE_ID", 2) // dog
                .containsEntry("BIRTH_DATE", java.sql.Date.valueOf(LocalDate.of(2020, 1, 15)));
        }

        @Test
        void isoDateFormatIsAlsoAccepted() throws Exception {
            // Pet.birthDate declares @DateTimeFormat(pattern = "yyyy/MM/dd"), yet an ISO date (what the flatpickr
            // picker on the form produces with default options) binds as well.
            mockMvc.perform(post("/owners/1/pets/new")
                    .param("name", "Rex")
                    .param("birthDate", "2020-01-15")
                    .param("type", "dog"))
                .andExpect(redirectedUrl("/owners/1"));

            assertThat(petRows(1, "Rex")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("BIRTH_DATE", java.sql.Date.valueOf(LocalDate.of(2020, 1, 15))));
        }

        @Test
        void unparseableBirthDateIsATypeMismatch() throws Exception {
            mockMvc.perform(post("/owners/1/pets/new")
                    .param("name", "Rex")
                    .param("birthDate", "15/01/2020")
                    .param("type", "dog"))
                .andExpect(status().isOk())
                .andExpect(view().name(PET_FORM))
                .andExpect(model().attributeHasFieldErrorCode("pet", "birthDate", "typeMismatch"));
        }

        @Test
        void missingNameBirthDateAndTypeAreAllRequired() throws Exception {
            mockMvc.perform(post("/owners/1/pets/new")
                    .param("name", "")
                    .param("birthDate", ""))
                .andExpect(status().isOk())
                .andExpect(view().name(PET_FORM))
                .andExpect(model().attributeErrorCount("pet", 3))
                .andExpect(model().attributeHasFieldErrorCode("pet", "name", "required"))
                .andExpect(model().attributeHasFieldErrorCode("pet", "birthDate", "required"))
                .andExpect(model().attributeHasFieldErrorCode("pet", "type", "required"));
        }

        @Test
        void unknownPetTypeIsATypeMismatch() throws Exception {
            mockMvc.perform(post("/owners/1/pets/new")
                    .param("name", "Rex")
                    .param("birthDate", "2020/01/15")
                    .param("type", "unicorn"))
                .andExpect(view().name(PET_FORM))
                .andExpect(model().attributeHasFieldErrorCode("pet", "type", "typeMismatch"));
        }

        @Test
        void duplicatePetNameForSameOwnerIsRejected() throws Exception {
            mockMvc.perform(post("/owners/1/pets/new")
                    .param("name", "Leo")
                    .param("birthDate", "2020/01/15")
                    .param("type", "cat"))
                .andExpect(view().name(PET_FORM))
                .andExpect(model().attributeHasFieldErrorCode("pet", "name", "duplicate"));
        }

        @Test
        void duplicatePetNameCheckIgnoresCase() throws Exception {
            mockMvc.perform(post("/owners/1/pets/new")
                    .param("name", "leo")
                    .param("birthDate", "2020/01/15")
                    .param("type", "cat"))
                .andExpect(view().name(PET_FORM))
                .andExpect(model().attributeHasFieldErrorCode("pet", "name", "duplicate"));
        }

        @Test
        void samePetNameIsAllowedForADifferentOwner() throws Exception {
            mockMvc.perform(post("/owners/2/pets/new")
                    .param("name", "Leo")
                    .param("birthDate", "2020/01/15")
                    .param("type", "cat"))
                .andExpect(redirectedUrl("/owners/2"));
            assertThat(petRows(2, "Leo")).hasSize(1);
        }

        @Test
        void unknownOwnerShowsGenericErrorPage() throws Exception {
            mockMvc.perform(get("/owners/9999/pets/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("exception"))
                .andExpect(model().attribute("exception", instanceOf(EmptyResultDataAccessException.class)));
        }
    }

    @Nested
    class EditPet {

        @Test
        void editFormShowsExistingPet() throws Exception {
            MvcResult result = mockMvc.perform(get("/owners/1/pets/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name(PET_FORM))
                .andExpect(model().attribute("pet", hasProperty("name", is("Leo"))))
                .andExpect(model().attribute("pet", hasProperty("new", is(false))))
                .andReturn();
            assertForwardedJspExists(result);
        }

        @Test
        void validEditIsSavedAndRedirectsToOwner() throws Exception {
            mockMvc.perform(post("/owners/1/pets/1/edit")
                    .param("id", "1")
                    .param("name", "Leonardo")
                    .param("birthDate", "2010/09/08")
                    .param("type", "dog"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/owners/1"));

            Map<String, Object> row = jdbc.sql("SELECT name, birth_date, type_id, owner_id FROM pets WHERE id = 1")
                .query().singleRow();
            assertThat(row)
                .containsEntry("NAME", "Leonardo")
                .containsEntry("BIRTH_DATE", java.sql.Date.valueOf(LocalDate.of(2010, 9, 8)))
                .containsEntry("TYPE_ID", 2)
                .containsEntry("OWNER_ID", 1);
        }

        @Test
        void emptyNameOnEditIsRejected() throws Exception {
            mockMvc.perform(post("/owners/1/pets/1/edit")
                    .param("id", "1")
                    .param("name", "")
                    .param("birthDate", "2010/09/07")
                    .param("type", "cat"))
                .andExpect(status().isOk())
                .andExpect(view().name(PET_FORM))
                .andExpect(model().attributeHasFieldErrorCode("pet", "name", "required"));
        }

        @Test
        void renamingAPetToItsSiblingsNameIsAllowedOnEdit() throws Exception {
            // The duplicate-name check in PetController only runs for new pets.
            // Owner 3 (Eduardo Rodriquez) has Rosy (pet 3) and Jewel (pet 4).
            mockMvc.perform(post("/owners/3/pets/4/edit")
                    .param("id", "4")
                    .param("name", "Rosy")
                    .param("birthDate", "2010/03/07")
                    .param("type", "dog"))
                .andExpect(redirectedUrl("/owners/3"));

            assertThat(petRows(3, "Rosy")).hasSize(2);
        }
    }
}
