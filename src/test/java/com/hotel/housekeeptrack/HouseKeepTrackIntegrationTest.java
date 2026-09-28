package com.hotel.housekeeptrack;

import com.hotel.housekeeptrack.dto.*;
import com.hotel.housekeeptrack.exception.BusinessRuleException;
import com.hotel.housekeeptrack.exception.InvalidRoomStateException;
import com.hotel.housekeeptrack.model.InspectionResult;
import com.hotel.housekeeptrack.model.RoomStatus;
import com.hotel.housekeeptrack.model.TaskPriority;
import com.hotel.housekeeptrack.model.TaskStatus;
import com.hotel.housekeeptrack.presenter.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HouseKeepTrackIntegrationTest {

    @Autowired
    private RoomPresenter roomPresenter;

    @Autowired
    private HousekeeperPresenter housekeeperPresenter;

    @Autowired
    private CleaningTaskPresenter cleaningTaskPresenter;

    @Autowired
    private InspectionPresenter inspectionPresenter;

    @Autowired
    private MetricsPresenter metricsPresenter;

    @Autowired
    private AuditLogPresenter auditLogPresenter;

    @Test
    @DisplayName("End-to-End Hotel Lifecycle in MVP: Create -> Check-In -> Checkout -> Auto-Assign -> Complete -> Inspect (PASSED) -> Ready -> Check-In")
    void testFullRoomLifecycle() {
        // 1. Create Room 501
        CreateRoomRequest roomReq = new CreateRoomRequest("501", "DELUXE");
        ResponseEntity<RoomResponse> roomRes = roomPresenter.presentCreatedRoom(roomReq);
        assertEquals(HttpStatus.CREATED, roomRes.getStatusCode());
        assertNotNull(roomRes.getBody());
        long roomId = roomRes.getBody().getId();
        assertEquals(RoomStatus.READY, roomRes.getBody().getStatus());

        // 2. Guest Check-In into READY room
        ResponseEntity<RoomResponse> checkInRes = roomPresenter.presentCheckedInRoom(roomId);
        assertEquals(HttpStatus.OK, checkInRes.getStatusCode());
        assertEquals(RoomStatus.OCCUPIED, checkInRes.getBody().getStatus());

        // 3. Register Housekeeper Alice
        CreateHousekeeperRequest hkReq = new CreateHousekeeperRequest("Alice Walker", "alice.w@hotel.com", "555-1111");
        ResponseEntity<HousekeeperResponse> hkRes = housekeeperPresenter.presentCreatedHousekeeper(hkReq);
        assertEquals(HttpStatus.CREATED, hkRes.getStatusCode());
        long housekeeperId = hkRes.getBody().getId();

        // 4. Guest Check-Out (Room transitions OCCUPIED -> DIRTY, auto-generates cleaning task & auto-assigns to available Alice)
        ResponseEntity<RoomResponse> checkOutRes = roomPresenter.presentCheckedOutRoom(roomId);
        assertEquals(HttpStatus.OK, checkOutRes.getStatusCode());
        // Since Alice was AVAILABLE, task was immediately assigned and room transitioned to IN_CLEANING!
        assertEquals(RoomStatus.IN_CLEANING, checkOutRes.getBody().getStatus());

        // Verify Cleaning Task was auto-assigned
        ResponseEntity<List<CleaningTaskResponse>> tasksRes = cleaningTaskPresenter.presentTasks(roomId, null);
        assertEquals(HttpStatus.OK, tasksRes.getStatusCode());
        assertEquals(1, tasksRes.getBody().size());
        CleaningTaskResponse task = tasksRes.getBody().get(0);
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(housekeeperId, task.getHousekeeperId());
        long taskId = task.getId();

        // 5. Housekeeper completes cleaning task
        ResponseEntity<CleaningTaskResponse> completeRes = cleaningTaskPresenter.presentCompletedTask(taskId);
        assertEquals(HttpStatus.OK, completeRes.getStatusCode());
        assertEquals(TaskStatus.COMPLETED, completeRes.getBody().getStatus());

        // Verify Room is now CLEANED
        ResponseEntity<RoomResponse> cleanedRoomRes = roomPresenter.presentRoomById(roomId);
        assertEquals(RoomStatus.CLEANED, cleanedRoomRes.getBody().getStatus());

        // 6. Supervisor inspects room with PASSED result -> Room transitions to INSPECTED
        CreateInspectionRequest inspectReq = new CreateInspectionRequest("Supervisor David", InspectionResult.PASSED, null);
        ResponseEntity<InspectionResponse> inspectRes = inspectionPresenter.presentInspection(roomId, inspectReq);
        assertEquals(HttpStatus.CREATED, inspectRes.getStatusCode());
        assertEquals(InspectionResult.PASSED, inspectRes.getBody().getResult());

        ResponseEntity<RoomResponse> inspectedRoomRes = roomPresenter.presentRoomById(roomId);
        assertEquals(RoomStatus.INSPECTED, inspectedRoomRes.getBody().getStatus());

        // 7. Supervisor marks room as READY
        ResponseEntity<RoomResponse> readyRes = roomPresenter.presentReadyRoom(roomId);
        assertEquals(HttpStatus.OK, readyRes.getStatusCode());
        assertEquals(RoomStatus.READY, readyRes.getBody().getStatus());
        assertNotNull(readyRes.getBody().getReadyAt());

        // 8. Guest Check-In again succeeds!
        ResponseEntity<RoomResponse> reOccupiedRes = roomPresenter.presentCheckedInRoom(roomId);
        assertEquals(HttpStatus.OK, reOccupiedRes.getStatusCode());
        assertEquals(RoomStatus.OCCUPIED, reOccupiedRes.getBody().getStatus());
    }

    @Test
    @DisplayName("Allocation Barrier: Cannot check-in to a room unless it is strictly READY")
    void testAllocationBarrierEnforcement() {
        // Create Room 502
        CreateRoomRequest roomReq = new CreateRoomRequest("502", "STANDARD");
        ResponseEntity<RoomResponse> roomRes = roomPresenter.presentCreatedRoom(roomReq);
        long roomId = roomRes.getBody().getId();

        // 1. Initial check-in (READY -> OCCUPIED)
        roomPresenter.presentCheckedInRoom(roomId);

        // 2. Attempt check-in while OCCUPIED -> Throws InvalidRoomStateException
        InvalidRoomStateException ex1 = assertThrows(InvalidRoomStateException.class, () -> roomPresenter.presentCheckedInRoom(roomId));
        assertTrue(ex1.getMessage().contains("strictly READY"));

        // 3. Checkout (OCCUPIED -> DIRTY / IN_CLEANING)
        roomPresenter.presentCheckedOutRoom(roomId);

        // 4. Attempt check-in while room is not READY -> Throws InvalidRoomStateException
        InvalidRoomStateException ex2 = assertThrows(InvalidRoomStateException.class, () -> roomPresenter.presentCheckedInRoom(roomId));
        assertTrue(ex2.getMessage().contains("strictly READY"));
    }

    @Test
    @DisplayName("Dual-Path Inspection: FAILED inspection reverts room to DIRTY, generates HIGH-priority task")
    void testFailedInspectionLoop() {
        // Register an available housekeeper to process cleaning tasks
        CreateHousekeeperRequest hkReq = new CreateHousekeeperRequest("Inspector Tester", "inspector.tester@hotel.com", "555-8888");
        housekeeperPresenter.presentCreatedHousekeeper(hkReq);

        // Create Room 503
        CreateRoomRequest roomReq = new CreateRoomRequest("503", "SUITE");
        long roomId = roomPresenter.presentCreatedRoom(roomReq).getBody().getId();

        // Check-in and checkout to initiate cleaning cycle
        roomPresenter.presentCheckedInRoom(roomId);
        roomPresenter.presentCheckedOutRoom(roomId);

        // Complete cleaning task so room reaches CLEANED
        List<CleaningTaskResponse> tasks = cleaningTaskPresenter.presentTasks(roomId, null).getBody();
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        CleaningTaskResponse initialTask = tasks.get(0);
        assertEquals(TaskStatus.IN_PROGRESS, initialTask.getStatus());
        cleaningTaskPresenter.presentCompletedTask(initialTask.getId());

        // Supervisor inspects and FAILS the room
        CreateInspectionRequest failReq = new CreateInspectionRequest("Supervisor Karen", InspectionResult.FAILED, "Bed sheets not changed, stains found");
        ResponseEntity<InspectionResponse> inspectRes = inspectionPresenter.presentInspection(roomId, failReq);
        assertEquals(HttpStatus.CREATED, inspectRes.getStatusCode());
        assertEquals(InspectionResult.FAILED, inspectRes.getBody().getResult());

        // Room status reverts to DIRTY (or IN_CLEANING if an idle housekeeper is present)
        RoomResponse roomAfterFail = roomPresenter.presentRoomById(roomId).getBody();
        assertNotNull(roomAfterFail);
        assertTrue(roomAfterFail.getStatus() == RoomStatus.DIRTY || roomAfterFail.getStatus() == RoomStatus.IN_CLEANING);
        assertEquals(InspectionResult.FAILED, roomAfterFail.getLastInspectionResult());

        // A new HIGH priority re-cleaning task must exist
        List<CleaningTaskResponse> allTasks = cleaningTaskPresenter.presentTasks(roomId, null).getBody();
        assertNotNull(allTasks);
        assertTrue(allTasks.size() >= 2);
        CleaningTaskResponse latestTask = allTasks.get(allTasks.size() - 1);
        assertEquals(TaskPriority.HIGH, latestTask.getPriority());
        assertTrue(latestTask.getNotes().contains("Bed sheets not changed"));
    }

    @Test
    @DisplayName("Metrics Endpoints: Workloads, Room Turnaround, System Summary")
    void testMetricsReporting() {
        ResponseEntity<List<HousekeeperWorkloadDto>> workloads = metricsPresenter.presentHousekeeperWorkloads();
        assertEquals(HttpStatus.OK, workloads.getStatusCode());
        assertNotNull(workloads.getBody());

        ResponseEntity<List<RoomTurnaroundDto>> turnarounds = metricsPresenter.presentRoomTurnarounds();
        assertEquals(HttpStatus.OK, turnarounds.getStatusCode());
        assertNotNull(turnarounds.getBody());

        ResponseEntity<SystemSummaryDto> summary = metricsPresenter.presentSummary();
        assertEquals(HttpStatus.OK, summary.getStatusCode());
        assertNotNull(summary.getBody());
        assertTrue(summary.getBody().getTotalRooms() >= 0);
    }

    @Test
    @DisplayName("Dual-Path from INSPECTED: Supervisor sends INSPECTED room back to cleaning via send-to-cleaning")
    void testSendInspectedRoomBackToCleaning() {
        // Register available housekeeper
        CreateHousekeeperRequest hkReq = new CreateHousekeeperRequest("Staff Charlie", "charlie@hotel.com", "555-4444");
        housekeeperPresenter.presentCreatedHousekeeper(hkReq);

        // Create Room 504
        CreateRoomRequest roomReq = new CreateRoomRequest("504", "DELUXE");
        long roomId = roomPresenter.presentCreatedRoom(roomReq).getBody().getId();

        // Lifecycle: Check-in -> Checkout -> Complete cleaning -> Inspect (PASSED)
        roomPresenter.presentCheckedInRoom(roomId);
        roomPresenter.presentCheckedOutRoom(roomId);

        CleaningTaskResponse task = cleaningTaskPresenter.presentTasks(roomId, null).getBody().get(0);
        cleaningTaskPresenter.presentCompletedTask(task.getId());

        CreateInspectionRequest passReq = new CreateInspectionRequest("Supervisor Dan", InspectionResult.PASSED, null);
        inspectionPresenter.presentInspection(roomId, passReq);

        RoomResponse inspectedRoom = roomPresenter.presentRoomById(roomId).getBody();
        assertEquals(RoomStatus.INSPECTED, inspectedRoom.getStatus());

        // Supervisor notices defect before marking ready and sends back to cleaning
        ResponseEntity<RoomResponse> recleanRes = roomPresenter.presentSentBackToCleaning(roomId, "Found broken light fixture", "Supervisor Dan");
        assertEquals(HttpStatus.OK, recleanRes.getStatusCode());

        // Room reverts to DIRTY (or IN_CLEANING if staff was free)
        RoomResponse roomAfterReclean = roomPresenter.presentRoomById(roomId).getBody();
        assertTrue(roomAfterReclean.getStatus() == RoomStatus.DIRTY || roomAfterReclean.getStatus() == RoomStatus.IN_CLEANING);

        // Verify HIGH-priority cleaning task created
        List<CleaningTaskResponse> tasks = cleaningTaskPresenter.presentTasks(roomId, null).getBody();
        CleaningTaskResponse latestTask = tasks.get(tasks.size() - 1);
        assertEquals(TaskPriority.HIGH, latestTask.getPriority());
        assertTrue(latestTask.getNotes().contains("broken light fixture"));
    }

    @Test
    @DisplayName("Staff Availability: When housekeeper goes OFFLINE, active task is re-queued to PENDING and dispatched to next available staff")
    void testHousekeeperGoesOfflineWhileCleaningRequeuesTask() {
        // Register Housekeeper 1 (will go offline)
        CreateHousekeeperRequest hk1Req = new CreateHousekeeperRequest("Worker John", "john.w@hotel.com", "555-5555");
        long hk1Id = housekeeperPresenter.presentCreatedHousekeeper(hk1Req).getBody().getId();

        // Create Room 505 and start cleaning with Housekeeper 1
        CreateRoomRequest roomReq = new CreateRoomRequest("505", "EXECUTIVE");
        long roomId = roomPresenter.presentCreatedRoom(roomReq).getBody().getId();
        roomPresenter.presentCheckedInRoom(roomId);
        roomPresenter.presentCheckedOutRoom(roomId);

        // Verify Housekeeper 1 is BUSY with task on Room 505
        HousekeeperResponse hk1 = housekeeperPresenter.presentHousekeeperById(hk1Id).getBody();
        assertEquals(com.hotel.housekeeptrack.model.HousekeeperStatus.BUSY, hk1.getStatus());
        assertEquals(1, hk1.getActiveTaskCount());

        // Register Housekeeper 2 (stands by as AVAILABLE)
        CreateHousekeeperRequest hk2Req = new CreateHousekeeperRequest("Worker Emma", "emma.w@hotel.com", "555-6666");
        long hk2Id = housekeeperPresenter.presentCreatedHousekeeper(hk2Req).getBody().getId();

        // Housekeeper 1 goes OFFLINE (e.g. shift ends early / emergency)
        java.util.Map<String, String> statusPayload = java.util.Map.of("status", "OFFLINE");
        housekeeperPresenter.presentUpdatedStatus(hk1Id, statusPayload);

        // Verify Housekeeper 1 is now OFFLINE with 0 active tasks
        HousekeeperResponse hk1After = housekeeperPresenter.presentHousekeeperById(hk1Id).getBody();
        assertEquals(com.hotel.housekeeptrack.model.HousekeeperStatus.OFFLINE, hk1After.getStatus());
        assertEquals(0, hk1After.getActiveTaskCount());

        // Verify task was automatically reassigned to Housekeeper 2 who was AVAILABLE!
        List<CleaningTaskResponse> tasks = cleaningTaskPresenter.presentTasks(roomId, null).getBody();
        assertFalse(tasks.isEmpty());
        CleaningTaskResponse task = tasks.get(0);
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(hk2Id, task.getHousekeeperId());

        // Housekeeper 2 is now BUSY
        HousekeeperResponse hk2 = housekeeperPresenter.presentHousekeeperById(hk2Id).getBody();
        assertEquals(com.hotel.housekeeptrack.model.HousekeeperStatus.BUSY, hk2.getStatus());
    }

    @Test
    @DisplayName("Audit Logging & Download: Tracks operational lifecycle events and exports in MD and TXT")
    void testAuditLoggingAndDownloadWorkflow() {
        // 1. Create room, register housekeeper, check-in, checkout
        CreateRoomRequest roomReq = new CreateRoomRequest("506", "SUITE");
        long roomId = roomPresenter.presentCreatedRoom(roomReq).getBody().getId();

        CreateHousekeeperRequest hkReq = new CreateHousekeeperRequest("Audited Staff", "audit.staff@hotel.com", "555-9999");
        long hkId = housekeeperPresenter.presentCreatedHousekeeper(hkReq).getBody().getId();

        roomPresenter.presentCheckedInRoom(roomId);
        roomPresenter.presentCheckedOutRoom(roomId);

        // 2. Fetch Paginated Audit Logs
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        ResponseEntity<org.springframework.data.domain.Page<AuditLogResponse>> logRes = auditLogPresenter.presentAuditLogs(pageable);
        assertEquals(HttpStatus.OK, logRes.getStatusCode());
        assertNotNull(logRes.getBody());
        org.springframework.data.domain.Page<AuditLogResponse> page = logRes.getBody();
        assertTrue(page.getTotalElements() >= 4);

        List<AuditLogResponse> logs = page.getContent();
        boolean hasRoomCreated = logs.stream().anyMatch(l -> l.getAction() == com.hotel.housekeeptrack.model.AuditAction.ROOM_CREATED);
        boolean hasStaffReg = logs.stream().anyMatch(l -> l.getAction() == com.hotel.housekeeptrack.model.AuditAction.STAFF_REGISTERED);
        boolean hasCheckIn = logs.stream().anyMatch(l -> l.getAction() == com.hotel.housekeeptrack.model.AuditAction.CHECK_IN);
        boolean hasCheckout = logs.stream().anyMatch(l -> l.getAction() == com.hotel.housekeeptrack.model.AuditAction.CHECKOUT);

        assertTrue(hasRoomCreated, "Audit log should contain ROOM_CREATED");
        assertTrue(hasStaffReg, "Audit log should contain STAFF_REGISTERED");
        assertTrue(hasCheckIn, "Audit log should contain CHECK_IN");
        assertTrue(hasCheckout, "Audit log should contain CHECKOUT");

        // 3. Test Markdown Download
        ResponseEntity<byte[]> mdRes = auditLogPresenter.presentDownload("md");
        assertEquals(HttpStatus.OK, mdRes.getStatusCode());
        assertTrue(mdRes.getHeaders().getContentType().toString().contains("text/markdown"));
        String mdFilename = mdRes.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(mdFilename);
        assertTrue(mdFilename.contains("housekeeptrack-audit-log-"));
        assertTrue(mdFilename.endsWith(".md\""));
        String mdBody = new String(mdRes.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(mdBody.contains("# HouseKeepTrack - Operational Audit Log Report"));

        // 4. Test Plain Text Download
        ResponseEntity<byte[]> txtRes = auditLogPresenter.presentDownload("txt");
        assertEquals(HttpStatus.OK, txtRes.getStatusCode());
        assertTrue(txtRes.getHeaders().getContentType().toString().contains("text/plain"));
        String txtFilename = txtRes.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(txtFilename);
        assertTrue(txtFilename.endsWith(".txt\""));
        String txtBody = new String(txtRes.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(txtBody.contains("HOUSEKEEPTRACK - OPERATIONAL AUDIT TRAIL LOG REPORT"));
    }

    @Test
    @DisplayName("Mistake Handling: Single action revert restores previous entity states and logs REVERTED")
    void testRevertLastActionLifecycle() {
        // 1. Create Room 601
        CreateRoomRequest roomReq = new CreateRoomRequest("601", "DELUXE");
        long roomId = roomPresenter.presentCreatedRoom(roomReq).getBody().getId();
        assertEquals(RoomStatus.READY, roomPresenter.presentRoomById(roomId).getBody().getStatus());

        // 2. Check-in Room 601 -> OCCUPIED
        roomPresenter.presentCheckedInRoom(roomId);
        assertEquals(RoomStatus.OCCUPIED, roomPresenter.presentRoomById(roomId).getBody().getStatus());

        // 3. Revert last action (CHECK_IN) -> Restores to READY
        ResponseEntity<RevertActionResponse> revertRes1 = auditLogPresenter.presentRevertLastAction();
        assertEquals(HttpStatus.OK, revertRes1.getStatusCode());
        assertEquals(com.hotel.housekeeptrack.model.AuditAction.CHECK_IN, revertRes1.getBody().getRevertedAction());
        assertEquals(RoomStatus.READY, roomPresenter.presentRoomById(roomId).getBody().getStatus());

        // 4. Check-in again -> OCCUPIED, then Checkout -> DIRTY
        roomPresenter.presentCheckedInRoom(roomId);
        roomPresenter.presentCheckedOutRoom(roomId);
        RoomResponse roomAfterCheckout = roomPresenter.presentRoomById(roomId).getBody();
        assertTrue(roomAfterCheckout.getStatus() == RoomStatus.DIRTY || roomAfterCheckout.getStatus() == RoomStatus.IN_CLEANING);

        // 5. Revert last action (CHECKOUT) -> Restores to OCCUPIED and cancels cleaning task
        ResponseEntity<RevertActionResponse> revertRes2 = auditLogPresenter.presentRevertLastAction();
        assertEquals(HttpStatus.OK, revertRes2.getStatusCode());
        assertEquals(com.hotel.housekeeptrack.model.AuditAction.CHECKOUT, revertRes2.getBody().getRevertedAction());
        assertEquals(RoomStatus.OCCUPIED, roomPresenter.presentRoomById(roomId).getBody().getStatus());

        // Verify Audit Log records REVERTED entries
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        List<AuditLogResponse> logs = auditLogPresenter.presentAuditLogs(pageable).getBody().getContent();
        long revertCount = logs.stream().filter(l -> l.getAction() == com.hotel.housekeeptrack.model.AuditAction.REVERTED).count();
        assertTrue(revertCount >= 2, "Audit log must contain REVERTED records");
    }

    @Test
    @DisplayName("Delete Room: Safely deletes room, cascades cleaning tasks and inspections, logs ROOM_DELETED")
    void testDeleteRoomLifecycle() {
        // 1. Create Room 602
        CreateRoomRequest roomReq = new CreateRoomRequest("602", "STANDARD");
        long roomId = roomPresenter.presentCreatedRoom(roomReq).getBody().getId();

        // 2. Delete Room 602
        ResponseEntity<Void> deleteRes = roomPresenter.presentDeletedRoom(roomId);
        assertEquals(HttpStatus.NO_CONTENT, deleteRes.getStatusCode());

        // 3. Fetching deleted room throws ResourceNotFoundException
        assertThrows(com.hotel.housekeeptrack.exception.ResourceNotFoundException.class, () -> roomPresenter.presentRoomById(roomId));

        // 4. Audit Log records ROOM_DELETED
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        List<AuditLogResponse> logs = auditLogPresenter.presentAuditLogs(pageable).getBody().getContent();
        boolean hasRoomDeleted = logs.stream().anyMatch(l -> l.getAction() == com.hotel.housekeeptrack.model.AuditAction.ROOM_DELETED && l.getEntityId().equals(roomId));
        assertTrue(hasRoomDeleted, "Audit log should contain ROOM_DELETED");
    }

    @Test
    @DisplayName("Delete Housekeeper: Safely deletes worker, re-queues active tasks to PENDING, logs STAFF_DELETED")
    void testDeleteHousekeeperLifecycle() {
        // 1. Register Housekeeper Dave
        CreateHousekeeperRequest hkReq = new CreateHousekeeperRequest("Dave Worker", "dave.worker@hotel.com", "555-7777");
        long hkId = housekeeperPresenter.presentCreatedHousekeeper(hkReq).getBody().getId();

        // 2. Create room 603, check-in and checkout to start cleaning with Dave
        CreateRoomRequest roomReq = new CreateRoomRequest("603", "SUITE");
        long roomId = roomPresenter.presentCreatedRoom(roomReq).getBody().getId();
        roomPresenter.presentCheckedInRoom(roomId);
        roomPresenter.presentCheckedOutRoom(roomId);

        // 3. Delete Housekeeper Dave
        ResponseEntity<Void> deleteRes = housekeeperPresenter.presentDeletedHousekeeper(hkId);
        assertEquals(HttpStatus.NO_CONTENT, deleteRes.getStatusCode());

        // 4. Dave no longer exists
        assertThrows(com.hotel.housekeeptrack.exception.ResourceNotFoundException.class, () -> housekeeperPresenter.presentHousekeeperById(hkId));

        // 5. Room 603 is restored to DIRTY and task is re-queued to PENDING
        RoomResponse room = roomPresenter.presentRoomById(roomId).getBody();
        assertEquals(RoomStatus.DIRTY, room.getStatus());

        List<CleaningTaskResponse> tasks = cleaningTaskPresenter.presentTasks(roomId, null).getBody();
        assertFalse(tasks.isEmpty());
        assertEquals(TaskStatus.PENDING, tasks.get(0).getStatus());
        assertNull(tasks.get(0).getHousekeeperId());

        // 6. Audit Log records STAFF_DELETED
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        List<AuditLogResponse> logs = auditLogPresenter.presentAuditLogs(pageable).getBody().getContent();
        boolean hasStaffDeleted = logs.stream().anyMatch(l -> l.getAction() == com.hotel.housekeeptrack.model.AuditAction.STAFF_DELETED && l.getEntityId().equals(hkId));
        assertTrue(hasStaffDeleted, "Audit log should contain STAFF_DELETED");
    }

    @Test
    @DisplayName("Delete Audit Log: Safely deletes individual audit log record")
    void testDeleteAuditLogLifecycle() {
        // Create Room 604 to produce an audit log entry
        CreateRoomRequest roomReq = new CreateRoomRequest("604", "STANDARD");
        roomPresenter.presentCreatedRoom(roomReq);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        List<AuditLogResponse> logsBefore = auditLogPresenter.presentAuditLogs(pageable).getBody().getContent();
        assertFalse(logsBefore.isEmpty());
        long logIdToDelete = logsBefore.get(0).getId();

        ResponseEntity<Void> deleteRes = auditLogPresenter.presentDeletedAuditLog(logIdToDelete);
        assertEquals(HttpStatus.NO_CONTENT, deleteRes.getStatusCode());

        // Verify log is deleted
        List<AuditLogResponse> logsAfter = auditLogPresenter.presentAuditLogs(pageable).getBody().getContent();
        boolean stillPresent = logsAfter.stream().anyMatch(l -> l.getId().equals(logIdToDelete));
        assertFalse(stillPresent, "Audit log record should have been deleted");
    }

    @Test
    @DisplayName("Mistake Handling: Reverting send-to-cleaning restores room back to INSPECTED and cancels re-cleaning task")
    void testRevertSendToCleaningRestoresInspectedStatus() {
        // 1. Create Room 605 and Housekeeper, then advance to INSPECTED
        CreateRoomRequest roomReq = new CreateRoomRequest("605", "DELUXE");
        long roomId = roomPresenter.presentCreatedRoom(roomReq).getBody().getId();
        housekeeperPresenter.presentCreatedHousekeeper(new CreateHousekeeperRequest("Staff Danica", "danica@hotel.com", "555-9988"));
        roomPresenter.presentCheckedInRoom(roomId);
        roomPresenter.presentCheckedOutRoom(roomId);

        // Complete cleaning
        CleaningTaskResponse task = cleaningTaskPresenter.presentTasks(roomId, null).getBody().get(0);
        cleaningTaskPresenter.presentCompletedTask(task.getId());

        // Pass inspection -> INSPECTED
        CreateInspectionRequest passReq = new CreateInspectionRequest("Supervisor Dan", InspectionResult.PASSED, null);
        inspectionPresenter.presentInspection(roomId, passReq);
        assertEquals(RoomStatus.INSPECTED, roomPresenter.presentRoomById(roomId).getBody().getStatus());

        // 2. Supervisor sends room back to cleaning by mistake
        roomPresenter.presentSentBackToCleaning(roomId, "Found broken bulb", "Supervisor Dan");
        RoomResponse roomAfterSend = roomPresenter.presentRoomById(roomId).getBody();
        assertTrue(roomAfterSend.getStatus() == RoomStatus.DIRTY || roomAfterSend.getStatus() == RoomStatus.IN_CLEANING);

        // 3. Revert last action
        ResponseEntity<RevertActionResponse> revertRes = auditLogPresenter.presentRevertLastAction();
        assertEquals(HttpStatus.OK, revertRes.getStatusCode());
        assertEquals(com.hotel.housekeeptrack.model.AuditAction.SEND_TO_CLEANING, revertRes.getBody().getRevertedAction());

        // 4. Room status is restored to INSPECTED with PASSED inspection result
        RoomResponse restoredRoom = roomPresenter.presentRoomById(roomId).getBody();
        assertEquals(RoomStatus.INSPECTED, restoredRoom.getStatus());
        assertEquals(InspectionResult.PASSED, restoredRoom.getLastInspectionResult());

        // 5. Active cleaning task is cancelled
        List<CleaningTaskResponse> tasks = cleaningTaskPresenter.presentTasks(roomId, null).getBody();
        CleaningTaskResponse recleaningTask = tasks.get(tasks.size() - 1);
        assertEquals(TaskStatus.CANCELLED, recleaningTask.getStatus());
    }

    @Test
    @DisplayName("Self-Healing Revert: Reverting skips deleted room and rolls back surviving room")
    void testDeleteRoomSkipsInRevertToHealEarlierActions() {
        // 1. Create Room 606 and Room 607
        CreateRoomRequest room606Req = new CreateRoomRequest("606", "STANDARD");
        long r606Id = roomPresenter.presentCreatedRoom(room606Req).getBody().getId();

        CreateRoomRequest room607Req = new CreateRoomRequest("607", "STANDARD");
        long r607Id = roomPresenter.presentCreatedRoom(room607Req).getBody().getId();

        // 2. Check in 606, then check in 607
        roomPresenter.presentCheckedInRoom(r606Id);
        roomPresenter.presentCheckedInRoom(r607Id);
        assertEquals(RoomStatus.OCCUPIED, roomPresenter.presentRoomById(r606Id).getBody().getStatus());
        assertEquals(RoomStatus.OCCUPIED, roomPresenter.presentRoomById(r607Id).getBody().getStatus());

        // 3. Delete Room 607 (its check-in action is now orphaned)
        roomPresenter.presentDeletedRoom(r607Id);

        // 4. Revert last action: skips deleted 607, reverts 606 back to READY!
        ResponseEntity<RevertActionResponse> revertRes = auditLogPresenter.presentRevertLastAction();
        assertEquals(HttpStatus.OK, revertRes.getStatusCode());
        assertEquals(com.hotel.housekeeptrack.model.AuditAction.CHECK_IN, revertRes.getBody().getRevertedAction());
        assertEquals(r606Id, revertRes.getBody().getTargetEntityId());
        assertEquals(RoomStatus.READY, roomPresenter.presentRoomById(r606Id).getBody().getStatus());
    }
}
