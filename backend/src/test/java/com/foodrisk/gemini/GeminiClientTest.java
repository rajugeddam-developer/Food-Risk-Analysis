package com.foodrisk.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.exception.NormalizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientTest {

    private GeminiProperties properties;
    private GeminiPromptBuilder promptBuilder;
    private ObjectMapper objectMapper;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        properties = new GeminiProperties();
        properties.setApiKey("test-api-key");
        properties.setModel("gemini-3.6-flash");
        properties.setTimeoutSeconds(5);

        promptBuilder = new GeminiPromptBuilder();
        objectMapper = new ObjectMapper();
        client = new GeminiClient(properties, promptBuilder, objectMapper, RestClient.builder());
    }

    @Test
    @DisplayName("Should parse valid Gemini response with markdown JSON fencing")
    void testParseValidResponseWithMarkdownFencing() {
        String geminiJson = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "```json\\n{\\n  \\"productName\\": \\"Crispy Biscuits\\",\\n  \\"servingSize\\": \\"30g\\",\\n  \\"servingSizeGrams\\": 30.0,\\n  \\"ingredients\\": [\\n    {\\n      \\"name\\": \\"Palm Oil\\",\\n      \\"rawText\\": \\"Pa1m Oi1\\",\\n      \\"isAdditive\\": false,\\n      \\"additiveCode\\": null,\\n      \\"uncertain\\": true\\n    }\\n  ],\\n  \\"nutrition\\": {\\n    \\"basis\\": \\"per 100g\\",\\n    \\"energyKcal\\": 550.0,\\n    \\"proteinG\\": 7.5,\\n    \\"carbohydrateG\\": 65.0,\\n    \\"totalSugarsG\\": 22.0,\\n    \\"addedSugarsG\\": 18.0,\\n    \\"totalFatG\\": 30.0,\\n    \\"saturatedFatG\\": 12.0,\\n    \\"transFatG\\": 0.1,\\n    \\"sodiumMg\\": 350.0,\\n    \\"fiberG\\": 2.5,\\n    \\"rawEntries\\": [\\"Fat 30g\\"]\\n  },\\n  \\"uncertainties\\": [\\"Spelling fixed for Palm Oil\\"]\\n}\\n```"
                  }
                ],
                "role": "model"
              }
            }
          ]
        }
        """;

        NormalizedFoodData data = client.parseGeminiResponse(geminiJson);

        assertThat(data).isNotNull();
        assertThat(data.productName()).isEqualTo("Crispy Biscuits");
        assertThat(data.servingSizeGrams()).isEqualTo(30.0);
        assertThat(data.ingredients()).hasSize(1);
        assertThat(data.ingredients().get(0).name()).isEqualTo("Palm Oil");
        assertThat(data.ingredients().get(0).rawText()).isEqualTo("Pa1m Oi1");
        assertThat(data.ingredients().get(0).uncertain()).isTrue();
        assertThat(data.nutrition().energyKcal()).isEqualTo(550.0);
        assertThat(data.nutrition().totalFatG()).isEqualTo(30.0);
        assertThat(data.uncertainties()).contains("Spelling fixed for Palm Oil");
    }

    @Test
    @DisplayName("Should parse valid raw JSON without markdown fencing")
    void testParseValidRawJson() {
        String geminiJson = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "{\\"productName\\": null, \\"ingredients\\": [], \\"nutrition\\": null, \\"uncertainties\\": []}"
                  }
                ]
              }
            }
          ]
        }
        """;

        NormalizedFoodData data = client.parseGeminiResponse(geminiJson);
        assertThat(data).isNotNull();
        assertThat(data.productName()).isNull();
        assertThat(data.ingredients()).isEmpty();
        assertThat(data.nutrition()).isNull();
    }

    @Test
    @DisplayName("Should reject empty Gemini response")
    void testEmptyGeminiResponse() {
        assertThatThrownBy(() -> client.parseGeminiResponse(""))
                .isInstanceOf(NormalizationException.class)
                .hasMessageContaining("empty response");

        assertThatThrownBy(() -> client.parseGeminiResponse("{\"candidates\": []}"))
                .isInstanceOf(NormalizationException.class)
                .hasMessageContaining("No response candidates");
    }

    @Test
    @DisplayName("Should reject malformed JSON in Gemini text part")
    void testMalformedJsonText() {
        String invalidJson = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "{ not valid json here ..."
                  }
                ]
              }
            }
          ]
        }
        """;

        assertThatThrownBy(() -> client.parseGeminiResponse(invalidJson))
                .isInstanceOf(NormalizationException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    @DisplayName("Should throw clear configuration error if Gemini API key is missing")
    void testMissingApiKeyThrowsConfigError() {
        properties.setApiKey("");
        assertThatThrownBy(() -> client.normalize("Sugar", "Energy 100"))
                .isInstanceOf(NormalizationException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("Regression test: URL must never contain API key as query parameter")
    void testUrlDoesNotContainApiKeyQueryParam() {
        String url = client.buildGenerateContentUrl();
        assertThat(url).doesNotContain("?key=");
        assertThat(url).doesNotContain("key=");
        assertThat(url).doesNotContain(properties.getApiKey());
        assertThat(url).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent");
    }

    @Test
    @DisplayName("Regression test: Outgoing HTTP request sends API key via x-goog-api-key header and not in query string")
    void testRequestUsesHeaderAndNoQueryParam() {
        RestClient.Builder builder = RestClient.builder();
        org.springframework.test.web.client.MockRestServiceServer mockServer =
                org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();

        GeminiClient testClient = new GeminiClient(properties, promptBuilder, objectMapper, builder.build());

        mockServer.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        org.hamcrest.Matchers.equalTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("x-goog-api-key", "test-api-key"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("key="))))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"{\\\"productName\\\": null, \\\"ingredients\\\": [], \\\"nutrition\\\": null, \\\"uncertainties\\\": []}\"}]}}]}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        NormalizedFoodData result = testClient.normalize("Sugar", "Fat 1g");
        assertThat(result).isNotNull();
        mockServer.verify();
    }
}
