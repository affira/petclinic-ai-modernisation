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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Characterization tests for how the Accept header picks a view or response format.
 * <p>
 * These pin the behaviour of the original XML MVC configuration (mvc-view-config.xml) so the Java
 * {@code WebMvcConfig} keeps it. They were added when moving to Java config, after an HTTP comparison showed that
 * {@code GET /vets} with {@code Accept: application/xml} changed from 406 to an XML response.
 */
class ContentNegotiationCharacterizationTests extends AbstractWebCharacterizationTests {

    @Test
    void htmlPagesRenderForBrowsersAndWildcardAccept() throws Exception {
        mockMvc.perform(get("/vets").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(view().name("vets/vetList"));
        mockMvc.perform(get("/vets").accept(MediaType.ALL))
            .andExpect(status().isOk())
            .andExpect(view().name("vets/vetList"));
    }

    @Test
    void vetsPageAskedForAsXmlIsNotAcceptable() throws Exception {
        // The 'vets/vetList.xml' MarshallingView bean exists, but the original configuration never selected it:
        // asking /vets for XML gives 406. Use /vets.xml for XML.
        mockMvc.perform(get("/vets").accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable());
    }

    @Test
    void htmlPagesAskedForAsJsonAreNotAcceptable() throws Exception {
        mockMvc.perform(get("/vets").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotAcceptable());
        mockMvc.perform(get("/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotAcceptable());
    }

    @Test
    void jsonAndXmlFeedsHonourTheirOwnFormatOnly() throws Exception {
        mockMvc.perform(get("/vets.json").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mockMvc.perform(get("/vets.xml").accept(MediaType.APPLICATION_XML))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
        mockMvc.perform(get("/vets.json").accept(MediaType.TEXT_HTML))
            .andExpect(status().isNotAcceptable());
        mockMvc.perform(get("/vets.xml").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotAcceptable());
    }
}
