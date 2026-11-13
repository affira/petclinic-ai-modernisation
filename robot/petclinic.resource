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
Documentation       Reusable keywords for checking PetClinic over plain HTTP (no browser).
...                 Every request is sent with redirects switched off, so a test can check the
...                 302 and its Location itself, like PetclinicSmokeIT does.
...                 The last response is kept in the test variable ${RESPONSE}.
Library             RequestsLibrary
Library             String
Variables           variables.py


*** Keywords ***
# --- Setup ----------------------------------------------------------------------------------------

Open PetClinic Session
    [Documentation]    Creates the HTTP session all keywords use, then fails fast if the app is not up.
    Create Session    petclinic    ${BASE_URL}    max_retries=0
    ${response}=    GET On Session    petclinic    /    expected_status=any
    Should Be Equal As Integers    ${response.status_code}    200
    ...    msg=PetClinic is not answering at ${BASE_URL}. Start it first (see robot/README.md).

# --- Given / When -------------------------------------------------------------------------------

I open the page
    [Arguments]    ${path}
    ${response}=    GET On Session    petclinic    ${path}    expected_status=any    allow_redirects=${False}
    Set Test Variable    ${RESPONSE}    ${response}

I search for owners with last name
    [Arguments]    ${last_name}
    ${params}=    Create Dictionary    lastName=${last_name}
    ${response}=    GET On Session    petclinic    /owners    params=${params}
    ...    expected_status=any    allow_redirects=${False}
    Set Test Variable    ${RESPONSE}    ${response}

I submit the new owner form with
    [Documentation]    Posts the "Add Owner" form with the given fields, for example firstName=Jo.
    [Arguments]    &{fields}
    ${response}=    POST On Session    petclinic    /owners/new    data=${fields}
    ...    expected_status=any    allow_redirects=${False}
    Set Test Variable    ${RESPONSE}    ${response}

I follow the redirect
    ${path}=    Redirect Path
    I open the page    ${path}

A unique last name
    [Documentation]    The in-memory database keeps what earlier runs added, so new owners get a fresh name.
    ${suffix}=    Generate Random String    8    [LETTERS]
    RETURN    Robot${suffix}

# --- Then ---------------------------------------------------------------------------------------

The status should be
    [Arguments]    ${expected}
    Should Be Equal As Integers    ${RESPONSE.status_code}    ${expected}
    ...    msg=Expected HTTP ${expected} but got ${RESPONSE.status_code} for ${RESPONSE.url}

The page should contain
    [Arguments]    @{texts}
    FOR    ${text}    IN    @{texts}
        Should Contain    ${RESPONSE.text}    ${text}
    END

I should be redirected to
    [Arguments]    ${expected_path}
    The status should be    302
    ${path}=    Redirect Path
    Should Be Equal    ${path}    ${expected_path}

I should be redirected to an owner page
    The status should be    302
    ${path}=    Redirect Path
    Should Match Regexp    ${path}    ^/owners/\\d+$

# --- Helpers ------------------------------------------------------------------------------------

Redirect Path
    [Documentation]    Path part of the Location header (Jetty may send an absolute URL).
    ${location}=    Set Variable    ${RESPONSE.headers}[Location]
    ${path}=    Evaluate    urllib.parse.urlparse($location).path    modules=urllib.parse
    RETURN    ${path}
