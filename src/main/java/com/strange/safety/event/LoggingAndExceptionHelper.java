package com.strange.safety.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
/**
 * <h1>스마트 안전 관제 시스템 예외 처리 및 다차원 로깅 유틸리티</h1>
 * <p>
 * 본 헬퍼 클래스는 설계 메커니즘 가이드라인에 따라 아래 로깅 레벨 규칙을 철저히 구현합니다:
 * </p>
 * <ul>
 *   <li><b>Debug():</b> 비즈니스 오퍼레이션 시작/끝 지점 로깅, DB Connection 및 Transaction Start/Commit 시점 확인</li>
 *   <li><b>Info():</b> 비즈니스 로직 진행 단계 로깅, 입력 파라미터 및 리턴값 기록</li>
 *   <li><b>Warn():</b> 복구 가능한 예외 및 catch/finally 내 경고성 예외 로깅</li>
 *   <li><b>Error():</b> 복구 불가능한 일반 예외 및 catch/finally 내 에러 로깅</li>
 *   <li><b>FatalError():</b> 즉시 조치가 필요한 치명적 시스템 결함(MQTT 영구 끊김, 메모리 고갈 등) 감지 시 로깅 및 비상 통보</li>
 * </ul>
 */
public class LoggingAndExceptionHelper {

    private static final Logger log = LoggerFactory.getLogger(LoggingAndExceptionHelper.class);

    // ==========================================
    // 1. Debug 로깅 메커니즘
    // ==========================================

    /**
     * 비즈니스 오퍼레이션(Business Operation)의 시작과 끝을 로깅합니다.
     * @param className  메서드가 속한 클래스 명
     * @param methodName 수행 중인 메서드 명
     * @param isStart    진입(true) 또는 이탈(false) 여부
     * @param details    추가 상세 데이터 (인자 또는 상태값)
     */
    public static void debugOperation(String className, String methodName, boolean isStart, String details) {
        if (log.isDebugEnabled()) {
            String stage = isStart ? "▶ START" : "■ END";
            log.debug("[DEBUG][BIZ-OP] {} - {}.{} | Details: {}", stage, className, methodName, details);
        }
    }

    /**
     * 데이터베이스 커넥션 및 트랜잭션의 시작/커밋 시점을 로깅합니다.
     * @param action       커넥션 획득, 트랜잭션 시작, 트랜잭션 커밋 등 행위
     * @param resourceId   커넥션 해시코드 또는 트랜잭션 ID
     * @param extraContext 추가 정보
     */
    public static void debugDatabase(String action, String resourceId, String extraContext) {
        if (log.isDebugEnabled()) {
            log.debug("[DEBUG][DB-TX] Action: {} | ResourceId: {} | Context: {}", action, resourceId, extraContext);
        }
    }

    // ==========================================
    // 2. Info 로깅 메커니즘
    // ==========================================

    /**
     * 비즈니스 로직의 진행 단계 및 주요 진행 데이터를 로깅합니다.
     * @param stageName 현재 실행 단계 이름
     * @param message   진행 내용 설명
     */
    public static void infoProgress(String stageName, String message) {
        log.info("[INFO][PROGRESS] Step: [{}] - {}", stageName, message);
    }

    /**
     * 비즈니스 메서드의 입력값과 리턴값을 로깅합니다.
     * @param methodName 호출된 메서드 이름
     * @param inputs     입력 파라미터 정보 (Map 또는 String)
     * @param returnValue 리턴되는 결과값
     */
    public static void infoIO(String methodName, Object inputs, Object returnValue) {
        log.info("[INFO][IO] Method: {}() | INPUT: {} | RETURN: {}", methodName, inputs, returnValue);
    }

    // ==========================================
    // 3. Warn 로깅 메커니즘 (예외 처리 catch / finally 용)
    // ==========================================

    /**
     * 복구 가능한 catch 블록 예외를 경고 수준으로 기록합니다.
     * @param context   예외가 발생한 컨텍스트 설명
     * @param throwable 발생한 예외 객체
     */
    public static void warnException(String context, Throwable throwable) {
        log.warn("[WARN][EXCEPTION] Recoverable issue in context: [{}]. Message: {}. Auto-reconnecting or retrying...",
                context, throwable.getMessage());
    }

    /**
     * finally 블록에서 리소스 해제 중 발생하는 경고성 예외를 기록합니다.
     * @param resourceName 리소스 이름 (예: PreparedStatement, MqttClient 등)
     * @param throwable    발생한 예외 객체
     */
    public static void warnFinally(String resourceName, Throwable throwable) {
        log.warn("[WARN][FINALLY] Exception during closing resource [{}]: {}", resourceName, throwable.getMessage());
    }

    // ==========================================
    // 4. Error 로깅 메커니즘 (예외 처리 catch / finally 용)
    // ==========================================

    /**
     * 복구 불가능한 비즈니스 에러나 치명적 catch 블록 예외를 기록합니다.
     * @param context   에러가 발생한 위치 및 상황 설명
     * @param throwable 발생한 예외 객체
     */
    public static void errorException(String context, Throwable throwable) {
        log.error("[ERROR][EXCEPTION] Unrecoverable error in context: [{}]. Message: {}", context, throwable.getMessage());
        if (log.isErrorEnabled()) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            log.error("[ERROR][STACKTRACE]\n{}", sw.toString());
        }
    }

    // ==========================================
    // 5. FatalError 로깅 메커니즘 (핵심 요구사항 - 복사하여 즉시 활용 가능)
    // ==========================================

    /**
     * <h2>[CRITICAL] FatalError() - 즉시 복사하여 붙여넣을 수 있는 핵심 장애 처리 로거</h2>
     * <p>
     * 시스템 가동이 불가능하거나 관제 불능 상태(예: MQTT Broker 연결 완전 붕괴, WebSocket 포트 충돌, 엣지 AI 수신 쓰레드 강제 중단 등)가
     * 감지되었을 때 호출되어 하드웨어 정보, 가용 메모리, 쓰레드 덤프 등 런타임 진단 정보를 강제로 출력하고 대시보드 경보 및 SMS/Mail 발송을 유도합니다.
     * </p>
     *
     * @param component     장애가 발생한 시스템 컴포넌트 명 (예: MQTT_SUBSCRIBER, STOMP_BROKER)
     * @param failureReason 장애 구체적 원인 메시지
     * @param cause         원인이 된 핵심 예외 객체 (null 허용)
     */
    public static void fatalError(String component, String failureReason, Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n================================================================================\n");
        sb.append("🚨🚨🚨 [FATAL SYSTEM FAILURE DETECTED] 🚨🚨🚨\n");
        sb.append("Timestamp   : ").append(Instant.now().toString()).append("\n");
        sb.append("Component   : ").append(component).append("\n");
        sb.append("Failure     : ").append(failureReason).append("\n");
        
        // 런타임 시스템 메트릭 긴급 진단
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long allocatedMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = allocatedMemory - freeMemory;
        
        sb.append("JVM Memory  : Used ").append(usedMemory).append("MB / Allocated ").append(allocatedMemory)
          .append("MB (Max: ").append(maxMemory).append("MB)\n");
        sb.append("Active Threads: ").append(Thread.activeCount()).append("\n");
        
        if (cause != null) {
            sb.append("Cause Class : ").append(cause.getClass().getName()).append("\n");
            sb.append("Cause Msg   : ").append(cause.getMessage()).append("\n");
            
            StringWriter sw = new StringWriter();
            cause.printStackTrace(new PrintWriter(sw));
            sb.append("Stack Trace :\n").append(sw.toString());
        } else {
            sb.append("Stack Trace : No exception provided. Current Thread Stack:\n");
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
        }
        sb.append("================================================================================");

        // 1. 최상위 FATAL 에러 로그 강제 출력
        log.error(sb.toString());

        // 2. 비상 연동 통보 트리거 (SMS, E-Mail, Slack, PagerDuty 등)
        try {
            triggerEmergencyNotification(component, failureReason, cause);
        } catch (Exception ex) {
            log.error("[FATAL-NOTIFY-FAILED] Failed to dispatch emergency notification: {}", ex.getMessage());
        }
    }

    /**
     * 치명적 에러 발생 시 사내 비상 연락망(Slack, SMS, Admin Mail 등)에 즉각 전송하는 채널 API의 모크 메서드입니다.
     */
    private static void triggerEmergencyNotification(String component, String reason, Throwable cause) {
        log.info("[EMERGENCY-DISPATCH] Sending system failure alert to Admin Center...");
        // 119 출동 하달 및 관제 인프라 백업 전환 명령 송신 로직 포함 가능
        log.info("[EMERGENCY-DISPATCH] Broadcast Complete: Admin SMS & Admin Mail Dispatched successfully.");
    }
}
