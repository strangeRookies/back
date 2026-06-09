package com.strange.safety.auth.service;

import com.strange.safety.auth.dto.*;
import com.strange.safety.auth.entity.Role;
import com.strange.safety.auth.entity.VerificationPurpose;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.company.repository.InstallationRequestRepository;
import com.strange.safety.emergency.entity.EmergencyContact;
import com.strange.safety.emergency.repository.EmergencyContactRepository;
import com.strange.safety.facility.dto.EmergencyJurisdictionResolveRequest;
import com.strange.safety.facility.dto.EmergencyJurisdictionResponse;
import com.strange.safety.facility.entity.*;
import com.strange.safety.facility.repository.*;
import com.strange.safety.facility.service.EmergencyJurisdictionService;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.repository.UserRepository;
import com.strange.safety.user.service.UserAgreementService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SignupService {

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final InstallationRequestRepository installationRequestRepository;
    private final FacilityRepository facilityRepository;
    private final UserFacilityRepository userFacilityRepository;
    private final ProtectedTargetRepository protectedTargetRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsVerificationService smsVerificationService;
    private final UserAgreementService userAgreementService;
    private final EmergencyJurisdictionService emergencyJurisdictionService;

    public SignupResponse signupIndividual(IndividualSignupRequest request) {
        String email = normalizeEmail(request.email());
        validateEmailAvailable(email);
        userAgreementService.validateRequiredAgreements(request.agreements());
        smsVerificationService.consume(request.verificationToken(), request.phone(), VerificationPurpose.SIGN_UP);

        User user = saveUser(email, request.password(), request.name(), request.phone(), Role.INDIVIDUAL);
        userAgreementService.saveSignupAgreements(user, request.agreements());
        IndividualSignupRequest.CareTargetRequest targetRequest = request.careTarget();
        EmergencyJurisdictionResponse jurisdiction = resolveJurisdiction(
                targetRequest.postcode(), targetRequest.address(), targetRequest.addressDetail(),
                targetRequest.region3DepthName());
        Facility facility = facilityRepository.save(Facility.builder()
                .facilityName(targetRequest.name() + " 보호 시설")
                .facilityType(FacilityType.HOME)
                .postalCode(targetRequest.postcode())
                .address(targetRequest.address())
                .addressDetail(targetRequest.addressDetail())
                .district(jurisdiction.district())
                .emergency119Jurisdiction(jurisdiction.jurisdiction())
                .build());
        userFacilityRepository.save(UserFacility.builder()
                .user(user).facility(facility).accessType(AccessType.MANAGER).build());
        ProtectedTarget target = protectedTargetRepository.save(ProtectedTarget.builder()
                .guardianUser(user).facility(facility)
                .targetName(targetRequest.name())
                .relationship(targetRequest.relation())
                .ageGroup(toAgeGroup(targetRequest.ageGroup()))
                .build());

        List<IndividualSignupRequest.EmergencyContactRequest> contacts = request.emergencyContacts();
        if (contacts != null) {
            contacts.forEach(contact -> emergencyContactRepository.save(EmergencyContact.builder()
                    .guardianUser(user).protectedTarget(target).name(contact.name())
                    .relationship(contact.relation())
                    .phoneNumber(smsVerificationService.normalizePhone(contact.phone()))
                    .build()));
        }
        return SignupResponse.from(user);
    }

    public SignupResponse signupCorporate(CorporateSignupRequest request) {
        String email = normalizeEmail(request.email());
        String businessNumber = normalizeNumber(request.company().businessNumber());
        validateEmailAvailable(email);
        if (companyProfileRepository.existsByBusinessRegistrationNumber(businessNumber)) {
            throw new CustomException(ErrorCode.COMPANY_BUSINESS_NUMBER_ALREADY_EXISTS);
        }
        userAgreementService.validateRequiredAgreements(request.agreements());
        smsVerificationService.consume(request.verificationToken(), request.phone(), VerificationPurpose.SIGN_UP);

        User user = saveUser(email, request.password(), request.manager().name(), request.phone(), Role.CORPORATE);
        userAgreementService.saveSignupAgreements(user, request.agreements());
        CorporateSignupRequest.CompanyRequest company = request.company();
        CorporateSignupRequest.ManagerRequest manager = request.manager();
        EmergencyJurisdictionResponse jurisdiction = resolveJurisdiction(
                company.postcode(), company.address(), company.addressDetail(), company.region3DepthName());
        CompanyProfile profile = companyProfileRepository.save(CompanyProfile.builder()
                .user(user).companyName(company.name()).businessRegistrationNumber(businessNumber)
                .industry(company.industry()).companySize(company.size()).postalCode(company.postcode())
                .address(company.address()).addressDetail(company.addressDetail()).district(jurisdiction.district())
                .emergency119Jurisdiction(jurisdiction.jurisdiction()).managerName(manager.name())
                .managerDepartment(manager.department()).managerRank(manager.rank())
                .managerEmail(normalizeEmail(manager.email()))
                .managerContact(smsVerificationService.normalizePhone(manager.contact()))
                .build());
        CorporateSignupRequest.InstallationRequest installation = request.installation();
        if (installation != null) {
            installationRequestRepository.save(com.strange.safety.company.entity.InstallationRequest.builder()
                    .companyProfile(profile).installationCount(installation.count())
                    .preferredDate(installation.preferredDate()).specialRequest(installation.specialRequest())
                    .build());
        }
        return SignupResponse.from(user);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse emailAvailability(String email) {
        return new AvailabilityResponse(!userRepository.existsByEmail(normalizeEmail(email)));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse businessNumberAvailability(String businessNumber) {
        return new AvailabilityResponse(
                !companyProfileRepository.existsByBusinessRegistrationNumber(normalizeNumber(businessNumber))
        );
    }

    private User saveUser(String email, String password, String name, String phone, Role role) {
        return userRepository.save(User.create(
                email, passwordEncoder.encode(password), name.trim(),
                smsVerificationService.normalizePhone(phone), role
        ));
    }

    private void validateEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNumber(String value) {
        return value.replaceAll("[^0-9]", "");
    }

    private EmergencyJurisdictionResponse resolveJurisdiction(
            String postcode, String address, String addressDetail, String region3DepthName) {
        return emergencyJurisdictionService.resolve(
                new EmergencyJurisdictionResolveRequest(postcode, address, addressDetail, region3DepthName));
    }

    private AgeGroup toAgeGroup(String value) {
        if (value.contains("60") || value.contains("70") || value.contains("80") || value.contains("90")) {
            return AgeGroup.ELDERLY;
        }
        if (value.contains("영유아")) return AgeGroup.INFANT;
        if (value.contains("아동")) return AgeGroup.CHILD;
        if (value.contains("청소년")) return AgeGroup.TEENAGER;
        if (value.contains("성인") || value.contains("20") || value.contains("30")
                || value.contains("40") || value.contains("50")) return AgeGroup.ADULT;
        try {
            return AgeGroup.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.COMMON_INVALID_INPUT);
        }
    }
}
