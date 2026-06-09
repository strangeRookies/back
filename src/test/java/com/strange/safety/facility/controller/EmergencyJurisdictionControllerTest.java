package com.strange.safety.facility.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.strange.safety.event.MqttSafetyEventSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmergencyJurisdictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MqttSafetyEventSubscriber mqttSafetyEventSubscriber;

    @Test
    void resolveReturnsJurisdictionWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "04123",
                                  "address": "서울특별시 마포구 월드컵로 1",
                                  "addressDetail": "101동 101호",
                                  "region3DepthName": "공덕동"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.district").value("마포구"))
                .andExpect(jsonPath("$.data.jurisdiction").value("마포소방서"))
                .andExpect(jsonPath("$.data.stationName").value("마포소방서"))
                .andExpect(jsonPath("$.data.centerName").value("공덕119안전센터"));
    }

    @Test
    void resolvePrefersCenterMatchingRegion3DepthName() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "48073",
                                  "address": "부산광역시 해운대구 송정중앙로15번길 16",
                                  "region3DepthName": "송정동"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district").value("해운대구"))
                .andExpect(jsonPath("$.data.jurisdiction").value("기장소방서"))
                .andExpect(jsonPath("$.data.centerName").value("송정119안전센터"));
    }

    @Test
    void resolveFallsBackToRepresentativeWhenLocalityDoesNotMatch() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "48095",
                                  "address": "부산광역시 해운대구 해운대해변로 100",
                                  "region3DepthName": "없는동"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district").value("해운대구"))
                .andExpect(jsonPath("$.data.jurisdiction").value("해운대소방서"))
                .andExpect(jsonPath("$.data.centerName").value("반송119안전센터"));
    }

    @Test
    void resolveWorksWithoutAddressDetail() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "04123",
                                  "address": "서울특별시 마포구 월드컵로 1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district").value("마포구"))
                .andExpect(jsonPath("$.data.jurisdiction").value("마포소방서"));
    }

    @Test
    void resolveReturnsRegionalRepresentativeJurisdiction() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "48095",
                                  "address": "부산광역시 해운대구 해운대해변로 100"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district").value("해운대구"))
                .andExpect(jsonPath("$.data.jurisdiction").value("해운대소방서"))
                .andExpect(jsonPath("$.data.centerName").value("반송119안전센터"));
    }

    @Test
    void resolvePrefersLongProvinceAliasForDuplicateRegionName() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "12738",
                                  "address": "경기도 광주시 행정타운로 50"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district").value("광주시"))
                .andExpect(jsonPath("$.data.jurisdiction").value("광주소방서"));
    }

    @Test
    void resolveSupportsProvinceAbbreviationAndRegion3DepthName() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "63208",
                                  "address": "제주 제주시 중앙로 1",
                                  "region3DepthName": "노형동"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district").value("제주시"))
                .andExpect(jsonPath("$.data.jurisdiction").value("제주소방서"))
                .andExpect(jsonPath("$.data.centerName").value("노형119안전센터"));
    }

    @Test
    void resolveSupportsNorthernGyeonggiAsGyeonggiProvince() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "10500",
                                  "address": "경기도 고양시 덕양구 토당로 48"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district").value("고양시"))
                .andExpect(jsonPath("$.data.jurisdiction").value("고양소방서"));
    }

    @Test
    void resolveSupportsSejongSpecialSelfGoverningCity() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "30151",
                                  "address": "세종특별자치시 절재로 301"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district").value("세종시"))
                .andExpect(jsonPath("$.data.jurisdiction").value("세종남부소방서"));
    }

    @Test
    void resolveReturnsNotFoundForUnknownAddress() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "00000",
                                  "address": "알수없는시 알수없는구 알수없는로 1"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMERGENCY_JURISDICTION_NOT_FOUND"));
    }

    @Test
    void resolveRejectsBlankAddress() throws Exception {
        mockMvc.perform(post("/api/emergency-jurisdictions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postcode": "04123",
                                  "address": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors.address").exists());
    }
}
