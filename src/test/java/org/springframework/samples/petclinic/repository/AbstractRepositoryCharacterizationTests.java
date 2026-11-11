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
package org.springframework.samples.petclinic.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.samples.petclinic.model.NamedEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.util.EntityUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

/**
 * Characterization tests for the owner, pet and visit repositories, run once per persistence implementation (see the
 * subclasses). They call the repositories directly against the H2 test database seeded from {@code db/h2/data.sql}.
 * <p>
 * Most behaviour is the same for all three implementations. Where the implementations differ today (mostly what
 * happens for an unknown id), the expected behaviour is chosen per {@link Implementation}, so the differences are
 * visible in one place. Those differences are current behaviour, not a spec.
 * <p>
 * Each test runs in a transaction that is rolled back. Repositories are always called inside a transaction in
 * production too (through {@code ClinicServiceImpl}). To prove that a write reached the database, tests call
 * {@link #flushAndClear()} and then read with plain SQL.
 */
@SpringJUnitConfig(locations = "classpath:spring/business-config.xml")
@Transactional
abstract class AbstractRepositoryCharacterizationTests {

    enum Implementation { JPA, JDBC, SPRING_DATA_JPA }

    @Autowired
    protected OwnerRepository owners;

    @Autowired
    protected PetRepository pets;

    @Autowired
    protected VisitRepository visits;

    @Autowired
    private DataSource dataSource;

    protected JdbcClient jdbc;

    protected abstract Implementation implementation();

    /**
     * Makes pending writes visible to SQL and forces later repository reads to go to the database. A no-op for JDBC,
     * which writes immediately and has no persistence context.
     */
    protected abstract void flushAndClear();

    @BeforeEach
    void setUpJdbc() {
        this.jdbc = JdbcClient.create(this.dataSource);
    }

    private static List<String> names(Collection<? extends NamedEntity> entities) {
        return entities.stream().map(NamedEntity::getName).toList();
    }

    private static List<String> lastNames(Collection<Owner> owners) {
        return owners.stream().map(Owner::getLastName).toList();
    }

    private PetType petType(String name) {
        return this.pets.findPetTypes().stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- owners

    @Test
    void findByLastNameWithEmptyStringReturnsAllTenOwners() {
        assertThat(this.owners.findByLastName("")).hasSize(10);
    }

    @Test
    void findByLastNameIsAPrefixMatch() {
        assertThat(lastNames(this.owners.findByLastName("Davis"))).containsExactlyInAnyOrder("Davis", "Davis");
        assertThat(lastNames(this.owners.findByLastName("Es"))).containsExactlyInAnyOrder("Escobito", "Estaban");
        assertThat(this.owners.findByLastName("avis")).isEmpty();
    }

    @Test
    void findByLastNameIsCaseInsensitiveOnH2() {
        // Comes from the H2 column type VARCHAR_IGNORECASE, not from the repository code
        assertThat(lastNames(this.owners.findByLastName("davis"))).containsExactlyInAnyOrder("Davis", "Davis");
    }

    @Test
    void findByLastNameTreatsPercentSignAsWildcard() {
        assertThat(this.owners.findByLastName("%")).hasSize(10);
        // The repositories append "%", so a leading "%" turns the prefix search into a "contains" search
        assertThat(lastNames(this.owners.findByLastName("%vis")))
            .containsExactlyInAnyOrder("Davis", "Davis", "McTavish");
    }

    @Test
    void findByLastNameLoadsEachOwnersPets() {
        Owner rodriquez = this.owners.findByLastName("Rodriquez").iterator().next();
        assertThat(names(rodriquez.getPets())).containsExactly("Jewel", "Rosy");
    }

    @Test
    void findByLastNameReturnsEachOwnerOnceEvenWithSeveralPets() {
        // Eduardo Rodriquez has two pets; the JPA queries use DISTINCT with a fetch join
        assertThat(this.owners.findByLastName("Rodriquez")).hasSize(1);
    }

    @Test
    void findByIdReturnsOwnerWithAllFieldsAndPets() {
        Owner owner = this.owners.findById(1);
        assertThat(owner.getId()).isEqualTo(1);
        assertThat(owner.getFirstName()).isEqualTo("George");
        assertThat(owner.getLastName()).isEqualTo("Franklin");
        assertThat(owner.getAddress()).isEqualTo("110 W. Liberty St.");
        assertThat(owner.getCity()).isEqualTo("Madison");
        assertThat(owner.getTelephone()).isEqualTo("6085551023");
        assertThat(owner.getPets()).singleElement().satisfies(pet -> {
            assertThat(pet.getName()).isEqualTo("Leo");
            assertThat(pet.getBirthDate()).isEqualTo(LocalDate.of(2010, 9, 7));
            assertThat(pet.getType().getName()).isEqualTo("cat");
            assertThat(pet.getOwner()).isSameAs(owner);
        });
    }

    @Test
    void findByIdLoadsPetsVisitsNewestFirst() {
        Owner coleman = this.owners.findById(6);
        assertThat(names(coleman.getPets())).containsExactly("Max", "Samantha");
        Pet samantha = coleman.getPet("Samantha");
        assertThat(samantha.getVisits()).extracting(Visit::getDescription).containsExactly("spayed", "rabies shot");
        assertThat(samantha.getVisits()).extracting(Visit::getDate)
            .containsExactly(LocalDate.of(2013, 1, 4), LocalDate.of(2013, 1, 1));
    }

    @Test
    void findByIdForUnknownOwnerBehavesDifferentlyPerImplementation() {
        switch (implementation()) {
            case JPA -> assertThatThrownBy(() -> this.owners.findById(9999))
                .isInstanceOf(EmptyResultDataAccessException.class);
            case JDBC -> assertThatThrownBy(() -> this.owners.findById(9999))
                .isInstanceOf(ObjectRetrievalFailureException.class);
            case SPRING_DATA_JPA -> assertThat(this.owners.findById(9999)).isNull();
        }
    }

    @Test
    void saveNewOwnerAssignsIdAndInsertsRow() {
        Owner owner = new Owner();
        owner.setFirstName("Ada");
        owner.setLastName("Characterize");
        owner.setAddress("1 Test Street");
        owner.setCity("Testville");
        owner.setTelephone("0123456789");

        this.owners.save(owner);
        assertThat(owner.getId()).isNotNull();
        flushAndClear();

        Map<String, Object> row = this.jdbc.sql("SELECT * FROM owners WHERE id = ?").param(owner.getId()).query().singleRow();
        assertThat(row).containsEntry("FIRST_NAME", "Ada").containsEntry("LAST_NAME", "Characterize")
            .containsEntry("ADDRESS", "1 Test Street").containsEntry("CITY", "Testville")
            .containsEntry("TELEPHONE", "0123456789");
    }

    @Test
    void saveExistingOwnerUpdatesRowAndKeepsPets() {
        Owner owner = this.owners.findById(1);
        owner.setCity("Middleton");
        this.owners.save(owner);
        flushAndClear();

        assertThat(this.jdbc.sql("SELECT city FROM owners WHERE id = 1").query(String.class).single())
            .isEqualTo("Middleton");
        assertThat(names(this.owners.findById(1).getPets())).containsExactly("Leo");
    }

    // ---------------------------------------------------------------- pets

    @Test
    void findPetTypesReturnsAllTypesSortedByName() {
        assertThat(names(this.pets.findPetTypes()))
            .containsExactly("bird", "cat", "dog", "hamster", "lizard", "snake");
    }

    @Test
    void findPetByIdReturnsPetWithTypeOwnerAndVisits() {
        Pet samantha = this.pets.findById(7);
        assertThat(samantha.getName()).isEqualTo("Samantha");
        assertThat(samantha.getBirthDate()).isEqualTo(LocalDate.of(2012, 9, 4));
        assertThat(samantha.getType().getName()).isEqualTo("cat");
        assertThat(samantha.getOwner().getFirstName()).isEqualTo("Jean");
        assertThat(samantha.getVisits()).extracting(Visit::getDescription).containsExactly("spayed", "rabies shot");
    }

    @Test
    void findPetByIdForUnknownPetBehavesDifferentlyPerImplementation() {
        switch (implementation()) {
            case JPA, SPRING_DATA_JPA -> assertThat(this.pets.findById(9999)).isNull();
            case JDBC -> assertThatThrownBy(() -> this.pets.findById(9999))
                .isInstanceOf(ObjectRetrievalFailureException.class);
        }
    }

    @Test
    void saveNewPetAssignsIdAndInsertsRow() {
        Owner owner = this.owners.findById(6);
        Pet pet = new Pet();
        pet.setName("Bowser");
        pet.setBirthDate(LocalDate.of(2020, 1, 15));
        pet.setType(petType("dog"));
        owner.addPet(pet);

        this.pets.save(pet);
        assertThat(pet.getId()).isNotNull();
        flushAndClear();

        Map<String, Object> row = this.jdbc.sql("SELECT * FROM pets WHERE id = ?").param(pet.getId()).query().singleRow();
        assertThat(row).containsEntry("NAME", "Bowser").containsEntry("OWNER_ID", 6)
            .containsEntry("TYPE_ID", petType("dog").getId())
            .containsEntry("BIRTH_DATE", Date.valueOf(LocalDate.of(2020, 1, 15)));
        assertThat(names(this.owners.findById(6).getPets())).containsExactly("Bowser", "Max", "Samantha");
    }

    @Test
    void saveExistingPetUpdatesRow() {
        Pet pet = this.pets.findById(7);
        pet.setName("Sam");
        pet.setType(petType("dog"));
        this.pets.save(pet);
        flushAndClear();

        Map<String, Object> row = this.jdbc.sql("SELECT name, type_id, owner_id FROM pets WHERE id = 7").query().singleRow();
        assertThat(row).containsEntry("NAME", "Sam").containsEntry("TYPE_ID", petType("dog").getId())
            .containsEntry("OWNER_ID", 6);
    }

    // ---------------------------------------------------------------- visits

    @Test
    void findVisitsByPetIdReturnsThatPetsVisits() {
        List<Visit> found = this.visits.findByPetId(7);
        assertThat(found).extracting(Visit::getDescription).containsExactlyInAnyOrder("rabies shot", "spayed");
        assertThat(found).allSatisfy(visit -> assertThat(visit.getPet().getId()).isEqualTo(7));
    }

    @Test
    void findVisitsByPetIdForPetWithoutVisitsIsEmpty() {
        assertThat(this.visits.findByPetId(1)).isEmpty();
    }

    @Test
    void findVisitsByPetIdForUnknownPetBehavesDifferentlyPerImplementation() {
        switch (implementation()) {
            case JPA, SPRING_DATA_JPA -> assertThat(this.visits.findByPetId(9999)).isEmpty();
            case JDBC -> assertThatThrownBy(() -> this.visits.findByPetId(9999))
                .isInstanceOf(EmptyResultDataAccessException.class);
        }
    }

    @Test
    void saveNewVisitAssignsIdAndInsertsRow() {
        Pet pet = this.pets.findById(7);
        Visit visit = new Visit();
        visit.setDate(LocalDate.of(2026, 1, 2));
        visit.setDescription("annual checkup");
        pet.addVisit(visit);

        this.visits.save(visit);
        assertThat(visit.getId()).isNotNull();
        flushAndClear();

        Map<String, Object> row = this.jdbc.sql("SELECT * FROM visits WHERE id = ?").param(visit.getId()).query().singleRow();
        assertThat(row).containsEntry("PET_ID", 7).containsEntry("DESCRIPTION", "annual checkup")
            .containsEntry("VISIT_DATE", Date.valueOf(LocalDate.of(2026, 1, 2)));
        assertThat(this.visits.findByPetId(7)).hasSize(3);
    }

    @Test
    void saveExistingVisitBehavesDifferentlyPerImplementation() {
        Visit visit = EntityUtils.getById(this.visits.findByPetId(7), Visit.class, 1);
        visit.setDescription("rabies booster");

        switch (implementation()) {
            case JPA, SPRING_DATA_JPA -> {
                this.visits.save(visit);
                flushAndClear();
                assertThat(this.jdbc.sql("SELECT description FROM visits WHERE id = 1").query(String.class).single())
                    .isEqualTo("rabies booster");
            }
            case JDBC -> assertThatThrownBy(() -> this.visits.save(visit))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Visit update not supported");
        }
    }
}
