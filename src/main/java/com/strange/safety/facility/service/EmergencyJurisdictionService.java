package com.strange.safety.facility.service;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.facility.dto.EmergencyJurisdictionResolveRequest;
import com.strange.safety.facility.dto.EmergencyJurisdictionResponse;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class EmergencyJurisdictionService {

    private static final String DATA_PATH = "data/emergency-jurisdictions.csv";
    private static final String CENTER_DATA_PATH = "data/emergency-centers.csv";
    private static final List<ProvinceAlias> PROVINCE_ALIASES = List.of(
            new ProvinceAlias("서울특별시", List.of("서울", "서울시", "서울특별시")),
            new ProvinceAlias("부산광역시", List.of("부산", "부산시", "부산광역시")),
            new ProvinceAlias("대구광역시", List.of("대구", "대구시", "대구광역시")),
            new ProvinceAlias("인천광역시", List.of("인천", "인천시", "인천광역시")),
            new ProvinceAlias("광주광역시", List.of("광주", "광주시", "광주광역시")),
            new ProvinceAlias("대전광역시", List.of("대전", "대전시", "대전광역시")),
            new ProvinceAlias("울산광역시", List.of("울산", "울산시", "울산광역시")),
            new ProvinceAlias("세종특별자치시", List.of("세종", "세종시", "세종특별자치시")),
            new ProvinceAlias("경기도", List.of("경기", "경기도")),
            new ProvinceAlias("강원특별자치도", List.of("강원", "강원도", "강원특별자치도")),
            new ProvinceAlias("충청북도", List.of("충북", "충청북도")),
            new ProvinceAlias("충청남도", List.of("충남", "충청남도")),
            new ProvinceAlias("전북특별자치도", List.of("전북", "전라북도", "전북특별자치도")),
            new ProvinceAlias("전라남도", List.of("전남", "전라남도")),
            new ProvinceAlias("경상북도", List.of("경북", "경상북도")),
            new ProvinceAlias("경상남도", List.of("경남", "경상남도")),
            new ProvinceAlias("제주특별자치도", List.of("제주", "제주도", "제주특별자치도"))
    );

    private final List<EmergencyJurisdictionRecord> jurisdictions = new ArrayList<>();
    private final List<EmergencyCenterRecord> centers = new ArrayList<>();

    @PostConstruct
    void loadJurisdictions() throws IOException {
        readData(DATA_PATH).stream()
                .map(this::parseJurisdictionLine)
                .forEach(jurisdictions::add);
        readData(CENTER_DATA_PATH).stream()
                .map(this::parseCenterLine)
                .forEach(centers::add);
    }

    private List<String> readData(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1)
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .toList();
        }
    }

    public EmergencyJurisdictionResponse resolve(EmergencyJurisdictionResolveRequest request) {
        ParsedAddress parsedAddress = parseAddress(request);
        return findCenterByLocality(parsedAddress)
                .map(EmergencyCenterRecord::toResponse)
                .or(() -> findByProvinceAndDistrict(parsedAddress).map(EmergencyJurisdictionRecord::toResponse))
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_JURISDICTION_NOT_FOUND));
    }

    private java.util.Optional<EmergencyCenterRecord> findCenterByLocality(ParsedAddress parsedAddress) {
        if (parsedAddress.locality() == null) {
            return java.util.Optional.empty();
        }
        return centers.stream()
                .filter(record -> parsedAddress.province() == null || record.matchesProvince(parsedAddress.province()))
                .filter(record -> parsedAddress.containsDistrict(record.district()))
                .filter(record -> record.matchesLocality(parsedAddress.locality()))
                .findFirst();
    }

    private java.util.Optional<EmergencyJurisdictionRecord> findByProvinceAndDistrict(ParsedAddress parsedAddress) {
        return jurisdictions.stream()
                .filter(record -> parsedAddress.province() == null || record.matchesProvince(parsedAddress.province()))
                .filter(record -> parsedAddress.containsDistrict(record.district()))
                .findFirst();
    }

    private ParsedAddress parseAddress(EmergencyJurisdictionResolveRequest request) {
        String rawAddress = request.address() + " " + nullToEmpty(request.addressDetail());
        String address = normalize(rawAddress);
        String province = PROVINCE_ALIASES.stream()
                .flatMap(alias -> alias.matches(address).stream())
                .min(Comparator
                        .comparingInt(ProvinceMatch::aliasIndex)
                        .thenComparing(Comparator.comparingInt(ProvinceMatch::aliasLength).reversed()))
                .map(ProvinceMatch::province)
                .orElse(null);
        return new ParsedAddress(province, address, parseLocality(request.region3DepthName(), rawAddress));
    }

    private EmergencyJurisdictionRecord parseJurisdictionLine(String line) {
        String[] values = line.split(",", -1);
        if (values.length != 8) {
            throw new IllegalStateException("Invalid emergency jurisdiction data: " + line);
        }
        return new EmergencyJurisdictionRecord(
                values[0].trim(),
                values[1].trim(),
                values[2].trim(),
                values[3].trim(),
                values[4].trim(),
                values[5].trim(),
                parseDouble(values[6]),
                parseDouble(values[7])
        );
    }

    private EmergencyCenterRecord parseCenterLine(String line) {
        String[] values = line.split(",", -1);
        if (values.length != 9) {
            throw new IllegalStateException("Invalid emergency center data: " + line);
        }
        return new EmergencyCenterRecord(
                values[0].trim(),
                values[1].trim(),
                values[2].trim(),
                values[3].trim(),
                values[4].trim(),
                values[5].trim(),
                values[6].trim(),
                parseDouble(values[7]),
                parseDouble(values[8])
        );
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.valueOf(value.trim());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String parseLocality(String requestedLocality, String rawAddress) {
        if (requestedLocality != null && !requestedLocality.isBlank()) {
            return normalizeLocality(requestedLocality);
        }
        return java.util.regex.Pattern.compile("\\(([^)]*(?:읍|면|동|가))\\)")
                .matcher(rawAddress)
                .results()
                .map(match -> normalizeLocality(match.group(1)))
                .reduce((first, second) -> second)
                .orElseGet(() -> parseLocalityFromTokens(rawAddress));
    }

    private String parseLocalityFromTokens(String rawAddress) {
        for (String token : rawAddress.split("\\s+")) {
            String cleaned = token.replaceAll("[,()]", "");
            if (cleaned.matches("^[가-힣0-9]+(읍|면|동|가)$")) {
                return normalizeLocality(cleaned);
            }
        }
        return null;
    }

    private String normalizeLocality(String value) {
        String normalized = normalize(value.replaceAll("[,()]", ""));
        return normalized.isBlank() ? null : normalized;
    }

    private record ParsedAddress(String province, String normalizedAddress, String locality) {
        boolean containsDistrict(String district) {
            String normalizedDistrict = normalize(district);
            return normalizedAddress.contains(normalizedDistrict)
                    || ("세종시".equals(normalizedDistrict)
                    && normalizedAddress.contains(normalize("세종특별자치시")));
        }
    }

    private record ProvinceAlias(String province, List<String> aliases) {
        List<ProvinceMatch> matches(String normalizedAddress) {
            return aliases.stream()
                    .map(EmergencyJurisdictionService::normalize)
                    .filter(normalizedAddress::contains)
                    .map(alias -> new ProvinceMatch(province, alias.length(), normalizedAddress.indexOf(alias)))
                    .toList();
        }
    }

    private record ProvinceMatch(String province, int aliasLength, int aliasIndex) {
    }

    private record EmergencyJurisdictionRecord(
            String province,
            String district,
            String jurisdiction,
            String stationName,
            String centerName,
            String stationAddress,
            Double latitude,
            Double longitude
    ) {
        boolean matchesProvince(String targetProvince) {
            return province.equals(targetProvince);
        }

        EmergencyJurisdictionResponse toResponse() {
            return new EmergencyJurisdictionResponse(
                    district,
                    jurisdiction,
                    stationName,
                    centerName,
                    stationAddress,
                    latitude,
                    longitude
            );
        }
    }

    private record EmergencyCenterRecord(
            String province,
            String district,
            String locality,
            String jurisdiction,
            String stationName,
            String centerName,
            String stationAddress,
            Double latitude,
            Double longitude
    ) {
        boolean matchesProvince(String targetProvince) {
            return province.equals(targetProvince);
        }

        boolean matchesLocality(String targetLocality) {
            return !locality.isBlank() && normalize(locality).equals(targetLocality);
        }

        EmergencyJurisdictionResponse toResponse() {
            return new EmergencyJurisdictionResponse(
                    district,
                    jurisdiction,
                    stationName,
                    centerName,
                    stationAddress,
                    latitude,
                    longitude
            );
        }
    }
}
