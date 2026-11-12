*** Comments ***
Copyright 2002-2026 the original author or authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


*** Settings ***
Documentation       HTTP checks against the running PetClinic WAR, ported from PetclinicSmokeIT and the
...                 web characterization tests. They pin current behaviour, including the known bug
...                 tagged known-bug.
Resource            petclinic.resource
Suite Setup         Open PetClinic Session


*** Test Cases ***
Home page renders
    [Documentation]    From PetclinicSmokeIT.welcomePage.
    [Tags]    smoke
    When I open the page    /
    Then the status should be    200
    And the page should contain    Welcome

Owner search with one match redirects to that owner
    [Documentation]    From OwnerWebCharacterizationTests: a single match skips the list.
    [Tags]    owners    search
    When I search for owners with last name    Franklin
    Then I should be redirected to    /owners/1
    When I follow the redirect
    Then the status should be    200
    And the page should contain    Owner Information    George Franklin

Owner search with several matches shows the list
    [Documentation]    From OwnerWebCharacterizationTests.searchMatchingSeveralOwnersShowsList.
    [Tags]    owners    search
    When I search for owners with last name    Davis
    Then the status should be    200
    And the page should contain    ownersTable    Betty Davis    Harold Davis

Owner search with no match shows the form with an error
    [Documentation]    From OwnerWebCharacterizationTests: the field error code is notFound.
    [Tags]    owners    search
    When I search for owners with last name    Nobody
    Then the status should be    200
    And the page should contain    Find Owners    has not been found

Creating an owner shows validation errors, then accepts valid data
    [Documentation]    From PetclinicSmokeIT.invalidOwnerRedisplaysFormWithMessages and createOwnerThenShowIt.
    [Tags]    owners    forms
    When I submit the new owner form with
    ...    firstName=    lastName=X    address=a    city=b    telephone=abc
    Then the status should be    200
    And the page should contain    Add Owner    must not be empty    numeric value out of bounds

    ${last_name}=    A unique last name
    When I submit the new owner form with
    ...    firstName=Robot    lastName=${last_name}    address=1 Test Street    city=Robotville
    ...    telephone=0123456789
    Then I should be redirected to an owner page
    When I follow the redirect
    Then the page should contain    Owner Information    Robot ${last_name}    Robotville

Visit list returns 404 because its JSP is missing
    [Documentation]    KNOWN BUG (docs/ARCHITECTURE.md 7.4), pinned as it is today, like
    ...    PetclinicSmokeIT.visitListReturns404BecauseItsJspIsMissing. If this starts failing,
    ...    the bug was fixed: decide on the new behaviour and update this test on purpose.
    [Tags]    visits    known-bug
    When I open the page    /owners/6/pets/7/visits
    Then the status should be    404
