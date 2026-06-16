package com.strange.safety.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.strange.safety.auth.dto.CorporateSignupRequest;
import com.strange.safety.auth.dto.IndividualSignupRequest;
import com.strange.safety.auth.entity.Role;
import com.strange.safety.auth.entity.SmsVerification;
import com.strange.safety.auth.entity.VerificationPurpose;
import com.strange.safety.auth.repository.SmsVerificationRepository;
import com.strange.safety.auth.security.RefreshTokenHasher;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.emergency.repository.EmergencyContactRepository;
import com.strange.safety.event.MqttSafetyEventSubscriber;
import com.strange.safety.facility.repository.FacilityRepository;
import com.strange.safety.facility.repository.ProtectedTargetRepository;
import com.strange.safety.facility.repository.UserFacilityRepository;
import com.strange.safety.user.dto.AgreementRequest;
import com.strange.safety.user.entity.AgreementType;
import com.strange.safety.user.repository.UserAgreementRepository;
import com.strange.safety.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class SignupServiceIntegrationTest {

    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired CompanyProfileRepository companyProfileRepository;
    @Autowired FacilityRepository facilityRepository;
    @Autowired UserFacilityRepository userFacilityRepository;
    @Autowired ProtectedTargetRepository protectedTargetRepository;
    @Autowired EmergencyContactRepository emergencyContactRepository;
    @Autowired UserAgreementRepository userAgreementRepository;
    @Autowired SmsVerificationRepository smsVerificationRepository;
    @Autowired RefreshTokenHasher tokenHasher;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;

    @MockBean MqttSafetyEventSubscriber mqttSafetyEventSubscriber;

    @BeforeEach
    void cleanDatabase() {
        userAgreementRepository.deleteAll();
        emergencyContactRepository.deleteAll();
        protectedTargetRepository.deleteAll();
        userFacilityRepository.deleteAll();
        facilityRepository.deleteAll();
        companyProfileRepository.deleteAll();
        smsVerificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void individualSignupCreatesAllRelatedData() {
        String token = verifiedToken("01011112222");

        var response = signupService.signupIndividual(individualRequest(
                "individual@example.com", "010-1111-2222", token, "70s"));

        assertThat(response.role()).isEqualTo(Role.INDIVIDUAL);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(facilityRepository.count()).isEqualTo(1);
        assertThat(userFacilityRepository.count()).isEqualTo(1);
        assertThat(protectedTargetRepository.count()).isEqualTo(1);
        assertThat(emergencyContactRepository.count()).isEqualTo(1);
        assertThat(userAgreementRepository.count()).isEqualTo(3);
        assertThat(userRepository.findAll().get(0).getPhoneNumber()).isEqualTo("01011112222");

        var facility = facilityRepository.findAll().get(0);
        assertThat(facility.getDistrict()).isEqualTo("마포구");
        assertThat(facility.getEmergency119Jurisdiction()).isEqualTo("마포소방서");
        assertThat(emergencyContactRepository.findAll().get(0).getPhoneNumber()).isEqualTo("021234567");
    }

    @Test
    void individualSignupUsesRegion3DepthNameForJurisdictionResolution() {
        String token = verifiedToken("01022223333");

        signupService.signupIndividual(new IndividualSignupRequest(
                "region3@example.com", "Password123!", "individual user", "01022223333", token,
                new IndividualSignupRequest.CareTargetRequest(
                        "care target", "parent", "70s", "48073",
                        "부산광역시 해운대구 송정중앙로15번길 16", null, "송정동", "해운대구", "해운대소방서"),
                List.of(),
                requiredAgreements(true)
        ));

        var facility = facilityRepository.findAll().get(0);
        assertThat(facility.getDistrict()).isEqualTo("해운대구");
        assertThat(facility.getEmergency119Jurisdiction()).isEqualTo("기장소방서");
    }

    @Test
    void individualSignupFallsBackToRepresentativeWithoutRegion3DepthName() {
        String token = verifiedToken("01022224444");

        signupService.signupIndividual(new IndividualSignupRequest(
                "fallback-region3@example.com", "Password123!", "individual user", "01022224444", token,
                new IndividualSignupRequest.CareTargetRequest(
                        "care target", "parent", "70s", "48095",
                        "부산광역시 해운대구 해운대해변로 100", null, null, "강남구", "강남소방서"),
                List.of(),
                requiredAgreements(true)
        ));

        var facility = facilityRepository.findAll().get(0);
        assertThat(facility.getDistrict()).isEqualTo("해운대구");
        assertThat(facility.getEmergency119Jurisdiction()).isEqualTo("해운대소방서");
    }

    @Test
    void individualSignupFailureRollsBackAllRelatedData() {
        String token = verifiedToken("01011112222");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                signupService.signupIndividual(individualRequest(
                        "rollback@example.com", "01011112222", token, "UNKNOWN"))))
                .isInstanceOf(CustomException.class);

        assertThat(userRepository.count()).isZero();
        assertThat(userAgreementRepository.count()).isZero();
        assertThat(facilityRepository.count()).isZero();
        assertThat(userFacilityRepository.count()).isZero();
        assertThat(protectedTargetRepository.count()).isZero();
        assertThat(emergencyContactRepository.count()).isZero();
    }

    @Test
    void individualSignupRejectsInvalidMobilePhone() {
        String token = verifiedToken("01011112222");

        assertThatThrownBy(() -> signupService.signupIndividual(individualRequest(
                "invalid-phone@example.com", "011-1111-2222", token, "70s")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_INPUT);

        assertThat(userRepository.count()).isZero();
        assertThat(userAgreementRepository.count()).isZero();
    }

    @Test
    void corporateSignupCreatesProfile() {
        String token = verifiedToken("01055556666");

        var response = signupService.signupCorporate(corporateRequest(
                "corporate@example.com", "01055556666", token, "123-45-67890"));

        assertThat(response.role()).isEqualTo(Role.CORPORATE);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(companyProfileRepository.count()).isEqualTo(1);
        assertThat(userAgreementRepository.count()).isEqualTo(3);

        var profile = companyProfileRepository.findAll().get(0);
        assertThat(profile.getDistrict()).isEqualTo("강남구");
        assertThat(profile.getEmergency119Jurisdiction()).isEqualTo("강남소방서");
        assertThat(profile.getManagerContact()).isEqualTo("025551234");
    }

    @Test
    void corporateSignupAllowsMissingBusinessNumberAndPublicFacilityIndustry() {
        String token = verifiedToken("01055556666");

        var response = signupService.signupCorporate(corporateRequest(
                "public@example.com", "01055556666", token, null, "공공시설"));

        assertThat(response.role()).isEqualTo(Role.CORPORATE);
        var profile = companyProfileRepository.findAll().get(0);
        assertThat(profile.getBusinessRegistrationNumber()).isNull();
        assertThat(profile.getIndustry()).isEqualTo("공공시설");
    }

    @Test
    void corporateSignupTreatsBlankBusinessNumberAsMissing() {
        String token = verifiedToken("01055556666");

        signupService.signupCorporate(corporateRequest(
                "blank-business@example.com", "01055556666", token, "   ", "medical"));

        var profile = companyProfileRepository.findAll().get(0);
        assertThat(profile.getBusinessRegistrationNumber()).isNull();
    }

    @Test
    void corporateSignupFailureRollsBackUserAndProfile() {
        String token = verifiedToken("01055556666");
        var invalid = new CorporateSignupRequest(
                "corporate-rollback@example.com", "Password123!", "01055556666", token,
                new CorporateSignupRequest.CompanyRequest(
                        "Smart Safety Hospital", "1234567890", "medical", "50-200",
                        "99999", "Unknown address", null, null, null, null),
                new CorporateSignupRequest.ManagerRequest(
                        "company manager", "safety team", "manager@example.com", "02-555-1234"),
                requiredAgreements(true)
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                signupService.signupCorporate(invalid)))
                .isInstanceOf(RuntimeException.class);

        assertThat(userRepository.count()).isZero();
        assertThat(userAgreementRepository.count()).isZero();
        assertThat(companyProfileRepository.count()).isZero();
    }

    @Test
    void duplicateEmailAndBusinessNumberAreRejected() {
        signupService.signupCorporate(corporateRequest(
                "duplicate@example.com", "01055556666",
                verifiedToken("01055556666"), "123-45-67890"));

        assertThatThrownBy(() -> signupService.signupIndividual(individualRequest(
                "DUPLICATE@example.com", "01011112222",
                verifiedToken("01011112222"), "70s")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_EMAIL_ALREADY_EXISTS);

        assertThatThrownBy(() -> signupService.signupCorporate(corporateRequest(
                "other@example.com", "01077778888",
                verifiedToken("01077778888"), "1234567890")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_BUSINESS_NUMBER_ALREADY_EXISTS);
    }

    @Test
    void signupRejectsMissingRequiredAgreementsBeforeConsumingSmsToken() {
        String phone = "01011112222";
        String token = verifiedToken(phone);

        assertThatThrownBy(() -> signupService.signupIndividual(
                individualRequest("agreement-required@example.com", phone, token, "70s", null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGREEMENT_REQUIRED);

        SmsVerification verification = smsVerificationRepository.findAll().get(0);
        assertThat(verification.getUsedAt()).isNull();
        assertThat(userRepository.count()).isZero();
        assertThat(userAgreementRepository.count()).isZero();
    }

    @Test
    void signupRejectsFalseRequiredAgreements() {
        String phone = "01011112222";
        String token = verifiedToken(phone);

        assertThatThrownBy(() -> signupService.signupIndividual(
                individualRequest(
                        "agreement-false@example.com", phone, token, "70s",
                        new AgreementRequest(true, false, true))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGREEMENT_REQUIRED);

        assertThat(userRepository.count()).isZero();
        assertThat(userAgreementRepository.count()).isZero();
    }

    @Test
    void marketingAgreementCanBeFalseOnSignup() {
        String token = verifiedToken("01011112222");

        var response = signupService.signupIndividual(individualRequest(
                "marketing-false@example.com", "01011112222", token, "70s", requiredAgreements(false)));

        var marketingAgreement = userAgreementRepository
                .findByUserIdAndAgreementType(response.userId(), AgreementType.MARKETING)
                .orElseThrow();
        assertThat(marketingAgreement.isRequired()).isFalse();
        assertThat(marketingAgreement.isAgreed()).isFalse();
        assertThat(marketingAgreement.getAgreedAt()).isNull();
        assertThat(marketingAgreement.getWithdrawnAt()).isNotNull();
    }

    private String verifiedToken(String phone) {
        String token = "verified-" + phone + "-" + System.nanoTime();
        SmsVerification verification = SmsVerification.issue(
                phone, VerificationPurpose.SIGN_UP,
                passwordEncoder.encode("123456"), Instant.now().plusSeconds(300));
        verification.verify(tokenHasher.hash(token), Instant.now().plusSeconds(900), Instant.now());
        smsVerificationRepository.save(verification);
        return token;
    }

    private IndividualSignupRequest individualRequest(String email, String phone, String token, String ageGroup) {
        return individualRequest(email, phone, token, ageGroup, requiredAgreements(true));
    }

    private IndividualSignupRequest individualRequest(String email, String phone, String token,
                                                      String ageGroup, AgreementRequest agreements) {
        return new IndividualSignupRequest(
                email, "Password123!", "individual user", phone, token,
                new IndividualSignupRequest.CareTargetRequest(
                        "care target", "parent", ageGroup, "04123",
                        "서울특별시 마포구 월드컵로 1", "101", "공덕동", "강남구", "강남소방서"),
                List.of(new IndividualSignupRequest.EmergencyContactRequest(
                        "emergency contact", "child", "02-123-4567")),
                agreements
        );
    }

    private CorporateSignupRequest corporateRequest(String email, String phone, String token, String businessNumber) {
        return corporateRequest(email, phone, token, businessNumber, "medical");
    }

    private CorporateSignupRequest corporateRequest(
            String email,
            String phone,
            String token,
            String businessNumber,
            String industry
    ) {
        return new CorporateSignupRequest(
                email, "Password123!", phone, token,
                new CorporateSignupRequest.CompanyRequest(
                        "Smart Safety Hospital", businessNumber, industry, "50-200",
                        "06123", "서울특별시 강남구 테헤란로 1", "Safety Office", "역삼동", "마포구", "마포소방서"),
                new CorporateSignupRequest.ManagerRequest(
                        "company manager", "safety team", "manager@example.com", "02-555-1234"),
                requiredAgreements(true)
        );
    }

    private AgreementRequest requiredAgreements(boolean marketingAgreed) {
        return new AgreementRequest(true, true, marketingAgreed);
    }
}
