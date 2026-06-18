# Current Auth API Contract

CI/CD 배포 전 로그인, 회원가입, 비밀번호 재설정, 중복확인 연동은 이 문서를 기준으로 확인한다.

## Auth APIs

| Method | URL | Description | Request | Response |
|---|---|---|---|---|
| POST | `/api/auth/signup/individual` | 개인 회원가입 | `IndividualSignupRequest` | `SignupResponse` |
| POST | `/api/auth/signup/corporate` | 기업 회원가입 | `CorporateSignupRequest` | `SignupResponse` |
| POST | `/api/auth/login` | 로그인 및 Access Token 발급 | `LoginRequest` | `TokenResponse` + `Set-Cookie: REFRESH_TOKEN=...` |
| POST | `/api/auth/reissue` | Refresh Cookie 기반 Access Token 재발급 | 없음 | `TokenResponse` + `Set-Cookie: REFRESH_TOKEN=...` |
| POST | `/api/auth/logout` | Refresh Cookie 기반 로그아웃 | 없음 | `REFRESH_TOKEN` 만료 쿠키 |
| POST | `/api/auth/verifications/sms` | 회원가입 SMS 인증번호 발송 | `SmsVerificationRequest` | `SmsVerificationResponse` |
| POST | `/api/auth/verifications/sms/confirm` | 회원가입 SMS 인증 확인 | `SmsVerificationConfirmRequest` | `SmsVerificationConfirmResponse` |
| POST | `/api/auth/password-reset/verifications/sms` | 비밀번호 재설정 SMS 요청 접수 | `PasswordResetSmsRequest` | `PasswordResetSmsResponse` |
| POST | `/api/auth/password-reset/verifications/sms/confirm` | 비밀번호 재설정 SMS 확인 | `PasswordResetSmsConfirmRequest` | `SmsVerificationConfirmResponse` |
| POST | `/api/auth/password-reset` | 비밀번호 재설정 실행 | `PasswordResetRequest` | N/A |
| POST | `/api/auth/email-availability` | 이메일 중복 확인 | `{ "email": "user@example.com" }` | `AvailabilityResponse` |
| POST | `/api/companies/business-number-availability` | 사업자번호 중복 확인 | `{ "businessNumber": "1234567890" }` | `AvailabilityResponse` |

## Token Contract

- `TokenResponse`에는 `refreshToken`이 포함되지 않는다.
- Refresh Token은 `HttpOnly` 쿠키 `REFRESH_TOKEN`으로만 전달한다.
- `/api/auth/reissue`, `/api/auth/logout`은 request body를 받지 않고 `REFRESH_TOKEN` 쿠키를 사용한다.
- 프론트는 cookie 송수신을 위해 `credentials: "include"`를 설정해야 한다.

## Corporate Signup Contract

- `company.businessNumber`는 선택값이다.
- 사업자번호가 없으면 중복검사를 하지 않고 `CompanyProfile.businessRegistrationNumber`에 `null`로 저장한다.
- 사업자번호가 있으면 숫자만 정규화한 뒤 중복검사를 수행한다.
- 사업자번호 중복확인 API는 사용자가 값을 입력한 경우에만 호출한다.
- `company.industry`는 문자열로 저장되며, 프론트는 `공공시설` 항목을 전달할 수 있다.

## Deployment Notes

- 운영에서는 `REFRESH_TOKEN_COOKIE_SECURE=true`를 사용한다.
- 같은 사이트 배포 기준 기본 SameSite 값은 `Lax`를 사용한다.
- 운영 DB에 `company_profiles.business_registration_number NOT NULL` 제약이 남아 있으면 nullable 변경 마이그레이션이 필요하다.
